package tv.cleanyour.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.backup.DocumentSnapshotStore
import tv.cleanyour.core.discovery.DiscoveryEvent
import tv.cleanyour.core.engine.PauseReason
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.store.SnapshotStore
import tv.cleanyour.testing.fixtures.FakeLauncherPin
import tv.cleanyour.testing.fixtures.FakeRestoreNote
import tv.cleanyour.testing.fixtures.FakeTvRestoreFiles
import tv.cleanyour.testing.fixtures.FakeTvSession
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// T7 required behaviours whose seam lives in :core:flows: 1 (the wizard holds its session),
// 3 (a probe-only session never sends debug_off), 5 (a corrupt baseline stops, never overwritten).
// The real-session halves of 1 (the 60 s idle close, the engine's NOT_CONNECTED, the re-open in
// place) run against AdbSessionManager and the fake adbd in :app (SessionSeamTest).
@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationBehavioursTest {

    private suspend fun TestScope.toFound(h: Harness, m: SetupMachine) {
        h.discovery.emit(DiscoveryEvent.Found(miTvFound))
        settle()
        m.dispatch(SetupIntent.Start)
        settle()
        h.discovery.open5555[HOST] = true
        settle(2_100)
        settle(700)
    }

    // The Harness wiring with another snapshot store (the real W6 one for item 5).
    private fun deps(h: Harness, snapshots: SnapshotStore): SetupDeps {
        val runner = PlanRunner(h.planner, h.engine, h.gate, h.ledger, h.facts, snapshots, h.prober)
        val sessionEnd = SessionEnd(h.sessions, h.discovery, h.tvs, runner, h.foreground)
        return SetupDeps(
            h.discovery, h.sessions, h.prober, h.profiles, h.facts, runner, h.engine, h.gate, h.tvs, snapshots, h.ledger,
            FakeLauncherPin(Samples.launcherArtifact()), h.themes, RestorePointWriter(FakeTvRestoreFiles(), h.blobs, h.ledger, runner),
            FakeRestoreNote(), sessionEnd, h.access, h.clock, KeyCopy, blobs = h.blobs,
            dock = DockPicker { _, _, _, _, _ -> listOf(PackageName("com.netflix.ninja")) }, screenLock = { true }, diagnostics = { "d" },
        )
    }

    // Item 1: one hold from the connect, through a slow read of "What we found", until Done.
    @Test fun theWizardHoldsTheSessionFromTheConnectUntilDone() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        val m = h.setup(backgroundScope)
        toFound(h, m)
        val found = assertIs<SetupState.Found>(m.state.value)
        assertEquals(1, h.session.openHolds)
        assertTrue(h.access.holding(found.tvId))
        settle(5 * 60_000L)   // far past the session's 60 s idle close
        assertEquals(1, h.session.openHolds, "still held while S3 is read")
        m.dispatch(SetupIntent.MakeItClean); settle()
        assertIs<SetupState.Done>(m.state.value)
        assertEquals(0, h.session.openHolds, "Done releases the hold")
        assertFalse(h.access.holding(found.tvId))
    }

    // Item 1: a stop card releases the hold; carrying on takes it again for the run.
    @Test fun aStopCardReleasesTheHoldAndCarryingOnTakesItAgain() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        h.engine.onStart = { p -> emit(p.tvId, RunState.Paused(p, 1, PauseReason.CONNECTION_LOST, AdbError.Transport.Closed)) }
        val m = h.setup(backgroundScope)
        toFound(h, m)
        assertEquals(1, h.session.openHolds)
        m.dispatch(SetupIntent.MakeItClean); settle()
        val stopped = assertIs<SetupState.Stopped>(m.state.value)
        assertEquals(StopCase.CONNECTION_LOST, stopped.card.case)
        assertEquals(0, h.session.openHolds)
        // The run moves again (Carry on, after the TV came back): the wizard holds the session again.
        h.engine.emit(stopped.tvId, RunState.Running(h.setupPlan, 1, emptyList(), 50, null))
        settle()
        assertIs<SetupState.Working>(m.state.value)
        assertEquals(1, h.session.openHolds)
    }

    @Test fun leavingTheWizardReleasesItsHold() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        val screenJob = kotlinx.coroutines.Job()
        val m = h.setup(kotlinx.coroutines.CoroutineScope(backgroundScope.coroutineContext + screenJob))
        toFound(h, m)
        assertEquals(1, h.session.openHolds)
        screenJob.cancel()
        settle()
        assertEquals(0, h.session.openHolds)
    }

    // Item 3: connect, probe, plan preview (the list and the commands), 10 minutes in the
    // background: no DebugOff plan and no debug_off line, since no write reached the TV.
    @Test fun aProbeOnlySessionNeverSendsDebugOff() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        val m = h.setup(backgroundScope)
        h.sessionEnd.watch(backgroundScope)
        toFound(h, m)
        m.dispatch(SetupIntent.Open(Sheet.LIST)); settle()
        m.dispatch(SetupIntent.Open(null)); settle()
        h.foreground.set(false)
        settle(10 * 60_000L + 1_000)
        assertFalse(h.planner.requests.any { it == PlanRequest.DebugOff })
        assertTrue(h.engine.calls.none { it is tv.cleanyour.testing.fixtures.FakeEngine.Call.Start })
        assertTrue(h.session.execLines.none { "adb_enabled" in it })
        assertEquals(tv.cleanyour.core.model.DebugState.ON, h.tvs.tvs.value.single().debugging)
    }

    // Item 5: a corrupt BASELINE document stops the probe with a card; no fresh BASELINE is written
    // over (or beside) the broken one, and the snapshot dir is exactly as it was.
    @Test fun aCorruptBaselineStopsAndIsNeverReplaced() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        val store = DocumentSnapshotStore(h.documents)
        val tvId = FakeTvSession().tvId
        val dir = DocumentSnapshotStore.dir(tvId)
        val broken = "1760000000000-BASELINE-" + "a".repeat(64) + ".json.gz"
        h.documents.write("$dir/$broken", "not a gzip stream".encodeToByteArray())
        assertFailsWith<Exception> { store.baseline(tvId) }

        val m = SetupMachine(deps(h, store), backgroundScope)
        toFound(h, m)
        val s = assertIs<SetupState.Stopped>(m.state.value)
        assertEquals(StopCase.PROBE_FAILED, s.card.case)
        assertEquals(listOf(broken), h.documents.list(dir))
        assertFalse(h.prober.calls.any { it.startsWith("snapshot") }, "no snapshot was taken to replace it")
        assertEquals(0, h.session.openHolds)

        // "Try again" asks the store again and stops again; still nothing written.
        m.dispatch(SetupIntent.StopAction(StopAction.PRIMARY)); settle()
        assertEquals(StopCase.PROBE_FAILED, assertIs<SetupState.Stopped>(m.state.value).card.case)
        assertEquals(listOf(broken), h.documents.list(dir))
    }

    // Item 5 for the Manage writes: PlanRunner.snapshotFor propagates the failure (TvWriter turns it
    // into a failed write) and saves nothing.
    @Test fun aCorruptLatestSnapshotFailsTheManageWriteAndSavesNothing() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val store = DocumentSnapshotStore(h.documents)
        val tvId = FakeTvSession().tvId
        val dir = DocumentSnapshotStore.dir(tvId)
        val broken = "1760000000000-BASELINE-" + "b".repeat(64) + ".json.gz"
        h.documents.write("$dir/$broken", byteArrayOf(1, 2, 3))
        val runner = PlanRunner(h.planner, h.engine, h.gate, h.ledger, h.facts, store, h.prober)
        assertFailsWith<Exception> { runner.snapshotFor(Samples.tv(), h.session) }
        assertEquals(listOf(broken), h.documents.list(dir))
    }
}
