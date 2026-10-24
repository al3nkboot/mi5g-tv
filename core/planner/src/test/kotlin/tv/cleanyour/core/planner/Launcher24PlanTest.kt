package tv.cleanyour.core.planner

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.InstallFlag
import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.Quirk
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.ops.FailurePolicy
import tv.cleanyour.core.planner.PlannerFixtures.choices
import tv.cleanyour.core.planner.PlannerFixtures.installedLauncher
import tv.cleanyour.core.planner.PlannerFixtures.launcher
import tv.cleanyour.core.planner.PlannerFixtures.plan
import tv.cleanyour.core.planner.PlannerFixtures.planner
import tv.cleanyour.core.planner.PlannerFixtures.profile
import tv.cleanyour.core.planner.PlannerFixtures.refused
import tv.cleanyour.core.planner.PlannerFixtures.setup
import tv.cleanyour.core.planner.PlannerFixtures.snapshot
import tv.cleanyour.core.planner.PlannerFixtures.stock
import tv.cleanyour.core.planner.PlannerFixtures.wizard
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Launcher v2.4.0 (LAUNCHER-240-PLAN, RFC-LAUNCHER-1): the setup wizard and the saved Wi-Fi (D3),
// the takeover's start check, the signer that the probe now reads, "Update the home screen" (D4)
// and the accessibility services a restart of Home switches off.
class Launcher24PlanTest {
    private fun ops(p: Plan) = p.steps.map { it.op }
    private val ours = Samples.launcherArtifact()
    private fun newer(versionCode: Long = 5) = LauncherArtifact(ours.pkg, versionCode, "2.4", ours.sha256, ours.signer, ours.blob)
    private val otherKey = Sha256("e".repeat(64))
    private val quirky get() = profile(quirks = setOf(Quirk.HOME_TAKEOVER_SETUPWRAITH), setupWraith = wizard)

    // D3: the wizard is switched off only when the TV says its Wi-Fi was saved by someone else.
    // RF-SAFETY-250: while it may own the Wi-Fi it stays on, and the switch is refused, since with it
    // on and the TV's own home screen off the wizard takes Home (upstream #506); the clean-up alone
    // is planned and never touches the wizard or the stock home.
    @Test fun theWizardStaysOnWhileItMayOwnTheWifi() {
        val off = plan(setup(profile = quirky, choices = choices(wizardWifi = WizardWifi.NOT_OWNER)))
        assertTrue(Op.Disable(wizard) in ops(off))
        assertTrue(Op.Enable(wizard) in off.sections.single().rollback)
        for ((w, risk) in listOf(WizardWifi.OWNER to HomeSwitchRisk.Risk.WIZARD_WIFI, WizardWifi.UNKNOWN to HomeSwitchRisk.Risk.WIZARD_WIFI_UNKNOWN)) {
            assertEquals(listOf(risk.reason), refused(setup(profile = quirky, choices = choices(wizardWifi = w))), "$w")
            val clean = plan(setup(profile = quirky, choices = choices(installLauncher = false, takeover = false, wizardWifi = w)))
            assertTrue(clean.sections.isEmpty(), "$w")
            assertTrue(Op.Disable(wizard) !in ops(clean) && Op.Disable(stock.pkg) !in ops(clean), "$w")
        }
        // A Choices made without the answer is UNKNOWN: the wizard stays on, so no switch.
        val unknown = Choices(emptySet(), emptySet(), null, null, true, true, false, false)
        assertEquals(WizardWifi.UNKNOWN, unknown.wizardWifi)
        assertEquals(listOf(HomeSwitchRisk.Risk.WIZARD_WIFI_UNKNOWN.reason), refused(setup(profile = quirky, choices = unknown)))
    }

    // Launcher v2.4.0's precondition: the TV's own home screen is only touched once the new one
    // really started. The start CONTINUEs; the engine opens the section only when it is done
    // (RF-LAUNCHER-240 F3, SectionTest.aLauncherThatDoesNotStartSkipsTheSwitch).
    @Test fun theTakeoverStartsTheLauncherBeforeTheSwitch() {
        val p = plan(setup())
        val start = p.steps.single { it.op == Op.StartActivity(launcher, wait = true) && it.group == DefaultPlanner.GROUP_HOME }
        assertEquals(FailurePolicy.CONTINUE, start.onFailure)
        val section = p.sections.single()
        assertTrue(start.index < section.from, "before the watchdog is armed")
        assertTrue(p.steps.filter { it.op == Op.Disable(stock.pkg) }.all { it.index > start.index })
    }

