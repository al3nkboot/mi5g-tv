package tv.cleanyour.core.registry

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.store.BlobStore
import tv.cleanyour.core.trust.ApkInspector
import tv.cleanyour.core.trust.DownloadHosts
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

// Downloads a module APK the signed index describes (ARCHITECTURE D12, SECURITY 2.4 S1):
//
// - a blob already in the store under ref.sha256 with ref.size is returned without a request;
// - the URL must be https on an allowed host before anything is sent (HOST_NOT_ALLOWED), and the
//   HttpFetcher checks every redirect hop again;
// - more than maxBytes is refused up front (TOO_LARGE);
// - bytes stream into a partial file while size and sha256 are computed; more bytes than ref.size,
//   or a Content-Length that disagrees, is SIZE_MISMATCH; a finished file with another digest is
//   HASH_MISMATCH; both drop the partial;
// - a cut stream keeps the partial and resumes with Range and If-Range (the stored ETag) up to
//   maxAttempts times in one call, and on the next call; a 200 instead of 206 (server ignored the
//   range, or If-Range did not match) restarts from byte 0; a 416 drops the partial and restarts;
// - with an inspector, the finished APK's signer digest must equal ref.signer (SIGNER_MISMATCH, the
//   blob is deleted; an unreadable APK counts as a mismatch). Without one, the caller runs the
//   ApkInspector itself, as the 5.11 comment says.
class ResumableArtifactFetcher(
    private val http: HttpFetcher,
    private val blobs: BlobStore,
    private val partials: PartialStore,
    private val inspector: ApkInspector? = null,
    private val urlAllowed: (String) -> Boolean = { DownloadHosts.isAllowed(it) },
    private val maxBytes: Long = 200L * 1024 * 1024,
    private val maxAttempts: Int = 3,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ArtifactFetcher {

    // One fetch per sha256 at a time. Two fetches of the same artifact would share one partial file:
    // the second could append through its open stream to a file the first had already verified and
    // renamed into a blob, so a blob would no longer have the digest its name says. The second call
    // waits and then finds the finished blob.
    override suspend fun fetch(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult = withContext(io) {
        inFlight.computeIfAbsent(ref.sha256.hex) { Mutex() }.withLock { fetchLocked(ref, onProgress) }
    }

    private suspend fun fetchLocked(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult {
        blobs.get(ref.sha256)?.takeIf { it.size == ref.size }?.let { cached ->
            onProgress(ref.size, ref.size)
            return checkSigner(ref, FetchResult.Ok(cached))
        }
        if (!urlAllowed(ref.url)) return FetchResult.Failed(FetchFailure.HOST_NOT_ALLOWED, null)
        if (ref.size > maxBytes || ref.size <= 0) return FetchResult.Failed(FetchFailure.TOO_LARGE, null)

        var last: FetchResult.Failed = FetchResult.Failed(FetchFailure.OFFLINE, null)
        repeat(maxAttempts) {
            when (val step = attempt(ref, onProgress)) {
                is Step.Done -> {
                    val blob = try { partials.commitPartial(ref.sha256, ref.size) } catch (e: IOException) {
                        partials.dropPartial(ref.sha256)
                        return FetchResult.Failed(FetchFailure.HASH_MISMATCH, null)
                    }
                    return checkSigner(ref, FetchResult.Ok(blob))
                }
                is Step.Final -> return step.result
                is Step.Retry -> last = step.result
            }
        }
        return last
    }

    private companion object {
        // Shared by every instance, so two fetchers over the same store cannot race either.
        val inFlight = ConcurrentHashMap<String, Mutex>()
    }

    private sealed interface Step {
        data object Done : Step
        data class Final(val result: FetchResult.Failed) : Step
        data class Retry(val result: FetchResult.Failed) : Step
    }

    private suspend fun attempt(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): Step {
        val sha = ref.sha256
        var have = partials.partialLength(sha)
        val etag = partials.partialEtag(sha)
        if (have > ref.size) { partials.dropPartial(sha); have = 0 }
        if (have > 0 && etag == null) { partials.dropPartial(sha); have = 0 }   // no validator, a resume could splice two files
        if (have == ref.size) return if (hashPartial(sha).second == sha.hex) Step.Done else { partials.dropPartial(sha); Step.Retry(FetchResult.Failed(FetchFailure.HASH_MISMATCH, null)) }

        val resp = try {
            if (have > 0) http.get(ref.url, etag, have) else http.get(ref.url, null, null)
        } catch (e: FetchException) {
            return Step.Final(FetchResult.Failed(e.failure, e.httpCode))
        } catch (e: IllegalArgumentException) {
            return Step.Final(FetchResult.Failed(FetchFailure.HOST_NOT_ALLOWED, null))   // a fetcher that refuses by require()
        } catch (e: IOException) {
            return Step.Retry(FetchResult.Failed(FetchFailure.OFFLINE, null))
        }

        val body = resp.body
        val digest = MessageDigest.getInstance("SHA-256")
        val from: Long
        when (resp.code) {
            206 -> {
                if (have == 0L) { body?.close(); return Step.Final(FetchResult.Failed(FetchFailure.HTTP_ERROR, 206)) }
                hashPartial(sha, digest)
                from = have
            }
            200 -> { partials.startPartial(sha, resp.etag); from = 0 }
            416 -> { body?.close(); partials.dropPartial(sha); return Step.Retry(FetchResult.Failed(FetchFailure.HTTP_ERROR, 416)) }
            else -> { body?.close(); return Step.Final(FetchResult.Failed(FetchFailure.HTTP_ERROR, resp.code)) }
        }
        if (body == null) return Step.Final(FetchResult.Failed(FetchFailure.HTTP_ERROR, resp.code))
        val expected = ref.size - from
        if (resp.length != null && resp.length != expected) {
            body.close(); partials.dropPartial(sha)
            return Step.Final(FetchResult.Failed(FetchFailure.SIZE_MISMATCH, null))
        }

        var total = from
        var overflow = false
        onProgress(total, ref.size)
        try {
            body.use { input ->
                partials.appendPartial(sha).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        if (total + n > ref.size) { overflow = true; break }
                        total += n
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        onProgress(total, ref.size)
                    }
                }
            }
        } catch (e: IOException) {
            return Step.Retry(FetchResult.Failed(FetchFailure.OFFLINE, null))   // partial kept for the next attempt
        }
        if (overflow) {
            partials.dropPartial(sha)
            return Step.Final(FetchResult.Failed(FetchFailure.SIZE_MISMATCH, null))
        }
        if (total < ref.size) return Step.Retry(FetchResult.Failed(FetchFailure.OFFLINE, null))
        if (DiskBlobStore.hex(digest.digest()) != sha.hex) {
            partials.dropPartial(sha)
            return Step.Final(FetchResult.Failed(FetchFailure.HASH_MISMATCH, null))
        }
        return Step.Done
    }

    // Feeds the partial's bytes into digest; returns its length and the digest so far (digest keeps going).
    private fun hashPartial(sha: Sha256, digest: MessageDigest = MessageDigest.getInstance("SHA-256")): Pair<Long, String> {
        var n = 0L
        partials.readPartial(sha).use { input: InputStream ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                n += r
                digest.update(buf, 0, r)
            }
        }
        return n to DiskBlobStore.hex((digest.clone() as MessageDigest).digest())
    }

    private suspend fun checkSigner(ref: ArtifactRef, ok: FetchResult.Ok): FetchResult {
        val inspector = inspector ?: return ok
        val info = inspector.inspect(ok.blob)
        if (info == null || info.signer != ref.signer || info.sha256 != ref.sha256) {
            blobs.delete(ref.sha256)
            return FetchResult.Failed(FetchFailure.SIGNER_MISMATCH, null)
        }
        return ok
    }
}
