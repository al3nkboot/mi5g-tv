package tv.cleanyour.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.Timeout
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.StartRefusal
import tv.cleanyour.core.engine.StartResult
import tv.cleanyour.core.flows.AppForeground
import tv.cleanyour.core.flows.GateKeeper
import tv.cleanyour.core.flows.PlanRunner
import tv.cleanyour.core.flows.RunLedger
import tv.cleanyour.core.flows.SecureIdGen
import tv.cleanyour.core.flows.SessionEnd
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.DefaultPlanner
import tv.cleanyour.core.engine.FileOverlayTransport
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.core.spec.BundledFactsTable
import tv.cleanyour.core.spec.BundledProfileSource
import tv.cleanyour.testing.fixtures.FakeCredentialGate
import tv.cleanyour.testing.fixtures.FakeDiscovery
import tv.cleanyour.testing.fixtures.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

// T7 required behaviour 1 on the real W2 session and W3 engine (the fake adbd under the test
// scheduler), and the R5 merge correction: a run's own session must read Connected again after a
// reconnect, also after "Reset my key" closed every session for good.
@OptIn(ExperimentalCoroutinesApi::class)
class SessionSeamTest {
    @get:Rule val timeout: Timeout = Timeout.seconds(120)

    private fun lines(stack: RealStack): List<String> = stack.adbd.connections.flatMap { it.openedServices }

    @Test fun theSessionIdlesSixtySecondsAfterTheLastHoldAndTheEngineRefusesIt() = runTest {
        val stack = RealStack(this)
        val s = stack.open()
        val hold = s.hold()
        advanceTimeBy(5 * 60_000L)
        assertIs<ConnectionState.Connected>(s.state.value, "a hold keeps the socket")
        hold.close()
        advanceTimeBy(59_000)
        assertIs<ConnectionState.Connected>(s.state.value)
        advanceTimeBy(2_000)
        assertEquals(ConnectionState.Idle, s.state.value)

        val plan = stack.plan(s.tvId)
        val r = stack.raw.start(plan, s, plan.hash, stack.pass(s.tvId))
        assertEquals(StartResult.Refused(StartRefusal.NOT_CONNECTED), r)
    }

    // AdbTvSession reconnects in place: a second open of the same TV is adopted by the same object.
    @Test fun aReopenOfAnIdleSessionIsAdoptedInPlace() = runTest {
        val stack = RealStack(this)
        val s = stack.open()
        advanceTimeBy(61_000)
        assertEquals(ConnectionState.Idle, s.state.value)
        val again = assertIs<OpenResult.Ok>(stack.sessions.open(stack.endpoint, s.tvId) {}).session
        assertSame(s, again)
        assertIs<ConnectionState.Connected>(s.state.value)
        assertSame(s, stack.sessions.existing(s.tvId))
    }

    // The graph's engine re-opens an idle session before the start: the first "Make it clean" after
    // a slow read of S3, or a Manage write after a long download, is not refused NOT_CONNECTED.
    @Test fun theGraphEngineReopensAnIdleSessionBeforeTheStart() = runTest {
        val stack = RealStack(this)
        val s = stack.open()
        stack.remember(s.tvId)
        advanceTimeBy(61_000)
        assertEquals(ConnectionState.Idle, s.state.value)
        val plan = stack.plan(s.tvId)
        assertEquals(StartResult.Started, stack.engine.start(plan, s, plan.hash, stack.pass(s.tvId)))
        assertIs<ConnectionState.Connected>(s.state.value)
        assertSame(s, stack.sessions.existing(s.tvId))
        assertEquals(0, stack.adbd.prompts.get(), "a quiet re-open: the key is known, no Allow dialog")
    }

    // A run that paused, then "Reset my key" (closeAll: every session closed for good), then the TV is
    // reconnected: the new session is a new object. The raw engine still holds the dead one and
    // ignores "Carry on"; the graph's engine follows the manager and carries on.
    @Test fun aPausedRunCarriesOnOnTheNewSessionAfterAKeyReset() = runTest {
        val stack = RealStack(this)
        val s1 = stack.open()
        stack.remember(s1.tvId)
        val plan = stack.plan(s1.tvId)
        val pass = stack.pass(s1.tvId)
        assertEquals(StartResult.Started, stack.engine.start(plan, s1, plan.hash, pass))
        pauseByDroppingTheTv(stack, s1.tvId)

        stack.sessions.closeAll()
        assertEquals(ConnectionState.Idle, s1.state.value)
        stack.adbd.acceptConnections = true
        stack.engine.continueRun(s1.tvId, pass)
        val now = assertNotNull(stack.sessions.existing(s1.tvId))
        assertNotSame(s1, now)
        assertIs<ConnectionState.Connected>(now.state.value)
        assertFalse(stack.raw.runState(s1.tvId).value is RunState.Paused, "the run moved: ${stack.raw.runState(s1.tvId).value}")
    }