    // Finding 4: with the signer known, our own older launcher is updated in place, another key's is
    // replaced (with consent), and a newer build of ours is never downgraded.
    @Test fun theInstalledSignerDecidesUpdateReplaceOrNothing() {
        val older = snapshot(extra = mapOf(installedLauncher(signer = ours.signer, versionCode = 2)))
        val update = plan(setup(choices = choices(takeover = false), artifact = newer()), snapshot = older)
        val install = ops(update).filterIsInstance<Op.InstallStream>().single()
        assertEquals(5L, install.versionCode)
        assertTrue(ops(update).none { it is Op.Uninstall })
        assertEquals(false, update.summary.replacesLauncher)

        val same = snapshot(extra = mapOf(installedLauncher(signer = ours.signer, versionCode = 5)))
        assertTrue(ops(plan(setup(choices = choices(takeover = false), artifact = newer()), snapshot = same)).none { it is Op.InstallStream || it is Op.Install })
        val newerOnTv = snapshot(extra = mapOf(installedLauncher(signer = ours.signer, versionCode = 7)))
        assertTrue(ops(plan(setup(choices = choices(takeover = false), artifact = newer()), snapshot = newerOnTv)).none { it is Op.InstallStream || it is Op.Install })

        val theirs = snapshot(extra = mapOf(installedLauncher(signer = otherKey, versionCode = 9)))
        assertTrue(refused(setup(artifact = newer()), snapshot = theirs).single().contains("different key"))
        val replaced = plan(setup(choices = choices(replaceSigner = true), artifact = newer()), snapshot = theirs)
        val uninstall = replaced.steps.single { it.op == Op.Uninstall(launcher.pkg) }
        assertEquals(DefaultPlanner.NARRATION_REPLACE_LAUNCHER, uninstall.narration)
        assertTrue("look" in uninstall.narration && "settings" in uninstall.narration && "card answers" in uninstall.narration)
        assertEquals(true, replaced.summary.replacesLauncher)
    }

    // D4: "Update the home screen".
    @Test fun updateTheHomeScreen() {
        val older = snapshot(home = launcher, claimants = listOf(launcher, stock), extra = mapOf(installedLauncher(signer = ours.signer, versionCode = 2)))
        val p = plan(PlanRequest.UpdateLauncher(newer()), snapshot = older)
        assertEquals(PlanKind.UPDATE_LAUNCHER, p.kind)
        assertEquals(listOf("InstallStream", "StartActivity"), ops(p).map { it::class.simpleName })
        assertEquals("Updating the home screen to version 2.4", p.steps[0].narration)
        assertEquals(true, p.steps[0].prior?.present, "an update: the undo never uninstalls it")
        assertEquals(Op.StartActivity(launcher, wait = true), p.steps[1].op)
        assertTrue(p.sections.isEmpty())
        // Nothing touches the look, the home screen setting or any package state.
        assertTrue(ops(p).none { it is Op.PushAsset || it is Op.RemoveFile || it is Op.SetHome || it is Op.Disable || it is Op.Uninstall })

        // Not the TV's home screen right now: installed, not started.
        val notHome = snapshot(extra = mapOf(installedLauncher(signer = ours.signer, versionCode = 2)))
        assertEquals(listOf("InstallStream"), ops(plan(PlanRequest.UpdateLauncher(newer()), snapshot = notHome)).map { it::class.simpleName })

        // No cmd on the TV: push, pm install -r, tidy.
        val facts = older.facts.copy(adbFeatures = setOf("shell_v2"))
        val pushed = plan(PlanRequest.UpdateLauncher(newer()), snapshot = older.copy(facts = facts))
        assertEquals(setOf(InstallFlag.REPLACE), ops(pushed).filterIsInstance<Op.Install>().single().flags)

        // Already this version or newer: nothing to do.
        val same = snapshot(extra = mapOf(installedLauncher(signer = ours.signer, versionCode = 5)))
        assertTrue(plan(PlanRequest.UpdateLauncher(newer()), snapshot = same).steps.isEmpty())
        val ahead = snapshot(extra = mapOf(installedLauncher(signer = ours.signer, versionCode = 6)))
        assertTrue(plan(PlanRequest.UpdateLauncher(newer()), snapshot = ahead).steps.isEmpty())

        // Not ours, unknown, or absent: refused with a reason.
        assertTrue(refused(PlanRequest.UpdateLauncher(newer()), snapshot = snapshot(extra = mapOf(installedLauncher(signer = otherKey, versionCode = 2)))).single().contains("different key"))
        val unknown = snapshot(extra = mapOf(launcher.pkg to installedLauncher(versionCode = 2).second.copy(signer = null)))
        assertTrue(refused(PlanRequest.UpdateLauncher(newer()), snapshot = unknown).single().contains("cannot be read"))
        assertTrue(refused(PlanRequest.UpdateLauncher(newer()), snapshot = snapshot()).single().contains("not on this TV"))
        val other = LauncherArtifact(stock.pkg, 5, "2.4", ours.sha256, ours.signer, ours.blob)
        assertTrue(refused(PlanRequest.UpdateLauncher(other), snapshot = older).isNotEmpty())
    }

