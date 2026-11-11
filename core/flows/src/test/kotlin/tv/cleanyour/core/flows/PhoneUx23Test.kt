package tv.cleanyour.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.discovery.DiscoveryEvent
import tv.cleanyour.core.engine.PauseReason
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.HomeResolution
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.spec.KnownLaunchers
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// 2.3 item 4: phone UX from cleanyourtv-app 0.4.8 to 0.5.0-beta1 (MIT).
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneUx23Test {
    private suspend fun kotlinx.coroutines.test.TestScope.toFound(h: Harness, m: SetupMachine) {
        h.discovery.emit(DiscoveryEvent.Found(miTvFound)); settle()
        m.dispatch(SetupIntent.Start); settle()
        h.discovery.open5555[HOST] = true
        settle(2_100); settle(700)
    }

    // #1222: a kept row goes off in one tap, also a row the list leaves on to ask (it needs extraOff).
    @Test fun aKeptRowGoesOffInOneTap() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        val m = h.setup(backgroundScope)
        toFound(h, m)
        m.dispatch(SetupIntent.Keep(promoPkg, keep = true)); settle()
        m.dispatch(SetupIntent.Keep(promoPkg, keep = false)); settle()
        val choices = h.planner.requests.filterIsInstance<PlanRequest.Setup>().last().choices
        assertEquals(emptySet(), choices.kept)
        assertEquals(setOf(promoPkg), choices.extraOff)
    }

    // #2851: the TV ended the launcher's install and is clearly low on space: the card says so.
    @Test fun aLauncherInstallOnAFullTvSaysToFreeSpace() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        h.prober.freeMb = 120
        val blob = BlobRef(Samples.zeroHash, 700_000, "bundled:cleanyourtv-launcher.apk")
        h.setupPlan = h.plan(listOf(Op.InstallStream(blob, blob.sha256, Launcher.pkg, 11, Samples.zeroHash), Op.Disable(adPkg)))
        h.engine.onStart = { p -> emit(p.tvId, RunState.Paused(p, 0, PauseReason.STEP_FAILED, AdbError.NonZeroExit(1, "the TV closed the stream"))) }
        val m = h.setup(backgroundScope)
        toFound(h, m)
        m.dispatch(SetupIntent.MakeItClean); settle()
        val card = assertIs<SetupState.Stopped>(m.state.value).card
        assertEquals(StopCase.INSTALL_FAILED, card.case)
        assertEquals(KeyCopy.text(CopyKey.STOP_LOW_STORAGE_BODY, mapOf("free" to 120, "done" to 0, "total" to 1)), card.body)
        assertTrue(h.logLines.any { it == "setup: install failed, TV storage 120 MB free" })
    }

    @Test fun plentyOfSpaceKeepsThePlainInstallCard() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        h.prober.freeMb = 4_000
        val blob = BlobRef(Samples.zeroHash, 700_000, "bundled:cleanyourtv-launcher.apk")
        h.setupPlan = h.plan(listOf(Op.InstallStream(blob, blob.sha256, Launcher.pkg, 11, Samples.zeroHash), Op.Disable(adPkg)))
        h.engine.onStart = { p -> emit(p.tvId, RunState.Paused(p, 0, PauseReason.STEP_FAILED, AdbError.NonZeroExit(1, "the TV closed the stream"))) }
        val m = h.setup(backgroundScope)
        toFound(h, m)
        m.dispatch(SetupIntent.MakeItClean); settle()
        val card = assertIs<SetupState.Stopped>(m.state.value).card
        assertEquals(KeyCopy.text(CopyKey.STOP_INSTALL_FAILED_BODY, mapOf("done" to 0, "total" to 1)), card.body)
    }

    @Test fun clearlyLowIsUnderTwoHundredMbOrTwiceTheApk() {
        assertTrue(InstallStorage.low(199, 1_000_000))
        assertEquals(false, InstallStorage.low(200, 1_000_000))
        assertTrue(InstallStorage.low(250, 150L * 1024 * 1024))
        assertEquals(false, InstallStorage.low(301, 150L * 1024 * 1024))
    }

    // #3760: saying no at the home check has a third way out that keeps the TV's own home screen.
    @Test fun saidNoHasAKeepOwnHomeButton() {
        assertEquals(CopyKey.STOP_HOME_KEEP_OWN, stopKeys(StopCase.HOME_USER_SAID_NO).tertiary)
    }

    // #5539: a Google TV home screen the person put on an Android TV set does not make it a Google TV.
    @Test fun theReportCallsAnAndroidTvWithASideloadedLauncherxAndroidTv() {
        val tvl = KnownLaunchers.TVLAUNCHER
        val lx = KnownLaunchers.LAUNCHERX
        fun st(system: Boolean) = PackageState(EnabledState.DEFAULT, system, 1000, null, false, false, null, false)
        val snap = Samples.snapshot(packages = mapOf(tvl.pkg to st(true), lx.pkg to st(false)),
            home = HomeResolution(tvl, listOf(HomeClaimant(tvl, 2), HomeClaimant(lx, 0))))
        assertEquals("Android TV (Leanback)", TvReport.platformOf(snap))
        val real = Samples.snapshot(packages = mapOf(lx.pkg to st(true)), home = HomeResolution(lx, listOf(HomeClaimant(lx, 2))))
        assertEquals("Google TV", TvReport.platformOf(real))
    }
}
