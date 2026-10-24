package tv.cleanyour.core.planner

import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.GroupDefault
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageEntry
import tv.cleanyour.core.model.PackageGroup
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.planner.PlannerFixtures.choices
import tv.cleanyour.core.planner.PlannerFixtures.plan
import tv.cleanyour.core.planner.PlannerFixtures.profile
import tv.cleanyour.core.planner.PlannerFixtures.setup
import tv.cleanyour.core.planner.PlannerFixtures.snapshot
import tv.cleanyour.core.spec.ProfileResolution
import tv.cleanyour.testing.fixtures.FakeConfigTransport
import tv.cleanyour.testing.fixtures.FakeIdGen
import tv.cleanyour.testing.fixtures.FakeProfileSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// STORES-PLAN 1, keepUpdatesOn (the Google Play build): the "updates go off" choice does not exist.
// Every software updater, by its name (D22) or by the profile's "updates" group, stays on in every
// plan; without the flag (the GitHub and F-Droid build) updates stay the person's choice.
class KeepUpdatesOnTest {
    private val ota = PackageName("com.mitv.ota")          // an updater by its name (D22)
    private val helper = PackageName("com.mitv.helper")    // an updater only by its profile group
    private val fota = PackageName("com.vendor.fota")      // an updater by name, in no group
    private val on = PackageState(EnabledState.ENABLED, false, 1, null, false, false, null, false)
    private val updates = PackageGroup("updates", "Software updates", GroupDefault.ASK, listOf(PackageEntry(ota, null), PackageEntry(helper, null)))
    private val prof = profile(extraGroups = listOf(updates))
    private val snap = snapshot(extra = mapOf(ota to on, helper to on, fota to on))
    private val source = FakeProfileSource(listOf(prof)).also { it.resolver = { _, all -> ProfileResolution.Exact(all.single()) } }

    private fun planner(keep: Boolean) = DefaultPlanner(PlannerFixtures.clock, FakeIdGen(), ShellRenderer(), FakeConfigTransport(), "0.3.0",
        profiles = source, keepUpdatesOn = keep)

    private fun disabled(p: Plan) = p.steps.map { it.op }.filterIsInstance<Op.Disable>().map { it.pkg }.toSet()

    @Test fun theFullBuildLetsTheUpdatesGoOffWhenThePersonSaysSo() {
        val p = plan(setup(profile = prof, choices = choices(extraOff = setOf(ota, helper, fota))), snap, planner = planner(false))
        val rows = p.summary.groups.first { it.id == "updates" }.rows
        assertTrue(rows.all { it.off && !it.protected }, rows.toString())
        assertTrue(disabled(p).containsAll(setOf(ota, helper, fota)))
        // Simple keeps them: the group asks.
        val simple = plan(setup(profile = prof), snap, planner = planner(false))
        assertTrue(simple.summary.groups.first { it.id == "updates" }.rows.none { it.off || it.protected })
    }

    @Test fun thePlayBuildKeepsEveryUpdaterOnAndSaysWhy() {
        // Even an earlier full build's draft that named them off.
        val p = plan(setup(profile = prof, choices = choices(extraOff = setOf(ota, helper, fota))), snap, planner = planner(true))
        val rows = p.summary.groups.first { it.id == "updates" }.rows
        assertEquals(setOf(ota, helper), rows.map { it.pkg }.toSet())
        assertTrue(rows.all { !it.off && it.protected && it.whyProtected == DefaultPlanner.REASON_UPDATES }, rows.toString())
        assertTrue(disabled(p).none { it in setOf(ota, helper, fota) }, disabled(p).toString())
        // An updater outside the groups is left alone quietly: no refusal, no "Also switched off" row.
        assertTrue(p.summary.groups.none { g -> g.rows.any { it.pkg == fota } })
        // The rest of the clean-up is unchanged.
        val full = plan(setup(profile = prof), snap, planner = planner(false))
        assertEquals(disabled(full), disabled(p))
    }

    @Test fun thePlayBuildDoesNotListAnUpdaterThatIsAlreadyOffAsKept() {
        // RF-STORES-030 S1: an earlier full build switched ota off. The Play build's Setup neither
        // switches it back on nor lists it under "We keep these" (it is not kept, it is off);
        // Manage's Switched off lists it with Put back. helper, still on, is kept and says why.
        val off = on.copy(enabled = EnabledState.DISABLED_USER)
        val asleep = snapshot(disabled = setOf(ota.value), extra = mapOf(ota to off, helper to on, fota to on))
        val p = plan(setup(profile = prof, choices = choices(extraOff = setOf(ota, helper))), asleep, planner = planner(true))
        val rows = p.summary.groups.first { it.id == "updates" }.rows
        assertEquals(listOf(helper), rows.map { it.pkg })
        assertTrue(rows.single().protected && rows.single().whyProtected == DefaultPlanner.REASON_UPDATES)
        assertTrue(p.steps.none { DefaultPlanner.packageOf(it.op) == ota }, p.steps.toString())
        assertTrue(p.summary.groups.none { g -> g.rows.any { it.pkg == ota } })
    }

    @Test fun thePlayBuildRefusesToSwitchAnUpdaterOffAndLeavesOneThatCameBack() {
        val refused = planner(true).plan(PlanRequest.SwitchOff(setOf(ota)), snap, PlannerFixtures.facts, emptyList())
        assertTrue(refused is PlanResult.Refused && refused.reasons.single().contains(DefaultPlanner.REASON_UPDATES), refused.toString())
        // Leftovers: an updater an earlier full build switched off came back on; it stays on, the
        // other leftovers are still switched off.
        val other = PackageName("com.netflix.ninja")
        val left = plan(PlanRequest.Leftovers(setOf(ota, helper, other)), snap, planner = planner(true))
        assertEquals(setOf(other), disabled(left))
        // The full build switches them off again, as before.
        val fullLeft = plan(PlanRequest.Leftovers(setOf(ota, helper, other)), snap, planner = planner(false))
        assertEquals(setOf(ota, helper, other), disabled(fullLeft))
        // Putting an updater back on is always allowed.
        val back = plan(PlanRequest.PutBack(ota), snapshot(disabled = setOf(ota.value), extra = mapOf(ota to on.copy(enabled = EnabledState.DISABLED_USER))), planner = planner(true))
        assertEquals(listOf<Op>(Op.Enable(ota)), back.steps.map { it.op })
    }
}
