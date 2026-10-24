package tv.cleanyour.e2e.scenarios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlinx.coroutines.delay
import org.junit.Test
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.store.SnapshotCodec
import tv.cleanyour.e2e.CountingSession
import tv.cleanyour.e2e.E2eScenario
import tv.cleanyour.e2e.Timings

// The probe against a real adbd (I1): the BASELINE snapshot agrees with what the platform's own
// tools report, in under 10 shell round trips (D40) and within the API 28 probe budget (trend).
class S02ProbeAndBaseline : E2eScenario() {

    @Test fun baselineSnapshotMatchesTheTv() = e2e {
        val prober = prober()
        val session = CountingSession(open())
        try {
            val t0 = System.nanoTime()
            val snap = prober.snapshot(session, SnapshotReason.BASELINE)
            timings.record("probe.full", (System.nanoTime() - t0) / 1e6, "ms", Timings.PROBE_API28_MS)
            timings.record("probe.shellCalls", session.shellCalls.toDouble(), "calls", Timings.PROBE_MAX_SHELL_CALLS.toDouble())
            assertTrue("probe used ${session.shellCalls} shell calls: ${session.lines}", session.shellCalls < Timings.PROBE_MAX_SHELL_CALLS)

            val onTv = oracle.allPackages()
            val inSnapshot = snap.packages.keys.map { it.value }.toSet()
            assertEquals("packages differ", onTv - "android", inSnapshot - "android")

            val disabledOnTv = oracle.disabledPackages()
            val disabledInSnapshot = snap.packages.filterValues { it.enabled == EnabledState.DISABLED_USER || it.enabled == EnabledState.DISABLED }
                .keys.map { it.value }.toSet()
            assertEquals(disabledOnTv, disabledInSnapshot)

            val home = oracle.homeActivity()
            assertEquals(home?.let(::fullClass), snap.home.resolved?.flat?.let(::fullClass))
            assertEquals(snap.contentHash, SnapshotCodec.hash(snap))
            assertEquals(session.tvId, snap.tvId)
            snapshots.save(snap)
        } finally {
            session.close()
        }
    }

    @Test fun homeFocusAndBootIdReadsAgree() = e2e {
        val prober = prober()
        val session = open()
        try {
            oracle.pressHome()
            delay(1_500)
            assertEquals(oracle.resumedPackage(), prober.homeFocus(session)?.pkg?.value)
            assertEquals(oracle.bootId(), prober.bootId(session))
            val png = prober.screenshot(session)
            assertTrue("screenshot missing or over 2 MiB", png != null && png.size in 1..(2 * 1024 * 1024))
        } finally {
            session.close()
        }
    }
}
