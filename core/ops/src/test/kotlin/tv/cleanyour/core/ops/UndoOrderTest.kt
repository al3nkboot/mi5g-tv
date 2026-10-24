package tv.cleanyour.core.ops

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.ProbeKind
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.model.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UndoOrderTest {
    private val launcher = Component(PackageName("com.gathin.tvhome"), ".MainActivity")
    private val stock = Component(PackageName("com.google.android.tvlauncher"), ".MainActivity")
    private val a = PackageName("com.miui.tv.analytics")
    private val sha = Sha256("c".repeat(64))
    private val cfg = RemotePath(RemoteDir.OVERLAY, listOf("config.json"))
    private fun rec(plan: String, step: Int, at: Long, op: Op, prior: Prior?) = ChangeRecord(PlanId(plan), step, op, prior, at)

    @Test fun earliestRecordOfEachItemWinsAndPhasesKeepTheTvWithAHomeScreen() {
        val changes = listOf(
            // Listed out of order on purpose: the helper sorts by time, then step.
            rec("p2", 0, 200, Op.Enable(a), Prior(packageState = EnabledState.DISABLED_USER)),
            rec("p1", 0, 100, Op.Disable(a), Prior(packageState = EnabledState.DEFAULT)),
            rec("p1", 1, 100, Op.InstallStream(BlobRef(sha, 5, "l"), sha, launcher.pkg, 1, sha), Prior(present = false)),
            rec("p1", 2, 100, Op.Disable(stock.pkg), Prior(packageState = EnabledState.ENABLED)),
            rec("p1", 3, 100, Op.SetHome(launcher), Prior(home = stock)),
            rec("p1", 4, 100, Op.Read(ProbeKind.HOME), null),
            rec("p3", 0, 300, Op.SetHome(launcher), Prior(home = launcher)),
            rec("p3", 1, 300, Op.SettingsPut(SettingKey.SCREENSAVER_ENABLED, "0"), Prior(settingValue = "1")),
            rec("p3", 2, 300, Op.PushAsset(BlobRef(sha, 5, "c"), cfg, sha), Prior(present = false)),
        )
        val order = UndoOrder.undoOrder(changes)
        assertEquals(listOf(
            "p1/2", "p1/0",   // package states, newest first touch first; a's earliest record is the p1 Disable
            "p3/1",           // settings
            "p1/3",           // the home screen: the first SetHome, whose prior is the stock launcher
            "p1/1",           // our apps, after the home screen is back
            "p3/2",           // our files
        ), order.map { "${it.planId.value}/${it.step}" })
        val r = ShellRenderer()
        assertEquals(listOf(Op.Enable(stock.pkg), Op.DefaultState(a), Op.SettingsPut(SettingKey.SCREENSAVER_ENABLED, "1"),
            Op.SetHome(stock), Op.Uninstall(launcher.pkg), Op.RemoveFile(cfg)),
            order.map { r.inverse(it.op, it.prior!!) })
    }

    // RFC-DOCK-1: the Setup plan sends the look after the launcher install and before the home
    // screen switch, so the overlay files are touched before SetHome. Undo still puts the home screen
    // back and removes our launcher before it touches the files, and the files go only because their
    // first records say they were not there before us.
    @Test fun theSetupLookIsUndoneLastThoughItWasSentBeforeTheSwitch() {
        val wallpaper = RemotePath(RemoteDir.OVERLAY, listOf("wallpaper.jpg"))
        val changes = listOf(
            rec("s", 0, 100, Op.Disable(a), Prior(packageState = EnabledState.ENABLED)),
            rec("s", 1, 101, Op.InstallStream(BlobRef(sha, 5, "l"), sha, launcher.pkg, 1, sha), Prior(present = false)),
            rec("s", 2, 102, Op.PushAsset(BlobRef(sha, 5, "c"), cfg, sha), Prior(present = false)),
            rec("s", 3, 103, Op.RemoveFile(wallpaper), Prior(present = false)),
            rec("s", 4, 104, Op.Disable(stock.pkg), Prior(packageState = EnabledState.ENABLED)),
            rec("s", 5, 105, Op.SetHome(launcher), Prior(home = stock)),
        )
        val order = UndoOrder.undoOrder(changes)
        assertEquals(listOf("s/4", "s/0", "s/5", "s/1", "s/3", "s/2"), order.map { "${it.planId.value}/${it.step}" })
        assertEquals(listOf(UndoOrder.Phase.PACKAGES, UndoOrder.Phase.HOME, UndoOrder.Phase.APPS, UndoOrder.Phase.FILES),
            order.map { UndoOrder.target(it.op)!!.phase }.distinct())
        val r = ShellRenderer()
        assertEquals(Op.RemoveFile(cfg), r.inverse(order.last().op, order.last().prior!!))
    }

    @Test fun tiesOnTimeBreakOnThePlanStepAndTransientOpsHaveNoTarget() {
        val x = rec("p", 5, 10, Op.Disable(a), Prior(packageState = EnabledState.ENABLED))
        val y = rec("p", 2, 10, Op.Enable(a), Prior(packageState = EnabledState.DEFAULT))
        assertEquals(listOf(y), UndoOrder.earliest(listOf(x, y)))
        for (op in listOf(Op.PressHome, Op.Reboot, Op.DebugOff, Op.Settle(1), GoldenCases.cue(tv.cleanyour.core.model.CurtainStage.OFF), Op.Mkdir(RemoteDir.TMP), Op.VerifyHome(stock), Op.RestartHome(launcher)))
            assertNull(UndoOrder.target(op), op.toString())
        assertEquals(emptyList(), UndoOrder.undoOrder(emptyList()))
    }
}
