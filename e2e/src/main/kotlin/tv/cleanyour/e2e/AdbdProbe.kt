package tv.cleanyour.e2e

import tv.cleanyour.adb.wire.AuthToken
import java.io.DataInputStream
import java.io.EOFException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

// A minimal ADB handshake used only to prove that the harness reaches a real adbd before any
// scenario relies on :adb:session (which W2 delivers): CNXN, AUTH with the host key when the
// device asks, and one fixed shell stream (`echo`). It is test code with one constant service
// string, never a client for the app; the app's protocol code is :adb:wire.
class AdbdProbe(private val signer: HostKeySigner?, private val timeoutMs: Int = 10_000) {

    enum class AuthPath { NONE, SIGNATURE, PUBLIC_KEY }

    data class Result(
        val banner: String,
        val props: Map<String, String>,
        val features: Set<String>,
        val version: Int,
        val maxPayload: Int,
        val auth: AuthPath,
        val connectMillis: Long,
        val echo: String?,
    )

    class Message(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray) {
        val name: String get() = String(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array(), Charsets.US_ASCII)
    }

    companion object {
        const val A_VERSION = 0x01000001
        const val MAX_PAYLOAD = 256 * 1024
        const val MAX_ACCEPTED = 1024 * 1024
        val CNXN = cmd("CNXN"); val AUTH = cmd("AUTH"); val OPEN = cmd("OPEN"); val OKAY = cmd("OKAY")
        val WRTE = cmd("WRTE"); val CLSE = cmd("CLSE")
        const val AUTH_TOKEN = 1; const val AUTH_SIGNATURE = 2; const val AUTH_RSAPUBLICKEY = 3
        const val ECHO_TEXT = "cyt-e2e-ok"

        fun cmd(s: String): Int = ByteBuffer.wrap(s.toByteArray(Charsets.US_ASCII)).order(ByteOrder.LITTLE_ENDIAN).int

        fun encode(command: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)): ByteArray {
            val b = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.size)
            b.putInt(data.fold(0) { acc, x -> acc + (x.toInt() and 0xFF) })
            b.putInt(command xor -1)
            b.put(data)
            return b.array()
        }

        fun read(input: DataInputStream): Message {
            val h = ByteArray(24)
            input.readFully(h)
            val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
            val command = b.int; val a0 = b.int; val a1 = b.int; val len = b.int; b.int; val magic = b.int
            if (magic != command xor -1) throw IllegalStateException("bad magic")
            if (len < 0 || len > MAX_ACCEPTED) throw IllegalStateException("payload length $len out of bounds")
            val data = ByteArray(len)
            input.readFully(data)
            return Message(command, a0, a1, data)
        }

        // "device::ro.product.name=x;ro.product.model=y;features=a,b" into props and features.
        fun parseBanner(banner: String): Pair<Map<String, String>, Set<String>> {
            val props = banner.substringAfter("::", "").split(';').filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            val features = props["features"]?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
            return props - "features" to features
        }
    }

    fun connect(host: String, port: Int, allowPublicKey: Boolean = false, allowTimeoutMs: Long = 30_000, echo: Boolean = true): Result {
        val started = System.nanoTime()
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val input = DataInputStream(socket.getInputStream().buffered())
            val out = socket.getOutputStream()
            out.send(encode(CNXN, A_VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray()))

            var auth = AuthPath.NONE
            var signedOnce = false
            var connected: Message? = null
            while (connected == null) {
                val m = try { read(input) } catch (e: EOFException) { throw IllegalStateException("adbd closed the connection during the handshake", e) }
                when (m.command) {
                    CNXN -> connected = m
                    AUTH -> {
                        check(m.arg0 == AUTH_TOKEN) { "unexpected AUTH type ${m.arg0}" }
                        val s = signer ?: error("adbd asked for AUTH and the harness has no host key")
                        if (!signedOnce) {
                            signedOnce = true
                            auth = AuthPath.SIGNATURE
                            out.send(encode(AUTH, AUTH_SIGNATURE, 0, s.signBlocking(AuthToken(m.data))))
                        } else {
                            check(allowPublicKey) { "adbd does not trust the host key and allowPublicKey is off" }
                            auth = AuthPath.PUBLIC_KEY
                            socket.soTimeout = allowTimeoutMs.toInt()
                            out.send(encode(AUTH, AUTH_RSAPUBLICKEY, 0, (s.publicKeyLine + "\u0000").toByteArray()))
                        }
                    }
                    else -> error("unexpected ${m.name} before CNXN")
                }
            }
            socket.soTimeout = timeoutMs
            val connectMillis = (System.nanoTime() - started) / 1_000_000
            val banner = String(connected.data, Charsets.UTF_8).trimEnd('\u0000')
            val (props, features) = parseBanner(banner)
            val echoed = if (echo) echo(input, out, minOf(connected.arg1, MAX_PAYLOAD)) else null
            return Result(banner, props, features, connected.arg0, connected.arg1, auth, connectMillis, echoed)
        }
    }

    // One stream: OPEN "shell:echo cyt-e2e-ok", collect WRTE payloads until CLSE.
    private fun echo(input: DataInputStream, out: OutputStream, maxPayload: Int): String {
        val local = 1
        out.send(encode(OPEN, local, 0, "shell:echo $ECHO_TEXT\u0000".toByteArray()))
        val text = StringBuilder()
        var remote = 0
        while (true) {
            val m = read(input)
            when (m.command) {
                OKAY -> remote = m.arg0
                WRTE -> {
                    text.append(String(m.data, Charsets.UTF_8))
                    out.send(encode(OKAY, local, m.arg0))
                }
                CLSE -> {
                    if (remote != 0) out.send(encode(CLSE, local, remote))
                    return text.toString().trim()
                }
                else -> error("unexpected ${m.name} on the echo stream")
            }
            check(text.length <= maxPayload) { "echo output too long" }
        }
    }

    private fun OutputStream.send(bytes: ByteArray) { write(bytes); flush() }
}
