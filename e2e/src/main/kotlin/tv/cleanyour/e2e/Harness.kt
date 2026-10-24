package tv.cleanyour.e2e

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import tv.cleanyour.adb.wire.AdbSocket
import tv.cleanyour.adb.wire.SocketFactory
import tv.cleanyour.core.engine.JournalRecord
import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.core.session.ExecResult
import tv.cleanyour.core.session.ExecSpec
import tv.cleanyour.core.session.TvSession
import tv.cleanyour.core.store.Clock
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// Plain TCP sockets on the JVM, the harness's stand-in for :android:platform's PlainSocketFactory
// (an Android class), so :adb:session runs unchanged against the emulator's adbd.
class JvmSocketFactory : SocketFactory {
    override suspend fun connect(endpoint: Endpoint, timeoutMs: Long): AdbSocket = withContext(Dispatchers.IO) {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMs.toInt())
        JvmAdbSocket(s)
    }
}

class JvmAdbSocket(private val socket: Socket) : AdbSocket {
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()
    override suspend fun read(buf: ByteArray, off: Int, len: Int): Int = withContext(Dispatchers.IO) { input.read(buf, off, len) }
    override suspend fun write(buf: ByteArray, off: Int, len: Int) = withContext(Dispatchers.IO) { output.write(buf, off, len) }
    override suspend fun flush() = withContext(Dispatchers.IO) { output.flush() }
    override fun close() = socket.close()
}

object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun monotonicMs(): Long = System.nanoTime() / 1_000_000
}

// Counts shell round trips through a session, for the "under 10 shell calls per full probe" rule
// (W4 definition of done) measured against a real adbd.
class CountingSession(private val delegate: TvSession) : TvSession by delegate {
    val execs = AtomicInteger()
    val streams = AtomicInteger()
    val lines = CopyOnWriteArrayList<String>()
    val shellCalls: Int get() = execs.get() + streams.get()

    override suspend fun exec(line: ShellLine, spec: ExecSpec): ExecResult {
        execs.incrementAndGet(); lines += line.text
        return delegate.exec(line, spec)
    }

    override fun lines(line: ShellLine, spec: ExecSpec): Flow<String> =
        delegate.lines(line, spec).onStart { streams.incrementAndGet(); lines += line.text }
}

// ChangeRecords from an engine journal, the way undo consumes them: every Begin whose step has a
// Done record, in step order.
fun changesFrom(records: List<JournalRecord>): List<ChangeRecord> {
    val done = records.filterIsInstance<JournalRecord.Done>().map { it.planId to it.i }.toSet()
    return records.filterIsInstance<JournalRecord.Begin>()
        .filter { (it.planId to it.i) in done }
        .sortedBy { it.i }
        .map { ChangeRecord(it.planId, it.i, it.op, it.prior, it.atMs) }
}

// Timing and throughput numbers against the ARCHITECTURE 4 budgets. In 0.1 they are trend data
// (PRODUCT-SPEC 7.2): recorded, printed, written as JSON lines for the nightly artifact, never a
// failure.
class Timings(private val out: File, private val api: Int) {
    data class Entry(val name: String, val value: Double, val unit: String, val budget: Double?, val higherIsBetter: Boolean) {
        val withinBudget: Boolean? get() = budget?.let { if (higherIsBetter) value >= it else value <= it }
    }

    val entries = CopyOnWriteArrayList<Entry>()

    fun record(name: String, value: Double, unit: String, budget: Double? = null, higherIsBetter: Boolean = false): Entry {
        val e = Entry(name, value, unit, budget, higherIsBetter)
        entries += e
        val verdict = when (e.withinBudget) { true -> "within budget"; false -> "OVER BUDGET (trend only)"; null -> "no budget" }
        println("TIMING api=$api $name=${"%.2f".format(Locale.ROOT, value)} $unit budget=${budget ?: "-"} $verdict")
        out.mkdirs()
        File(out, "timings-api$api.jsonl").appendText(
            "{\"api\":$api,\"name\":\"$name\",\"value\":${"%.3f".format(Locale.ROOT, value)},\"unit\":\"$unit\"," +
                "\"budget\":${budget ?: "null"},\"withinBudget\":${e.withinBudget},\"atMs\":${System.currentTimeMillis()}}\n",
        )
        return e
    }

    inline fun <T> time(name: String, budgetMs: Double? = null, block: () -> T): T {
        val t0 = System.nanoTime()
        try { return block() } finally { record(name, (System.nanoTime() - t0) / 1e6, "ms", budgetMs) }
    }

    companion object {
        // ARCHITECTURE 4: the e2e rows (CI hard gate column), used as trend thresholds in 0.1.
        const val CONNECT_MS = 3_000.0
        const val PROBE_API28_MS = 15_000.0
        const val DEBLOAT_40_MS = 150_000.0
        const val PUSH_MB_PER_S = 0.5
        const val PROBE_MAX_SHELL_CALLS = 10
    }
}
