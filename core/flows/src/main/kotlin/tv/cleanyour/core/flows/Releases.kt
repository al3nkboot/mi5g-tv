package tv.cleanyour.core.flows

// M4 item 34: reading GitHub's "latest release" answer. A tag counts as an update only when it is
// numerically newer than the running version ("2.0.0-dev" is older than "2.0.0", newer than "1.6.0").
object Releases {
    const val DOWNLOAD_PREFIX = "https://github.com/al3nkboot/mi5g-tv/releases/download/"

    data class Latest(val version: String, val apkUrl: String?)

    fun compare(a: String, b: String): Int {
        fun split(v: String): Pair<List<Int>, Boolean> {
            val clean = v.trim().removePrefix("v")
            val core = clean.substringBefore('-')
            return core.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 } to clean.contains('-')
        }
        val (pa, preA) = split(a)
        val (pb, preB) = split(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return when { preA == preB -> 0; preA -> -1; else -> 1 }
    }

    /** The main APK of a release, by the naming the releases use (Mi5G-TV-v1.6.0.apk). */
    fun apkUrlFor(version: String): String {
        require(version.matches(Regex("[0-9][0-9A-Za-z.\\-]{0,30}")) && !version.contains("..")) { "bad version" }
        return "${DOWNLOAD_PREFIX}v$version/Mi5G-TV-v$version.apk"
    }

    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    fun parse(body: String): Latest? {
        val tag = Regex("\"tag_name\"\\s*:\\s*\"v?([0-9][0-9A-Za-z.\\-]*)\"").find(body)?.groupValues?.get(1) ?: return null
        val apk = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"").findAll(body).map { it.groupValues[1] }
            .filter { it.startsWith(DOWNLOAD_PREFIX) && !it.contains("..") }
            .firstOrNull { Regex("/Mi5G-TV-v[0-9][^/]*\\.apk$").containsMatchIn(it) }
        return Latest(tag, apk)
    }
}
