package tv.cleanyour.core.engine

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.HomeResolution
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.DefaultPlanner
import tv.cleanyour.core.planner.GuardedSection
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.ops.Watchdog
import tv.cleanyour.testing.fixtures.Samples
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Issue #2 (Insignia NS-55F501NA26, Fire OS 8.1.8.5): the takeover's switch-off of Fire OS's own
// home screen is refused (a protected package), step R's pm enable of it is refused too although it
// was never off and Home is Amazon's, and 0.3.1 paused on every Carry on, Undo and Stop, and after a
// restart "at step 0". These tests replay that TV and that journal with the 0.3.2 engine.
class Issue2Test {
    private val amazon = Component(PackageName("com.amazon.tv.launcher"), ".ui.HomeActivity_vNext")
    private val token = RunToken("0123456789abcdef")
    private val protectedStderr = "Error: java.lang.SecurityException: Cannot disable a protected package: com.amazon.tv.launcher"

    // A Fire TV as the scripted TV: Fire OS's home is HOME (priority 950) and protected, so the TV
    // refuses both pm disable-user and pm enable of it.
    private fun fireTv(): ScriptedTv {
        val packages = Samples.miTvPackages(emptySet()) + (amazon.pkg to PackageState(EnabledState.ENABLED, true, 1, null, false, false, "Home", false))
        val tv = ScriptedTv(Samples.snapshot(packages = packages, home = HomeResolution(amazon, listOf(HomeClaimant(amazon, 950)))))
        tv.refuse = { it == amazon.pkg }
        return tv
    }

    // The plan 0.3.1 made for that TV (no profile, the Amazon sure set): two switch-offs, then the
    // takeover with Fire OS's home as the stock launcher, rendered by the one renderer.
    private fun olderPlan(h: Harness): Plan {
        val ops = listOf(
            Op.Disable(h.debloat[0]), Op.Disable(h.debloat[1]),
            Op.StartActivity(h.launcher, wait = true), Op.Mkdir(RemoteDir.TMP),
            Op.ArmHomeWatchdog(amazon, DefaultPlanner.WATCHDOG_SECONDS, token), Op.Disable(amazon.pkg),
            Op.SetHome(h.launcher), Op.VerifyHome(h.launcher), Op.ConfirmHome(token), Op.KillWatchdog(token),
        )
        val rollback = listOf(Op.ConfirmHome(token), Op.KillWatchdog(token), Op.Enable(amazon.pkg), Op.SetHome(amazon), Op.VerifyHome(amazon), Op.PressHome)
        val section = GuardedSection(DefaultPlanner.SECTION_HOME, 4, 7, token, 90_000, h.launcher, setOf(amazon.pkg), rollback)
        return Samples.plan(ops, tvId = h.tv.tvId, renderer = h.renderer, id = h.idGen.planId(), sections = listOf(section),
            base = h.tv.snapshot().contentHash)
    }

    private fun lines(h: Harness, op: Op) = h.renderer.render(op).lines.map { it.text }

    // Step R as 0.3.1 ran it: the defusing ops done, the refused enable a failure, Home put back and
    // verified, then the pause.
    private suspend fun stepR031(h: Harness, plan: Plan, pausedAt: Int) {
        val j = h.journal
        j.append(JournalRecord.Rollback(plan.id, 4))
        for (op in listOf(Op.ConfirmHome(token), Op.KillWatchdog(token))) {
            j.append(JournalRecord.Begin(plan.id, -1, op, lines(h, op), null, Samples.NOW_MS)); j.append(JournalRecord.Done(plan.id, -1, 0, true, 1))
        }
        j.append(JournalRecord.Begin(plan.id, 5, Op.Enable(amazon.pkg), lines(h, Op.Enable(amazon.pkg)), null, Samples.NOW_MS))
        j.append(JournalRecord.Failed(plan.id, 5, 1, "exit 1", protectedStderr))
        j.append(JournalRecord.Begin(plan.id, 6, Op.SetHome(amazon), lines(h, Op.SetHome(amazon)), null, Samples.NOW_MS)); j.append(JournalRecord.Done(plan.id, 6, 0, true, 1))
        for (op in listOf(Op.VerifyHome(amazon), Op.PressHome)) {
            j.append(JournalRecord.Begin(plan.id, -1, op, lines(h, op), null, Samples.NOW_MS)); j.append(JournalRecord.Done(plan.id, -1, 0, true, 1))
        }
        j.append(JournalRecord.Paused(plan.id, pausedAt, PauseReason.STEP_FAILED))
    }

