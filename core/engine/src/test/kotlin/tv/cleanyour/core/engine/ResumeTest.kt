package tv.cleanyour.core.engine

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.Quirk
import tv.cleanyour.core.ops.Watchdog
import tv.cleanyour.testing.fixtures.FakeProfileSource
import tv.cleanyour.testing.fixtures.MemoryJournal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// The journal crash matrix (ARCHITECTURE 6.3, F5): the process dies after every journal record of a
// full Setup run with the takeover; a fresh engine resumes from the journal and the plan file and
// the TV ends consistent with the change records. Killing is simulated by a journal that throws
// after n appends, which is where a real process death leaves a torn or missing record.
class ResumeTest {

    // Drives a run to its end, answering "yes" on the check screen.
    private suspend fun TestScope.drive(engine: Engine, h: Harness): RunState {
        var guard = 0
        while (true) {
            val s = engine.runState(h.tv.tvId).value
            if (s is RunState.Finished || s is RunState.Paused || s is RunState.Idle) return s
            if (s is RunState.AwaitingConfirm) engine.confirmHome(h.tv.tvId)
            advanceTimeBy(1_000); runCurrent()
            check(guard++ < 10_000) { "run did not settle" }
        }
    }

    @Test fun killAfterEveryRecordThenResumeReachesAConsistentState() = runTest(timeout = TEST_TIMEOUT) {
        val probe = Harness(this)
        val full = probe.setupPlan()
        val e0 = probe.engine()
        e0.start(full, probe.tv, full.hash, probe.pass())
        assertIs<RunState.Finished>(drive(e0, probe))
        val total = probe.records(full).size
        assertTrue(total > 30, "records in a full run: $total")

        for (n in 1 until total) {
            val h = Harness(this)
            val crashing = MemoryJournal().also { it.crashAfter = n }
            val e1 = h.engine(journal = crashing)
            val plan = h.setupPlan()
            e1.start(plan, h.tv, plan.hash, h.pass())
            val first = drive(e1, h)
            assertIs<RunState.Paused>(first, "crash after $n records")
            // The app comes back: a new engine over the same journal, plan file and TV.
            crashing.crashAfter = null
            val e2 = DefaultEngine(h.renderer, h.prober, crashing, h.snapshots, h.plans, h.clock, backgroundScope, h.profiles)
            e2.resume(h.tv.tvId, h.tv)
            val resumed = e2.runState(h.tv.tvId).value
            assertIs<RunState.Paused>(resumed, "after resume, crash at $n")
            assertTrue(resumed.reason in setOf(PauseReason.IN_DOUBT, PauseReason.CONNECTION_LOST, PauseReason.STEP_FAILED), "crash at $n: ${resumed.reason}")
            val records = crashing.read(plan.id)
            assertTrue(records.none { it is JournalRecord.End }, "still open")
            if (records.filterIsInstance<JournalRecord.Section>().any { it.state == SectionState.ROLLED_BACK }) assertTrue(h.tv.watchdogs.isEmpty(), "crash at $n: no watchdog left after step R")
            // Every step before `at` is done on the TV; `at` itself may or may not be.
            for (s in plan.steps.take(resumed.at)) {
                val op = s.op
                if (op is Op.Disable && op.pkg != h.stock.pkg) assertEquals(EnabledState.DISABLED_USER, h.tv.state(op.pkg), "crash at $n: step ${s.index} ${op.pkg.value}")
            }
            e2.continueRun(h.tv.tvId, h.pass())
            val fin = drive(e2, h)
            assertIs<RunState.Finished>(fin, "crash at $n")
            val all = crashing.read(plan.id)
            assertEquals(1, all.count { it is JournalRecord.End }, "crash at $n")
            // Consistency: the change records describe the TV exactly.
            val changes = ChangeRecords.fromJournal(all)
            for (p in h.debloat) {
                assertEquals(EnabledState.DISABLED_USER, h.tv.state(p), "crash at $n: ${p.value}")
                assertTrue(changes.any { (it.op as? Op.Disable)?.pkg == p }, "crash at $n: a change record for ${p.value}")
            }
            assertEquals(h.apkSha, h.tv.installed[h.launcher.pkg], "crash at $n: launcher installed")
            val rolledBack = all.filterIsInstance<JournalRecord.Section>().any { it.state == SectionState.ROLLED_BACK }
            if (rolledBack) {
                assertEquals(RunStatus.COMPLETED_WITH_SKIPS, fin.status, "crash at $n")
                assertEquals(h.stock, h.tv.home, "crash at $n")
                assertEquals(EnabledState.ENABLED, h.tv.state(h.stock.pkg), "crash at $n")
                assertTrue(changes.none { it.op is Op.SetHome || (it.op as? Op.Disable)?.pkg == h.stock.pkg }, "crash at $n")
            } else {
                assertEquals(RunStatus.COMPLETED, fin.status, "crash at $n")
                assertEquals(h.launcher, h.tv.home, "crash at $n")
                assertTrue(changes.any { it.op is Op.SetHome }, "crash at $n")
            }
            assertTrue(h.tv.watchdogs.isEmpty(), "crash at $n: no watchdog left running")
            // No step ran twice without being idempotent: at most one disable attempt per package beyond the retry of the in-doubt step.
            for (p in h.debloat) assertTrue(h.tv.execs("pm disable-user --user 0 ${p.value}") <= 2, "crash at $n: ${p.value}")
            assertTrue(crashing.openRuns(h.tv.tvId).isEmpty(), "crash at $n")
        }
    }

