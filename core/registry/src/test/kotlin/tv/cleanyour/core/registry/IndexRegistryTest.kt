package tv.cleanyour.core.registry

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.trust.IndexFailure
import tv.cleanyour.core.trust.TrustRoots
import tv.cleanyour.testing.fixtures.FakeClock
import tv.cleanyour.testing.fixtures.FakeHttp
import tv.cleanyour.testing.fixtures.MemoryDocumentStore
import java.io.IOException

// The index state machine (SECURITY 2.4 S2, S3; PRODUCT-SPEC F9) against FakeHttp and a memory
// document store, with real minisign signatures from a test publisher.
class IndexRegistryTest {
    private val pub = Publisher()
    private val http = FakeHttp()
    private val store = MemoryDocumentStore()
    private val clock = FakeClock(wallMs = Times.NOW)
    private val src = IndexSources.under("https://github.com/example/index/releases/download/latest")

    private fun registry(seed: IndexBundle? = pub.bundle(indexSeq = 1), sources: IndexSources? = src, fetcher: HttpFetcher = http) =
        IndexRegistry(fetcher, store, clock, seed, sources, pub.verifier)

    private fun serve(index: Pair<ByteArray, ByteArray>, keys: Pair<ByteArray, ByteArray>) {
        http.serve(src.index, index.first); http.serve(src.indexSig, index.second)
        http.serve(src.keys, keys.first); http.serve(src.keysSig, keys.second)
    }

    private fun failed(s: IndexState) = s as IndexState.Failed

    @Test fun startsEmptyWithoutSeedOrCache() = runBlocking {
        val r = registry(seed = null, sources = null)
        assertEquals(IndexState.Empty, r.index.value)
        assertEquals(IndexState.Empty, r.restore())
        assertNull(r.module("retro"))
    }

    @Test fun restoreShowsTheSeed() = runBlocking {
        val r = registry()
        val s = r.restore() as IndexState.Ready
        assertEquals(1, s.index.sequence)
        assertEquals(false, s.stale)
        assertEquals(0L, s.fetchedAtMs)
        assertNotNull(r.module("retro"))
        assertNotNull(s.installableIndex(clock.nowMs()))
        assertNotNull(r.installableIndex())
        assertTrue(http.requests.isEmpty())
    }

    @Test fun expiredSeedIsShownButStale() = runBlocking {
        clock.setWall(Times.EXPIRY)
        val s = registry().restore() as IndexState.Ready
        assertTrue(s.stale)
        assertNull(s.installableIndex(clock.nowMs()))
        assertNotNull(s.shownIndex())
    }

    @Test fun aReadyIndexStopsBeingInstallableAtItsExpiryWithoutARefresh() = runBlocking {
        val r = registry()
        val s = r.restore() as IndexState.Ready
        assertEquals(false, s.stale)
        assertNotNull(r.installableIndex())
        clock.setWall(Times.EXPIRY)                // the process lives on past the expiry
        assertEquals(s, r.index.value)             // the published state still says not stale
        assertNull(s.installableIndex(clock.nowMs()))
        assertNull(r.installableIndex())
        assertNotNull(s.shownIndex())
    }

    @Test fun refreshWithoutSourcesKeepsTheSeed() = runBlocking {
        val s = registry(sources = null).refresh() as IndexState.Ready
        assertEquals(1, s.index.sequence)
        assertTrue(http.requests.isEmpty())
    }

    @Test fun refreshAcceptsANewerIndexAndStoresIt() = runBlocking {
        serve(pub.index(5), pub.keys(1))
        val s = registry().refresh() as IndexState.Ready
        assertEquals(5, s.index.sequence)
        assertEquals(Times.NOW, s.fetchedAtMs)
        assertEquals(false, s.stale)
        assertEquals(setOf(IndexRegistry.CACHE_PATH), store.paths())
        // a new process restores the stored copy (higher sequence than the seed) with no network
        val again = registry(sources = null).restore() as IndexState.Ready
        assertEquals(5, again.index.sequence)
        assertEquals(Times.NOW, again.fetchedAtMs)
    }

    @Test fun newerSeedWinsOverAnOlderStoredCopy() = runBlocking {
        serve(pub.index(5), pub.keys(1))
        registry().refresh()
        val s = registry(seed = pub.bundle(indexSeq = 9), sources = null).restore() as IndexState.Ready
        assertEquals(9, s.index.sequence)
    }

    @Test fun offlineKeepsTheLastIndex() = runBlocking {
        val offline = object : HttpFetcher { override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse = throw IOException("no network") }
        val r = registry(fetcher = offline)
        val s = failed(r.refresh())
        assertEquals(FetchFailure.OFFLINE, s.failure)
        assertNull(s.verdict)
        assertEquals(1L, s.last?.sequence)
        assertNotNull(r.module("retro"))
        assertNull(s.installableIndex(clock.nowMs()))
        assertNull(r.installableIndex())
        assertEquals(1L, s.shownIndex()?.sequence)
    }

