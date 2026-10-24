package tv.cleanyour.testing.fixtures

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.cleanyour.core.model.ApkInfo
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.FetchFailure
import tv.cleanyour.core.registry.FetchResult
import tv.cleanyour.core.registry.HttpFetcher
import tv.cleanyour.core.registry.HttpResponse
import tv.cleanyour.core.registry.IndexState
import tv.cleanyour.core.registry.Registry
import tv.cleanyour.core.trust.ApkInspector
import tv.cleanyour.core.trust.IndexVerdict
import tv.cleanyour.core.trust.IndexVerifier
import tv.cleanyour.core.trust.LauncherPin
import tv.cleanyour.core.trust.Minisign
import tv.cleanyour.core.trust.MinisignKey
import tv.cleanyour.core.trust.VerifyOutcome
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// HTTP by URL with the behaviours W5's fetcher must handle: ETag 304, Range 206, If-Range
// mismatch (full 200), a server that ignores Range (200 instead of 206), a stream cut short,
// unknown URL 404, and an optional host allowlist that refuses before any "network" access.
class FakeHttp(private val allowedHosts: Set<String>? = null) : HttpFetcher {
    data class Route(val bytes: ByteArray, val etag: String? = null, val code: Int = 200, val ignoresRange: Boolean = false, val cutAfter: Int? = null)
    data class Request(val url: String, val etag: String?, val rangeFrom: Long?)

    val routes = ConcurrentHashMap<String, Route>()
    val requests = CopyOnWriteArrayList<Request>()

    fun serve(url: String, bytes: ByteArray, etag: String? = null) { routes[url] = Route(bytes, etag) }

    override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse {
        requests += Request(url, etag, rangeFrom)
        allowedHosts?.let { hosts -> require(URI(url).host in hosts) { "fake http: host not allowed: ${URI(url).host}" } }
        val r = routes[url] ?: return HttpResponse(404, null, null, null)
        if (r.code != 200) return HttpResponse(r.code, r.etag, ByteArrayInputStream(ByteArray(0)), 0)
        fun body(from: Int): HttpResponse {
            val part = r.bytes.copyOfRange(from, r.bytes.size)
            val sent = r.cutAfter?.let { part.copyOf(minOf(it, part.size)) } ?: part
            return HttpResponse(if (from > 0) 206 else 200, r.etag, ByteArrayInputStream(sent), part.size.toLong())
        }
        return when {
            rangeFrom == null && etag != null && etag == r.etag -> HttpResponse(304, r.etag, null, null)
            rangeFrom == null -> body(0)
            r.ignoresRange -> body(0)
            etag != null && etag != r.etag -> body(0)
            rangeFrom >= r.bytes.size -> HttpResponse(416, r.etag, null, null)
            else -> body(rangeFrom.toInt())
        }
    }
}

// Fetches from a map of URL to bytes into a MemoryBlobStore, checking size then sha256 while
// "streaming" (the signer is the ApkInspector's job afterwards, as in 5.11).
class FakeArtifactFetcher(private val store: MemoryBlobStore, private val sources: Map<String, ByteArray> = emptyMap()) : ArtifactFetcher {
    val fetched = CopyOnWriteArrayList<ArtifactRef>()
    override suspend fun fetch(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult {
        fetched += ref
        val bytes = sources[ref.url] ?: return FetchResult.Failed(FetchFailure.HTTP_ERROR, 404)
        onProgress(bytes.size.toLong(), ref.size)
        if (bytes.size.toLong() != ref.size) return FetchResult.Failed(FetchFailure.SIZE_MISMATCH, null)
        if (sha256Of(bytes) != ref.sha256) return FetchResult.Failed(FetchFailure.HASH_MISMATCH, null)
        return FetchResult.Ok(store.add(bytes))
    }
}

class FakeRegistry(initial: IndexState = IndexState.Empty, var onRefresh: (IndexState) -> IndexState = { it }) : Registry {
    private val state = MutableStateFlow(initial)
    override val index: StateFlow<IndexState> = state.asStateFlow()
    @Volatile var refreshes = 0
        private set
    override suspend fun refresh(): IndexState { refreshes++; return onRefresh(state.value).also { state.value = it } }
    override fun module(id: String): Module? = (state.value as? IndexState.Ready)?.index?.modules?.firstOrNull { it.id == id }
}

class FakeMinisign(@Volatile var outcome: VerifyOutcome = VerifyOutcome.Ok("fake trusted comment")) : Minisign {
    val calls = CopyOnWriteArrayList<ByteArray>()
    override fun verify(data: ByteArray, signature: ByteArray, keys: List<MinisignKey>): VerifyOutcome { calls += data; return outcome }
}

class FakeIndexVerifier(@Volatile var verdict: IndexVerdict) : IndexVerifier {
    data class Call(val lastSequence: Long?, val nowMs: Long)
    val calls = CopyOnWriteArrayList<Call>()
    override fun verify(indexBytes: ByteArray, sigBytes: ByteArray, keysBytes: ByteArray, keysSig: ByteArray, lastSequence: Long?, nowMs: Long): IndexVerdict {
        calls += Call(lastSequence, nowMs)
        return verdict
    }
}

class FakeLauncherPin(private val artifact: LauncherArtifact) : LauncherPin {
    override fun bundled(): LauncherArtifact = artifact
}

// ApkInfo by blob hash; anything unknown is "not a readable APK" (null, R0: 13).
class FakeApkInspector(private val known: Map<Sha256, ApkInfo> = emptyMap()) : ApkInspector {
    override fun inspect(blob: BlobRef): ApkInfo? = known[blob.sha256]
}