    @Test fun resumeWithNothingOpenDoesNothing() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val engine = h.engine()
        engine.resume(h.tv.tvId, h.tv)
        assertEquals(RunState.Idle, engine.runState(h.tv.tvId).value)
        assertTrue(h.tv.execLog.isEmpty())
    }

    @Test fun aTamperedPlanFileIsNeverResumed() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val e1 = h.engine()
        val plan = h.setupPlan(takeover = false, installLauncher = false)
        h.tv.dropOnLine = { it.contains(h.debloat[1].value) && it.contains("disable-user") }
        e1.start(plan, h.tv, plan.hash, h.pass())
        drive(e1, h)
        assertIs<RunState.Paused>(e1.runState(h.tv.tvId).value)
        // Someone edits the plan file: a different package in a step.
        val path = "tvs/${h.tv.tvId.value}/plans/${plan.id.value}.json"
        val text = h.documents.bytes(path)!!.decodeToString().replace(h.debloat[3].value, "com.android.systemui")
        h.documents.write(path, text.encodeToByteArray())
        h.tv.dropOnLine = null; h.tv.reconnect()
        val e2 = h.engine()
        e2.resume(h.tv.tvId, h.tv)
        assertEquals(RunState.Idle, e2.runState(h.tv.tvId).value)
        assertEquals("end:ABANDONED", h.types(plan).last())
        assertTrue(h.journal.openRuns(h.tv.tvId).isEmpty())
        assertEquals(0, h.tv.execs("dumpsys package com.android.systemui") + h.tv.execs("pm disable-user --user 0 com.android.systemui"))
    }

    // R1: the plan file's privilege, verify read, timeout and cap are compared on resume too, so a
    // file edited to make a write step look like a read (no gate) is refused.
    @Test fun aPlanFileWithATamperedPrivilegeIsNeverResumed() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val e1 = h.engine()
        val plan = h.setupPlan(takeover = false, installLauncher = false)
        h.tv.dropOnLine = { it.contains(h.debloat[1].value) && it.contains("disable-user") }
        e1.start(plan, h.tv, plan.hash, h.pass())
        drive(e1, h)
        assertIs<RunState.Paused>(e1.runState(h.tv.tvId).value)
        val path = "tvs/${h.tv.tvId.value}/plans/${plan.id.value}.json"
        val text = h.documents.bytes(path)!!.decodeToString()
        assertTrue(text.contains("\"privilege\":\"PROFILE_STEP\""))
        h.documents.write(path, text.replace("\"privilege\":\"PROFILE_STEP\"", "\"privilege\":\"READ\"").encodeToByteArray())
        h.tv.dropOnLine = null; h.tv.reconnect()
        val e2 = h.engine()
        e2.resume(h.tv.tvId, h.tv)
        assertEquals(RunState.Idle, e2.runState(h.tv.tvId).value)
        assertEquals("end:ABANDONED", h.types(plan).last())
    }

    @Test fun resumeVerifiesTheInDoubtStepAndMarksItResumed() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val crashing = MemoryJournal()
        val e1 = h.engine(journal = crashing)
        val plan = h.setupPlan(takeover = false, installLauncher = false)
        crashing.crashAfter = 4   // plan, begin0, done0, begin1, then the crash on done1: the op ran, the record did not land
        e1.start(plan, h.tv, plan.hash, h.pass())
        drive(e1, h)
        assertEquals(EnabledState.DISABLED_USER, h.tv.state(h.debloat[1]))
        assertEquals(listOf("plan", "begin0", "done0", "begin1"), crashing.read(plan.id).map { r ->
            when (r) { is JournalRecord.PlanStart -> "plan"; is JournalRecord.Begin -> "begin${r.i}"; is JournalRecord.Done -> "done${r.i}"; else -> r::class.simpleName!! }
        })
        crashing.crashAfter = null
        val e2 = DefaultEngine(h.renderer, h.prober, crashing, h.snapshots, h.plans, h.clock, backgroundScope, h.profiles)
        e2.resume(h.tv.tvId, h.tv)
        val paused = e2.runState(h.tv.tvId).value as RunState.Paused
        assertEquals(PauseReason.IN_DOUBT, paused.reason)
        assertEquals(2, paused.at, "the in-doubt step verified as done, so the run continues after it")
        val done = crashing.read(plan.id).filterIsInstance<JournalRecord.Done>().last()
        assertEquals(1, done.i); assertTrue(done.resumed)
        e2.continueRun(h.tv.tvId, h.pass())
        assertEquals(RunStatus.COMPLETED, (drive(e2, h) as RunState.Finished).status)
        assertEquals(1, h.tv.execs("pm disable-user --user 0 ${h.debloat[1].value}"), "not sent again")
    }

    // Pauses a takeover inside its guarded section: the TV vanishes right after set-home-activity,
    // so our launcher is HOME, the stock launcher is off and the watchdog sleeps.
    private suspend fun TestScope.pauseInsideTheSection(h: Harness, plan: tv.cleanyour.core.planner.Plan) {
        val e1 = h.engine()
        h.tv.dropOnLine = { it.contains("resolve-activity") }
        e1.start(plan, h.tv, plan.hash, h.pass())
        drive(e1, h)
        assertEquals(PauseReason.CONNECTION_LOST, (e1.runState(h.tv.tvId).value as RunState.Paused).reason)
        assertEquals(h.launcher, h.tv.home)
        assertEquals(EnabledState.DISABLED_USER, h.tv.state(h.stock.pkg))
        assertTrue(plan.sections.single().token.value in h.tv.watchdogs)
        h.tv.dropOnLine = null
    }

    private fun planPath(h: Harness, plan: tv.cleanyour.core.planner.Plan) = "tvs/${h.tv.tvId.value}/plans/${plan.id.value}.json"

    // R1 R-2: a plan file that is missing or tampered while the journal shows an open section: step
    // R is built from the journal (the token, the section's Disable ops, the SetHome prior) and runs
    // before the run is given up, so the TV's own home screen is back even without the watchdog.
    @Test fun anUntrustedPlanFileWithAnOpenSectionStillGetsStepRFromTheJournal() = runTest(timeout = TEST_TIMEOUT) {
        for (case in listOf("missing", "tampered", "wizard")) {
            val h = Harness(this)
            val profile = if (case == "wizard") h.profile(setOf(Quirk.HOME_TAKEOVER_SETUPWRAITH), h.wizard) else h.profile()
            h.profiles = FakeProfileSource(listOf(profile))
            val plan = h.setupPlan(profile = profile)
            pauseInsideTheSection(h, plan)
            if (case == "wizard") assertEquals(EnabledState.DISABLED_USER, h.tv.state(h.wizard))
            when (case) {
                "missing" -> h.documents.delete(planPath(h, plan))
                else -> h.documents.write(planPath(h, plan), h.documents.bytes(planPath(h, plan))!!.decodeToString().replace("\"privilege\":\"PROFILE_STEP\"", "\"privilege\":\"READ\"").encodeToByteArray())
            }
            // While the TV is away nothing can be put back: the journal stays open for the next resume.
            h.tv.disconnect()
            val e2 = h.engine()
            e2.resume(h.tv.tvId, h.tv)
            assertEquals(listOf(plan.id), h.journal.openRuns(h.tv.tvId), case)
            assertEquals(h.launcher, h.tv.home, case)
            h.tv.reconnect()
            val e3 = h.engine()
            e3.resume(h.tv.tvId, h.tv)
            val fin = e3.runState(h.tv.tvId).value
            assertIs<RunState.Finished>(fin, case)
            assertEquals(RunStatus.ABANDONED, fin.status, case)
            assertEquals(HomeOutcome.CONNECTION_LOST, fin.home, case)
            assertEquals(h.stock, h.tv.home, case)
            assertEquals(EnabledState.ENABLED, h.tv.state(h.stock.pkg), case)
            if (case == "wizard") assertEquals(EnabledState.ENABLED, h.tv.state(h.wizard), case)
            assertTrue(h.tv.watchdogs.isEmpty(), case)
            assertTrue(h.tv.files.containsKey("/data/local/tmp/cleanyourtv/${Watchdog.TOKEN_FILE_PREFIX}${plan.sections.single().token.value}"), case)
            assertEquals(listOf("section:ARMED", "section:ROLLED_BACK"), h.types(plan).filter { it.startsWith("section") }, case)
            assertEquals("end:ABANDONED", h.types(plan).last(), case)
            assertTrue(h.journal.openRuns(h.tv.tvId).isEmpty(), case)
            for (p in h.debloat) assertEquals(EnabledState.DISABLED_USER, h.tv.state(p), "$case: the debloat is kept")
        }
    }

    // R1 R-2 with a failing step R: the run pauses with the error and the section open; continue
    // runs step R again and only then gives the run up.
    @Test fun aFailingJournalStepRPausesAndContinueRetriesIt() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val plan = h.setupPlan()
        pauseInsideTheSection(h, plan)
        h.documents.delete(planPath(h, plan))
        h.tv.reconnect()
        h.tv.refuse = { it == h.stock.pkg }
        val e2 = h.engine()
        e2.resume(h.tv.tvId, h.tv)
        val paused = e2.runState(h.tv.tvId).value
        assertIs<RunState.Paused>(paused)
        assertEquals(PauseReason.STEP_FAILED, paused.reason)
        assertEquals(listOf("section:ARMED"), h.types(plan).filter { it.startsWith("section") })
        h.tv.refuse = { false }
        e2.continueRun(h.tv.tvId, null)   // step R needs no pass
        h.settle(e2)
        assertEquals(RunStatus.ABANDONED, (e2.runState(h.tv.tvId).value as RunState.Finished).status)
        assertEquals(h.stock, h.tv.home)
        assertTrue(h.tv.watchdogs.isEmpty())
    }

    // A4 (issue #2): a resume whose step R fails pauses at the step the run stood at, from the
    // journal, never at step 0 (0.3.1 set the index only after step R).
    @Test fun aPauseInStepRAfterResumeNamesTheStepNotZero() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val plan = h.setupPlan()
        val section = plan.sections.single()
        pauseInsideTheSection(h, plan)
        h.tv.reconnect()
        h.tv.refuse = { it == h.stock.pkg }   // step R cannot switch the TV's own home screen back on
        val e2 = h.engine()
        e2.resume(h.tv.tvId, h.tv)
        val paused = assertIs<RunState.Paused>(e2.runState(h.tv.tvId).value)
        assertEquals(PauseReason.STEP_FAILED, paused.reason)
        assertEquals(section.to - 1, paused.at, "the in-doubt SetHome, as the journal says")
        assertTrue(paused.at != 0)
        assertEquals("paused:STEP_FAILED@${section.to - 1}", h.types(plan).last())
        assertEquals(listOf("section:ARMED"), h.types(plan).filter { it.startsWith("section") }, "Home is not back: still open")
    }

    // R1 R-6: an undo cut off by a crash carries on undoing after resume; "Continue" never turns it
    // back into a forward run that re-applies what the user chose to undo.
    @Test fun aCutOffUndoCarriesOnUndoingAfterResume() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val crashing = MemoryJournal()
        val e1 = h.engine(journal = crashing)
        val plan = h.setupPlan(takeover = false, installLauncher = false)
        h.tv.dropOnLine = { it.contains(h.debloat[3].value) && it.contains("disable-user") }
        e1.start(plan, h.tv, plan.hash, h.pass())
        drive(e1, h)
        assertEquals(3, (e1.runState(h.tv.tvId).value as RunState.Paused).at)
        h.tv.dropOnLine = null; h.tv.reconnect()
        crashing.crashAfter = 4   // rollback, begin2, done2, begin1; the crash lands on done1
        e1.undoRun(h.tv.tvId, h.pass())
        h.settle(e1)
        assertIs<RunState.Paused>(e1.runState(h.tv.tvId).value)
        for (p in listOf(h.debloat[1], h.debloat[2])) assertTrue(!h.tv.isOff(p), "${p.value}: undone before the crash")
        assertEquals(EnabledState.DISABLED_USER, h.tv.state(h.debloat[0]), "not undone yet")
        crashing.crashAfter = null
        val e2 = DefaultEngine(h.renderer, h.prober, crashing, h.snapshots, h.plans, h.clock, backgroundScope, h.profiles)
        e2.resume(h.tv.tvId, h.tv)
        assertIs<RunState.Paused>(e2.runState(h.tv.tvId).value)
        e2.continueRun(h.tv.tvId, h.pass())
        val fin = h.settle(e2)
        assertIs<RunState.Finished>(fin)
        assertEquals(RunStatus.ROLLED_BACK, fin.status)
        for (p in h.debloat) assertTrue(!h.tv.isOff(p), p.value)
        for (p in h.debloat.drop(3)) assertEquals(0, h.tv.execs("pm disable-user --user 0 ${p.value}") - (if (p == h.debloat[3]) 1 else 0), "${p.value}: not run forward")
        assertTrue(crashing.openRuns(h.tv.tvId).isEmpty())
    }
}
