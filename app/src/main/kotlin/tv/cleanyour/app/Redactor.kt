package tv.cleanyour.app

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.cleanyour.core.flows.Diagnostics

// SECURITY 4.6 and F13: everything the app logs goes through this redactor, and so does the
// "Share diagnostics" text. Replaced: base64 or hex blobs of 40 characters or more (AUTH tokens,
// keys, signatures), MD5 colon fingerprints, IPv4 addresses (the last octet is kept), serial
// numbers, 16 hex TV ids (serial-derived), 6 digit codes, Wi-Fi names, and anything the caller
// marks secret.
object Redactor {
    private val BLOB = Regex("[A-Za-z0-9+/_=-]{40,}")
    private val FINGERPRINT = Regex("\\b(?:[0-9A-Fa-f]{2}:){15}[0-9A-Fa-f]{2}\\b")
    // 0.3.3 D3 (issue #10): four octets of 0 to 255, not glued to a letter, a digit or a dot, so a
    // build string such as "V816.0.11.0.UZFAABX" or "UKG3.250107.001" stays whole. A dot that ends
    // a sentence ("from 192.168.1.20.") still masks: only a dot followed by a letter or digit counts.
    private const val OCTET = "(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)"
    private val IPV4 = Regex("(?<![A-Za-z0-9.])$OCTET\\.$OCTET\\.$OCTET\\.$OCTET(?![A-Za-z0-9]|\\.[A-Za-z0-9])")
    private val SERIAL = Regex("(?i)(serial(?:no)?|ro\\.serialno|ro\\.boot\\.serialno)([\"'\\]\\s:=\\[]+)([A-Za-z0-9]{4,})")
    private val TV_ID = Regex("\\b[0-9a-f]{16}\\b")
    private val CODE = Regex("(?<![\\d.])\\d{6}(?![\\d.])")
    private val SSID = Regex("(?i)(ssid|wifi[ _-]?name)([\"'\\s:=]+)([^,;\\s\"']+)")

    fun redact(text: String): String = text
        .replace(FINGERPRINT, "[fingerprint]")
        .replace(BLOB, "[blob]")
        .replace(SERIAL) { "${it.groupValues[1]}${it.groupValues[2]}[serial]" }
        .replace(SSID) { "${it.groupValues[1]}${it.groupValues[2]}[wifi]" }
        .replace(IPV4) { "x.x.x.${it.groupValues[4]}" }
        .replace(TV_ID, "[tv]")
        .replace(CODE, "[code]")
}

// The app's only logger: redacted lines to logcat and to a small ring (files/logs, 5 files of
// 1 MB) that "Share diagnostics" reads. Release builds strip d and v calls with R8.
object CytLog {
    private const val TAG = "CleanYourTv"
    private const val MAX_FILE = 1_000_000L
    private const val FILES = 5
    private var dir: File? = null
    private val ring = ArrayDeque<String>()

    fun init(logDir: File) { dir = logDir.also { it.mkdirs() } }

    fun d(message: String) = write("D", message, toLogcat = false)
    fun i(message: String) = write("I", message, toLogcat = true)
    fun w(message: String, t: Throwable? = null) = write("W", message + (t?.let { ": ${it.javaClass.simpleName}" } ?: ""), toLogcat = true)
    fun secret(@Suppress("UNUSED_PARAMETER") value: String): String = "[secret]"

    @Synchronized
    private fun write(level: String, message: String, toLogcat: Boolean) {
        val line = "${System.currentTimeMillis()} $level ${Redactor.redact(message)}"
        if (toLogcat) Log.i(TAG, line)
        ring.addLast(line)
        while (ring.size > 2_000) ring.removeFirst()
        val d = dir ?: return
        runCatching {
            val f = File(d, "session-0.log")
            if (f.length() > MAX_FILE) {
                for (n in FILES - 1 downTo 1) File(d, "session-${n - 1}.log").takeIf { it.exists() }?.renameTo(File(d, "session-$n.log"))
            }
            File(d, "session-0.log").appendText(line + "\n")
        }
    }

    @Synchronized
    fun snapshot(): String {
        val d = dir
        val files = if (d == null) emptyList() else (FILES - 1 downTo 0).map { File(d, "session-$it.log") }.filter { it.exists() }
        val text = if (files.isEmpty()) ring.joinToString("\n") else files.joinToString("") { runCatching { it.readText() }.getOrDefault("") }
        return Redactor.redact(text)
    }

    fun clear() { synchronized(this) { ring.clear(); dir?.listFiles()?.forEach { it.delete() } } }
}

class LogDiagnostics(private val header: () -> String) : Diagnostics {
    override suspend fun redactedText(): String = Redactor.redact(header()) + "\n" + CytLog.snapshot()
    override suspend fun clear() = withContext(Dispatchers.IO) { CytLog.clear() }
}
