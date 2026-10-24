package tv.cleanyour.e2e.scenarios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.e2e.AdbdProbe
import tv.cleanyour.e2e.E2eScenario
import tv.cleanyour.e2e.Timings
import java.io.File

// The harness itself, with no other stream's code: the oracle sees the emulator, the harness
// reaches adbd on the TCP endpoint the app's session will use, completes the handshake (AUTH with
// the host key when the image asks for it) and runs one stream. Runs today.
class S00HarnessSmoke : E2eScenario() {

    @Test fun oracleSeesTheBootedEmulator() {
        assertTrue("boot not completed", oracle.bootCompleted())
        assertEquals(cfg.api, oracle.sdk())
    }

    @Test fun adbdAnswersOnTheTcpEndpoint() {
        val r = AdbdProbe(signer).connect(cfg.endpoint.host, cfg.endpoint.port)
        println("adbd banner: ${r.banner}")
        println("adbd version=0x${r.version.toString(16)} maxPayload=${r.maxPayload} auth=${r.auth} features=${r.features.sorted()}")
        assertTrue("banner ${r.banner}", r.banner.startsWith("device::"))
        assertTrue("shell_v2 missing: ${r.features}", "shell_v2" in r.features)
        assertEquals(AdbdProbe.ECHO_TEXT, r.echo)
        timings.record("harness.connect", r.connectMillis.toDouble(), "ms", Timings.CONNECT_MS)
    }

    // W1's AdbKeyFormat (the reference encoder the fakes use) must produce exactly the public key
    // line the platform's own adb wrote for the same key: same struct, same base64.
    @Test fun adbKeyFormatMatchesThePlatformEncoder() {
        val pub = File(cfg.adbKey.path + ".pub").readText().trim()
        assertEquals(pub.substringBefore(' '), signer.publicKeyLine.substringBefore(' '))
        assertTrue(signer.publicKeyLine.endsWith(" cleanyourtv"))
    }

    @Test fun twoConnectionsAtOnceAreServed() {
        // The host adb server keeps its own transport open; the harness connection must coexist,
        // as the app's session coexists with nothing else on a real TV.
        val a = AdbdProbe(signer).connect(cfg.endpoint.host, cfg.endpoint.port, echo = true)
        val b = AdbdProbe(signer).connect(cfg.endpoint.host, cfg.endpoint.port, echo = true)
        assertEquals(a.echo, b.echo)
    }
}
