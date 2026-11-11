package tv.cleanyour.core.planner

import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.ops.UndoOrder
import tv.cleanyour.core.planner.PlannerFixtures.change
import tv.cleanyour.core.planner.PlannerFixtures.plan
import tv.cleanyour.core.planner.PlannerFixtures.snapshot
import tv.cleanyour.core.planner.PlannerFixtures.stock
import tv.cleanyour.core.planner.PlannerFixtures.wizard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Mi5G 2.3 (upstream Gathin23/cleanyourtv #3578, PR #3653): the setup wizard never comes back on while
// the TV's own home screen is off; launcherx (or tvlauncher) first, then setupwraith.
class WizardAfterStockHomeTest {
    private fun ops(p: Plan) = p.steps.map { it.op }

    @Test fun undoOrderPutsTheWizardAfterEveryOtherPackage() {
        // The takeover switched the stock home off first and the wizard last, so "most recent first"
        // would have put the wizard back first.
        val changes = listOf(
            change(1, Op.Disable(stock.pkg), Prior(packageState = EnabledState.ENABLED)),
            change(2, Op.Disable(wizard), Prior(packageState = EnabledState.ENABLED)),
        )
        val order = UndoOrder.undoOrder(changes).map { (it.op as Op.Disable).pkg }
        assertEquals(listOf(stock.pkg, wizard), order)
    }

    @Test fun undoEverythingEnablesTheStockHomeBeforeTheWizard() {
        val changes = listOf(
            change(1, Op.Disable(stock.pkg), Prior(packageState = EnabledState.ENABLED)),
            change(2, Op.Disable(wizard), Prior(packageState = EnabledState.ENABLED)),
        )
        val now = snapshot(disabled = setOf(stock.pkg.value, wizard.value))
        val enables = ops(plan(PlanRequest.UndoEverything(changes, removeLauncher = false), now))
            .filter { it is Op.Enable || it is Op.DefaultState }
            .map { (it as? Op.Enable)?.pkg ?: (it as Op.DefaultState).pkg }
        assertTrue(stock.pkg in enables && wizard in enables, enables.toString())
        assertTrue(enables.indexOf(stock.pkg) < enables.indexOf(wizard), enables.toString())
    }

    @Test fun undoLeavesTheWizardOffWhenAStockHomeStaysOff() {
        // Only the wizard was switched off by this app; the stock home was off before it.
        val changes = listOf(change(1, Op.Disable(wizard), Prior(packageState = EnabledState.ENABLED)))
        val now = snapshot(disabled = setOf(stock.pkg.value, wizard.value))
        val p = plan(PlanRequest.UndoEverything(changes, removeLauncher = false), now)
        assertTrue(ops(p).none { it == Op.Enable(wizard) || it == Op.DefaultState(wizard) }, ops(p).toString())
    }

    @Test fun beforeFactoryResetBringsTheStockHomeBackFirst() {
        val journals = listOf(change(1, Op.Disable(wizard), Prior(packageState = EnabledState.ENABLED)))
        val now = snapshot(disabled = setOf(stock.pkg.value, wizard.value), home = null, claimants = emptyList())
        val enables = ops(plan(PlanRequest.BeforeFactoryReset, now, journals)).filterIsInstance<Op.Enable>().map { it.pkg }
        assertEquals(listOf(stock.pkg, wizard), enables)
    }
}
