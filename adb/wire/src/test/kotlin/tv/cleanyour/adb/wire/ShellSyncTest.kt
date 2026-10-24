package tv.cleanyour.adb.wire

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.testing.fakeadbd.Faults
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellSyncTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    private fun protocolDetail(block: suspend () -> Unit): String = runBlocking {
        val e = assertFailsWith<AdbException> { block() }
        assertIs<AdbError.Transport.Protocol>(e.error).detail
    }

    @Test fun packetsSplitAcrossFramesReassemble() = runTest {
        val tv = adbd(faults = Faults().apply { splitPackets = true; shellChunkBytes = 7 })
        val conn = tv.open()
        val r = conn.shell("getprop")
        assertEquals(tv.tv.getpropText(), r.stdout)
        assertEquals(0, r.exit)
        conn.close()
    }

    @Test fun shellReaderBoundsEveryLength() {
        assertEquals("shell packet of 2147483647 bytes", protocolDetail { ShellV2.next(AdbStreamReader(ScriptedStream(listOf(byteArrayOf(1, -1, -1, -1, 127))))) })
        assertEquals("shell packet of ${AdbProtocol.MAX_PAYLOAD + 1} bytes", protocolDetail {
            val h = ByteArray(5).also { it[0] = 1; FrameCodec.putIntLe(it, 1, AdbProtocol.MAX_PAYLOAD + 1) }
            ShellV2.next(AdbStreamReader(ScriptedStream(listOf(h))))
        })
        assertEquals("shell packet id 9", protocolDetail { ShellV2.next(AdbStreamReader(ScriptedStream(listOf(ShellV2.encode(9, ByteArray(3)))))) })
        assertEquals("shell packet id 0", protocolDetail { ShellV2.next(AdbStreamReader(ScriptedStream(listOf(ShellV2.encode(ShellV2.ID_STDIN, ByteArray(1)))))) })
        assertEquals("shell exit packet of 2 bytes", protocolDetail { ShellV2.next(AdbStreamReader(ScriptedStream(listOf(ShellV2.encode(ShellV2.ID_EXIT, ByteArray(2)))))) })
        assertEquals("the TV ended the stream mid-packet", protocolDetail { ShellV2.next(AdbStreamReader(ScriptedStream(listOf(ShellV2.encode(1, ByteArray(10)).copyOf(8))))) })
        assertEquals("the TV ended the stream mid-packet", protocolDetail { ShellV2.next(AdbStreamReader(ScriptedStream(listOf(byteArrayOf(1, 2))))) })
    }

    @Test fun shellReaderHandlesAnyFraming() = runBlocking<Unit> {
        val packets = ShellV2.encode(1, "hello ".toByteArray()) + ShellV2.encode(2, "oops".toByteArray()) + ShellV2.encode(1, "world".toByteArray()) + ShellV2.exitPacket(3)
        for (chunk in listOf(1, 2, 3, 5, 7, 11, 1000)) {
            val reader = AdbStreamReader(ScriptedStream(packets.chunked(chunk)))
            val out = StringBuilder(); val err = StringBuilder(); var exit = -1
            while (true) {
                when (val p = ShellV2.next(reader) ?: break) {
                    is ShellPacket.Stdout -> out.append(String(p.bytes))
                    is ShellPacket.Stderr -> err.append(String(p.bytes))
                    is ShellPacket.Exit -> exit = p.code
                }
            }
            assertEquals("hello world", out.toString(), "chunk $chunk")
            assertEquals("oops", err.toString())
            assertEquals(3, exit)
        }
        assertNull(ShellV2.next(AdbStreamReader(ScriptedStream(emptyList()))))
    }

    @Test fun exitCodeIsUnsigned() = runBlocking<Unit> {
        val p = ShellV2.next(AdbStreamReader(ScriptedStream(listOf(ShellV2.exitPacket(255)))))
        assertEquals(255, assertIs<ShellPacket.Exit>(p).code)
    }

    @Test fun syncPushAndPullRoundTrip() = runTest {
        val tv = adbd()
        val conn = tv.open()
        val data = ByteArray(200_000) { (it % 13).toByte() }
        val chunks = ArrayDeque(data.chunked(SyncProtocol.DATA_MAX))
        val progress = ArrayList<Long>()
        val stream = conn.openStream(AdbService.Sync)
        val sync = SyncClient(stream)
        sync.send("/data/local/tmp/a.bin", 0x1A4, 1_700_000_000, chunks = { chunks.removeFirstOrNull() }, onProgress = { progress += it })
        assertContentEquals(data, tv.tv.files["/data/local/tmp/a.bin"])
        assertEquals(data.size.toLong(), progress.last())
        assertContentEquals(data, sync.recv("/data/local/tmp/a.bin", 1_000_000))
        sync.quit()
        stream.close()
        conn.close()
    }

    @Test fun pullRefusesFilesOverTheCap() = runTest {
        val tv = adbd()
        tv.tv.files["/sdcard/big.png"] = ByteArray(100_000)
        val conn = tv.open()
        val stream = conn.openStream(AdbService.Sync)
        val e = assertFailsWith<AdbException> { SyncClient(stream).recv("/sdcard/big.png", 10_000) }
        assertEquals("pull larger than 10000 bytes", assertIs<AdbError.Transport.Protocol>(e.error).detail)
        stream.close()
        conn.close()
    }

    @Test fun syncFailuresAreTyped() = runTest {
        val tv = adbd()
        val conn = tv.open()
        val s1 = conn.openStream(AdbService.Sync)
        val missing = assertFailsWith<SyncFailedException> { SyncClient(s1).recv("/sdcard/nope", 1000) }
        assertTrue(missing.reason.contains("No such file"), missing.reason)
        s1.close()
        val s2 = conn.openStream(AdbService.Sync)
        var sent = false
        val noDir = assertFailsWith<SyncFailedException> { SyncClient(s2).send("/sdcard/missingdir/x", 0x1A4, 0, chunks = { if (sent) null else { sent = true; ByteArray(10) } }) }
        assertTrue(noDir.reason.contains("couldn't create file"), noDir.reason)
        s2.close()
        conn.close()
    }

    @Test fun syncReaderBoundsEveryLength() {
        assertEquals("sync packet id is not known", protocolDetail { SyncProtocol.readPacket(AdbStreamReader(ScriptedStream(listOf("JUNK\u0000\u0000\u0000\u0000".toByteArray())))) })
        assertEquals("sync DATA chunk of 70000 bytes", protocolDetail { SyncClient(ScriptedStream(listOf(SyncProtocol.header("DATA", 70000)))).recv("/x", 1_000_000) })
        assertEquals("sync DATA chunk of -1 bytes", protocolDetail { SyncClient(ScriptedStream(listOf(SyncProtocol.header("DATA", -1)))).recv("/x", 1_000_000) })
        assertEquals("sync FAIL message of 1000000 bytes", protocolDetail { SyncClient(ScriptedStream(listOf(SyncProtocol.header("FAIL", 1_000_000)))).recv("/x", 1_000_000) })
        assertEquals("unexpected sync reply OKAY to RECV", protocolDetail { SyncClient(ScriptedStream(listOf(SyncProtocol.header("OKAY", 0)))).recv("/x", 1_000_000) })
        assertEquals("the TV closed the sync stream", protocolDetail { SyncClient(ScriptedStream(emptyList())).recv("/x", 1_000_000) })
        assertFailsWith<IllegalArgumentException> { SyncProtocol.request("SEND", "x".repeat(2000)) }
    }

    @Test fun readerSkipAndChunk() = runBlocking<Unit> {
        val r = AdbStreamReader(ScriptedStream(listOf(byteArrayOf(1, 2, 3), byteArrayOf(), byteArrayOf(4, 5))))
        r.skip(2)
        assertContentEquals(byteArrayOf(3), r.readChunk(10))
        assertContentEquals(byteArrayOf(4, 5), r.readFully(2))
        assertNull(r.readChunk(1))
        assertTrue(r.atEof)
    }
}
