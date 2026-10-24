package tv.cleanyour.app

import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.registry.FetchResult
import tv.cleanyour.core.registry.IndexState
import tv.cleanyour.core.registry.installableIndex
import tv.cleanyour.core.trust.VerifiedIndex
import tv.cleanyour.testing.fixtures.FakeArtifactFetcher
import tv.cleanyour.testing.fixtures.MemoryBlobStore
import tv.cleanyour.testing.fixtures.sha256Of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// T7 required behaviour 4 (R5-W5 F8): modules install only from IndexRegistry.installableIndex().
// The graph's fetcher refuses, before any download, an artifact the installable index does not
// offer: a stale list, the last list behind a failed refresh, a revoked or quarantined module, a
// revoked APK or signer, or a ref the machine took from anywhere else.
class InstallableOnlyFetcherTest {
    private val now = 1_760_000_000_000L
    private val apk = "retro apk".encodeToByteArray()
    private val signer = Sha256("5".repeat(64))
    private val ref = ArtifactRef("https://github.com/x/retro/releases/download/v1/retro.apk", apk.size.toLong(), sha256Of(apk), signer)
    private val version = ModuleVersion(3, "1.2", "2026-10-01", ref, emptyList(), "No network", 28, emptyList(), emptyList())

    private fun module(tier: Tier = Tier.VERIFIED) = Module("retro", "Retro", "Old games", "", PackageName("tv.retro.app"), "app", tier,
        "pub", "https://github.com/x/retro", "MIT", null, null, listOf(version))

    private fun index(m: Module = module(), revocations: List<Sha256> = emptyList()) = VerifiedIndex(7, now + 86_400_000, listOf(m), revocations)

    private fun fetcherOver(state: () -> IndexState): Pair<InstallableOnlyFetcher, FakeArtifactFetcher> {
        val inner = FakeArtifactFetcher(MemoryBlobStore(), mapOf(ref.url to apk))
        return InstallableOnlyFetcher(inner) { state().installableIndex(now) } to inner
    }

    @Test fun aFreshVerifiedIndexOffersTheArtifact() = runTest {
        val (f, inner) = fetcherOver { IndexState.Ready(index(), stale = false, fetchedAtMs = now) }
        assertIs<FetchResult.Ok>(f.fetch(ref) { _, _ -> })
        assertEquals(listOf(ref), inner.fetched)
    }

    @Test fun aStaleReadyRefusesBeforeAnyDownload() = runTest {
        val (f, inner) = fetcherOver { IndexState.Ready(index(), stale = true, fetchedAtMs = now) }
        assertEquals(InstallableOnlyFetcher.refused, f.fetch(ref) { _, _ -> })
        assertTrue(inner.fetched.isEmpty())
    }

    @Test fun aReadyPastItsExpiryRefuses() = runTest {
        val (f, inner) = fetcherOver { IndexState.Ready(index().copy(expiresAtMs = now - 1), stale = false, fetchedAtMs = now) }
        assertEquals(InstallableOnlyFetcher.refused, f.fetch(ref) { _, _ -> })
        assertTrue(inner.fetched.isEmpty())
    }

    @Test fun theLastListBehindAFailedRefreshRefuses() = runTest {
        val (f, inner) = fetcherOver { IndexState.Failed(null, null, last = index()) }
        assertEquals(InstallableOnlyFetcher.refused, f.fetch(ref) { _, _ -> })
        assertTrue(inner.fetched.isEmpty())
    }

    @Test fun revokedQuarantinedOrUnlistedArtifactsRefuse() = runTest {
        for (state in listOf(
            IndexState.Ready(index(module(Tier.REVOKED)), false, now),
            IndexState.Ready(index(module(Tier.QUARANTINED)), false, now),
            IndexState.Ready(index(revocations = listOf(ref.sha256)), false, now),
            IndexState.Ready(index(revocations = listOf(signer)), false, now),
        )) {
            val (f, inner) = fetcherOver { state }
            assertEquals(InstallableOnlyFetcher.refused, f.fetch(ref) { _, _ -> }, state.toString())
            assertTrue(inner.fetched.isEmpty())
        }
        val (f, inner) = fetcherOver { IndexState.Ready(index(), false, now) }
        assertEquals(InstallableOnlyFetcher.refused, f.fetch(ref.copy(url = ref.url + "?other")) { _, _ -> })
        assertTrue(inner.fetched.isEmpty())
    }
}