    // A look push restarts Home; a TV whose launcher had an accessibility service on gets it back
    // (tools/theme.sh), as typed settings steps after the restart. Without one, nothing is added.
    @Test fun aRestartOfHomePutsTheLaunchersAccessibilityServicesBack() {
        val list = "com.gathin.tvhome/com.gathin.tvhome.InputKeyService:com.example.reader/.Reader"
        val base = snapshot(home = launcher, claimants = listOf(launcher, stock), extra = mapOf(installedLauncher()))
        val with = base.withSettings(base.settings + mapOf(SettingKey.ENABLED_ACCESSIBILITY to list, SettingKey.ACCESSIBILITY_ENABLED to "1"))
        val config = BlobRef(Sha256("c".repeat(64)), 100, "blob:c")
        val transport = OverlayTransport()
        val p = plan(PlanRequest.ApplyTheme(tv.cleanyour.core.spec.LauncherConfigDoc(), config, null), snapshot = with, planner = planner(transport))
        val restart = p.steps.indexOfFirst { it.op is Op.RestartHome }
        assertEquals(
            listOf(Op.Settle(1), Op.SettingsPut(SettingKey.ENABLED_ACCESSIBILITY, null), Op.SettingsPut(SettingKey.ACCESSIBILITY_ENABLED, "0"),
                Op.Settle(1), Op.SettingsPut(SettingKey.ENABLED_ACCESSIBILITY, list), Op.SettingsPut(SettingKey.ACCESSIBILITY_ENABLED, "1")),
            ops(p).drop(restart + 1),
        )
        assertTrue(p.steps.drop(restart + 1).all { it.onFailure == FailurePolicy.CONTINUE && it.group == DefaultPlanner.GROUP_THEME })
        assertEquals(list, p.steps[restart + 2].prior?.settingValue)
        // Launcher 2.5's step (upstream #424): accessibility goes off with the list cleared, and on again
        // after the list is back; the off step undoes to on.
        assertEquals("1", p.steps[restart + 3].prior?.settingValue)
        assertEquals(list, p.steps[restart + 5].prior?.settingValue)
        // Accessibility off on the TV (switched off, or a vendor build): nothing is switched on, so no
        // service in the list, ours or anyone's, is bound by the app (RF-LAUNCHER-240 F7).
        for (off in listOf("0", null)) {
            val settings = base.settings + mapOf(SettingKey.ENABLED_ACCESSIBILITY to list) + (if (off != null) mapOf(SettingKey.ACCESSIBILITY_ENABLED to off) else emptyMap())
            val r = plan(PlanRequest.ApplyTheme(tv.cleanyour.core.spec.LauncherConfigDoc(), config, null), snapshot = base.withSettings(settings), planner = planner(transport))
            assertTrue(ops(r).last() is Op.RestartHome, "accessibility_enabled $off")
            assertTrue(ops(r).none { it is Op.SettingsPut })
        }
                // Someone else's service only: the restart does not touch it.
        val others = base.withSettings(base.settings + mapOf(SettingKey.ENABLED_ACCESSIBILITY to "com.example.reader/.Reader"))
        val q = plan(PlanRequest.ApplyTheme(tv.cleanyour.core.spec.LauncherConfigDoc(), config, null), snapshot = others, planner = planner(transport))
        assertTrue(ops(q).last() is Op.RestartHome)
        assertTrue(ops(plan(PlanRequest.ApplyTheme(tv.cleanyour.core.spec.LauncherConfigDoc(), config, null), snapshot = base, planner = planner(transport))).last() is Op.RestartHome)
    }
}
