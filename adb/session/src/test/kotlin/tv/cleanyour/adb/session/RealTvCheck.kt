package tv.cleanyour.adb.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.Timeout
import tv.cleanyour.adb.wire.AdbSocket
import tv.cleanyour.adb.wire.SignerSelfTest
import tv.cleanyour.adb.wire.SocketFactory
import tv.cleanyour.adb.wire.SoftwareRsaAdbSigner
import tv.cleanyour.adb.wire.selfTest
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.testing.fakeadbd.BlockingAdbSocket
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyPair
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// The laptop half of docs/W2-device-check.md. Skipped unless -Pcleanyourtv.realTv=<ip> is given,
// so CI and forge never touch a real TV. Read-only: the only lines it sends are getprop.
// -Pcleanyourtv.realTvExpectNoPrompt=1 makes the first connect fail if the Allow dialog appears
// (the run after "Always allow" was ticked).
class RealTvCheck {
    @get:Rule val timeout: Timeout = Timeout.seconds(240)

    private object JvmSocketFactory : SocketFactory {
        override suspend fun connect(endpoint: Endpoint, timeoutMs: Long): AdbSocket = withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(endpoint.host, endpoint.port), timeoutMs.toInt())
                BlockingAdbSocket(socket)
            } catch (e: Exception) {
                socket.close()
                throw e
            }
        }
    }

    // One key per laptop, kept outside the repository with owner-only permissions.
    private fun signer(file: File): SoftwareRsaAdbSigner {
        val kf = KeyFactory.getInstance("RSA")
        if (file.isFile) {
            val priv = kf.generatePrivate(PKCS8EncodedKeySpec(file.readBytes())) as RSAPrivateCrtKey
            return SoftwareRsaAdbSigner(KeyPair(kf.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent)), priv))
        }
        file.parentFile.mkdirs()
        val pair = SoftwareRsaAdbSigner.generateKeyPair()
        file.writeBytes(pair.private.encoded)
        runCatching { Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------")) }
        return SoftwareRsaAdbSigner(pair)
    }

    @Test fun oneAllowThenSilentReconnects() = runBlocking<Unit> {
        val host = System.getProperty("cleanyourtv.realTv")?.takeIf { it.isNotBlank() }
        assumeTrue("pass -Pcleanyourtv.realTv=<tv ip> to run the real TV check", host != null)
        val keyFile = File(System.getProperty("cleanyourtv.realTvKey")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.cleanyourtv-w2-check/adbkey.p8"))
        val expectNoPrompt = System.getProperty("cleanyourtv.realTvExpectNoPrompt") == "1"
        val signer = signer(keyFile)
        assertEquals(SignerSelfTest.Pass, signer.selfTest())
        println("W2-CHECK key file ${keyFile.absolutePath}")
        println("W2-CHECK fingerprint ${signer.fingerprint}")
        val endpoint = Endpoint.PlainTcp(host!!)

        val prompts = AtomicInteger()
        val first = AdbSessionManager(JvmSocketFactory, signer)
        val opened = first.open(endpoint, null) { fp ->
            prompts.incrementAndGet()
            println("W2-CHECK Allow dialog raised; the TV must show $fp")
        }
        val session = assertIs<OpenResult.Ok>(opened, "first connect: $opened").session
        val id = session.identity
        println("W2-CHECK connected: tvId ${id.tvId.value} model ${id.model} device ${id.device} sdk ${id.sdk} adbSecure ${id.adbSecure} maxPayload ${id.maxPayload}")
        println("W2-CHECK features ${id.features.sorted().joinToString(",")}")
        val r = session.exec(line("getprop ro.build.version.release"))
        println("W2-CHECK exec exit ${r.exit} release ${r.stdout.trim()}")
        assertEquals(0, r.exit)
        first.closeAll()
        println("W2-CHECK first connect prompts: ${prompts.get()}")
        if (expectNoPrompt) assertEquals(0, prompts.get(), "the TV raised the Allow dialog for a key it should know")

        // A fresh TCP connection and a fresh manager with the same key and the expected TvId (D37).
        val again = AtomicInteger()
        val second = AdbSessionManager(JvmSocketFactory, signer)
        val reopened = second.open(endpoint, id.tvId) { again.incrementAndGet() }
        assertIs<OpenResult.Ok>(reopened, "second connect: $reopened")
        second.closeAll()
        println("W2-CHECK second connect prompts: ${again.get()}")
        assertEquals(0, again.get(), "the second connect raised the Allow dialog")
        println("W2-CHECK PASS")
    }
}
