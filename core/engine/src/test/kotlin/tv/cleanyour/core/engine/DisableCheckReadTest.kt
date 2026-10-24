package tv.cleanyour.core.engine

import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.session.ExecResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// A switch-off whose pm write answered 0 is checked with pm list packages -d. When that check read
// comes back empty or races the write, the package's own dump decides: off is done (and recorded, so
// Switched off, Undo everything, the restore point and restore.txt all list it), and only a dump that
// shows the package on is the TV keeping it on (issue #10, Issue10Test). A check read or a dump read
// that errors is a failure: the run pauses, as in 0.3.2, and nothing is skipped.
class DisableCheckReadTest {
    private val empty = ExecResult(0, "", "", false, 1)
    private val error = ExecResult(1, "", "Error: could not access the package manager", false, 1)

    @Test fun anEmptyCheckReadOfAPackageThatIsOffIsDoneAndRecorded() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val pkg = h.debloat.first()
        h.tv.answer = { line -> if (line == "pm list packages -d") empty else null }
        val engine = h.engine()
        val plan = h.opsPlan(Op.Disable(pkg))
        engine.start(plan, h.tv, plan.hash, h.pass())
        val fin = assertIs<RunState.Finished>(h.settle(engine))
        assertEquals(RunStatus.COMPLETED, fin.status)
        assertTrue(fin.skipped.isEmpty())
        assertEquals(EnabledState.DISABLED_USER, h.tv.state(pkg))
        val records = h.records(plan)
        assertTrue(records.none { it is JournalRecord.Failed })
        assertEquals(0, records.filterIsInstance<JournalRecord.Done>().single().i)
        // The change records are what Switched off, Undo everything and the restore files read.
        assertEquals(listOf(Op.Disable(pkg)), ChangeRecords.fromJournal(records).map { it.op })
        assertEquals(listOf(Op.Disable(pkg)), ChangeRecords.fromJournals(listOf(records)).map { it.op })
    }

    @Test fun aCheckReadThatErrorsPausesAndSkipsNothing() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val pkg = h.debloat.first()
        h.tv.answer = { line -> if (line == "pm list packages -d") error else null }
        val engine = h.engine()
        val plan = h.opsPlan(Op.Disable(pkg))
        engine.start(plan, h.tv, plan.hash, h.pass())
        val paused = assertIs<RunState.Paused>(h.settle(engine))
        assertEquals(PauseReason.STEP_FAILED, paused.reason)
        assertEquals(0, paused.at)
        assertIs<tv.cleanyour.core.model.AdbError.NonZeroExit>(paused.error)
        val records = h.records(plan)
        assertTrue(records.none { it is JournalRecord.Done })
        assertTrue(h.records(plan).filterIsInstance<JournalRecord.Failed>().single().error.startsWith("verify read: exit 1"))
        assertTrue(h.types(plan).any { it.startsWith("paused:STEP_FAILED") })
        assertTrue(ChangeRecords.fromJournal(records).isEmpty())
    }

    @Test fun aDumpReadThatErrorsAfterAnEmptyCheckReadPauses() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val pkg = h.debloat.first()
        var dumps = 0
        h.tv.answer = { line ->
            when (line) {
                "pm list packages -d" -> empty
                // The live check's dump before the write answers; the re-read after it errors.
                "dumpsys package ${pkg.value}" -> if (++dumps >= 2) error else null
                else -> null
            }
        }
        val engine = h.engine()
        val plan = h.opsPlan(Op.Disable(pkg))
        engine.start(plan, h.tv, plan.hash, h.pass())
        val paused = assertIs<RunState.Paused>(h.settle(engine))
        assertEquals(PauseReason.STEP_FAILED, paused.reason)
        assertEquals(2, dumps)
        assertTrue(h.records(plan).none { it is JournalRecord.Done })
    }

    @Test fun anEmptyCheckReadOfAPackageTheDumpShowsOnIsTheKeptOnSkip() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val pkg = h.debloat.first()
        h.tv.answer = { line ->
            if (line == "pm disable-user --user 0 ${pkg.value}") ExecResult(0, "Package ${pkg.value} new state: enabled", "", false, 1) else null
        }
        val engine = h.engine()
        val plan = h.opsPlan(Op.Disable(pkg))
        engine.start(plan, h.tv, plan.hash, h.pass())
        val fin = assertIs<RunState.Finished>(h.settle(engine))
        assertEquals(RunStatus.COMPLETED_WITH_SKIPS, fin.status)
        assertEquals(listOf(Op.Disable(pkg)), fin.skipped.map { it.op })
        assertTrue(ChangeRecords.fromJournal(h.records(plan)).isEmpty())
    }
}
