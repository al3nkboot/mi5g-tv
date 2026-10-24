/*
 * Copyright (c) 2021 mobile.dev inc.
 * Copyright (c) 2026 the Clean Your TV authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Derived from dadb 2.0.0 (AdbShell.kt, AdbSync.kt); see NOTICE. Changes: every length read from
 * the TV is bounded before allocation, the reader works on AdbStream frames, pulls carry a byte
 * cap, and sync failures are typed.
 */

package tv.cleanyour.adb.wire

import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import java.io.ByteArrayOutputStream

// Reads exact byte counts from an AdbStream whose WRTE frames do not line up with the protocol
// packets inside them (shell v2 and sync both run over a byte stream).
class AdbStreamReader(private val stream: AdbStream) {
    private var buf: ByteArray = FrameCodec.EMPTY
    private var pos = 0
    private var eof = false

    private suspend fun fill(): Boolean {
        if (eof) return false
        val next = stream.read()
        if (next == null) { eof = true; return false }
        if (next.isEmpty()) return fill()
        buf = next
        pos = 0
        return true
    }

    val atEof: Boolean get() = eof && pos >= buf.size

    // Exactly n bytes, or null when the stream ended before the first byte; a stream that ends in
    // the middle of the n bytes is a protocol error.
    suspend fun readFully(n: Int): ByteArray? {
        require(n >= 0)
        if (n == 0) return FrameCodec.EMPTY
        val out = ByteArray(n)
        var got = 0
        while (got < n) {
            if (pos >= buf.size && !fill()) {
                if (got == 0) return null
                throw protocolError("the TV ended the stream mid-packet")
            }
            val take = minOf(n - got, buf.size - pos)
            System.arraycopy(buf, pos, out, got, take)
            pos += take
            got += take
        }
        return out
    }

    suspend fun readByte(): Int? = readFully(1)?.let { it[0].toInt() and 0xff }

    suspend fun readIntLe(): Int? = readFully(4)?.let { FrameCodec.getIntLe(it, 0) }

    // Whatever is available, at most max bytes; null at end of stream.
    suspend fun readChunk(max: Int): ByteArray? {
        require(max > 0)
        if (pos >= buf.size && !fill()) return null
        val take = minOf(max, buf.size - pos)
        val out = buf.copyOfRange(pos, pos + take)
        pos += take
        return out
    }

    // Discards n bytes (used to skip an oversized packet without holding it).
    suspend fun skip(n: Long) {
        var left = n
        while (left > 0) {
            if (pos >= buf.size && !fill()) throw protocolError("the TV ended the stream mid-packet")
            val take = minOf(left, (buf.size - pos).toLong()).toInt()
            pos += take
            left -= take
        }
    }
}

// shell v2 packets: one id byte, a little-endian length, the payload (AOSP shell_protocol.h).
sealed interface ShellPacket {
    class Stdout(val bytes: ByteArray) : ShellPacket
    class Stderr(val bytes: ByteArray) : ShellPacket
    class Exit(val code: Int) : ShellPacket
}

object ShellV2 {
    const val ID_STDIN = 0
    const val ID_STDOUT = 1
    const val ID_STDERR = 2
    const val ID_EXIT = 3
    const val ID_CLOSE_STDIN = 4
    const val ID_WINDOW_SIZE_CHANGE = 5
    const val MAX_PACKET = AdbProtocol.MAX_PAYLOAD

    fun encode(id: Int, payload: ByteArray = FrameCodec.EMPTY): ByteArray {
        val out = ByteArray(5 + payload.size)
        out[0] = id.toByte()
        FrameCodec.putIntLe(out, 1, payload.size)
        System.arraycopy(payload, 0, out, 5, payload.size)
        return out
    }

    fun exitPacket(code: Int): ByteArray = encode(ID_EXIT, byteArrayOf(code.toByte()))

    // Null when the TV closed the stream. Only stdout, stderr and exit may come from the TV.
    suspend fun next(reader: AdbStreamReader): ShellPacket? {
        val id = reader.readByte() ?: return null
        val length = reader.readIntLe() ?: throw protocolError("the TV ended the stream mid-packet")
        if (length < 0 || length > MAX_PACKET) throw protocolError("shell packet of $length bytes")
        return when (id) {
            ID_STDOUT -> ShellPacket.Stdout(reader.readFully(length) ?: throw protocolError("the TV ended the stream mid-packet"))
            ID_STDERR -> ShellPacket.Stderr(reader.readFully(length) ?: throw protocolError("the TV ended the stream mid-packet"))
            ID_EXIT -> {
                if (length != 1) throw protocolError("shell exit packet of $length bytes")
                ShellPacket.Exit(reader.readByte() ?: throw protocolError("the TV ended the stream mid-packet"))
            }
            else -> throw protocolError("shell packet id $id")
        }
    }
}

// Raised when adbd answers a sync request with FAIL; the connection and the stream are healthy.
class SyncFailedException(val reason: String) : Exception(reason)

// The sync service (AOSP file_sync_protocol.h, v1): 4-byte ids, little-endian arguments, DATA
// chunks of at most 64 KiB.
object SyncProtocol {
    const val DATA_MAX = 64 * 1024
    const val MAX_PATH_BYTES = 1024
    const val MAX_FAIL_BYTES = 4096
    const val SEND = "SEND"
    const val RECV = "RECV"
    const val STAT = "STAT"
    const val DATA = "DATA"
    const val DONE = "DONE"
    const val OKAY = "OKAY"
    const val FAIL = "FAIL"
    const val QUIT = "QUIT"
    val IDS: Set<String> = setOf(SEND, RECV, STAT, DATA, DONE, OKAY, FAIL, QUIT, "LIST")

