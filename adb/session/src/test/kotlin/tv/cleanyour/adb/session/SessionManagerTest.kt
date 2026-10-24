package tv.cleanyour.adb.session

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.testing.fakeadbd.AllowBehaviour
import tv.cleanyour.testing.fakeadbd.FakeTv
import tv.cleanyour.testing.fakeadbd.Faults
import tv.cleanyour.testing.fakeadbd.Personas
import tv.cleanyour.testing.fixtures.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionManagerTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    @Test fun opensTheMiTvAndReadsItsIdentity() = runTest {
        val rig = Rig(this)
        val session = rig.open()
        assertEquals(Fixtures.miTv().identity(), session.identity)
        assertEquals(TvId(rig.tv.tvIdHex()), session.tvId)
        assertIs<ConnectionState.Connected>(session.state.value)
        assertEquals(session.state.value, rig.manager.states.value[session.tvId])
        assertSame(session, rig.manager.existing(session.tvId))
        assertEquals(0, rig.adbd.prompts.get())
        // The identity read is the only thing sent before the caller gets the session (D37).
        assertEquals(listOf("shell,v2,raw:getprop"), rig.adbd.connections.single().openedServices)
    }

    @Test fun aDifferentTvAtTheAddressIsRefusedBeforeAnythingElse() = runTest {
        val rig = Rig(this)
        val expected = TvId(Personas.tclG05().tvIdHex())
        val r = assertIs<OpenResult.Failed>(rig.manager.open(rig.endpoint, expected, {}))
        val err = assertIs<AdbError.Policy.WrongTv>(r.error)
        assertEquals(expected, err.expected)
        assertEquals(TvId(rig.tv.tvIdHex()), err.found)
        assertNull(rig.manager.existing(expected))
        assertNull(rig.manager.existing(err.found))
        assertIs<ConnectionState.Failed>(rig.manager.states.value[expected])
        val conn = rig.adbd.connections.singleOrNull()
        settle()
        if (conn != null) assertEquals(listOf("shell,v2,raw:getprop"), conn.openedServices)
        assertTrue(rig.adbd.connections.isEmpty(), "the socket is closed, nothing else was sent: ${rig.diag()}")
    }

    @Test fun expectedTvMatches() = runTest {
        val rig = Rig(this)
        val session = rig.open(expected = TvId(rig.tv.tvIdHex()))
        assertIs<ConnectionState.Connected>(session.state.value)
    }

    @Test fun aTvWithoutShellV2IsRefused() = runTest {
        val mi = Personas.miTv()
        val old = FakeTv(mi.name, mi.getpropText().lines().filter { it.isNotEmpty() }, mi.packages.values.toList(), emptyMap(), mi.home, setOf("cmd"))
        val rig = Rig(this, old)
        val r = assertIs<OpenResult.Failed>(rig.manager.open(rig.endpoint, null, {}))
        assertEquals(AdbError.Transport.NoShellV2, r.error)
    }

    @Test fun connectFailuresAreClassified() = runTest {
        val rig = Rig(this)
        rig.adbd.adbEnabled = false
        assertEquals(AdbError.Transport.Refused, assertIs<OpenResult.Failed>(rig.manager.open(rig.endpoint, null, {})).error)
        assertEquals(AdbError.Transport.Unreachable, assertIs<OpenResult.Failed>(rig.manager.open(Endpoint.PlainTcp("192.0.2.99"), null, {})).error)
        assertEquals(AdbError.Transport.Refused, assertIs<OpenResult.Failed>(rig.manager.open(Endpoint.PlainTcp(HOST, 5556), null, {})).error)
    }

    @Test fun allowCallbackFiresExactlyWhenTheDialogIsRaised() = runTest {
        val rig = Rig(this, faults = Faults().apply { allow = AllowBehaviour.Allow(afterMs = 2_000) }, trusted = false)
        val seen = ArrayList<String>()
        val expected = TvId(rig.tv.tvIdHex())
        val session = rig.open(expected = expected, onAllow = { seen += it })
        assertEquals(listOf(Keys.signer.fingerprint), seen)
        assertEquals(1, rig.adbd.prompts.get())
        assertIs<ConnectionState.Connected>(session.state.value)
        // Second open: no dialog (F2).
        val again = rig.open(onAllow = { seen += it })
        assertSame(session, again)
        assertEquals(1, seen.size)
    }

    @Test fun deniedAndIgnoredDialogs() = runTest {
        val denied = Rig(this, faults = Faults().apply { allow = AllowBehaviour.Deny() }, trusted = false)
        assertEquals(AdbError.Auth.Denied, assertIs<OpenResult.Failed>(denied.manager.open(denied.endpoint, null, {})).error)
        val ignored = Rig(this, faults = Faults().apply { allow = AllowBehaviour.Ignore }, trusted = false)
        assertEquals(AdbError.Auth.PendingTooLong, assertIs<OpenResult.Failed>(ignored.manager.open(ignored.endpoint, null, {})).error)
    }

    @Test fun openingTheSameTvAgainAdoptsTheNewConnection() = runTest {
        val rig = Rig(this)
        val first = rig.open()
        settle()
        val second = rig.open()
        assertSame(first, second)
        settle()
        assertEquals(1, rig.adbd.connections.size, "the older socket is closed: ${rig.diag()}")
        assertEquals("ok\n", first.exec(line("echo ok")).stdout)
    }

    @Test fun closeAllForgetsEverything() = runTest {
        val rig = Rig(this)
        val session = rig.open()
        rig.manager.closeAll()
        assertEquals(ConnectionState.Idle, session.state.value)
        assertNull(rig.manager.existing(session.tvId))
        assertTrue(rig.manager.states.value.isEmpty())
        settle()
        assertTrue(rig.adbd.connections.isEmpty(), rig.diag())
    }
}
