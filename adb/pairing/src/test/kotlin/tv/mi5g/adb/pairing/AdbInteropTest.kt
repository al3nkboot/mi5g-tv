package tv.mi5g.adb.pairing

import org.conscrypt.Conscrypt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class AdbInteropTest {
    @Test fun adbAndClientInteroperateWithPairingServer() {
        val adb = "/home/box/android-sdk/platform-tools/adb"
        // Runs only where the SDK adb and openssl exist (this box); the real host adb is the oracle.
        org.junit.Assume.assumeTrue(Files.isExecutable(java.nio.file.Path.of(adb)) && runCatching { ProcessBuilder("openssl", "version").start().waitFor() == 0 }.getOrDefault(false))
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        val temp = Files.createTempDirectory("adb-pair-test")
        val p12 = temp.resolve("server.p12")
        val gen = ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048", "-keyout", temp.resolve("key.pem").toString(), "-out", temp.resolve("cert.pem").toString(), "-days", "1", "-nodes", "-subj", "/CN=localhost").redirectErrorStream(true).start()
        assertTrue(gen.inputStream.readBytes().toString(Charsets.UTF_8), gen.waitFor() == 0)
        val pkcs = ProcessBuilder("openssl", "pkcs12", "-export", "-out", p12.toString(), "-inkey", temp.resolve("key.pem").toString(), "-in", temp.resolve("cert.pem").toString(), "-passout", "pass:pairing").redirectErrorStream(true).start()
        assertTrue(pkcs.inputStream.readBytes().toString(Charsets.UTF_8), pkcs.waitFor() == 0)
        val store = KeyStore.getInstance("PKCS12").apply { Files.newInputStream(p12).use { load(it, "pairing".toCharArray()) } }
        val kmf = KeyManagerFactory.getInstance("SunX509").apply { init(store, "pairing".toCharArray()) }
        val trustAny = arrayOf(object : javax.net.ssl.X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        })
        val context = SSLContext.getInstance("TLSv1.3", "Conscrypt").apply { init(kmf.keyManagers, trustAny, null) }
        val fakeTvGuid = "adb-MI5GTEST-a1b2c3"

        fun server(runAdb: Boolean): PairingServer {
            val s = PairingServer(context, fakeTvGuid)
            val result = s.start()
            if (runAdb) {
                val adbPort = 20000 + (System.nanoTime() % 20000).toInt()
                try {
                    val env = mapOf("HOME" to temp.resolve("adb-home").toString())
                    Files.createDirectories(temp.resolve("adb-home"))
                    val p = ProcessBuilder(adb, "-P", adbPort.toString(), "pair", "127.0.0.1:${s.port}", "123456").apply { environment().putAll(env); redirectErrorStream(true) }.start()
                    val output = p.inputStream.readBytes().toString(Charsets.UTF_8)
                    assertEquals(0, result.get())
                    assertTrue(output, p.waitFor() == 0 && output.contains("Successfully paired"))
                } finally { ProcessBuilder(adb, "-P", adbPort.toString(), "kill-server").start().waitFor() }
            } else {
                val resultClient = PairingClient { host, port ->
                    val socket = context.socketFactory.createSocket(host, port) as SSLSocket
                    socket.startHandshake()
                    SocketPairingTls(socket) { label, length -> Conscrypt.exportKeyingMaterial(socket, label.toString(Charsets.UTF_8), null, length) }
                }.pair("127.0.0.1", s.port, "123456", "QUJD phone\u0000")
                assertEquals(0, result.get())
                assertEquals(PairingResult.Paired(fakeTvGuid), resultClient)
            }
            assertEquals(0, result.get())
            return s
        }
        server(false)
        server(true)
    }

    private class PairingServer(private val context: SSLContext, private val ownKey: String) {
        private val listener = ServerSocket(0)
        val port get() = listener.localPort
        fun start(): java.util.concurrent.Future<Int> {
            listener.close()
            return java.util.concurrent.Executors.newSingleThreadExecutor().submit<Int> {
            listener.use {
                val socket = context.serverSocketFactory.createServerSocket().apply { bind(java.net.InetSocketAddress("127.0.0.1", port)) }
                socket.use {
                    val tls = socket.accept() as SSLSocket
                    tls.use {
                        tls.startHandshake()
                        val exported = Conscrypt.exportKeyingMaterial(tls, "adb-label\u0000", null, 64)
                        val auth = Spake2(false, "123456".toByteArray() + exported)
                        val input = DataInputStream(tls.inputStream); val output = DataOutputStream(tls.outputStream)
                        val (type, theirs) = readPacket(input); check(type == 0)
                        writePacket(output, 0, auth.message)
                        val key = auth.process(theirs) ?: error("Bad SPAKE2")
                        val cipher = PairCipher(key)
                        val (peerType, payload) = readPacket(input); check(peerType == 1)
                        val remote = cipher.decrypt(payload) ?: error("Peer info authentication failed")
                        check(remote.size == 8192 && remote[0].toInt() == 0)
                        val line = remote.copyOfRange(1, remote.size).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
                        val info = ByteArray(8192); info[0] = 1; ownKey.toByteArray().copyInto(info, 1)
                        writePacket(output, 1, cipher.encrypt(info))
                        check(line.isNotBlank()); 0
                    }
                }
            }
            }
        }
        private fun readPacket(i: DataInputStream): Pair<Int, ByteArray> { check(i.readUnsignedByte() == 1); val t=i.readUnsignedByte(); val n=i.readInt(); return t to ByteArray(n).also(i::readFully) }
        private fun writePacket(o: DataOutputStream, t: Int, b: ByteArray) { o.writeByte(1);o.writeByte(t);o.writeInt(b.size);o.write(b);o.flush() }
    }
}

