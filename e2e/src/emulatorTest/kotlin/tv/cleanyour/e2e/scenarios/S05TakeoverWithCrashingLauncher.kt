package tv.cleanyour.e2e.scenarios

import com.android.apksig.ApkVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.engine.HomeOutcome
import tv.cleanyour.core.engine.JournalRecord
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.RunStatus
import tv.cleanyour.core.engine.StartResult
import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.planner.Choices
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.session.TvSession
import tv.cleanyour.e2e.E2eDeps
import tv.cleanyour.e2e.E2eScenario
import tv.cleanyour.e2e.E2eWiring
import java.io.File
import java.security.MessageDigest

// S10 and T-V4 (PRODUCT-SPEC 3.4, SECURITY 6.3): the takeover with a launcher that crashes when it
// is HOME (fixture e2e/fixtures/crashing-launcher, package com.gathin.tvhome). First with the phone
// side alive: the live check sees the wrong focus and step R puts the stock home back. Then with
// the phone side killed right after the section opens: the TV-side watchdog alone restores the
// stock home after 120 s.
class S05TakeoverWithCrashingLauncher : E2eScenario() {

    private fun sha256(bytes: ByteArray) = Sha256(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

    private fun crashingArtifact(): LauncherArtifact {
        val apk: File = need(cfg.crashingLauncherApk, "crashing launcher fixture APK (CYT_E2E_CRASHING_LAUNCHER_APK, built by nightly)")
        val bytes = apk.readBytes()
        val cert = ApkVerifier.Builder(apk).build().verify().signerCertificates.first().encoded
        // Package and version are fixed by the fixture's build file.
        return LauncherArtifact(PackageName(LAUNCHER_PKG), 1, "e2e-crash", sha256(bytes), sha256(cert), blobs.add(bytes))
    }

    private suspend fun takeoverPlan(session: TvSession): Pair<Plan, Snapshot> {
        // RF-SAFETY-250: no profile matches the emulator, so its setup wizard would stay on while the
        // stock home goes off and the planner gives no switch; switched off here (and on again after).
        oracle.shell("pm disable-user --user 0 com.google.android.tungsten.setupwraith")
        val base = baseline(session)
        snapshots.save(base)
        val profiles = need(E2eWiring.profiles(deps), "W4 ProfileSource (E2eWiring.profiles)")
        val resolution = profiles.resolve(prober().facts(session))
        // Keep every package: this scenario is about the HOME section alone.
        val choices = Choices(kept = base.packages.keys, extraOff = emptySet(), dock = null, theme = null,
            installLauncher = true, takeover = true, replaceLauncherSigner = false, keepDebuggingOn = true)
        return plan(PlanRequest.Setup(resolution, choices, crashingArtifact(), Level.SIMPLE), base) to base
    }

    @After fun putTheStockHomeBack() {
        oracle.uninstall(LAUNCHER_PKG)
        oracle.enable(STOCK_HOME_PKG)
        oracle.enable("com.google.android.tungsten.setupwraith")
        oracle.shell("cmd package set-home-activity $STOCK_HOME_PKG/.HomeScreen")
        oracle.shell("pkill -f cleanyourtv-rollback")
        oracle.pressHome()
    }

    private fun assertStockHomeIsBack() {
        assertEquals("$STOCK_HOME_PKG/$STOCK_HOME_PKG.HomeScreen", oracle.homeActivity()?.let(::fullClass))
        assertFalse("stock launcher still disabled", STOCK_HOME_PKG in oracle.disabledPackages())
        oracle.pressHome()
        Thread.sleep(3_000)
        assertEquals(STOCK_HOME_PKG, oracle.resumedPackage())
    }

    @Test fun liveCheckRollsBackAWrongFocus() = e2e(timeoutMs = 600_000) {
        val engine = engine()
        val session = open()
        try {
            val (plan, _) = takeoverPlan(session)
            assertTrue("no guarded section in the takeover plan", plan.sections.isNotEmpty())
            val end = runToEnd(engine, plan, session, pass(session.tvId)) { awaiting ->
                // What the person does on the check screen: press Home on the remote.
                assertTrue(awaiting.deadlineMs > System.currentTimeMillis())
                oracle.pressHome()
            }
            assertTrue("run ended $end", end is RunState.Finished)
            end as RunState.Finished
            assertEquals(RunStatus.COMPLETED_WITH_SKIPS, end.status)
            assertTrue("home outcome ${end.home}", end.home == HomeOutcome.WRONG_FOCUS || end.home == HomeOutcome.TIMED_OUT)
            assertStockHomeIsBack()
            assertFalse("watchdog still running after step R", oracle.processAlive("cleanyourtv-rollback"))
        } finally {
            session.close()
        }
    }

    @Test fun watchdogAloneRestoresTheStockHomeWhenThePhoneIsGone() = e2e(timeoutMs = 600_000) {
        // An engine in its own scope, so killing "the phone" leaves the shared harness intact.
        val phoneScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val d = E2eDeps(deps.config, deps.signer, deps.sockets, deps.blobs, deps.clock, deps.ids, deps.journal, deps.snapshots, phoneScope)
        val engine = need(E2eWiring.engine(d, prober(), renderer()), "W3 Engine (E2eWiring.engine)")
        val session = open()
        val (plan, _) = takeoverPlan(session)
        assertTrue(engine.start(plan, session, plan.hash, pass(session.tvId)) is StartResult.Started)
        engine.runState(plan.tvId).first { it is RunState.AwaitingConfirm || it is RunState.Finished }
        val armedAt = System.currentTimeMillis()
        val token = journal.read(plan.id).filterIsInstance<JournalRecord.Section>().first().token

        // The phone dies: no rollback from this side, no more lines.
        phoneScope.cancel()
        runCatching { session.close() }
        assertTrue("watchdog not running on the TV", oracle.processAlive("cleanyourtv-rollback-${token.value}"))

        val deadline = armedAt + 150_000
        while (System.currentTimeMillis() < deadline &&
            oracle.homeActivity()?.let(::fullClass) != "$STOCK_HOME_PKG/$STOCK_HOME_PKG.HomeScreen") delay(2_000)
        val restoredAfter = (System.currentTimeMillis() - armedAt) / 1000.0
        timings.record("watchdog.restoreSeconds", restoredAfter, "s", 130.0)
        assertStockHomeIsBack()
        assertTrue("restored too early (${restoredAfter}s): the phone side must have been gone", restoredAfter >= 100)
    }
}