    class Packet(val id: String, val arg: Int)

    fun header(id: String, arg: Int): ByteArray {
        val out = ByteArray(8)
        System.arraycopy(id.toByteArray(Charsets.US_ASCII), 0, out, 0, 4)
        FrameCodec.putIntLe(out, 4, arg)
        return out
    }

    fun request(id: String, path: String): ByteArray {
        val p = path.toByteArray(Charsets.UTF_8)
        require(p.size in 1..MAX_PATH_BYTES) { "sync path length" }
        return header(id, p.size) + p
    }

    suspend fun readPacket(reader: AdbStreamReader): Packet? {
        val h = reader.readFully(8) ?: return null
        val id = String(h, 0, 4, Charsets.US_ASCII)
        if (id !in IDS) throw protocolError("sync packet id is not known")
        return Packet(id, FrameCodec.getIntLe(h, 4))
    }

    suspend fun readFailMessage(reader: AdbStreamReader, length: Int): String {
        if (length < 0 || length > MAX_FAIL_BYTES) throw protocolError("sync FAIL message of $length bytes")
        val bytes = reader.readFully(length) ?: throw protocolError("the TV ended the stream mid-packet")
        return String(bytes, Charsets.UTF_8)
    }
}

// Client side of one sync: stream. Push (SEND) and pull (RECV) with a byte cap. With
// idleTimeoutMs > 0 every wait on the TV (an OKAY for our frame, the next packet) is bounded and
// fails as Transport.Timeout(phase).
class SyncClient(private val stream: AdbStream, private val idleTimeoutMs: Long = 0, private val phase: String = "sync") {
    private val reader = AdbStreamReader(stream)

    private class Box<T>(val value: T)

    private suspend fun <T> bounded(block: suspend () -> T): T {
        if (idleTimeoutMs <= 0) return block()
        val r = kotlinx.coroutines.withTimeoutOrNull(idleTimeoutMs) { Box(block()) }
            ?: throw AdbException(AdbError.Transport.Timeout(phase))
        return r.value
    }

    private suspend fun write(bytes: ByteArray) = bounded { stream.write(bytes) }

    private suspend fun readPacket(): SyncProtocol.Packet? = bounded { SyncProtocol.readPacket(reader) }

    // chunks() returns at most DATA_MAX bytes per call and null at the end; the caller counts.
    suspend fun send(remotePath: String, mode: Int, mtimeSeconds: Int, chunks: suspend () -> ByteArray?, onProgress: suspend (Long) -> Unit = {}) {
        write(SyncProtocol.request(SyncProtocol.SEND, "$remotePath,$mode"))
        var sent = 0L
        try {
            while (true) {
                val chunk = chunks() ?: break
                if (chunk.isEmpty()) continue
                require(chunk.size <= SyncProtocol.DATA_MAX) { "sync chunk over 64 KiB" }
                write(SyncProtocol.header(SyncProtocol.DATA, chunk.size) + chunk)
                sent += chunk.size
                onProgress(sent)
            }
            write(SyncProtocol.header(SyncProtocol.DONE, mtimeSeconds))
        } catch (e: AdbException) {
            // adbd answers FAIL and closes the stream when it cannot open the file; the FAIL is
            // still queued even though our next write was refused, so report it instead.
            if (e.error is AdbError.Transport.Protocol) pendingFail()?.let { throw it }
            throw e
        }
        val reply = readPacket() ?: throw goneWhileSyncing()
        when (reply.id) {
            SyncProtocol.OKAY -> return
            SyncProtocol.FAIL -> throw SyncFailedException(SyncProtocol.readFailMessage(reader, reply.arg))
            else -> throw protocolError("unexpected sync reply ${reply.id} to SEND")
        }
    }

    private suspend fun pendingFail(): SyncFailedException? {
        val p = try { SyncProtocol.readPacket(reader) } catch (e: AdbException) { null } ?: return null
        if (p.id != SyncProtocol.FAIL) return null
        return try { SyncFailedException(SyncProtocol.readFailMessage(reader, p.arg)) } catch (e: AdbException) { null }
    }

    // Returns the file; a file over maxBytes is refused before the extra bytes are kept.
    suspend fun recv(remotePath: String, maxBytes: Long): ByteArray {
        require(maxBytes >= 0)
        write(SyncProtocol.request(SyncProtocol.RECV, remotePath))
        val out = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val p = readPacket() ?: throw goneWhileSyncing()
            when (p.id) {
                SyncProtocol.DATA -> {
                    if (p.arg < 0 || p.arg > SyncProtocol.DATA_MAX) throw protocolError("sync DATA chunk of ${p.arg} bytes")
                    total += p.arg
                    if (total > maxBytes) throw protocolError("pull larger than $maxBytes bytes")
                    out.write(bounded { reader.readFully(p.arg) } ?: throw protocolError("the TV ended the stream mid-packet"))
                }
                SyncProtocol.DONE -> return out.toByteArray()
                SyncProtocol.FAIL -> throw SyncFailedException(SyncProtocol.readFailMessage(reader, p.arg))
                else -> throw protocolError("unexpected sync reply ${p.id} to RECV")
            }
        }
    }

    suspend fun quit() {
        try { stream.write(SyncProtocol.header(SyncProtocol.QUIT, 0)) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
    }

    private fun goneWhileSyncing() = protocolError("the TV closed the sync stream")
}
