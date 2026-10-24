package tv.cleanyour.core.flows

import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.engine.JournalRecord
import tv.cleanyour.core.engine.PauseReason
import tv.cleanyour.core.engine.SectionState
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.testing.fixtures.MemoryDocumentStore
import tv.cleanyour.testing.fixtures.MemoryJournal
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// S1: the op a pause line names is the one that made the run pause, read from the journal: in step R
// the first must-succeed failure after the latest Rollback, otherwise the failure of the step the run
// paused at, and none at all rather than an older refusal that was only skipped.
class RunLedgerTest {
    private val plan = PlanId("01FAKEPLAN0000000000000001")
    private val token = RunToken("0123456789abcdef")
    private val amazon = Component(PackageName("com.amazon.tv.launcher"), ".ui.HomeActivity_vNext")
    private val absent = PackageName("com.example.absent")
    private val ad = PackageName("com.example.ads")

    private suspend fun ledger(vararg records: JournalRecord): RunLedger {
        val journal = MemoryJournal()
        journal.append(JournalRecord.PlanStart(plan, Samples.miTvId(), Samples.zeroHash, 10, Samples.zeroHash, "0.3.2", 0))
        for (r in records) journal.append(r)
        return RunLedger(MemoryDocumentStore(), journal)
    }

    private fun begin(i: Int, op: Op) = JournalRecord.Begin(plan, i, op, listOf("line"), null, 0)
    private fun done(i: Int) = JournalRecord.Done(plan, i, 0, true, 1)
    private fun failed(i: Int, stderr: String) = JournalRecord.Failed(plan, i, 1, "exit 1", stderr)

    // Issue #2's step R: ConfirmHome done, KillWatchdog refused (not a must-succeed op), the Enable
    // refused, SetHome and VerifyHome failing after it. The pause is about the Enable.
    private val stepR = arrayOf(
        JournalRecord.Section(plan, "home", token, SectionState.ARMED, 0),
        begin(5, Op.Disable(amazon.pkg)), failed(5, "protected"),
        JournalRecord.Rollback(plan, 4),
        begin(-1, Op.ConfirmHome(token)), done(-1),
        begin(-1, Op.KillWatchdog(token)), failed(-1, "pkill"),
        begin(5, Op.Enable(amazon.pkg)), failed(5, "enable refused"),
        begin(6, Op.SetHome(amazon)), failed(6, "set-home refused"),
        begin(-1, Op.VerifyHome(amazon)), failed(-1, "not home"),
        begin(-1, Op.PressHome), done(-1),
    )

    @Test fun aStepRPauseNamesTheFirstMustSucceedFailureAfterTheRollback() = runTest {
        for (withRecord in listOf(true, false)) {
            val records = if (withRecord) stepR + JournalRecord.Paused(plan, 5, PauseReason.STEP_FAILED) else stepR
            val f = assertNotNull(ledger(*records).lastFailure(plan, 5), "$withRecord")
            assertEquals(Op.Enable(amazon.pkg), f.op)
            assertEquals("enable refused", f.stderrHead)
            assertTrue(f.undo)
        }
    }

    // A refused Enable before a VerifyHome that passed was a skip; the SetHome failing after it is
    // what kept the TV's own home screen from coming back.
    @Test fun aRefusalBeforeAPassingVerifyHomeIsASkip() = runTest {
        val l = ledger(
            JournalRecord.Section(plan, "home", token, SectionState.ARMED, 0),
            JournalRecord.Rollback(plan, 4),
            begin(5, Op.Enable(amazon.pkg)), failed(5, "enable refused"),
            begin(-1, Op.VerifyHome(amazon)), done(-1),
            begin(6, Op.SetHome(amazon)), failed(6, "set-home refused"),
            JournalRecord.Paused(plan, 5, PauseReason.STEP_FAILED),
        )
        assertEquals(Op.SetHome(amazon), assertNotNull(l.lastFailure(plan, 5)).op)
    }

    // A second round of step R (Carry on after the first pause) names its own failure, and a round
    // in which no must-succeed op failed names none.
    @Test fun eachRoundOfStepRIsReadOnItsOwn() = runTest {
        val l = ledger(
            *stepR, JournalRecord.Paused(plan, 5, PauseReason.STEP_FAILED),
            JournalRecord.Rollback(plan, 4),
            begin(-1, Op.KillWatchdog(token)), failed(-1, "pkill"),
            JournalRecord.Paused(plan, 5, PauseReason.STEP_FAILED),
        )
        assertNull(l.lastFailure(plan, 5))
    }

    // Outside step R: the failure of the step the run paused at, never an older skipped refusal.
    @Test fun aForwardPauseNamesTheStepItPausedAtOrNothing() = runTest {
        val skipped = arrayOf<JournalRecord>(begin(0, Op.Disable(absent)), failed(0, "package not on the TV"), begin(1, Op.Disable(ad)), done(1))
        assertNull(ledger(*skipped, JournalRecord.Paused(plan, 2, PauseReason.STEP_FAILED)).lastFailure(plan, 2))
        assertNull(ledger(*skipped).lastFailure(plan, 2))
        val l = ledger(*skipped, begin(2, Op.Disable(ad)), failed(2, "refused"), JournalRecord.Paused(plan, 2, PauseReason.STEP_FAILED))
        val f = assertNotNull(l.lastFailure(plan, 2))
        assertEquals(Op.Disable(ad), f.op)
        assertEquals(false, f.undo)
    }

    // A closed section resets step R: a later forward pause is not an undo step.
    @Test fun aClosedSectionEndsStepR() = runTest {
        val l = ledger(
            *stepR.take(4).toTypedArray(),
            begin(5, Op.Enable(amazon.pkg)), failed(5, "enable refused"),
            begin(-1, Op.VerifyHome(amazon)), done(-1),
            JournalRecord.Section(plan, "home", token, SectionState.ROLLED_BACK, 0),
            begin(8, Op.Disable(ad)), failed(8, "refused"),
            JournalRecord.Paused(plan, 8, PauseReason.STEP_FAILED),
        )
        val f = assertNotNull(l.lastFailure(plan, 8))
        assertEquals(Op.Disable(ad), f.op)
        assertEquals(false, f.undo)
    }

    // An undo of the whole run (a Rollback with no section open) skips what it cannot put back: those
    // failures are never the pause's op.
    @Test fun anUndoOfTheWholeRunNamesNoSkippedStep() = runTest {
        val l = ledger(
            begin(2, Op.Disable(ad)), done(2),
            JournalRecord.Rollback(plan, 3),
            begin(3, Op.Enable(ad)), failed(3, "refused"),
            JournalRecord.Paused(plan, 3, PauseReason.STEP_FAILED),
        )
        assertNull(l.lastFailure(plan, 3))
    }
}
