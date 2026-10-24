package tv.cleanyour.core.registry

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.cleanyour.core.model.ApkInfo
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.testing.fixtures.FakeApkInspector
import tv.cleanyour.testing.fixtures.FakeHttp
import tv.cleanyour.testing.fixtures.sha256Of
import java.io.File
import java.io.FilterInputStream
import java.io.IOException

// FakeHttp cases from the W5 definition of done: Range, If-Range, 200 instead of 206, hash
// mismatch; plus size, host, offline, 416, signer and resume across calls.
class ArtifactFetcherTest {
    @get:Rule val tmp = TemporaryFolder()

    private val url = "https://github.com/udhaykumarbala/tv-vibehack-retro/releases/download/v0.1.0/retro-release.apk"
    private val bytes = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val signer = Sha256("0202281bb726dbc12b82622dee50a278656128a82204db3cf055f8f5c006e405")
    private val ref = ArtifactRef(url, bytes.size.toLong(), sha256Of(bytes), signer)

    private val http = FakeHttp()
    private val blobs by lazy { DiskBlobStore(File(tmp.root, "artifacts"), File(tmp.root, "bundled"), { null }) }

    private fun fetcher(h: HttpFetcher = http, attempts: Int = 3, inspector: FakeApkInspector? = null) =
        ResumableArtifactFetcher(h, blobs, blobs, inspector, maxAttempts = attempts)

    private fun route(etag: String? = "\"v1\"", ignoresRange: Boolean = false, cutAfter: Int? = null, body: ByteArray = bytes) {
        http.routes[url] = FakeHttp.Route(body, etag, ignoresRange = ignoresRange, cutAfter = cutAfter)
    }

    private fun failure(r: FetchResult) = (r as FetchResult.Failed).failure

    @Test fun downloadsVerifiesAndStores() = runBlocking {
        route()
        val progress = mutableListOf<Long>()
        val r = fetcher().fetch(ref) { done, total -> assertEquals(ref.size, total); progress += done }
        val blob = (r as FetchResult.Ok).blob
        assertEquals(ref.sha256, blob.sha256)
        assertArrayEquals(bytes, File(blob.path).readBytes())
        assertEquals(ref.size, progress.last())
        assertEquals(1, http.requests.size)
        assertNull(http.requests.single().rangeFrom)
        assertEquals(0L, blobs.partialLength(ref.sha256))
    }

    @Test fun cachedBlobNeedsNoRequest() = runBlocking {
        route()
        fetcher().fetch(ref) { _, _ -> }
        http.requests.clear()
        assertTrue(fetcher().fetch(ref) { _, _ -> } is FetchResult.Ok)
        assertTrue(http.requests.isEmpty())
    }

    @Test fun cutStreamResumesWithRangeAndIfRange() = runBlocking {
        route(cutAfter = 100_000)
        val r = fetcher().fetch(ref) { _, _ -> }
        assertTrue(r.toString(), r is FetchResult.Ok)
        assertEquals(listOf(null, 100_000L, 200_000L), http.requests.map { it.rangeFrom })
        assertEquals(listOf(null, "\"v1\"", "\"v1\""), http.requests.map { it.etag })
    }

    @Test fun resumesOnTheNextCallWhenAttemptsRunOut() = runBlocking {
        route(cutAfter = 120_000)
        val first = fetcher(attempts = 1).fetch(ref) { _, _ -> }
        assertEquals(FetchFailure.OFFLINE, failure(first))
        assertEquals(120_000L, blobs.partialLength(ref.sha256))
        route()
        assertTrue(fetcher(attempts = 1).fetch(ref) { _, _ -> } is FetchResult.Ok)
        assertEquals(120_000L, http.requests.last().rangeFrom)
    }

    @Test fun ifRangeMismatchRestartsFromZero() = runBlocking {
        route(etag = "\"v1\"", cutAfter = 50_000)
        fetcher(attempts = 1).fetch(ref) { _, _ -> }
        route(etag = "\"v2\"")                     // same bytes, new validator: FakeHttp answers 200 in full
        val r = fetcher(attempts = 1).fetch(ref) { _, _ -> }
        assertTrue(r is FetchResult.Ok)
        val last = http.requests.last()
        assertEquals(50_000L, last.rangeFrom)
        assertEquals("\"v1\"", last.etag)
    }

    @Test fun serverIgnoringRangeAnswers200AndRestarts() = runBlocking {
        route(cutAfter = 70_000)
        fetcher(attempts = 1).fetch(ref) { _, _ -> }
        route(ignoresRange = true)
        assertTrue(fetcher(attempts = 1).fetch(ref) { _, _ -> } is FetchResult.Ok)
        assertEquals(70_000L, http.requests.last().rangeFrom)
    }

    @Test fun partialWithoutValidatorIsNotSpliced() = runBlocking {
        route(etag = null, cutAfter = 40_000)
        fetcher(attempts = 1).fetch(ref) { _, _ -> }
        route(etag = null)
        assertTrue(fetcher(attempts = 1).fetch(ref) { _, _ -> } is FetchResult.Ok)
        assertNull(http.requests.last().rangeFrom)
    }

