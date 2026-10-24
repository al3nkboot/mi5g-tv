package tv.cleanyour.core.spec

import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Profile

// D27: the dock is picked from a fixed table by locale, never from usage stats. Order (UX 2):
// the profile's suggested dock, then installed streaming apps by the region's popularity table,
// then Live TV if the TV has inputs. Live TV keeps the fourth slot when the TV has inputs and its
// Live TV app is installed, which is how section 4 reads for the reference TV ("Netflix, YouTube,
// Prime Video, Live TV"): the Live TV app is the only way to HDMI from the dock.
internal object DockRankingImpl {
    const val SLOTS = 4

    private const val NETFLIX = "com.netflix.ninja"
    private const val YOUTUBE = "com.google.android.youtube.tv"
    private const val PRIME = "com.amazon.amazonvideo.livingroom"
    private const val DISNEY = "com.disney.disneyplus"

    private val GLOBAL = listOf(NETFLIX, YOUTUBE, PRIME, DISNEY, "com.apple.atve.androidtv.appletv", "com.spotify.tv.android", "com.plexapp.android")
    private val BY_REGION: Map<String, List<String>> = mapOf(
        "IN" to listOf(NETFLIX, YOUTUBE, PRIME, "in.startv.hotstar", "com.jio.media.ondemand", "com.sonyliv", "com.graymatrix.did"),
        "US" to listOf(NETFLIX, YOUTUBE, PRIME, DISNEY, "com.hulu.livingroomplus", "com.wbd.stream", "com.peacocktv.peacockandroid", "com.cbs.ott", "com.tubitv"),
        "GB" to listOf(NETFLIX, YOUTUBE, PRIME, DISNEY, "uk.co.bbc.iplayer"),
        "CA" to listOf(NETFLIX, YOUTUBE, PRIME, DISNEY, "com.wbd.stream"),
        "AU" to listOf(NETFLIX, YOUTUBE, PRIME, DISNEY),
    )

    // "en-IN", "en_IN", "hi-Deva-IN" and "IN" all give IN.
    fun region(locale: String): String? =
        locale.split('-', '_').drop(1).lastOrNull { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
            ?: locale.takeIf { it.length == 2 && it.all(Char::isUpperCase) }

    fun ranking(locale: String): List<PackageName> =
        ((region(locale)?.let { BY_REGION[it] } ?: emptyList()) + GLOBAL).distinct().map(::PackageName)

    fun pick(profile: Profile?, installed: Set<PackageName>, hasInputs: Boolean, liveTv: PackageName?, locale: String): List<PackageName> {
        val live = (liveTv ?: profile?.liveTv)?.takeIf { hasInputs && it in installed }
        val apps = ((profile?.suggestedDock ?: emptyList()) + ranking(locale))
            .filter { it in installed && it != live }
            .distinct()
            .take(if (live != null) SLOTS - 1 else SLOTS)
        return if (live != null) apps + live else apps
    }
}
