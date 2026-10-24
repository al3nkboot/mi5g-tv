package tv.cleanyour.adb.session

import kotlinx.coroutines.withTimeoutOrNull
import tv.cleanyour.adb.wire.AdbConnection
import tv.cleanyour.adb.wire.AdbService
import tv.cleanyour.adb.wire.AdbStreamReader
import tv.cleanyour.adb.wire.ShellPacket
import tv.cleanyour.adb.wire.ShellV2
import tv.cleanyour.adb.wire.protocolError
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.CLASS_RE
import tv.cleanyour.core.model.PACKAGE_RE
import tv.cleanyour.core.model.RawShell
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.session.ExecResult
import tv.cleanyour.core.session.ExecSpec
import tv.cleanyour.core.session.TvIdentity
import tv.cleanyour.core.store.Clock
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlin.random.Random

// ARCHITECTURE 3.3, 3.4 and 3.10 as constants.
object SessionConfig {
    const val CONNECT_TIMEOUT_MS = 3_000L
    const val FIRST_AUTH_TIMEOUT_MS = 120_000L
    const val IDENTITY_TIMEOUT_MS = 5_000L
    const val IDENTITY_IDLE_MS = 3_000L
    const val PING_INTERVAL_MS = 20_000L
    const val PING_TIMEOUT_MS = 5_000L
    const val IDLE_CLOSE_MS = 60_000L
    const val RECONNECT_BASE_MS = 500L
    const val RECONNECT_CAP_MS = 8_000L
    const val RECONNECT_ATTEMPTS = 5
    const val BOOT_INITIAL_WAIT_MS = 10_000L
    const val BOOT_POLL_MIN_MS = 2_000L
    const val BOOT_POLL_MAX_MS = 10_000L
    const val BOOT_DEFAULT_TIMEOUT_MS = 300_000L
    const val PUSH_TOTAL_MS = 600_000L
    const val PUSH_IDLE_MS = 15_000L
    const val PULL_TOTAL_MS = 120_000L
    const val PULL_IDLE_MS = 10_000L
    const val INSTALL_BASE_MS = 60_000L
    const val INSTALL_BYTES_PER_SECOND = 50_000L
    const val INSTALL_IDLE_MS = 30_000L
    const val INSTALL_OUTPUT_CAP = 16 * 1024
    const val REBOOT_EXEC_TIMEOUT_MS = 10_000L
    const val HASH_EXEC_TIMEOUT_MS = 60_000L
    const val MAX_PUSH_BYTES = 200L * 1024 * 1024
    const val LINE_CAP_BYTES = 8 * 1024
    const val FILE_CHUNK_BYTES = 64 * 1024
    const val PUSH_MODE = 0x1A4   // 0644
}

// The constant read-only lines the session itself sends (R0: 12, D37, boot wait, push verify).
// This @RawShell opt-in is the one outside :core:ops and :core:probe apart from the Plan codec;
// RFC-W2-1 in DECISIONS-W2.md lists it for W3's single-call-site lint.
@OptIn(RawShell::class)
internal object SessionLines {
    val GETPROP = ShellLine("getprop")
    val BOOT_ID = ShellLine("cat /proc/sys/kernel/random/boot_id")
    val PM_PATH_ANDROID = ShellLine("pm path android")
    val REBOOT = ShellLine("reboot")

    // RemotePath admits [A-Za-z0-9._-] segments under a fixed root, so the quoted path is inert.
    fun sha256sum(path: RemotePath): ShellLine = ShellLine("sha256sum '${path.absolute}'")
}

internal object Identity {
    private val PROP_LINE = Regex("^\\[([^\\]]{1,256})\\]: \\[(.{0,4096})\\]$")

    fun parseProps(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (line in text.lineSequence()) {
            val m = PROP_LINE.matchEntire(line) ?: continue
            out.putIfAbsent(m.groupValues[1], m.groupValues[2])
        }
        return out
    }

    // D37: first 16 hex of sha256(ro.serialno + ro.product.device).
    fun tvId(serial: String, device: String): TvId {
        val digest = MessageDigest.getInstance("SHA-256").digest((serial + device).encodeToByteArray())
        return TvId(digest.joinToString("") { "%02x".format(it) }.take(16))
    }

    fun from(props: Map<String, String>, features: Set<String>, maxPayload: Int): TvIdentity {
        val serial = props["ro.serialno"]?.takeIf { it.isNotBlank() } ?: throw protocolError("the TV reports no serial number")
        val device = props["ro.product.device"]?.takeIf { it.isNotBlank() } ?: throw protocolError("the TV reports no device name")
        return TvIdentity(
            tvId = tvId(serial, device),
            serialLast4 = serial.takeLast(4),
            device = device,
            model = props["ro.product.model"] ?: "",
            sdk = props["ro.build.version.sdk"]?.trim()?.toIntOrNull() ?: 0,
            adbSecure = props["ro.adb.secure"]?.trim() == "1",
            features = features,
            maxPayload = maxPayload,
        )
    }

    suspend fun read(conn: AdbConnection, clock: Clock): TvIdentity {
        val r = Exec.run(conn, SessionLines.GETPROP, ExecSpec(timeoutMs = SessionConfig.IDENTITY_TIMEOUT_MS, idleTimeoutMs = SessionConfig.IDENTITY_IDLE_MS), clock)
        if (r.exit != 0) throw protocolError("getprop failed on the TV (exit ${r.exit})")
        return from(parseProps(r.stdout), conn.features, conn.maxPayload)
    }
}

