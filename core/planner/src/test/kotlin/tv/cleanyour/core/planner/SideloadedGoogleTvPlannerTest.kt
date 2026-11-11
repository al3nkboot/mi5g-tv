package tv.cleanyour.core.planner

import org.junit.Test
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.HomeResolution
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.spec.KnownLaunchers
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 2.3 (cleanyourtv-app 0.5.0, #5539): Google TV's home screen the person put on an Android TV set is
// theirs: never the stock home to hand Home back to, and protected as their launcher.
class SideloadedGoogleTvPlannerTest {
    private val tvl = KnownLaunchers.TVLAUNCHER
    private val lx = KnownLaunchers.LAUNCHERX
    private fun st(system: Boolean) = PackageState(EnabledState.DEFAULT, system, 1000, null, false, false, null, false)

    @Test fun theTvsOwnTvlauncherIsTheStockHomeNotTheSideloadedLauncherx() {
        // launcherx listed first and resolving Home would win without the rule.
        val snap = Samples.snapshot(packages = mapOf(tvl.pkg to st(true), lx.pkg to st(false)),
            home = HomeResolution(tvl, listOf(HomeClaimant(lx, 0), HomeClaimant(tvl, 2))))
        assertEquals(tvl, StockHome.of(snap, KnownLaunchers.generic))
        assertTrue(SwitchOffRule.isPersonsLauncher(lx.pkg, snap))
        assertFalse(SwitchOffRule.isPersonsLauncher(tvl.pkg, snap))
    }

    @Test fun aRealGoogleTvsLauncherxStaysTheStockHome() {
        val snap = Samples.snapshot(packages = mapOf(lx.pkg to st(true)), home = HomeResolution(lx, listOf(HomeClaimant(lx, 2))))
        assertEquals(lx, StockHome.of(snap, KnownLaunchers.generic))
        assertFalse(SwitchOffRule.isPersonsLauncher(lx.pkg, snap))
    }
}
