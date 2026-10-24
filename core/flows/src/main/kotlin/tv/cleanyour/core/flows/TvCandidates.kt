package tv.cleanyour.core.flows

import tv.cleanyour.core.discovery.DiscoverySource
import tv.cleanyour.core.discovery.FoundTv

// Which found devices the picker offers. A device that only announces Cast and whose Cast model is a
// speaker, a smart display or a cast group cannot be cleaned (no Android TV, no adb), so it is not
// offered (device test 2026-10-20: a Nest Hub showed up as a second TV). A Cast-only device of any
// other model stays: an older Android TV without the remote service still announces Cast alone.
object TvCandidates {
    private val NOT_A_TV = listOf(
        "nest hub", "nest audio", "nest mini", "nest wifi", "google home", "home mini", "home max",
        "chromecast audio", "cast group", "smart display", "smart clock", "speaker", "soundbar",
    )

    fun isCandidate(tv: FoundTv): Boolean {
        val castOnly = tv.sources == setOf(DiscoverySource.GOOGLE_CAST) && tv.port5555Open != true
        if (!castOnly) return true
        val model = tv.model?.lowercase() ?: return true
        return NOT_A_TV.none { it in model }
    }
}