    // Issue #2's journal and TV as the person left them: the plan file saved, the section ARMED and
    // never closed, the refused switch-off at step 5, step R's refused enable, Home Amazon's, and a
    // second round of step R after a restart paused "at step 0".
    private suspend fun stuckOn031(h: Harness): Plan {
        val plan = olderPlan(h)
        h.plans.save(plan)
        val j = h.journal
        j.append(JournalRecord.PlanStart(plan.id, plan.tvId, plan.baseSnapshot, plan.steps.size, plan.hash, "0.3.1", Samples.NOW_MS))
        for (i in 0..3) {
            j.append(JournalRecord.Begin(plan.id, i, plan.steps[i].op, lines(h, plan.steps[i].op), null, Samples.NOW_MS)); j.append(JournalRecord.Done(plan.id, i, 0, true, 1))
        }
        j.append(JournalRecord.Section(plan.id, DefaultPlanner.SECTION_HOME, token, SectionState.ARMED, Samples.NOW_MS + 150_000))
        j.append(JournalRecord.Begin(plan.id, 4, plan.steps[4].op, lines(h, plan.steps[4].op), null, Samples.NOW_MS)); j.append(JournalRecord.Done(plan.id, 4, 0, true, 1))
        j.append(JournalRecord.Begin(plan.id, 5, plan.steps[5].op, lines(h, plan.steps[5].op), null, Samples.NOW_MS))
        j.append(JournalRecord.Failed(plan.id, 5, 1, "exit 1", protectedStderr))
        stepR031(h, plan, pausedAt = 5)
        stepR031(h, plan, pausedAt = 0)
        // The TV: the two switch-offs done, the watchdog killed and defused, Amazon's home in charge.
        for (p in h.debloat.take(2)) h.tv.packages[p] = h.tv.packages.getValue(p).copy(enabled = EnabledState.DISABLED_USER)
        h.tv.files["${RemoteDir.TMP.path}/${Watchdog.TOKEN_FILE_PREFIX}${token.value}"] = ByteArray(0)
        assertEquals(listOf(plan.id), h.journal.openRuns(h.tv.tvId))
        return plan
    }