    @Test fun withoutTheSeamTheRunKeepsTheDeadSession() = runTest {
        val stack = RealStack(this)
        val s1 = stack.open()
        stack.remember(s1.tvId)
        val plan = stack.plan(s1.tvId)
        val pass = stack.pass(s1.tvId)
        assertEquals(StartResult.Started, stack.raw.start(plan, s1, plan.hash, pass))
        pauseByDroppingTheTv(stack, s1.tvId)
        stack.sessions.closeAll()
        stack.adbd.acceptConnections = true
        assertIs<OpenResult.Ok>(stack.sessions.open(stack.endpoint, s1.tvId) {})   // what TvAccess does
        stack.raw.continueRun(s1.tvId, pass)
        assertIs<RunState.Paused>(stack.raw.runState(s1.tvId).value)
    }

    private fun kotlinx.coroutines.test.TestScope.pauseByDroppingTheTv(stack: RealStack, tvId: TvId) {
        stack.adbd.acceptConnections = false
        stack.adbd.closeConnections()
        advanceTimeBy(60_000)
        runCurrent()
        assertIs<RunState.Paused>(stack.raw.runState(tvId).value)
    }

    // CurrentSession on its own: it reads the manager's session for the TV, and the original only
    // while the manager has none.
    @Test fun currentSessionFollowsTheManager() = runTest {
        val stack = RealStack(this)
        val s1 = stack.open()
        val proxy = CurrentSession(stack.sessions, s1)
        assertSame(s1, proxy.current())
        stack.sessions.closeAll()
        assertSame(s1, proxy.current())
        assertEquals(ConnectionState.Idle, proxy.state.value)
        val s2 = assertIs<OpenResult.Ok>(stack.sessions.open(stack.endpoint, s1.tvId) {}).session
        assertSame(s2, proxy.current())
        assertIs<ConnectionState.Connected>(proxy.state.value)
        assertEquals(s1.tvId, proxy.identity.tvId)
    }

    // A refused key or another TV is never retried quietly; those are the flows' cards.
    @Test fun finalFailuresAreNotReopened() {
        assertTrue(LiveSessionEngine.needsReopen(null))
        assertTrue(LiveSessionEngine.needsReopen(ConnectionState.Idle))
        assertTrue(LiveSessionEngine.needsReopen(ConnectionState.Failed(tv.cleanyour.core.model.AdbError.Transport.Refused)))
        assertFalse(LiveSessionEngine.needsReopen(ConnectionState.Failed(tv.cleanyour.core.model.AdbError.Auth.Denied)))
        assertFalse(LiveSessionEngine.needsReopen(ConnectionState.Failed(tv.cleanyour.core.model.AdbError.Transport.NoShellV2)))
        assertFalse(LiveSessionEngine.needsReopen(ConnectionState.AwaitingAllow(0, "AB:CD")))
    }

    // T7 item 3 on the real session: probe only, 10 minutes in the background, no debug_off line
    // reaches the TV. Then a write touches the TV, the session idles, and the 10 minute rule still
    // switches debugging off through a quiet re-open.
    @Test fun theBackgroundRuleSendsDebugOffOnlyAfterAWriteEvenOnAnIdleSession() = runTest {
        val stack = RealStack(this)
        val s = stack.open()
        assertEquals(Fixtures.miTv().tvId(), s.tvId, "the fake adbd persona is the reference TV")
        stack.remember(s.tvId)
        val clock = stack.clock
        val planner = DefaultPlanner(clock, SecureIdGen(clock), stack.renderer, FileOverlayTransport(stack.prober), "0.1.0", profiles = BundledProfileSource())
        val gate = GateKeeper(FakeCredentialGate(clock), clock, PlainCopy)
        val runner = PlanRunner(planner, stack.engine, gate, RunLedger(stack.documents, stack.journal), BundledFactsTable(), stack.snapshots, stack.prober)
        val discovery = FakeDiscovery().apply { open5555[TV_HOST] = true }
        val foreground = AppForeground(true)
        val end = SessionEnd(stack.sessions, discovery, stack.tvs, runner, foreground)
        end.watch(backgroundScope)

        stack.snapshots.save(stack.prober.snapshot(s, tv.cleanyour.core.model.SnapshotReason.BASELINE))   // the probe
        foreground.set(false)
        advanceTimeBy(11 * 60_000L); runCurrent()
        assertTrue(lines(stack).none { "adb_enabled" in it }, lines(stack).joinToString("\n"))
        assertTrue(stack.adbd.adbEnabled)

        foreground.set(true); runCurrent()
        end.touched(s.tvId)   // a write ran
        advanceTimeBy(2 * 60_000L)
        assertEquals(ConnectionState.Idle, s.state.value)
        discovery.open5555[TV_HOST] = false
        foreground.set(false)
        advanceTimeBy(11 * 60_000L); runCurrent()
        assertFalse(stack.adbd.adbEnabled, lines(stack).joinToString("\n"))
        assertEquals(DebugState.OFF, stack.tvs.tvs.value.single().debugging)
    }
}
