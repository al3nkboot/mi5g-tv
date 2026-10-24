package tv.cleanyour.adb.session

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.session.BootOutcome
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.testing.fakeadbd.Faults
import tv.cleanyour.testing.fakeadbd.Personas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionLifecycleTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    @Test fun noHolderClosesTheSocketAfterSixtySeconds() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        advanceTimeBy(59_000)
        assertIs<ConnectionState.Connected>(s.state.value)
        advanceTimeBy(2_000)
        assertEquals(ConnectionState.Idle, s.state.value)
        assertTrue(rig.adbd.connections.isEmpty())
        // The next call reconnects on demand, silently (the key is known).
        assertEquals("back\n", s.exec(line("echo back")).stdout)
        assertIs<ConnectionState.Connected>(s.state.value)
        assertEquals(0, rig.adbd.prompts.get())
    }

    @Test fun aHolderKeepsItOpenAndPingsEveryTwentySeconds() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        val hold = s.hold()
        advanceTimeBy(61_000)
        assertIs<ConnectionState.Connected>(s.state.value)
        val pings = rig.adbd.connections.single().openedServices.count { it == "exec:echo" }
        assertEquals(3, pings)
        hold.close()
        advanceTimeBy(61_000)
        assertEquals(ConnectionState.Idle, s.state.value)
    }

    @Test fun droppedSocketWhileHeldReconnects() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        val hold = s.hold()
        rig.adbd.closeConnections()
        advanceTimeBy(100)
        assertTrue(s.state.value is ConnectionState.Reconnecting || s.state.value is ConnectionState.Connecting || s.state.value is ConnectionState.Connected, s.state.value.toString())
        advanceTimeBy(10_000)
        assertIs<ConnectionState.Connected>(s.state.value)
        assertEquals(1, rig.adbd.connections.size)
        assertEquals("ok\n", s.exec(line("echo ok")).stdout)
        hold.close()
    }

    @Test fun reconnectBudgetEndsInFailedThenCallsThrowClosed() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        val hold = s.hold()
        rig.adbd.acceptConnections = false
        rig.adbd.closeConnections()
        // Five attempts with full-jitter backoff capped at 8 s finish well inside 19 s, before
        // the first keepalive ping (20 s) starts another budget.
        advanceTimeBy(19_000)
        val failed = assertIs<ConnectionState.Failed>(s.state.value)
        assertEquals(AdbError.Transport.Refused, failed.error)
        assertEquals(5, rig.factory.attempts.size - 1)
        val e = assertFailsWith<AdbException> { s.exec(line("echo x")) }
        assertEquals(AdbError.Transport.Closed, e.error)
        // A later call gets a fresh budget.
        rig.adbd.acceptConnections = true
        assertEquals("x\n", s.exec(line("echo x")).stdout)
        hold.close()
    }

    @Test fun rebootThenAwaitBoot() = runTest {
        val rig = Rig(this, faults = Faults().apply { rebootDownMs = 3_000; bootSettleMs = 4_000 })
        val s = rig.open()
        val before = rig.tv.bootId
        s.reboot()
        assertIs<ConnectionState.WaitingForBoot>(s.state.value)
        assertEquals(1, rig.tv.rebootCount)
        val outcome = assertIs<BootOutcome.Back>(s.awaitBoot(120_000), rig.diag())
        assertTrue(outcome.bootId != before)
        assertEquals(rig.tv.bootId, outcome.bootId)
        assertIs<ConnectionState.Connected>(s.state.value)
        assertEquals("up\n", s.exec(line("echo up")).stdout)
    }

    @Test fun awaitBootTimesOutRelativeToTheCall() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        s.reboot()
        rig.adbd.acceptConnections = false
        assertEquals(BootOutcome.Timeout, s.awaitBoot(30_000))
        assertEquals(ConnectionState.Failed(AdbError.Transport.Timeout("boot")), s.state.value)
    }

    @Test fun aDifferentTvAfterReconnectNeverRunsAStep() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        rig.faults.wrongTvOnReconnect = Personas.tclG05()
        rig.adbd.closeConnections()
        settle()
        val e = assertFailsWith<AdbException> { s.exec(line("echo never")) }
        val err = assertIs<AdbError.Policy.WrongTv>(e.error)
        assertEquals(s.tvId, err.expected)
        assertEquals(TvId(Personas.tclG05().tvIdHex()), err.found)
        assertIs<ConnectionState.Failed>(s.state.value)
        assertTrue(rig.adbd.connections.none { c -> c.openedServices.any { it.contains("echo never") } })
    }

    @Test fun debugOffEndsTheSessionAndThePortCloses() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        val r = runCatching { s.exec(line("settings put global adb_enabled 0")) }
        r.exceptionOrNull()?.let { assertEquals(AdbError.Transport.Closed, assertIs<AdbException>(it).error) }
        settle()
        assertTrue(!rig.adbd.adbEnabled, "${r.exceptionOrNull()} ${rig.diag()}")
        val reopen = rig.manager.open(rig.endpoint, s.tvId, {})
        assertEquals(AdbError.Transport.Refused, assertIs<OpenResult.Failed>(reopen).error)
    }

    @Test fun closeIsFinal() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        s.close()
        assertEquals(ConnectionState.Idle, s.state.value)
        assertNull(rig.manager.existing(s.tvId))
        assertNull(rig.manager.states.value[s.tvId])
        assertEquals(AdbError.Transport.Closed, assertFailsWith<AdbException> { s.exec(line("echo x")) }.error)
    }

    @Test fun cancellingACallLeavesTheConnectionUp() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        assertNull(withTimeoutOrNull(500) { s.exec(line("sleep 100")) })
        assertIs<ConnectionState.Connected>(s.state.value)
        assertEquals("fine\n", s.exec(line("echo fine")).stdout)
        settle()
        assertEquals(1, rig.adbd.connections.size)
    }

    @Test fun statesFlowTracksEverySession() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        val hold = s.hold()
        rig.adbd.closeConnections()
        advanceTimeBy(10_000)
        assertEquals(s.state.value, rig.manager.states.value[s.tvId])
        hold.close()
    }
}
