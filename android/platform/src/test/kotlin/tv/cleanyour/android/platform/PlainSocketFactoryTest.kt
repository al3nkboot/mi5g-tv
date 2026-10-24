package tv.cleanyour.android.platform

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import tv.cleanyour.adb.wire.AdbConnection
import tv.cleanyour.adb.wire.AdbService
import tv.cleanyour.adb.wire.AdbStreamReader
import tv.cleanyour.adb.wire.ShellPacket
import tv.cleanyour.adb.wire.ShellV2
import tv.cleanyour.adb.wire.SoftwareRsaAdbSigner
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.RawShell
import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.testing.fakeadbd.FakeAdbd
import tv.cleanyour.testing.fakeadbd.Faults
import tv.cleanyour.testing.fakeadbd.Personas
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Plain JVM test: the factory and the TCP socket use java.net only, so no Robolectric is needed.
class PlainSocketFactoryTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    @Test fun connectsHandshakesAndRunsAShellOverLoopback() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val signer = SoftwareRsaAdbSigner(SoftwareRsaAdbSigner.generateKeyPair())
        val adbd = FakeAdbd(Personas.miTv(), Faults(), scope).also { it.trust(signer) }
        try {
            val port = adbd.listenTcp()
            withTimeout(30_000) {
                val socket = PlainSocketFactory().connect(Endpoint.PlainTcp("127.0.0.1", port), 3_000)
                val conn = AdbConnection.open(socket, signer, null, {})
                assertTrue("shell_v2" in conn.features)
                @OptIn(RawShell::class)
                val stream = conn.openStream(AdbService.ShellV2(ShellLine("echo over-tcp")))
                val reader = AdbStreamReader(stream)
                val out = StringBuilder()
                var exit = -1
                while (true) {
                    when (val p = ShellV2.next(reader) ?: break) {
                        is ShellPacket.Stdout -> out.append(String(p.bytes))
                        is ShellPacket.Stderr -> {}
                        is ShellPacket.Exit -> exit = p.code
                    }
                }
                stream.close()
                assertEquals("over-tcp\n", out.toString())
                assertEquals(0, exit)
                conn.close()
            }
            assertEquals(0, adbd.prompts.get())
        } finally {
            adbd.stop()
            scope.cancel()
        }
    }

    @Test fun refusedPortIsAConnectException() = runBlocking<Unit> {
        val ss = java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        val port = ss.localPort
        ss.close()
        assertFailsWith<ConnectException> { PlainSocketFactory().connect(Endpoint.PlainTcp("127.0.0.1", port), 2_000) }
    }

    @Test fun onlyLiteralAddressesAreConnected() = runBlocking<Unit> {
        assertFailsWith<UnknownHostException> { PlainSocketFactory().connect(Endpoint.PlainTcp("tv.local", 5555), 1_000) }
        assertFailsWith<UnknownHostException> { PlainSocketFactory().connect(Endpoint.PlainTcp("192.168.0.300", 5555), 1_000) }
        assertTrue(PlainSocketFactory.isLiteralAddress("192.168.1.23"))
        assertTrue(PlainSocketFactory.isLiteralAddress("fe80::1"))
        assertFalse(PlainSocketFactory.isLiteralAddress("example.com"))
        assertFalse(PlainSocketFactory.isLiteralAddress("192.168.0"))
    }

    @Test fun tlsEndpointsAreRefusedInThisVersion() = runBlocking<Unit> {
        assertFailsWith<AdbException> { PlainSocketFactory().connect(Endpoint.Tls("127.0.0.1", 5555, null), 1_000) }
    }
}
