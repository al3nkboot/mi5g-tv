package tv.cleanyour.core.flows

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.store.DocumentStore

// Mi5G TV 1.6 (web-preview/src/components/mi5g/model.ts is the source of truth for the rules).

/** 1.6 item 2: cleaning, uninstall and modules are for Android/Google/Fire TV only. */
enum class TvPlatform { ANDROID, TIZEN, WEBOS }

/** 1.6 item 1: one health score per TV, as model.ts healthScore(). Null off Android. */
object TvHealth {
    fun score(platform: TvPlatform, cleaned: Boolean, ramFreed: Boolean, dnsSafe: Boolean, evening: Boolean, online: Boolean): Int? {
        if (platform != TvPlatform.ANDROID) return null
        var s = 58
        if (cleaned) s += 18
        if (ramFreed) s += 8
        if (dnsSafe) s += 8
        if (evening) s += 6
        if (!online) s -= 12
        return s.coerceIn(18, 98)
    }
}

/** 1.6 item 7: is DNS-over-TLS (port 853) reachable on this network before anything is written. */
enum class Port853 { UNKNOWN, OPEN, BLOCKED }

fun interface DotPortProbe {
    suspend fun check(): Port853
}

/** Tries a TCP connect to the AdGuard DoT host on 853 from the phone, which shares the TV's router. */
class SocketDotPortProbe(
    private val host: String = "dns.adguard-dns.com",
    private val port: Int = 853,
    private val timeoutMs: Int = 2500,
) : DotPortProbe {
    override suspend fun check(): Port853 = withContext(Dispatchers.IO) {
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) } }
            .fold({ Port853.OPEN }, { Port853.BLOCKED })
    }
}

/** What DNS mode the 853 check decides: encrypted family DoT, or the safe plain mode. */
enum class DnsMode { NONE, ENCRYPTED, SAFE }

object DnsDecision {
    const val FAMILY_HOST = "family.adguard-dns.com"
    const val SAFE_IP = "94.140.14.14"
    fun modeFor(port: Port853): DnsMode = if (port == Port853.OPEN) DnsMode.ENCRYPTED else DnsMode.SAFE
}

/** 1.6 items 5 and 6: evening mode and DNS are kept per TV, beside the TV record. */
data class TvPrefs16(val evening: Boolean = false, val dns: DnsMode = DnsMode.NONE, val ramFreed: Boolean = false)

class TvPrefs16Store(private val store: DocumentStore) {
    private fun path(id: TvId) = "tvs/${id.value}/v16.txt"

    suspend fun load(id: TvId): TvPrefs16 = store.read(path(id)) { bytes ->
        val m = bytes.decodeToString().lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        TvPrefs16(
            evening = m["evening"] == "true",
            dns = DnsMode.entries.firstOrNull { it.name == m["dns"] } ?: DnsMode.NONE,
            ramFreed = m["ram"] == "true",
        )
    } ?: TvPrefs16()

    suspend fun save(id: TvId, p: TvPrefs16) =
        store.write(path(id), "evening=${p.evening}\ndns=${p.dns.name}\nram=${p.ramFreed}\n".encodeToByteArray())
}

/** 1.6 item 8: streaming apps people use daily. They can be opened or stopped, never frozen or removed. */
object ProtectedStreaming {
    val PACKAGES = setOf(
        "net.mbc.shahid.tv", "net.mbc.shahid", "net.mbc.shahid.android",
        "com.tod.tv", "tv.tod.app", "com.bein.tod",
        "com.stc.tv", "com.stc.jawwytv", "sa.com.stc.jawwy", "com.jawwy.tv",
        "com.google.android.youtube.tv", "com.google.android.youtube",
        "com.parsifal.starz", "com.parsifal.starzplay",
        "com.osn.go", "com.osn.orion", "com.osnplus.tv",
        "com.bein.beinconnect", "com.mena.beinconnect",
    )
    private val KEYWORDS = listOf("shahid", "jawwy", "stctv", "starz", "osn", "beinconnect", ".tod")

    fun isProtected(pkg: PackageName): Boolean {
        val v = pkg.value.lowercase()
        return v in PACKAGES || KEYWORDS.any { it in v }
    }
}

/** 1.6 item 3: the word typed to unlock a permanent uninstall. */
const val UNINSTALL_WORD = "احذف"

fun uninstallUnlocked(typed: String, hasRestorePoint: Boolean): Boolean = hasRestorePoint && typed.trim() == UNINSTALL_WORD

/** 1.6 item 4: the Downloader catalog; free-form codes live under «متقدم». */
data class CatalogEntry(val code: String, val name: String, val note: String)

object DownloadCatalog {
    val ENTRIES = listOf(
        CatalogEntry("401740", "SmartTube", "يوتيوب بلا إعلانات وبلا Shorts"),
        CatalogEntry("482910", "شاهد", "خارج المتجر الرسمي"),
        CatalogEntry("310442", "TOD", "خارج المتجر الرسمي"),
    )
}
