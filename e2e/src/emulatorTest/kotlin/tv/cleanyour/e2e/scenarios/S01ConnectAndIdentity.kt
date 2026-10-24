package tv.cleanyour.e2e.scenarios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.e2e.E2eScenario
import tv.cleanyour.e2e.Timings
import java.security.MessageDigest

// F1 and D37 against a real adbd: :adb:session connects with the known key and no Allow, reads
// the identity the TV reports, derives the TvId the spec defines, and refuses a different TV.
class S01ConnectAndIdentity : E2eScenario() {

    private fun expectedTvId(): TvId {
        val raw = oracle.prop("ro.serialno") + oracle.prop("ro.product.device")
        val hex = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
        return TvId(hex.take(16))
    }

    @Test fun connectsWithTheKnownKeyAndReportsTheIdentity() = e2e {
        val manager = sessionManager()
        var allowShown = false
        val t0 = System.nanoTime()
        val session = open(manager) { allowShown = true }
        timings.record("session.connect", (System.nanoTime() - t0) / 1e6, "ms", Timings.CONNECT_MS)
        try {
            assertFalse("a known key must not raise the Allow dialog", allowShown)
            assertTrue(session.state.value is ConnectionState.Connected)
            val id = session.identity
            assertEquals(expectedTvId(), id.tvId)
            assertEquals(session.tvId, id.tvId)
            assertEquals(oracle.sdk(), id.sdk)
            assertEquals(oracle.prop("ro.product.model"), id.model)
            assertEquals(oracle.prop("ro.product.device"), id.device)
            assertEquals(oracle.prop("ro.adb.secure") == "1", id.adbSecure)
            assertTrue("shell_v2" in id.features)
        } finally {
            session.close()
        }
    }

    @Test fun aDifferentTvAtTheAddressIsRefusedBeforeAnyLine() = e2e {
        val wrong = TvId("0123456789abcdef")
        val r = sessionManager().open(cfg.endpoint, wrong) {}
        assertTrue("expected WrongTv, got $r", r is OpenResult.Failed && r.error is AdbError.Policy.WrongTv)
    }

    @Test fun reopeningReusesTheTrustWithoutASecondPrompt() = e2e {
        val manager = sessionManager()
        open(manager).close()
        var prompts = 0
        val again = open(manager, expected = expectedTvId()) { prompts++ }
        again.close()
        assertEquals(0, prompts)
    }
}