    @Test fun hashMismatchDropsEverything() = runBlocking {
        route(body = bytes.copyOf().also { it[5] = (it[5].toInt() xor 1).toByte() })
        assertEquals(FetchFailure.HASH_MISMATCH, failure(fetcher().fetch(ref) { _, _ -> }))
        assertNull(blobs.get(ref.sha256))
        assertEquals(0L, blobs.partialLength(ref.sha256))
    }

    @Test fun sizeMismatch() = runBlocking {
        route(body = bytes + ByteArray(10))
        assertEquals(FetchFailure.SIZE_MISMATCH, failure(fetcher().fetch(ref) { _, _ -> }))
        val lying = object : HttpFetcher {
            override suspend fun get(url: String, etag: String?, rangeFrom: Long?) = HttpResponse(200, null, (bytes + ByteArray(10)).inputStream(), null)
        }
        assertEquals(FetchFailure.SIZE_MISMATCH, failure(fetcher(lying).fetch(ref) { _, _ -> }))
        assertEquals(0L, blobs.partialLength(ref.sha256))
    }

    @Test fun refusedBeforeAnyRequest() = runBlocking {
        route()
        assertEquals(FetchFailure.HOST_NOT_ALLOWED, failure(fetcher().fetch(ref.copy(url = "https://evil.example/retro.apk")) { _, _ -> }))
        assertEquals(FetchFailure.HOST_NOT_ALLOWED, failure(fetcher().fetch(ref.copy(url = "http://github.com/retro.apk")) { _, _ -> }))
        assertEquals(FetchFailure.TOO_LARGE, failure(fetcher().fetch(ref.copy(size = 201L * 1024 * 1024)) { _, _ -> }))
        assertTrue(http.requests.isEmpty())
    }

    @Test fun fetcherSideHostRefusal() = runBlocking {
        val strict = FakeHttp(allowedHosts = setOf("objects.githubusercontent.com"))
        assertEquals(FetchFailure.HOST_NOT_ALLOWED, failure(fetcher(strict).fetch(ref) { _, _ -> }))
        val thrower = object : HttpFetcher {
            override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse = throw FetchException(FetchFailure.HOST_NOT_ALLOWED, null, "redirect off GitHub")
        }
        assertEquals(FetchFailure.HOST_NOT_ALLOWED, failure(fetcher(thrower).fetch(ref) { _, _ -> }))
    }

    @Test fun httpErrorsAndOffline() = runBlocking {
        assertEquals(404, (fetcher().fetch(ref) { _, _ -> } as FetchResult.Failed).httpCode)
        val offline = object : HttpFetcher { override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse = throw IOException("down") }
        assertEquals(FetchFailure.OFFLINE, failure(fetcher(offline).fetch(ref) { _, _ -> }))
    }

    @Test fun rangeNotSatisfiableRestarts() = runBlocking {
        route(cutAfter = 10_000)
        fetcher(attempts = 1).fetch(ref) { _, _ -> }
        route(body = bytes.copyOf(5_000))       // the partial now reaches past the end: 416, then a fresh 200
        val r = fetcher(attempts = 2).fetch(ref) { _, _ -> }
        assertEquals(FetchFailure.SIZE_MISMATCH, failure(r))   // the shorter file is not the one the index names
        assertEquals(listOf(10_000L, null), http.requests.takeLast(2).map { it.rangeFrom })
        assertEquals(0L, blobs.partialLength(ref.sha256))
    }

    @Test fun concurrentFetchesOfOneArtifactAreSingleFlight() = runBlocking {
        route()
        val slow = object : HttpFetcher {
            override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse {
                val r = http.get(url, etag, rangeFrom)
                val body = r.body ?: return r
                return r.copy(body = object : FilterInputStream(body) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int { Thread.sleep(20); return super.read(b, off, minOf(len, 30_000)) }
                })
            }
        }
        val results = (1..3).map { async { fetcher(slow).fetch(ref) { _, _ -> } } }.awaitAll()
        assertTrue(results.toString(), results.all { it is FetchResult.Ok })
        assertEquals(1, http.requests.size)        // the others waited and found the finished blob
        assertArrayEquals(bytes, File(blobs.get(ref.sha256)!!.path).readBytes())
        assertEquals(0L, blobs.partialLength(ref.sha256))
    }

    @Test fun signerIsCheckedWhenAnInspectorIsGiven() = runBlocking {
        route()
        fun info(s: Sha256) = ApkInfo(PackageName("io.github.udhaykumarbala.retro"), 1, "0.1.0", s, ref.sha256, ref.size, "test")
        val resigned = FakeApkInspector(mapOf(ref.sha256 to info(Sha256("e".repeat(64)))))
        assertEquals(FetchFailure.SIGNER_MISMATCH, failure(fetcher(inspector = resigned).fetch(ref) { _, _ -> }))
        assertNull(blobs.get(ref.sha256))
        assertEquals(FetchFailure.SIGNER_MISMATCH, failure(fetcher(inspector = FakeApkInspector()).fetch(ref) { _, _ -> }))
        val good = FakeApkInspector(mapOf(ref.sha256 to info(signer)))
        assertTrue(fetcher(inspector = good).fetch(ref) { _, _ -> } is FetchResult.Ok)
    }
}
