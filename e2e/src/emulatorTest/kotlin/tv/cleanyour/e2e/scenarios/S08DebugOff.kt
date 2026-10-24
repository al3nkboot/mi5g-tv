package tv.cleanyour.e2e.scenarios

import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.RunStatus
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.e2e.E2eScenario

// D7 and T-N4: `debug_off` needs no credential pass (SESSION_END), and afterwards the TV refuses
// the next connect. It switches adbd off for good, so this scenario always runs last in the
// suite; the session script then kills the emulator through its console.
class S08DebugOff : E2eScenario() {

    @Test fun debugOffThenTheNextConnectIsRefused() = e2e {
        val engine = engine()
        val session = open()
        val base = baseline(session)
        val p = plan(PlanRequest.DebugOff, base)
        val end = runToEnd(engine, p, session, pass = null)
        assertTrue("run ended $end", end is RunState.Finished && end.status == RunStatus.COMPLETED)
        runCatching { session.close() }

        Thread.sleep(3_000)
        val again = sessionManager().open(cfg.endpoint, session.tvId) {}
        assertTrue("connect after debug_off returned $again", again is OpenResult.Failed)
        val line = oracle.devicesLine()
        assertTrue("host adb still sees a usable device: $line", line == null || !line.trimEnd().endsWith("device"))
    }
}
