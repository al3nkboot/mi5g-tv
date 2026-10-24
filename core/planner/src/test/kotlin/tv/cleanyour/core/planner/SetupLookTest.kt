package tv.cleanyour.core.planner

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.ops.FailurePolicy
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.planner.PlannerFixtures.choices
import tv.cleanyour.core.planner.PlannerFixtures.debloatList
import tv.cleanyour.core.planner.PlannerFixtures.installedLauncher
import tv.cleanyour.core.planner.PlannerFixtures.launcher
import tv.cleanyour.core.planner.PlannerFixtures.plan
import tv.cleanyour.core.planner.PlannerFixtures.setup
import tv.cleanyour.core.planner.PlannerFixtures.snapshot
import tv.cleanyour.core.planner.PlannerFixtures.stock
import tv.cleanyour.core.store.SnapshotCodec
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// RFC-DOCK-1: the Setup plan sends the dock (with its rows) and the look as one config.json, with
// the same steps, priors and narration as ApplyTheme, after the launcher install and before the
// guarded home screen switch; never when the new home screen is not part of the plan.
class SetupLookTest {
    private val config = BlobRef(Sha256("c".repeat(64)), 100, "blobs/config")
    private val wallpaper = BlobRef(Sha256("d".repeat(64)), 2_000, "blobs/wallpaper")
    private val renderer = ShellRenderer()

    // A fresh planner per plan, so two plans draw the same run token and compare op by op.
    private fun planner() = PlannerFixtures.planner(OverlayTransport())
    private fun look(choices: Choices = choices(), wallpaper: BlobRef? = null) = setup(choices = choices).copy(config = config, wallpaper = wallpaper)
    private fun ops(p: Plan) = p.steps.map { it.op }
    private fun theme(p: Plan) = p.steps.filter { it.group == DefaultPlanner.GROUP_THEME }
    private fun names(steps: List<Step>) = steps.map { it.op::class.simpleName }

    @Test fun theLookComesAfterTheInstallAndBeforeTheSwitch() {
        val p = plan(look(wallpaper = wallpaper), planner = planner())
        val without = plan(setup(), planner = planner())
        val tail = p.steps.dropWhile { it.op is Op.Disable }
        assertEquals(
            listOf("Settle", "InstallStream", "StartActivity", "Mkdir", "PushAsset", "PushAsset", "RestartHome",
                "StartActivity", "Mkdir", "ArmHomeWatchdog", "Disable", "SetHome", "VerifyHome", "ConfirmHome", "KillWatchdog"),
            names(tail),
        )
        val look = theme(p)
        assertEquals(OverlayTransport().writePlanSteps(config, wallpaper), look.map { it.op })
        assertEquals((look.first().index..look.last().index).toList(), look.map { it.index }, "one run of steps")
        assertTrue(p.steps.indexOfFirst { it.op is Op.InstallStream } < look.first().index, "the launcher's first start comes after its install")
        // The guarded section is the same switch as without the look, and the look is outside it.
        val section = p.sections.single()
        assertTrue(look.last().index < section.from)
        assertEquals(p.steps.indexOfFirst { it.op is Op.ArmHomeWatchdog }, section.from)
        assertEquals(p.steps.indexOfFirst { it.op is Op.VerifyHome }, section.to)
        assertEquals(without.sections.single().copy(from = section.from, to = section.to), section)
        assertEquals(ops(without), p.steps.filter { it.group != DefaultPlanner.GROUP_THEME }.map { it.op }, "nothing else moved or changed")
        // ApplyTheme's narration; a fresh launcher has nothing in its folder, so both pushes say so;
        // nothing of the look stops the setup.
        assertEquals(listOf("Preparing the home screen", "Preparing the home screen", "Sending the look to the TV", "Sending the look to the TV",
            "Restarting the home screen"), look.map { it.narration })
        assertEquals(listOf(null, null, Prior(present = false), Prior(present = false), null), look.map { it.prior })
        assertTrue(look.all { it.onFailure == FailurePolicy.CONTINUE })
        // The hash and "Show commands" cover the pushes; S3's summary is the same as without them.
        assertEquals(renderer.planHash(p.steps.map { it.rendered }), p.hash)
        assertNotEquals(without.hash, p.hash)
        assertTrue(look.filter { it.op is Op.PushAsset }.all { it.rendered.transfer != null })
        assertEquals(without.summary, p.summary)
    }

    @Test fun noLauncherNoConfigPush() {
        // The switch is not possible (a Fire OS or TCL TV, or one that refuses installs): neither the
        // launcher nor the look.
        val none = plan(look(choices(installLauncher = false, takeover = false)), planner = planner())
        assertTrue(none.steps.none { it.group == DefaultPlanner.GROUP_THEME || it.op is Op.PushAsset || it.op is Op.RestartHome }, ops(none).toString())
        assertTrue(none.steps.none { it.op is Op.InstallStream })
        // The launcher installed but the switch not chosen: nothing would read the file.
        val noSwitch = plan(look(choices(takeover = false)), planner = planner())
        assertTrue(noSwitch.steps.any { it.op is Op.InstallStream })
        assertTrue(theme(noSwitch).isEmpty(), ops(noSwitch).toString())
        // No config in the request (the R0 shape every older caller sends): no look, as before.
        assertTrue(theme(plan(setup(), planner = planner())).isEmpty())
    }

