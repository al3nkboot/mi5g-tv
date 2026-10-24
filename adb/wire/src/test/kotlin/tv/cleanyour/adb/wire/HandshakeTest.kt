package tv.cleanyour.adb.wire

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.testing.fakeadbd.AllowBehaviour
import tv.cleanyour.testing.fakeadbd.Faults
import tv.cleanyour.testing.fakeadbd.Personas
import tv.cleanyour.testing.fakeadbd.PipeSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HandshakeTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    @Test fun knownKeySignsOnceAndNeverPrompts() = runTest {
        val tv = adbd()
        var prompted = 0
        val conn = tv.open(onAllow = { prompted++ })
        assertEquals(0, prompted)
        assertEquals(0, tv.prompts.get())
        assertTrue("shell_v2" in conn.features)
        assertTrue("cmd" in conn.features)
        assertEquals(1024 * 1024, conn.maxPayload)
        assertTrue(tv.events.contains("auth: signature accepted"), tv.events.toString())
        conn.close()
    }

    @Test fun unknownKeyPromptsOnceThenReconnectsSilently() = runTest {
        val tv = adbd(trusted = false)
        val seen = ArrayList<String>()
        val first = tv.open(onAllow = { seen += it })
        assertEquals(listOf(TestKeys.signer.fingerprint), seen)
        assertEquals(1, tv.prompts.get())
        first.close()
        val second = tv.open(onAllow = { seen += it })
        assertEquals(1, seen.size, "a second connect must not show the dialog again (F2)")
        assertEquals(1, tv.prompts.get())
        second.close()
    }

    @Test fun allowWithoutAlwaysPromptsNextTime() = runTest {
        val tv = adbd(faults = Faults().apply { allow = AllowBehaviour.Allow(afterMs = 3_000, always = false) }, trusted = false)
        tv.open().close()
        tv.open().close()
        assertEquals(2, tv.prompts.get())
    }

    @Test fun userTapsAllowAfterAWhile() = runTest {
        val tv = adbd(faults = Faults().apply { allow = AllowBehaviour.Allow(afterMs = 45_000) }, trusted = false)
        val start = currentTime
        tv.open().close()
        assertTrue(currentTime - start >= 45_000)
    }

    @Test fun denyIsAuthDenied() = runTest {
        val tv = adbd(faults = Faults().apply { allow = AllowBehaviour.Deny(afterMs = 1_000) }, trusted = false)
        val e = assertFailsWith<AdbException> { tv.open() }
        assertEquals(AdbError.Auth.Denied, e.error)
    }

    @Test fun ignoredDialogIsPendingTooLongAfterTheFirstAuthTimeout() = runTest {
        val tv = adbd(faults = Faults().apply { allow = AllowBehaviour.Ignore }, trusted = false)
        val start = currentTime
        val e = assertFailsWith<AdbException> { AdbConnection.open(tv.connect(), TestKeys.signer, null, {}, firstAuthTimeoutMs = 120_000) }
        assertEquals(AdbError.Auth.PendingTooLong, e.error)
        assertEquals(120_000, currentTime - start)
    }

    @Test fun insecureTvNeedsNoAuth() = runTest {
        val persona = Personas.miTv().also { it.setProp("ro.adb.secure", "0") }
        val tv = adbd(persona, trusted = false)
        var prompted = 0
        tv.open(onAllow = { prompted++ }).close()
        assertEquals(0, prompted)
        assertTrue(tv.events.none { it.startsWith("auth") })
    }

    @Test fun tlsOnlyTvIsRefusedInThisVersion() = runTest {
        val tv = adbd(faults = Faults().apply { requireTls = true })
        val e = assertFailsWith<AdbException> { tv.open() }
        assertTrue(assertIs<AdbError.Transport.Protocol>(e.error).detail.contains("TLS"))
        val e2 = assertFailsWith<AdbException> { tv.open(tls = NotSupportedTlsUpgrader) }
        assertIs<AdbError.Transport.Protocol>(e2.error)
    }

    @Test fun garbageInTheHandshakeIsAProtocolError() = runTest {
        val tv = adbd(faults = Faults().apply { garbageBeforeConnect = ByteArray(64) { 0x41 } })
        val e = assertFailsWith<AdbException> { tv.open() }
        assertIs<AdbError.Transport.Protocol>(e.error)
    }

    @Test fun signerOnlyEverSeesTwentyByteTokens() = runTest {
        val sizes = ArrayList<Int>()
        val recording = object : AdbSigner by TestKeys.signer {
            override suspend fun sign(token: AuthToken): ByteArray { sizes += token.bytes.size; return TestKeys.signer.sign(token) }
        }
        val tv = adbd(trusted = false)
        tv.open(signer = recording).close()
        tv.open(signer = recording).close()
        assertEquals(listOf(20, 20), sizes)
    }

    // Scripted peers for the frames the fake adbd never sends.

    private fun TestScope.scripted(block: suspend (PipeSocket) -> Unit): PipeSocket {
        val (client, server) = PipeSocket.pair()
        backgroundScope.launch { try { block(server) } catch (e: Exception) { } }
        return client
    }

    private suspend fun PipeSocket.send(command: Int, arg0: Int, arg1: Int, payload: ByteArray = FrameCodec.EMPTY) {
        val f = FrameCodec.encode(command, arg0, arg1, payload)
        write(f, 0, f.size)
    }

    @Test fun tokenOfTheWrongSizeIsRefusedBeforeSigning() = runTest {
        var signed = 0
        val counting = object : AdbSigner by TestKeys.signer {
            override suspend fun sign(token: AuthToken): ByteArray { signed++; return TestKeys.signer.sign(token) }
        }
        for (size in listOf(0, 19, 21, 256)) {
            val client = scripted { s -> FrameCodec.readMessage(s); s.send(AdbProtocol.A_AUTH, AdbProtocol.AUTH_TOKEN, 0, ByteArray(size)) }
            val e = assertFailsWith<AdbException> { AdbConnection.open(client, counting, null, {}) }
            assertEquals("AUTH token of $size bytes", assertIs<AdbError.Transport.Protocol>(e.error).detail)
        }
        assertEquals(0, signed)
    }

    @Test fun unexpectedAuthTypeIsRefused() = runTest {
        val client = scripted { s -> FrameCodec.readMessage(s); s.send(AdbProtocol.A_AUTH, AdbProtocol.AUTH_SIGNATURE, 0, ByteArray(20)) }
        val e = assertFailsWith<AdbException> { AdbConnection.open(client, TestKeys.signer, null, {}) }
        assertIs<AdbError.Transport.Protocol>(e.error)
    }

    @Test fun silentPeerTimesOutAfterFiveSeconds() = runTest {
        val client = scripted { s -> FrameCodec.readMessage(s); kotlinx.coroutines.awaitCancellation() }
        val start = currentTime
        val e = assertFailsWith<AdbException> { AdbConnection.open(client, TestKeys.signer, null, {}) }
        assertEquals(AdbError.Transport.Timeout("auth"), e.error)
        assertEquals(5_000, currentTime - start)
    }

    @Test fun peerClosingDuringTheHandshakeIsClosed() = runTest {
        val client = scripted { s -> FrameCodec.readMessage(s); s.close() }
        val e = assertFailsWith<AdbException> { AdbConnection.open(client, TestKeys.signer, null, {}) }
        assertEquals(AdbError.Transport.Closed, e.error)
    }

    @Test fun bannerMustBeADevice() = runTest {
        val client = scripted { s -> FrameCodec.readMessage(s); s.send(AdbProtocol.A_CNXN, AdbProtocol.VERSION, 1024 * 1024, "recovery::\u0000".toByteArray()) }
        val e = assertFailsWith<AdbException> { AdbConnection.open(client, TestKeys.signer, null, {}) }
        assertTrue(assertIs<AdbError.Transport.Protocol>(e.error).detail.contains("recovery"))
    }

    @Test fun tinyPeerWindowIsRefused() = runTest {
        val client = scripted { s -> FrameCodec.readMessage(s); s.send(AdbProtocol.A_CNXN, AdbProtocol.VERSION, 100, "device::features=shell_v2\u0000".toByteArray()) }
        val e = assertFailsWith<AdbException> { AdbConnection.open(client, TestKeys.signer, null, {}) }
        assertIs<AdbError.Transport.Protocol>(e.error)
    }

    @Test fun hugePeerWindowIsCappedAtOurs() = runTest {
        val client = scripted { s ->
            FrameCodec.readMessage(s)
            s.send(AdbProtocol.A_CNXN, AdbProtocol.VERSION, 16 * 1024 * 1024, "device::ro.product.model=x;features=shell_v2,cmd\u0000".toByteArray())
            kotlinx.coroutines.awaitCancellation()
        }
        val conn = AdbConnection.open(client, TestKeys.signer, null, {})
        assertEquals(AdbProtocol.MAX_PAYLOAD, conn.maxPayload)
        assertEquals(setOf("shell_v2", "cmd"), conn.features)
        conn.close()
    }

    @Test fun bannerWithoutFeaturesGivesAnEmptySet() = runTest {
        val client = scripted { s ->
            FrameCodec.readMessage(s)
            s.send(AdbProtocol.A_CNXN, AdbProtocol.VERSION, 256 * 1024, "device::\u0000".toByteArray())
            kotlinx.coroutines.awaitCancellation()
        }
        val conn = AdbConnection.open(client, TestKeys.signer, null, {})
        assertEquals(emptySet(), conn.features)
        assertEquals(256 * 1024, conn.maxPayload)
        val e = assertFailsWith<AdbException> { conn.openStream(AdbService.ShellV2(line("echo"))) }
        assertEquals(AdbError.Transport.NoShellV2, e.error)
        conn.close()
    }
}
