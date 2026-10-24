package tv.cleanyour.core.trust

import java.net.URI
import java.util.Locale

// The only hosts the phone app ever sends a request to (D41, SECURITY 2.4 S1 and 4.4). GitHub
// release assets redirect from github.com to one of the two asset hosts, so every hop is checked
// against this list, not just the first URL. Adding a host is a reviewed one-line change here, and
// the network security config in :app must list the same hosts.
object DownloadHosts {
    val ALL: Set<String> = setOf(
        "github.com",
        "api.github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    const val MAX_URL_LENGTH = 2048

    // https only, exact host match ignoring case (no subdomains), no user info, default port, no fragment.
    fun isAllowed(url: String, hosts: Set<String> = ALL): Boolean {
        if (url.length > MAX_URL_LENGTH) return false
        val u = try { URI(url) } catch (e: Exception) { return false }
        if (!u.isAbsolute || u.isOpaque) return false
        if (u.scheme != "https") return false
        if (u.rawUserInfo != null || u.rawFragment != null) return false
        if (u.port != -1 && u.port != 443) return false
        val host = u.host ?: return false
        return host.lowercase(Locale.ROOT) in hosts
    }
}
