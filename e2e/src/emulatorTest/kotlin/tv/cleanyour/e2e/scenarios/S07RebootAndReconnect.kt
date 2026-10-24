package tv.cleanyour.e2e.scenarios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.session.BootOutcome
import tv.cleanyour.e2e.E2eScenario

// ARCHITECTURE 3.10 on a real adbd: reboot through the session, wait for the boot, come back on
// the same session with a new boot id and the same TV identity.
class S07RebootAndReconnect : E2eScenario() {

    @Test fun rebootComesBackWithANewBootId() = e2e(timeoutMs = 420_000) {
        val session = open()
        try {
            val before = oracle.bootId()
            val tvId = session.tvId
            val t0 = System.nanoTime()
            session.reboot()
            val outcome = session.awaitBoot(300_000)
            timings.record("reboot.backSeconds", (System.nanoTime() - t0) / 1e9, "s")
            assertTrue("boot outcome $outcome", outcome is BootOutcome.Back)
            assertTrue(oracle.waitForBoot(60_000))
            val after = oracle.bootId()
            assertNotEquals(before, after)
            assertEquals(after, (outcome as BootOutcome.Back).bootId)
            assertTrue(session.state.value is ConnectionState.Connected)
            assertEquals(tvId, session.identity.tvId)
        } finally {
            session.close()
        }
    }
}
