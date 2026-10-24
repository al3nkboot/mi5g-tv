package tv.cleanyour.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.adb.wire.AuthToken
import tv.cleanyour.core.engine.JournalRecord
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.TvId
import tv.cleanyour.testing.fixtures.AdbKeyFormat
import tv.cleanyour.testing.fixtures.FakeAdbSigner
import java.io.DataInputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.concurrent.thread

// The harness pieces that need no emulator, in `check`: the host key loader and signer, the
// handshake against an in-process adbd that asks for AUTH, the journal-to-changes helper and the
// trend recorder.
class HarnessTest {

    private val keyPair = FakeAdbSigner.sharedKeyPair

    private fun pem(): String {
        val b64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----\n"
    }

    @Test fun hostKeyLoadsFromPkcs8PemAndSignsTheAdbdWay() = runBlocking {
        val signer = HostKeySigner.fromPem(pem())
        val pub = keyPair.public as RSAPublicKey
        assertEquals(AdbKeyFormat.publicKeyLine(pub), signer.publicKeyLine)
        assertEquals(AdbKeyFormat.fingerprint(pub), signer.fingerprint)
        val token = ByteArray(20) { it.toByte() }
        assertTrue(AdbKeyFormat.verify(pub, token, signer.sign(AuthToken(token))))
    }

    @Test fun bannerParsesPropsAndFeatures() {
        val (props, features) = AdbdProbe.parseBanner("device::ro.product.name=sdk_google_atv_x86;ro.product.model=m;features=shell_v2,cmd,stat_v2")
        assertEquals("m", props["ro.product.model"])
        assertEquals(setOf("shell_v2", "cmd", "stat_v2"), features)
        assertNull(props["features"])
    }

    // A tiny adbd on loopback: AUTH token, checks the signature with the client's key, CNXN,
    // then serves the echo stream.
    @Test fun handshakeSignsTheTokenAndRunsTheEchoStream() {
        val signer = HostKeySigner.fromPem(pem())
        val pub = keyPair.public as RSAPublicKey
        val server = ServerSocket(0)
        var serverError: Throwable? = null
        val t = thread {
            try {
                server.accept().use { s ->
                    val input = DataInputStream(s.getInputStream())
                    val out = s.getOutputStream()
                    val cnxn = AdbdProbe.read(input)
                    check(cnxn.command == AdbdProbe.CNXN && cnxn.arg0 == AdbdProbe.A_VERSION)
                    val token = ByteArray(20) { (it * 7).toByte() }
                    out.write(AdbdProbe.encode(AdbdProbe.AUTH, AdbdProbe.AUTH_TOKEN, 0, token))
                    val sig = AdbdProbe.read(input)
                    check(sig.command == AdbdProbe.AUTH && sig.arg0 == AdbdProbe.AUTH_SIGNATURE)
                    check(AdbKeyFormat.verify(pub, token, sig.data)) { "bad signature" }
                    out.write(AdbdProbe.encode(AdbdProbe.CNXN, AdbdProbe.A_VERSION, 4096, "device::ro.product.model=fake;features=shell_v2\u0000".toByteArray()))
                    val open = AdbdProbe.read(input)
                    check(open.command == AdbdProbe.OPEN && String(open.data).startsWith("shell:echo "))
                    out.write(AdbdProbe.encode(AdbdProbe.OKAY, 9, open.arg0))
                    out.write(AdbdProbe.encode(AdbdProbe.WRTE, 9, open.arg0, "${AdbdProbe.ECHO_TEXT}\n".toByteArray()))
                    check(AdbdProbe.read(input).command == AdbdProbe.OKAY)
                    out.write(AdbdProbe.encode(AdbdProbe.CLSE, 9, open.arg0))
                    check(AdbdProbe.read(input).command == AdbdProbe.CLSE)
                }
            } catch (e: Throwable) {
                serverError = e
            }
        }
        val r = AdbdProbe(signer, timeoutMs = 5_000).connect("127.0.0.1", server.localPort)
        t.join(5_000)
        server.close()
        serverError?.let { throw it }
        assertEquals(AdbdProbe.AuthPath.SIGNATURE, r.auth)
        assertEquals("fake", r.props["ro.product.model"])
        assertEquals(setOf("shell_v2"), r.features)
        assertEquals(4096, r.maxPayload)
        assertEquals(AdbdProbe.ECHO_TEXT, r.echo)
    }

    @Test fun oversizedPayloadIsRejected() {
        val header = AdbdProbe.encode(AdbdProbe.WRTE, 1, 1).copyOf()
        // Claim 2 MiB of payload: the reader refuses before allocating.
        java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(12, 2 * 1024 * 1024)
        val r = runCatching { AdbdProbe.read(DataInputStream(header.inputStream())) }
        assertTrue(r.exceptionOrNull()?.message.orEmpty().contains("out of bounds"))
    }

    @Test fun changesComeFromCompletedStepsOnly() {
        val plan = PlanId("01E2E")
        val zero = Sha256("0".repeat(64))
        val a = Op.Disable(PackageName("com.example.a"))
        val b = Op.Disable(PackageName("com.example.b"))
        val records = listOf(
            JournalRecord.PlanStart(plan, TvId("0123456789abcdef"), zero, 2, zero, "test", 1),
            JournalRecord.Begin(plan, 0, a, listOf("x"), null, 10),
            JournalRecord.Done(plan, 0, 0, true, 5),
            JournalRecord.Begin(plan, 1, b, listOf("y"), null, 20),
            JournalRecord.Failed(plan, 1, 1, "refused", ""),
        )
        val changes = changesFrom(records)
        assertEquals(listOf(a), changes.map { it.op })
        assertEquals(10L, changes.single().atMs)
    }

    @Test fun timingsAreRecordedAsTrendLines() {
        val dir = Files.createTempDirectory("e2e-timings").toFile()
        val t = Timings(dir, 28)
        assertEquals(true, t.record("connect", 120.0, "ms", Timings.CONNECT_MS).withinBudget)
        assertEquals(false, t.record("push", 0.2, "MB/s", Timings.PUSH_MB_PER_S, higherIsBetter = true).withinBudget)
        val lines = dir.resolve("timings-api28.jsonl").readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"name\":\"connect\"") && lines[0].contains("\"withinBudget\":true"))
    }

    @Test fun configIsAbsentOutsideASession() {
        if (System.getenv("CYT_E2E_ENDPOINT") == null && System.getProperty("cyt.e2e.endpoint") == null) {
            assertNull(E2eConfig.fromEnvironment())
        }
    }
}
