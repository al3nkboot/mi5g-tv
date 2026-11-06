package tv.cleanyour.core.flows.smarttv

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// M4 item 31: the smallest RFC 6455 client the Samsung and LG remotes need (text frames, ping, close).
// Only ever opened to a private LAN address the user typed (SmartTvProtocol.isPrivateLan): the TVs
// serve self-signed certificates on their wss ports, so TLS here is for transport, not identity.
class MiniWebSocket private constructor(private val socket: Socket) : AutoCloseable {
    private val input: InputStream = socket.getInputStream()
    private val output: OutputStream = socket.getOutputStream()
    private val random = SecureRandom()

    fun sendText(text: String) = synchronized(output) {
        output.write(Frames.encode(0x1, text.toByteArray(Charsets.UTF_8), mask()))
        output.flush()
    }

    /** The next text message, answering pings on the way; null when the TV closed the socket. */
    fun readText(): String? {
        while (true) {
            val f = Frames.read(input) ?: return null
            when (f.opcode) {
                0x1 -> return f.payload.toString(Charsets.UTF_8)
                0x9 -> synchronized(output) { output.write(Frames.encode(0xA, f.payload, mask())); output.flush() }
                0x8 -> return null
            }
        }
    }

    private fun mask(): ByteArray = ByteArray(4).also { random.nextBytes(it) }

    override fun close() { runCatching { socket.close() } }

    companion object {
        fun open(url: String, timeoutMs: Int = 5000): MiniWebSocket {
            val u = URI(url)
            require(u.scheme == "ws" || u.scheme == "wss") { "not a websocket url" }
            require(SmartTvProtocol.isPrivateLan(u.host)) { "TV address must be on the local network" }
            val port = if (u.port > 0) u.port else if (u.scheme == "wss") 443 else 80
            val raw = Socket()
            raw.connect(InetSocketAddress(u.host, port), timeoutMs)
            raw.soTimeout = 0
            val s = if (u.scheme == "wss") lanTls().socketFactory.createSocket(raw, u.host, port, true) else raw
            s.soTimeout = timeoutMs
            val key = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
            val path = (u.rawPath.ifEmpty { "/" }) + (u.rawQuery?.let { "?$it" } ?: "")
            val req = "GET $path HTTP/1.1\r\nHost: ${u.host}:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n"
            s.getOutputStream().apply { write(req.toByteArray(Charsets.US_ASCII)); flush() }
            val status = readHeaders(s.getInputStream())
            if (!status.startsWith("HTTP/1.1 101")) { s.close(); error("TV refused the connection: $status") }
            s.soTimeout = 0
            return MiniWebSocket(s)
        }

        private fun readHeaders(input: InputStream): String {
            val buf = ByteArrayOutputStream()
            var last4 = 0
            while (buf.size() < 16 * 1024) {
                val b = input.read()
                if (b < 0) break
                buf.write(b)
                last4 = (last4 shl 8) or b
                if (last4 == 0x0D0A0D0A) break
            }
            return buf.toString(Charsets.US_ASCII.name()).lineSequence().firstOrNull().orEmpty()
        }

        private fun lanTls(): SSLContext {
            val trustLan = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustLan), SecureRandom()) }
        }
    }
}

internal object Frames {
    class Frame(val opcode: Int, val payload: ByteArray)
    const val MAX_PAYLOAD = 1 shl 20

    fun encode(opcode: Int, payload: ByteArray, mask: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x80 or opcode)
        val m = if (mask != null) 0x80 else 0
        when {
            payload.size < 126 -> out.write(m or payload.size)
            payload.size < 65536 -> { out.write(m or 126); out.write(payload.size ushr 8); out.write(payload.size and 0xff) }
            else -> { out.write(m or 127); for (i in 7 downTo 0) out.write(((payload.size.toLong() ushr (8 * i)) and 0xff).toInt()) }
        }
        if (mask != null) {
            out.write(mask)
            out.write(ByteArray(payload.size) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() })
        } else out.write(payload)
        return out.toByteArray()
    }

    /** One whole message (continuation frames joined); null at end of stream. */
    fun read(input: InputStream): Frame? {
        var opcode = -1
        val data = ByteArrayOutputStream()
        while (true) {
            val b0 = input.read(); if (b0 < 0) return null
            val b1 = input.read(); if (b1 < 0) return null
            var len = (b1 and 0x7f).toLong()
            if (len == 126L) len = ((readN(input, 1) shl 8) or readN(input, 1)).toLong()
            else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or readN(input, 1).toLong() } }
            if (len > MAX_PAYLOAD || data.size() + len > MAX_PAYLOAD) error("frame too large")
            val mask = if (b1 and 0x80 != 0) ByteArray(4) { readN(input, 1).toByte() } else null
            val p = ByteArray(len.toInt())
            var off = 0
            while (off < p.size) { val r = input.read(p, off, p.size - off); if (r < 0) return null; off += r }
            if (mask != null) for (i in p.indices) p[i] = (p[i].toInt() xor mask[i % 4].toInt()).toByte()
            val op = b0 and 0x0f
            if (op >= 0x8) return Frame(op, p)          // control frames are never fragmented
            if (op != 0) opcode = op
            data.write(p)
            if (b0 and 0x80 != 0) return Frame(opcode, data.toByteArray())
        }
    }

    private fun readN(input: InputStream, @Suppress("UNUSED_PARAMETER") n: Int): Int {
        val b = input.read(); if (b < 0) error("unexpected end of stream"); return b
    }
}
