package tv.cleanyour.testing.fakeadbd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.cleanyour.adb.wire.AdbSocket
import java.io.IOException
import java.net.Socket
import kotlin.random.Random

// Shaping applied to what the fake adbd sends to the client (ARCHITECTURE 6.4 fault knobs).
class WireShaping(
    val latencyMs: Long = 0,
    val jitterMs: Long = 0,
    val bytesPerSecond: Long = 0,        // 0: unlimited
    val dropAfterBytes: Long = -1,       // -1: never; else the connection dies after this many bytes
)

private class Lane {
    val chunks = Channel<ByteArray>(Channel.UNLIMITED)
    var current: ByteArray? = null
    var pos = 0
}

// In-memory socket pair over coroutine channels, so a fake adbd and the client run under one
// test scheduler (virtual time for every timeout). The protocol's own windows bound the bytes in
// flight, which is why the lanes can be unbounded.
class PipeSocket private constructor(
    private val inbound: Lane,
    private val outbound: Lane,
    private val shaping: WireShaping?,
) : AdbSocket {
    @Volatile var closed = false
        private set
    private var bytesOut = 0L

    override suspend fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var cur = inbound.current
        if (cur == null || inbound.pos >= cur.size) {
            cur = try { inbound.chunks.receive() } catch (e: ClosedReceiveChannelException) { return -1 }
            inbound.current = cur
            inbound.pos = 0
        }
        val n = minOf(len, cur.size - inbound.pos)
        System.arraycopy(cur, inbound.pos, buf, off, n)
        inbound.pos += n
        return n
    }

    override suspend fun write(buf: ByteArray, off: Int, len: Int) {
        if (closed) throw IOException("socket closed")
        if (len == 0) return
        var n = len
        shaping?.let { s ->
            if (s.latencyMs > 0 || s.jitterMs > 0) delay(s.latencyMs + if (s.jitterMs > 0) Random.nextLong(s.jitterMs + 1) else 0)
            if (s.bytesPerSecond > 0) delay(n * 1000L / s.bytesPerSecond)
            if (s.dropAfterBytes >= 0 && bytesOut + n > s.dropAfterBytes) {
                n = (s.dropAfterBytes - bytesOut).toInt().coerceAtLeast(0)
                if (n > 0) deliver(buf.copyOfRange(off, off + n))
                close()
                throw IOException("connection dropped (fault)")
            }
        }
        deliver(buf.copyOfRange(off, off + n))
        bytesOut += n
    }

    private fun deliver(copy: ByteArray) {
        if (outbound.chunks.trySend(copy).isClosed) throw IOException("peer closed")
    }

    override suspend fun flush() {}

    override fun close() {
        closed = true
        outbound.chunks.close()
        inbound.chunks.close()
    }

    companion object {
        // First the client's end, then the fake adbd's end (which carries the shaping).
        fun pair(serverShaping: WireShaping? = null): Pair<PipeSocket, PipeSocket> {
            val a = Lane()
            val b = Lane()
            return PipeSocket(a, b, null) to PipeSocket(b, a, serverShaping)
        }
    }
}

// A blocking TCP socket as an AdbSocket (the fake adbd's loopback listener uses it). Reads and
// writes run on Dispatchers.IO; close() from any thread unblocks a pending read.
class BlockingAdbSocket(private val socket: Socket) : AdbSocket {
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()

    override suspend fun read(buf: ByteArray, off: Int, len: Int): Int = withContext(Dispatchers.IO) {
        try { input.read(buf, off, len) } catch (e: IOException) { if (socket.isClosed) -1 else throw e }
    }

    override suspend fun write(buf: ByteArray, off: Int, len: Int) = withContext(Dispatchers.IO) {
        output.write(buf, off, len)
    }

    override suspend fun flush() = withContext(Dispatchers.IO) { output.flush() }

    override fun close() {
        try { socket.close() } catch (e: IOException) { } catch (e: CancellationException) { throw e }
    }
}
