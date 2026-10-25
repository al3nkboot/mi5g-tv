package tv.cleanyour.core.spec

import tv.cleanyour.core.model.PackageName

// The name a person reads for an app (UX 2: the profile's facts, then the TV's own label, a package
// name only at Custom level). The probe reads no labels: no read-only shell line on Android 9 prints
// them, and `dumpsys package` has none. So when neither the facts nor a label know the app, its
// name comes from the package itself, with no shell call: "com.lego.android.tvleanback" reads
// "Lego", "com.android.traceur" reads "Traceur", "com.spotify.tv.android" reads "Spotify" (emulator
// run, round 3: the dock sheet listed package names).
object AppNames {
    fun of(pkg: PackageName, facts: FactsTable, label: String? = null): String =
        facts.lookup(pkg)?.friendlyName ?: label?.trim()?.takeIf { it.isNotEmpty() } ?: fromPackage(pkg)

    // Words that say where an app runs or who ships the platform, not what the app is.
    private val GENERIC = setOf(
        "com", "org", "net", "io", "co", "android", "androidtv", "google", "gms", "tv", "atv", "gtv", "leanback",
        "app", "apps", "application", "mobile", "phone", "client", "main", "release", "prod", "free", "ui", "launcher",
        "smarttv", "tvapp", "ott", "x86", "arm", "example",
    )
    // Affixes trimmed from a segment ("tvleanback", "youtubetv") while four letters are left. Not
    // "app": "plexapp" would read well, "whatsapp" would not.
    private val AFFIXES = listOf("android", "leanback", "atv", "tv")

    val ARABIC_LABELS: Map<String, String> = mapOf(
        "net.mbc.shahid" to "شاهد (Shahid)",
        "net.mbc.shahid.tv" to "شاهد (Shahid)",
        "com.mbc.shahid" to "شاهد (Shahid)",
        "tv.tod.android" to "TOD",
        "com.todtv.tod" to "TOD",
        "com.intigral.jawwytv" to "stc tv (Jawwy TV)",
        "com.stctv.app" to "stc tv",
        "tv.stc.android" to "stc tv",
        "com.parsifal.starz" to "STARZPLAY",
        "com.starzplay.arabia" to "STARZPLAY",
        "com.osn.go" to "OSN+",
        "com.anghami.osnplus" to "OSN+",
        "com.watchit.android" to "WATCH IT",
        "com.watchit.tv" to "WATCH IT",
        "com.yango.play" to "Yango Play",
        "sa.sba.aloula" to "الأولى (Aloula)",
        "ae.dmi.awaan" to "أوان (Awaan)",
        "com.anghami" to "أنغامي (Anghami)",
                "com.bein.connect" to "beIN CONNECT",
        "com.midroid.mi5gtv.prayer" to "الطقس والصلاة",
        "com.midroid.mi5gtv.files" to "مدير الملفات",
        "com.midroid.mi5gtv.speedtest" to "قياس السرعة",
        "com.midroid.mi5gtv.dns" to "مانع الإعلانات",
        "com.midroid.mi5gtv.matches" to "مباريات اليوم",
    )

    fun fromPackage(pkg: PackageName): String {
        ARABIC_LABELS[pkg.value]?.let { return it }
        val all = pkg.value.split('.').filter { it.isNotEmpty() }
        val segments = if (all.size > 1) all.drop(1) else all
        val meaningful = segments.map(::trimAffixes).filter { it.length >= 2 && it.lowercase() !in GENERIC }
        if (meaningful.isEmpty()) return words(all.last())
        // The maker, then what it is, when the package names both: "com.miui.tv.analytics" reads
        // "Miui Analytics", "com.google.android.play.games" reads "Play Games".
        val last = meaningful.last().takeIf { meaningful.size > 1 && it.length >= 3 }
        return listOfNotNull(meaningful.first(), last).joinToString(" ") { words(it) }
    }

    private fun trimAffixes(segment: String): String {
        var s = segment
        var changed = true
        while (changed) {
            changed = false
            val low = s.lowercase()
            if (low in GENERIC) return ""
            for (a in AFFIXES) {
                if (low.length >= a.length + 4 && low.startsWith(a)) { s = s.substring(a.length); changed = true; break }
                if (low.length >= a.length + 4 && low.endsWith(a)) { s = s.substring(0, s.length - a.length); changed = true; break }
            }
        }
        return s.trim('_', '-')
    }

    // "myApp" and "my_app" read "My App"; digits stay where they are.
    private fun words(s: String): String =
        s.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").split('_', '-', ' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercaseChar() } }
}