    @Test fun httpErrorAndOversizedBodies() = runBlocking {
        assertEquals(FetchFailure.HTTP_ERROR, failed(registry().refresh()).failure)   // nothing served: 404
        serve(ByteArray(IndexLimits.INDEX_BYTES + 1) to ByteArray(10), pub.keys(1))
        assertEquals(FetchFailure.TOO_LARGE, failed(registry().refresh()).failure)
    }

    @Test fun hostOutsideTheFetchersAllowlist() = runBlocking {
        val strict = FakeHttp(allowedHosts = setOf("objects.githubusercontent.com"))
        assertEquals(FetchFailure.HOST_NOT_ALLOWED, failed(registry(fetcher = strict).refresh()).failure)
    }

    @Test fun tamperedIndexFailsAndKeepsTheLast() = runBlocking {
        val (idx, sig) = pub.index(5)
        serve(idx.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() } to sig, pub.keys(1))
        val r = registry()
        val s = failed(r.refresh())
        assertEquals(IndexFailure.BAD_SIGNATURE, s.verdict)
        assertEquals(1L, s.last?.sequence)
        assertTrue(store.paths().isEmpty())
    }

    @Test fun replayedIndexIsRefusedAlsoAfterARestart() = runBlocking {
        serve(pub.index(8), pub.keys(1))
        registry().refresh()
        serve(pub.index(7), pub.keys(1))
        val fresh = registry()        // new process: high-water comes from the stored copy
        assertEquals(IndexFailure.ROLLBACK, failed(fresh.refresh()).verdict)
        assertEquals(8, (registry(sources = null).restore() as IndexState.Ready).index.sequence)
    }

    @Test fun expiredDownloadIsRefused() = runBlocking {
        serve(pub.index(5, expires = "2026-10-19T12:00:00Z"), pub.keys(1))
        assertEquals(IndexFailure.EXPIRED, failed(registry().refresh()).verdict)
    }

    @Test fun keyRotationAndRevocationTakeEffectAtTheNextFetch() = runBlocking {
        val old = pub.ci
        serve(pub.index(5), pub.keys(1))
        val r = registry()
        assertTrue(r.refresh() is IndexState.Ready)
        val next = Signer()
        // the root rotates: new key active, old key revoked
        serve(pub.index(6, by = next), pub.keys(2, active = listOf(next), revoked = listOf(old)))
        assertEquals(6, (r.refresh() as IndexState.Ready).index.sequence)
        // an index still signed by the old key is refused
        serve(pub.index(7, by = old), pub.keys(2, active = listOf(next), revoked = listOf(old)))
        assertEquals(IndexFailure.REVOKED_KEY, failed(r.refresh()).verdict)
        // replaying the older keys file that still lists the old key as active is refused too
        serve(pub.index(8, by = old), pub.keys(1, active = listOf(old)))
        assertEquals(IndexFailure.BAD_KEYS_FILE, failed(r.refresh()).verdict)
        // and the keys file is reported first (fixed order) even when the replayed index is also old
        serve(pub.index(5, by = old), pub.keys(1, active = listOf(old)))
        assertEquals(IndexFailure.BAD_KEYS_FILE, failed(r.refresh()).verdict)
        assertEquals(6L, failed(r.index.value).last?.sequence)
    }

    @Test fun unknownKeyIsRefused() = runBlocking {
        serve(pub.index(5, by = Signer()), pub.keys(1))
        assertEquals(IndexFailure.UNKNOWN_KEY, failed(registry().refresh()).verdict)
    }

    @Test fun badSeedIsReported() = runBlocking {
        val good = pub.bundle(indexSeq = 1)
        val s = registry(seed = IndexBundle(good.index, good.indexSig, good.keys, "garbage".toByteArray()), sources = null).restore()
        assertEquals(IndexFailure.BAD_KEYS_FILE, failed(s).verdict)
    }

    @Test fun corruptStoredCopyIsIgnored() = runBlocking {
        store.write(IndexRegistry.CACHE_PATH, "{not json".toByteArray())
        assertEquals(1, (registry().restore() as IndexState.Ready).index.sequence)
    }

    @Test fun theShippedSeedRestoresWithTheCompiledRoot() = runBlocking {
        val seed = IndexBundle(RepoFiles.bundled("index.json"), RepoFiles.bundled("index.json.minisig"), RepoFiles.bundled("keys.json"), RepoFiles.bundled("keys.json.minisig"))
        val r = IndexRegistry(http, store, clock, seed, null, TrustRoots.indexVerifier())
        val s = r.restore() as IndexState.Ready
        assertEquals("io.github.udhaykumarbala.retro", r.module("retro")?.pkg?.value)
        assertEquals(2, s.index.sequence)
    }
}
