package tv.cleanyour.e2e.scenarios

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.RunStatus
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.ops.Protected
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.e2e.E2eScenario
import tv.cleanyour.e2e.Timings
import tv.cleanyour.e2e.changesFrom

// I2 on a real adbd: switch off a set of preloaded packages through planner and engine (with the
// credential pass), check on the TV, then undo everything from the journal and check again.
class S03DebloatAndUndo : E2eScenario() {

    companion object {
        // Preloaded packages of the API 28 Android TV image (captures/api28-atv-packages.txt)
        // that a person could switch off without losing the TV: samples, tutorials,
        // recommendations, screensaver, print, voice, accessibility extras. 18 of them: the
        // ARCHITECTURE 4 budget is for 40, so the number is scaled per package.
        val EMULATOR_SAFE = listOf(
            "com.google.android.tvtutorials", "com.google.android.tvrecommendations",
            "com.google.android.leanbacklauncher.recommendations", "com.lego.android.tvleanback",
            "com.android.printspooler", "com.android.dreams.basic", "com.google.android.backdrop",
            "com.google.android.katniss", "com.android.htmlviewer", "com.android.traceur",
            "com.google.android.marvin.talkback", "com.google.android.tts", "com.android.netspeed",
            "com.android.emergency", "com.google.android.sss.authbridge", "com.android.companiondevicemanager",
            "com.google.android.syncadapters.contacts", "com.android.wallpaperbackup",
        )
    }

    private var touched: List<String> = emptyList()

    @After fun leaveTheTvAsFound() {
        // Safety net only: the undo plan is what is under test.
        touched.forEach { oracle.enable(it) }
    }

    @Test fun switchOffThenUndoEverything() = e2e(timeoutMs = 600_000) {
        val engine = engine()
        val session = open()
        try {
            val base = baseline(session)
            snapshots.save(base)
            val enabledNow = oracle.enabledSystemPackages()
            val candidates = EMULATOR_SAFE.filter { it in enabledNow }
                .map(::PackageName)
                .filter { Protected.isProtected(it, base.packages[it], null) == null }
                .toSet()
            assertTrue("no candidates left on this image", candidates.isNotEmpty())
            touched = candidates.map { it.value }

            val off = plan(PlanRequest.SwitchOff(candidates), base)
            val t0 = System.nanoTime()
            val end = runToEnd(engine, off, session, pass(session.tvId))
            val ms = (System.nanoTime() - t0) / 1e6
            timings.record("debloat.perPackage", ms / candidates.size, "ms", Timings.DEBLOAT_40_MS / 40)
            timings.record("debloat.scaledTo40", ms / candidates.size * 40, "ms", Timings.DEBLOAT_40_MS)
            assertTrue("run ended $end", end is RunState.Finished && end.status == RunStatus.COMPLETED)
            assertEquals(candidates.map { it.value }.toSet(), oracle.disabledPackages() intersect touched.toSet())

            val changes = changesFrom(journal.read(off.id))
            assertEquals(candidates.size, changes.size)
            val after = prober().snapshot(session, tv.cleanyour.core.model.SnapshotReason.POST_PLAN)
            val undo = plan(PlanRequest.UndoEverything(changes, removeLauncher = false), after)
            val undone = runToEnd(engine, undo, session, pass(session.tvId))
            assertTrue("undo ended $undone", undone is RunState.Finished && undone.status == RunStatus.COMPLETED)
            assertTrue("still disabled: ${oracle.disabledPackages() intersect touched.toSet()}",
                (oracle.disabledPackages() intersect touched.toSet()).isEmpty())
        } finally {
            session.close()
        }
    }

    @Test fun aWritePlanWithoutThePassIsRefused() = e2e {
        val engine = engine()
        val session = open()
        try {
            val base = baseline(session)
            val off = plan(PlanRequest.SwitchOff(setOf(PackageName("com.google.android.tvtutorials"))), base)
            val r = engine.start(off, session, off.hash, null)
            assertEquals(tv.cleanyour.core.engine.StartResult.Refused(tv.cleanyour.core.engine.StartRefusal.GATE_REQUIRED), r)
            assertTrue("com.google.android.tvtutorials" !in oracle.disabledPackages())
        } finally {
            session.close()
        }
    }
}
