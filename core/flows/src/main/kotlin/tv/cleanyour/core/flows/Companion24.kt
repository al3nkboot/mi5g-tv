package tv.cleanyour.core.flows

import java.net.URLDecoder

/** 2.4 M3 (backlog 15, SUL idea, own code): the TV the Mi5G launcher's "Connect a phone" QR points at. */
data class ConnectTarget(val host: String, val port: Int = 5555, val name: String = "")

/**
 * `mi5gtv://connect?host=192.168.1.50&port=5555&name=Living%20room`, opened from the page behind the TV's QR.
 * Only a home-network IPv4 address is taken; the phone then asks before it connects (nothing runs from the link).
 */
object ConnectLink {
    const val SCHEME = "mi5gtv"
    const val HOST = "connect"

    fun parse(uri: String?): ConnectTarget? {
        if (uri == null || uri.length > 512) return null
        val prefix = "$SCHEME://$HOST"
        if (!uri.startsWith(prefix, ignoreCase = true)) return null
        val rest = uri.substring(prefix.length).removePrefix("/")
        if (rest.isNotEmpty() && !rest.startsWith("?")) return null
        val query = rest.removePrefix("?").substringBefore('#')
        val params = query.split('&').filter { it.isNotEmpty() }.mapNotNull { kv ->
            val k = kv.substringBefore('=')
            val v = runCatching { URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: return@mapNotNull null
            k to v
        }.toMap()
        val host = params["host"]?.trim() ?: return null
        if (!isHomeAddress(host)) return null
        val port = params["port"]?.toIntOrNull() ?: 5555
        if (port !in 1..65535) return null
        val name = params["name"].orEmpty().filter { !it.isISOControl() && it != '\u202E' && it != '\u202D' }.trim().take(40)
        return ConnectTarget(host, port, name)
    }
}

/** 2.4 M3 (backlog 17): the mini store, well-known legal TV apps opened on their Play Store page on the TV. */
data class MiniStoreApp(val pkg: String, val name: String)

val MINI_STORE: List<MiniStoreApp> = listOf(
    MiniStoreApp("com.google.android.youtube.tv", "YouTube"),
    MiniStoreApp("com.netflix.ninja", "Netflix"),
    MiniStoreApp("net.mbc.shahidTV", "Shahid"),
    MiniStoreApp("com.amazon.amazonvideo.livingroom", "Prime Video"),
    MiniStoreApp("com.disney.disneyplus", "Disney+"),
    MiniStoreApp("com.anghami", "Anghami"),
    MiniStoreApp("com.spotify.tv.android", "Spotify"),
    MiniStoreApp("org.videolan.vlc", "VLC"),
    MiniStoreApp("org.xbmc.kodi", "Kodi"),
    MiniStoreApp("com.plexapp.android", "Plex"),
    MiniStoreApp("com.esaba.downloader", "Downloader"),
)
