package tv.cleanyour.adb.wire

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.runBlocking
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.testing.fakeadbd.PipeSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class FrameCodecTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    private fun decode(bytes: ByteArray): AdbMessage? = runBlocking {
        val (a, b) = PipeSocket.pair()
        if (bytes.isNotEmpty()) b.write(bytes, 0, bytes.size)
        b.close()
        FrameCodec.readMessage(a)
    }

    private fun protocolDetail(block: () -> Unit): String {
        val e = assertFailsWith<AdbException> { block() }
        val err = assertIs<AdbError.Transport.Protocol>(e.error)
        return err.detail
    }

    @Test fun roundTripsEveryCommand() {
        val payload = "device::features=shell_v2,cmd\u0000".toByteArray()
        val m = decode(FrameCodec.encode(AdbProtocol.A_CNXN, 0x01000000, 1024 * 1024, payload))!!
        assertEquals(AdbProtocol.A_CNXN, m.command)
        assertEquals(0x01000000, m.arg0)
        assertEquals(1024 * 1024, m.arg1)
        assertContentEquals(payload, m.payload)
        val empty = decode(FrameCodec.encode(AdbProtocol.A_OKAY, 7, 9))!!
        assertEquals(AdbProtocol.A_OKAY, empty.command)
        assertEquals(0, empty.payload.size)
    }

    @Test fun headerLayoutIsLittleEndianWithMagic() {
        val f = FrameCodec.encode(AdbProtocol.A_WRTE, 1, 2, byteArrayOf(1, 2, 3))
        assertEquals(27, f.size)
        assertEquals(AdbProtocol.A_WRTE, FrameCodec.getIntLe(f, 0))
        assertEquals(3, FrameCodec.getIntLe(f, 12))
        assertEquals(6, FrameCodec.getIntLe(f, 16))
        assertEquals(AdbProtocol.A_WRTE xor -1, FrameCodec.getIntLe(f, 20))
    }

    @Test fun cleanEndOfStreamIsNull() {
        assertNull(decode(ByteArray(0)))
    }

    @Test fun truncatedHeaderAndPayloadAreProtocolErrors() {
        val f = FrameCodec.encode(AdbProtocol.A_WRTE, 1, 2, ByteArray(10))
        assertEquals("truncated header", protocolDetail { decode(f.copyOf(10)) })
        assertEquals("truncated payload", protocolDetail { decode(f.copyOf(30)) })
    }

    @Test fun badMagicIsRefused() {
        val f = FrameCodec.encode(AdbProtocol.A_OKAY, 1, 2)
        f[20] = (f[20].toInt() xor 1).toByte()
        assertEquals("bad magic for OKAY", protocolDetail { decode(f) })
    }

    @Test fun unknownCommandIsRefused() {
        val f = ByteArray(24)
        FrameCodec.putIntLe(f, 0, 0x41414141)
        FrameCodec.putIntLe(f, 20, 0x41414141 xor -1)
        assertEquals("unknown command 0x41414141", protocolDetail { decode(f) })
    }

    @Test fun lengthsAreBoundedPerCommand() {
        fun withLength(command: Int, len: Int): ByteArray {
            val f = FrameCodec.encode(command, 0, 0)
            FrameCodec.putIntLe(f, 12, len)
            return f
        }
        assertEquals("WRTE payload ${AdbProtocol.MAX_PAYLOAD + 1} exceeds ${AdbProtocol.MAX_PAYLOAD}", protocolDetail { decode(withLength(AdbProtocol.A_WRTE, AdbProtocol.MAX_PAYLOAD + 1)) })
        assertEquals("CNXN payload ${AdbProtocol.MAX_CONTROL_PAYLOAD + 1} exceeds ${AdbProtocol.MAX_CONTROL_PAYLOAD}", protocolDetail { decode(withLength(AdbProtocol.A_CNXN, AdbProtocol.MAX_CONTROL_PAYLOAD + 1)) })
        assertEquals("OKAY payload 1 exceeds 0", protocolDetail { decode(withLength(AdbProtocol.A_OKAY, 1)) })
        assertEquals("CLSE payload 4 exceeds 0", protocolDetail { decode(withLength(AdbProtocol.A_CLSE, 4)) })
        assertEquals("WRTE payload -1 exceeds ${AdbProtocol.MAX_PAYLOAD}", protocolDetail { decode(withLength(AdbProtocol.A_WRTE, -1)) })
        assertEquals("AUTH payload ${Int.MIN_VALUE} exceeds ${AdbProtocol.MAX_CONTROL_PAYLOAD}", protocolDetail { decode(withLength(AdbProtocol.A_AUTH, Int.MIN_VALUE)) })
    }

    @Test fun checksumMismatchIsRefused() {
        val f = FrameCodec.encode(AdbProtocol.A_WRTE, 1, 2, byteArrayOf(5, 6, 7))
        FrameCodec.putIntLe(f, 16, 999)
        assertEquals("checksum mismatch on WRTE", protocolDetail { decode(f) })
    }

    @Test fun checksumIsTheUnsignedByteSum() {
        assertEquals(0xff + 0x01 + 0x80, FrameCodec.checksum(byteArrayOf(0xff.toByte(), 1, 0x80.toByte())))
    }

    @Test fun maxPayloadFrameDecodes() {
        val big = ByteArray(AdbProtocol.MAX_PAYLOAD) { (it % 251).toByte() }
        val m = decode(FrameCodec.encode(AdbProtocol.A_WRTE, 1, 2, big))!!
        assertContentEquals(big, m.payload)
    }
}
