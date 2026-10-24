package tv.cleanyour.core.flows

import tv.cleanyour.core.discovery.DiscoverySource
import tv.cleanyour.core.discovery.DiscoverySource.ADB_MDNS
import tv.cleanyour.core.discovery.DiscoverySource.ANDROID_TV_REMOTE
import tv.cleanyour.core.discovery.DiscoverySource.GOOGLE_CAST
import tv.cleanyour.core.discovery.FoundTv
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCandidatesTest {
    private fun tv(model: String?, vararg sources: DiscoverySource, open: Boolean? = null) =
        FoundTv("192.168.0.10", "Hall Display", model, sources.toSet(), open, null, null, 0L)

    @Test fun castOnlySpeakersAndDisplaysAreNotOffered() {
        // The two devices of the first device test: the Mi TV (Cast and the remote service) and a Nest Hub (Cast only).
        assertTrue(TvCandidates.isCandidate(tv("MiTV-AXSO0", GOOGLE_CAST, ANDROID_TV_REMOTE)))
        assertFalse(TvCandidates.isCandidate(tv("Google Nest Hub", GOOGLE_CAST)))
        assertFalse(TvCandidates.isCandidate(tv("Google Nest Hub Max", GOOGLE_CAST, open = false)))
        assertFalse(TvCandidates.isCandidate(tv("Google Cast Group", GOOGLE_CAST)))
        assertFalse(TvCandidates.isCandidate(tv("Nest Audio", GOOGLE_CAST)))
    }

    @Test fun anythingElseStays() {
        assertTrue(TvCandidates.isCandidate(tv("MiTV-AXSO0", GOOGLE_CAST)), "an older Android TV may announce Cast alone")
        assertTrue(TvCandidates.isCandidate(tv(null, GOOGLE_CAST)))
        assertTrue(TvCandidates.isCandidate(tv("Google Nest Hub", GOOGLE_CAST, open = true)), "adb answers: let the probe decide")
        assertTrue(TvCandidates.isCandidate(tv("Google Nest Hub", GOOGLE_CAST, ADB_MDNS)))
    }
}