// Exponential with full jitter: base 500 ms, factor 2, cap 8 s (ARCHITECTURE 3.4).
internal object Backoff {
    fun delayMs(attempt: Int, random: Random = Random.Default): Long {
        val cap = minOf(SessionConfig.RECONNECT_CAP_MS, SessionConfig.RECONNECT_BASE_MS shl (attempt - 1).coerceIn(0, 20))
        return random.nextLong(cap + 1)
    }
}

// SECURITY 3.5 and ARCHITECTURE 3.8: one pm per shell call, never chained. The renderer never
// produces such a line; this is the session's own backstop for every caller of exec. The one
// sanctioned exception is the HOME watchdog (D14, SECURITY 3.6): W3's two template shapes, admitted
// only when WATCHDOG_RE matches the whole line (INTEGRATION-PLAN T4). The pattern is written here
// from the template text, not imported, so :adb:session keeps depending on :core:model only.
object PmGuard {
    private val PKG = PACKAGE_RE.pattern.removePrefix("^").removeSuffix("$").replace("(\\.", "(?:\\.")
    private val CLS = CLASS_RE.pattern.removePrefix("^").removeSuffix("$")
    private const val DIR = "/data/local/tmp/cleanyourtv"

    // sleep N & wait; token file check; pm enable; set-home-activity of a component in that same package;
    // start HOME. The seconds, the token (equal in all three places) and the package are the only
    // variable parts; every other byte is fixed.
    private fun body(v: String): String =
        """sleep [0-9]{2,3} & wait; \[ -f $DIR/home-ok-(?<t$v>[0-9a-f]{16}) \] && exit 0; """ +
            """pm enable --user 0 (?<p$v>$PKG); cmd package set-home-activity --user 0 \k<p$v>/$CLS; """ +
            """am start -W -a android\.intent\.action\.MAIN -c android\.intent\.category\.HOME"""

    private fun tail(v: String): String =
        """ cleanyourtv-rollback-\k<t$v> >$DIR/watchdog-\k<t$v>\.log 2>&1 &"""

    // Variant A (nohup setsid) and variant B (backgrounded subshell), W3 Watchdog.TEMPLATE_*.
    val WATCHDOG_RE: Regex = Regex(
        "^(?:" +
            "nohup setsid sh -c '${body("a")}'${tail("a")}" +
            "|" +
            "\\( sh -c '${body("b")}'${tail("b")} \\)" +
            ")$",
    )

    private val PM_WRITE = Regex("(?:^|[\\s;&|(])(?:pm|cmd\\s+package)\\s+(?:disable-user|disable|enable|default-state|install\\S*|uninstall|clear|grant|revoke|set-home-activity|hide|unhide|suspend|unsuspend)(?:\\s|$)")
    private val CHAIN = Regex("[;|&\\n\\r`]|\\$\\(")

    // The reason the line is refused, or null when it may run.
    fun refusal(text: String): String? {
        val plain = text.replace("'", "")
        if (!PM_WRITE.containsMatchIn(plain)) return null
        if (!CHAIN.containsMatchIn(text)) return null
        if (WATCHDOG_RE.matches(text)) return null
        return "a pm write may not share a line with another command"
    }
}

// Maps a socket connect failure to the 5.3 error (ARCHITECTURE 3.5).
internal fun classifyConnectFailure(e: Exception): AdbError = when (e) {
    is AdbException -> e.error
    is ConnectException -> AdbError.Transport.Refused
    is SocketTimeoutException -> AdbError.Transport.Timeout("connect")
    else -> AdbError.Transport.Unreachable
}

// Keeps the first cap bytes and remembers that more arrived.
internal class CappedBuffer(private val cap: Int) {
    private val bytes = ByteArrayOutputStream()
    var truncated = false
        private set
    val size: Int get() = bytes.size()

    fun append(chunk: ByteArray) {
        val room = cap - bytes.size()
        if (chunk.size <= room) bytes.write(chunk) else {
            if (room > 0) bytes.write(chunk, 0, room)
            truncated = true
        }
    }

    fun text(): String = String(bytes.toByteArray(), Charsets.UTF_8)
}

private class Box<T>(val value: T)

// Bounds one wait on the TV; null from the block is a value, not a timeout.
internal suspend fun <T> bounded(ms: Long, phase: String, block: suspend () -> T): T {
    val r = withTimeoutOrNull(ms) { Box(block()) } ?: throw AdbException(AdbError.Transport.Timeout(phase))
    return r.value
}

// shell v2 over one stream with the ExecSpec's two clocks and two caps (ARCHITECTURE 3.3, 3.7).
internal object Exec {
    suspend fun run(conn: AdbConnection, line: ShellLine, spec: ExecSpec, clock: Clock): ExecResult {
        val start = clock.monotonicMs()
        val stream = conn.openStream(AdbService.ShellV2(line))
        try {
            return bounded(spec.timeoutMs, "exec-total") {
                val reader = AdbStreamReader(stream)
                val out = CappedBuffer(spec.stdoutCap)
                val err = CappedBuffer(spec.stderrCap)
                while (true) {
                    when (val p = bounded(spec.idleTimeoutMs, "exec-idle") { ShellV2.next(reader) }) {
                        null -> throw if (conn.isClosed) AdbException(AdbError.Transport.Closed) else protocolError("the shell ended without an exit code")
                        is ShellPacket.Stdout -> out.append(p.bytes)
                        is ShellPacket.Stderr -> err.append(p.bytes)
                        is ShellPacket.Exit -> return@bounded ExecResult(p.code, out.text(), err.text(), out.truncated || err.truncated, clock.monotonicMs() - start)
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                throw IllegalStateException()
            }
        } finally {
            stream.close()
        }
    }
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun monotonicMs(): Long = System.nanoTime() / 1_000_000
}