    // Updating to 0.3.2 frees the stuck setup by itself (A6): the app's resume on reconnect runs step
    // R, the refused enable's target holds, Home verifies as Amazon's, and the section closes
    // ROLLED_BACK with no tap. The run then waits on the "We've put the old home screen back" card.
    @Test fun resumingIssue2sJournalClosesTheSectionWithoutATap() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this, fireTv())
        val plan = stuckOn031(h)
        val before = h.records(plan).size
        val engine = h.engine()
        engine.resume(h.tv.tvId, h.tv)
        val paused = assertIs<RunState.Paused>(engine.runState(h.tv.tvId).value)
        assertEquals(PauseReason.CONNECTION_LOST, paused.reason, "the old home screen is back card")
        assertEquals(5, paused.at, "the step the run stopped at, not step 0")
        val added = h.types(plan).drop(before)
        assertEquals(listOf("rollback4", "begin-1", "done-1", "begin-1", "done-1", "begin5", "done5", "begin6", "done6",
            "begin-1", "done-1", "begin-1", "done-1", "section:ROLLED_BACK", "paused:CONNECTION_LOST@5"), added)
        assertEquals(amazon, h.tv.home)
        assertEquals(EnabledState.ENABLED, h.tv.state(amazon.pkg))
        assertEquals(1, h.tv.execs("pm enable --user 0 ${amazon.pkg.value}"))
        assertEquals(0, h.tv.execs("pm disable-user --user 0 ${amazon.pkg.value}"), "nothing forward was re-run")
    }

    // From that card every way out finishes, with no second pause (A5, A6).
    @Test fun carryOnUndoAndStopEachFinishAfterTheResume() = runTest(timeout = TEST_TIMEOUT) {
        for (action in listOf("continue", "undo", "abandon")) {
            val h = Harness(this, fireTv())
            val plan = stuckOn031(h)
            val engine = h.engine()
            engine.resume(h.tv.tvId, h.tv)
            assertIs<RunState.Paused>(engine.runState(h.tv.tvId).value, action)
            val pausesBefore = h.types(plan).count { it.startsWith("paused") }
            when (action) {
                "continue" -> engine.continueRun(h.tv.tvId, h.pass())
                "undo" -> engine.undoRun(h.tv.tvId, h.pass())
                else -> engine.abandon(h.tv.tvId)
            }
            val fin = assertIs<RunState.Finished>(h.settle(engine), action)
            val expected = mapOf("continue" to RunStatus.COMPLETED_WITH_SKIPS, "undo" to RunStatus.ROLLED_BACK, "abandon" to RunStatus.ABANDONED).getValue(action)
            assertEquals(expected, fin.status, action)
            assertEquals(pausesBefore, h.types(plan).count { it.startsWith("paused") }, "$action: no new pause")
            assertEquals("end:${expected.name}", h.types(plan).last(), action)
            assertTrue(h.journal.openRuns(h.tv.tvId).isEmpty(), action)
            assertEquals(amazon, h.tv.home, action)
            assertEquals(EnabledState.ENABLED, h.tv.state(amazon.pkg), action)
            if (action == "undo") for (p in h.debloat.take(2)) assertEquals(EnabledState.ENABLED, h.tv.state(p), "$action: ${p.value} back on")
            else for (p in h.debloat.take(2)) assertEquals(EnabledState.DISABLED_USER, h.tv.state(p), "$action: ${p.value} kept off")
        }
    }

    // A plan file written by 0.3.1 for a Fire TV, started by the 0.3.2 engine: the switch-off of Fire
    // OS's home is refused by the live check before any line reaches the TV, step R's refused enable
    // holds, and the run ends with the switch skipped instead of a pause.
    @Test fun aFireTvTakeoverFromAnOlderPlanEndsWithTheSwitchSkipped() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this, fireTv())
        h.tv.installBytes(h.apkBytes)   // the new home screen is on the TV and starts
        val plan = olderPlan(h)
        val engine = h.engine()
        assertEquals(StartResult.Started, engine.start(plan, h.tv, plan.hash, h.pass()))
        val fin = assertIs<RunState.Finished>(h.settle(engine))
        assertEquals(RunStatus.COMPLETED_WITH_SKIPS, fin.status)
        assertEquals(HomeOutcome.VERIFY_FAILED, fin.home)
        assertEquals(amazon, h.tv.home)
        assertTrue(h.tv.watchdogs.isEmpty())
        assertEquals(0, h.tv.execs("pm disable-user --user 0 ${amazon.pkg.value}"), "never sent")
        val failed = h.records(plan).filterIsInstance<JournalRecord.Failed>().first { it.i == 5 }
        assertEquals(DefaultEngine.PROTECTED + DefaultPlanner.REASON_FIRE_OS_HOME, failed.error)
        assertEquals(listOf("section:ARMED", "section:ROLLED_BACK"), h.types(plan).filter { it.startsWith("section") })
        assertTrue(h.types(plan).none { it.startsWith("paused") })
    }

    // Only a journal with no records is a forgotten run: one that is merely not listed as open (as
    // in the moment between end() writing End and dropping the run) is never dropped. Here openRuns
    // never lists the run, and abandon still runs step R and leaves the run paused, not Idle.
    @Test fun aRunWhoseJournalStillHasRecordsIsNeverDropped() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val unlisted = object : Journal by h.journal {
            override suspend fun openRuns(tvId: TvId): List<PlanId> = emptyList()
        }
        val engine = h.engine(journal = unlisted)
        val plan = h.setupPlan()
        assertEquals(StartResult.Started, engine.start(plan, h.tv, plan.hash, h.pass()))
        advanceTimeBy(20_000); runCurrent()
        assertIs<RunState.AwaitingConfirm>(engine.runState(h.tv.tvId).value)
        h.tv.refuse = { it == h.stock.pkg }
        engine.rejectHome(h.tv.tvId)
        assertIs<RunState.Paused>(h.settle(engine))
        val rollbacks = h.types(plan).count { it.startsWith("rollback") }
        engine.abandon(h.tv.tvId)
        assertIs<RunState.Paused>(h.settle(engine), "not dropped: step R ran again and could not finish")
        assertEquals(rollbacks + 1, h.types(plan).count { it.startsWith("rollback") })
    }

    // A5: "Forget this TV" deletes the TV's journals; the engine then drops its paused run from memory
    // (abandon needs no TV for that), and a new Setup for the same TV is not refused RUN_OPEN.
    @Test fun aForgottenTvsPausedRunIsDroppedAndANewSetupStarts() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val root: File = Files.createTempDirectory("cyt-forget").toFile().also { it.deleteOnExit() }
        val journal = FileJournal(root, EmptyCoroutineContext)
        val engine = h.engine(journal = journal)
        val plan = h.setupPlan()
        assertEquals(StartResult.Started, engine.start(plan, h.tv, plan.hash, h.pass()))
        advanceTimeBy(20_000); runCurrent()
        assertIs<RunState.AwaitingConfirm>(engine.runState(h.tv.tvId).value)
        h.tv.refuse = { it == h.stock.pkg }   // step R cannot put the TV's own home screen back: the run pauses
        engine.rejectHome(h.tv.tvId)
        assertIs<RunState.Paused>(h.settle(engine))
        val again = h.setupPlan()
        assertEquals(StartResult.Refused(StartRefusal.RUN_OPEN), engine.start(again, h.tv, again.hash, h.pass()))
        // Forget this TV: the session closes and the TV's folder goes, journals included.
        h.tv.disconnect()
        assertTrue(File(root, "tvs/${h.tv.tvId.value}").deleteRecursively())
        engine.abandon(h.tv.tvId)
        assertEquals(RunState.Idle, engine.runState(h.tv.tvId).value)
        assertEquals(0, h.tv.holds, "the run's hold on the session is closed")
        // Added again later: a new Setup starts.
        h.tv.refuse = { false }
        h.tv.reconnect()
        val fresh = h.setupPlan()
        assertEquals(StartResult.Started, engine.start(fresh, h.tv, fresh.hash, h.pass()))
    }
}