    @Test fun aLauncherAlreadyOnTheTvKeepsTheProbedOverlayAsThePrior() {
        val overlay = "{\"columns\":4}".encodeToByteArray()
        val snap = snapshot(extra = mapOf(installedLauncher())).let { s -> s.copy(launcherOverlay = overlay).let { it.copy(contentHash = SnapshotCodec.hash(it)) } }
        val p = plan(look(), snap, planner = planner())
        assertTrue(p.steps.none { it.op is Op.InstallStream || it.op is Op.Install }, "the pin is already installed")
        val look = theme(p)
        assertEquals(listOf("StartActivity", "Mkdir", "PushAsset", "RemoveFile", "RestartHome"), names(look))
        val push = look.single { it.op is Op.PushAsset }.prior!!
        assertEquals(true, push.present)
        assertTrue(overlay.contentEquals(push.overlay))
        assertEquals(Prior(present = null), look.single { it.op is Op.RemoveFile }.prior)
        assertTrue(look.last().index < p.sections.single().from)
    }

    @Test fun aReplacedLauncherStartsWithAnEmptyFolder() {
        val other = Sha256("9".repeat(64))
        val snap = snapshot(extra = mapOf(installedLauncher(signer = other))).let { s -> s.copy(launcherOverlay = byteArrayOf(1)).let { it.copy(contentHash = SnapshotCodec.hash(it)) } }
        val p = plan(look(choices(replaceSigner = true), wallpaper = wallpaper), snap, planner = planner())
        assertTrue(p.summary.replacesLauncher)
        val uninstall = p.steps.indexOfFirst { it.op == Op.Uninstall(launcher.pkg) }
        val install = p.steps.indexOfFirst { it.op is Op.InstallStream }
        val look = theme(p)
        assertTrue(uninstall in 0 until install && install < look.first().index)
        assertTrue(look.filter { it.op is Op.PushAsset }.all { it.prior == Prior(present = false) }, "the uninstall took the old files")
    }

    @Test fun aLauncherAlreadyInChargeGetsOnlyTheLook() {
        val snap = snapshot(disabled = debloatList.map { it.value }.toSet(), home = launcher, claimants = listOf(launcher, stock), extra = mapOf(installedLauncher()))
        val p = plan(look(), snap, planner = planner())
        assertEquals(OverlayTransport().writePlanSteps(config, null), ops(p))
        assertTrue(p.sections.isEmpty())
        assertTrue(p.summary.homeScreenPossible)
    }

    @Test fun applyThemeKeepsItsPriorsAndStopsOnAFailedPush() {
        val snap = snapshot(extra = mapOf(installedLauncher()))
        val p = plan(PlanRequest.ApplyTheme(tv.cleanyour.core.spec.LauncherConfigDoc(), config, wallpaper), snap, planner = planner())
        val pushes = p.steps.filter { it.op is Op.PushAsset }
        assertEquals(listOf(FailurePolicy.STOP_AND_ROLLBACK, FailurePolicy.STOP_AND_ROLLBACK), pushes.map { it.onFailure })
        assertEquals(listOf(Prior(present = false, overlay = null), Prior(present = null)), pushes.map { it.prior })
    }

    // Undo everything after a Setup with the look: the home screen first, then our launcher's files,
    // and only the files the records say were not there before us. A removed wallpaper that was never
    // there is no step; with the launcher removed, its folder goes with it.
    @Test fun undoEverythingTakesTheSetupLookOffAgain() {
        val p = plan(look(), planner = planner())
        val changes = p.steps.filter { isChange(it.op) }.map { ChangeRecord(p.id, it.index, it.op, it.prior, Samples.NOW_MS + it.index) }
        val after = snapshot(disabled = debloatList.map { it.value }.toSet() + stock.pkg.value, home = launcher, claimants = listOf(launcher, stock), extra = mapOf(installedLauncher()))
        val undo = ops(plan(PlanRequest.UndoEverything(changes, removeLauncher = false), after, changes))
        assertEquals(listOf(Op.RemoveFile(OverlayTransport.CONFIG), Op.RestartHome(launcher)), undo.takeLast(2))
        assertTrue(Op.RemoveFile(OverlayTransport.WALLPAPER) !in undo, undo.toString())
        assertTrue(undo.indexOf(Op.SetHome(stock)) < undo.indexOf(Op.RemoveFile(OverlayTransport.CONFIG)))
        val gone = ops(plan(PlanRequest.UndoEverything(changes, removeLauncher = true), after, changes))
        assertTrue(Op.Uninstall(launcher.pkg) in gone)
        assertTrue(gone.none { it is Op.RemoveFile || it is Op.RestartHome }, gone.toString())
    }

    private fun isChange(op: Op) = op is Op.Disable || op is Op.Enable || op is Op.DefaultState || op is Op.Install || op is Op.InstallStream ||
        op is Op.Uninstall || op is Op.SettingsPut || op is Op.SetHome || op is Op.PushAsset || op is Op.RemoveFile
}
