package tv.cleanyour.android.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.cleanyour.adb.wire.AdbSocket
import tv.cleanyour.adb.wire.SocketFactory
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.Endpoint
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

// Plain TCP to port 5555 (D9). The host must be a literal IP address: no name resolution, so the
// address the user confirmed is the address we connect to. SO_KEEPALIVE and TCP_NODELAY as
// ARCHITECTURE 3.10; the real liveness check is the session's ping.
class PlainSocketFactory : SocketFactory {
    override suspend fun connect(endpoint: Endpoint, timeoutMs: Long): AdbSocket {
        if (endpoint !is Endpoint.PlainTcp) throw AdbException(AdbError.Transport.Protocol("TLS endpoints are not supported in this version"))
        if (!isLiteralAddress(endpoint.host)) throw UnknownHostException("host must be an IP address")
        return withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMs.toInt())
                TcpAdbSocket(socket)
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
        }
    }

    companion object {
        private val IPV4 = Regex("^(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])){3}$")
        private val IPV6 = Regex("^[0-9A-Fa-f:.]{2,45}$")
        fun isLiteralAddress(host: String): Boolean = IPV4.matches(host) || (host.contains(':') && IPV6.matches(host))
    }
}

// Blocking socket I/O on Dispatchers.IO; close() from any thread unblocks a pending read.
class TcpAdbSocket(private val socket: Socket) : AdbSocket {
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
