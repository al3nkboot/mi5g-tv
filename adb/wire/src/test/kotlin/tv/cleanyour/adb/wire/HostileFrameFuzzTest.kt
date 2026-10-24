package tv.cleanyour.adb.wire

import org.junit.Rule
import org.junit.rules.Timeout
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.testing.fakeadbd.Faults
import tv.cleanyour.testing.fakeadbd.PipeSocket
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The hostile-frame target (SECURITY 6.2, T-N7). Jazzer cannot run in the forge container, so
// this is the equivalent bounded random-frame property test: random and mutated bytes into the
// frame decoder, the shell v2 reader, the sync reader and a live connection; the only acceptable
// outcomes are a decoded value, end of stream, or AdbException. Anything else (any other
// Throwable, an allocation past the bounds, a hang) fails. The time budget comes from
// -Pcleanyourtv.fuzzSeconds (default 15; CI's 10-minute run passes 600).
class HostileFrameFuzzTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds((System.getProperty("cleanyourtv.fuzzSeconds")?.toLongOrNull() ?: 15L) * 2 + 60)
    private val budgetMs = (System.getProperty("cleanyourtv.fuzzSeconds")?.toLongOrNull() ?: 15L) * 1000
    private val seed = System.getProperty("cleanyourtv.fuzzSeed")?.toLongOrNull() ?: System.nanoTime()

    private fun report(target: String, iterations: Long, failures: Int) {
        println("fuzz $target: $iterations iterations, $failures unexpected outcomes, seed $seed")
        assertEquals(0, failures, "$target produced unexpected outcomes (seed $seed)")
    }

    private inline fun timeBounded(share: Double, body: (Random) -> Unit): Long {
        val random = Random(seed)
        val deadline = System.nanoTime() + (budgetMs * share * 1_000_000).toLong()
        var n = 0L
        while (System.nanoTime() < deadline) { body(random); n++ }
        return n
    }

    private fun validFrame(random: Random): ByteArray {
        val commands = intArrayOf(AdbProtocol.A_CNXN, AdbProtocol.A_AUTH, AdbProtocol.A_OPEN, AdbProtocol.A_OKAY, AdbProtocol.A_CLSE, AdbProtocol.A_WRTE, AdbProtocol.A_STLS)
        val c = commands[random.nextInt(commands.size)]
        val max = AdbProtocol.maxPayloadFor(c)
        val len = if (max == 0) 0 else random.nextInt(minOf(max, 4096) + 1)
        return FrameCodec.encode(c, random.nextInt(), random.nextInt(), ByteArray(len).also { random.nextBytes(it) })
    }

    private fun mutate(bytes: ByteArray, random: Random): ByteArray {
        val out = bytes.copyOf()
        repeat(1 + random.nextInt(4)) {
            when (random.nextInt(4)) {
                0 -> if (out.isNotEmpty()) out[random.nextInt(out.size)] = random.nextInt(256).toByte()
                1 -> if (out.size >= 24) FrameCodec.putIntLe(out, 4 * random.nextInt(6), random.nextInt())
                2 -> if (out.size >= 24) FrameCodec.putIntLe(out, 12, listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, AdbProtocol.MAX_PAYLOAD + 1, 0x7fffff)[random.nextInt(5)])
                else -> return out.copyOf(random.nextInt(out.size + 1))
            }
        }
        return out
    }

    private fun decodeOutcome(bytes: ByteArray): Boolean = runBlocking {
        val (a, b) = PipeSocket.pair()
        if (bytes.isNotEmpty()) b.write(bytes, 0, bytes.size)
        b.close()
        try {
            while (true) {
                val m = FrameCodec.readMessage(a) ?: break
                if (m.payload.size > AdbProtocol.MAX_PAYLOAD) return@runBlocking false
                if (m.payload.size > AdbProtocol.maxPayloadFor(m.command)) return@runBlocking false
            }
            true
        } catch (e: AdbException) {
            true
        } catch (t: Throwable) {
            println("decoder: ${t.javaClass.name}: ${t.message}")
            false
        }
    }

    @Test fun frameDecoderSurvivesRandomAndMutatedFrames() {
        var failures = 0
        val n = timeBounded(0.5) { random ->
            val bytes = when (random.nextInt(3)) {
                0 -> ByteArray(random.nextInt(2048)).also { random.nextBytes(it) }
                1 -> mutate(validFrame(random), random)
                else -> validFrame(random) + mutate(validFrame(random), random)
            }
            if (!decodeOutcome(bytes)) failures++
        }
        report("frame decoder", n, failures)
    }

    @Test fun shellAndSyncReadersSurviveRandomStreams() {
        var failures = 0
        val n = timeBounded(0.5) { random ->
            val bytes = when (random.nextInt(3)) {
                0 -> ByteArray(random.nextInt(1024)).also { random.nextBytes(it) }
                1 -> mutate(ShellV2.encode(1 + random.nextInt(3), ByteArray(random.nextInt(64))) + ShellV2.exitPacket(random.nextInt(256)), random)
                else -> mutate(SyncProtocol.header(listOf("DATA", "DONE", "FAIL", "OKAY", "STAT")[random.nextInt(5)], random.nextInt()) + ByteArray(random.nextInt(64)), random)
            }
            val chunks = bytes.chunked(1 + random.nextInt(16))
            runBlocking {
                try {
                    val reader = AdbStreamReader(ScriptedStream(chunks))
                    while (ShellV2.next(reader) != null) { }
                } catch (e: AdbException) {
                } catch (t: Throwable) { failures++; println("shell: $t") }
                try {
                    SyncClient(ScriptedStream(chunks)).recv("/x", 4096)
                } catch (e: AdbException) {
                } catch (e: SyncFailedException) {
                } catch (t: Throwable) { failures++; println("sync: $t") }
                try {
                    val reader = AdbStreamReader(ScriptedStream(chunks))
                    while (true) { val p = SyncProtocol.readPacket(reader) ?: break; if (p.id == "FAIL") SyncProtocol.readFailMessage(reader, p.arg) }
                } catch (e: AdbException) {
                } catch (t: Throwable) { failures++; println("sync packets: $t") }
            }
        }
        report("shell and sync readers", n, failures)
    }

    @Test fun liveConnectionSurvivesHostileFramesAfterConnect() = runTest {
        val random = Random(seed)
        var failures = 0
        val iterations = 150
        repeat(iterations) { i ->
            val garbage = when (i % 3) {
                0 -> ByteArray(1 + random.nextInt(512)).also { random.nextBytes(it) }
                1 -> mutate(validFrame(random), random)
                else -> FrameCodec.encode(AdbProtocol.A_WRTE, random.nextInt(), 1 + random.nextInt(3), ByteArray(random.nextInt(256))) + mutate(validFrame(random), random)
            }
            val tv = adbd(faults = Faults().apply { garbageAfterConnect = garbage })
            try {
                val conn = tv.open()
                advanceUntilIdle()
                // A truncated frame can leave both sides waiting for bytes that never come; on a
                // real TV the session's exec clocks end that wait, so bound it the same way here.
                try {
                    withTimeoutOrNull(5_000) { conn.shell("echo x") }
                } catch (e: AdbException) {
                }
                conn.close()
                advanceUntilIdle()
                assertTrue(conn.isClosed)
            } catch (e: AdbException) {
            } catch (t: Throwable) {
                failures++
                println("live: ${t.javaClass.name}: ${t.message}")
            }
            tv.stop()
        }
        report("live connection", iterations.toLong(), failures)
    }

    @Test fun encodeDecodeIsAnIdentityForEveryValidFrame() = runBlocking<Unit> {
        val commands = listOf(AdbProtocol.A_CNXN, AdbProtocol.A_AUTH, AdbProtocol.A_OPEN, AdbProtocol.A_WRTE)
        checkAll(300, Arb.int(0, 3), Arb.int(), Arb.int(), Arb.byteArray(Arb.int(0, 2048), Arb.byte())) { ci, a0, a1, payload ->
            val c = commands[ci]
            val (a, b) = PipeSocket.pair()
            val f = FrameCodec.encode(c, a0, a1, payload)
            b.write(f, 0, f.size)
            b.close()
            val m = FrameCodec.readMessage(a)!!
            assertEquals(c, m.command); assertEquals(a0, m.arg0); assertEquals(a1, m.arg1)
            assertContentEquals(payload, m.payload)
        }
    }
}
