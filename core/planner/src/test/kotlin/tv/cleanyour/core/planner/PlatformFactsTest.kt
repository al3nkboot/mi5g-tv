package tv.cleanyour.core.planner

import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.HomeResolution
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Platform
import tv.cleanyour.core.planner.PlannerFixtures.snapshot
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Issue #2 (A1, A2): the firmware that takes no other home screen, told from the TV's own facts.
class PlatformFactsTest {
    private val tv = snapshot()   // the reference Xiaomi: none of it
    private val facts = tv.facts

    @Test fun fireOsFromTheMakerTheModelOrItsHomeScreens() {
        assertFalse(FireOs.isFireOs(facts, tv))
        assertFalse(FireOs.isFireOs(facts, null))
        // The issue's set: an Insignia panel, Amazon as the maker, an AFT model code.
        assertTrue(FireOs.isFireOs(facts.copy(brand = "Insignia", manufacturer = "Amazon", model = "AFTDCT31"), null))
        assertTrue(FireOs.isFireOs(facts.copy(brand = "amazon", manufacturer = "Xiaomi"), null))
        assertTrue(FireOs.isFireOs(facts.copy(model = "aftkm"), null), "the model code, in any case")
        assertFalse(FireOs.isFireOs(facts.copy(model = "MiTV-AFT"), null), "only as a prefix")
        // Fire OS's home screens on the TV, installed or answering Home.
        val starter = PackageName("com.amazon.firehomestarter")
        assertTrue(FireOs.isFireOs(facts, tv.copy(packages = tv.packages + (starter to tv.packages.values.first()))))
        val amazon = Component(PackageName("com.amazon.tv.launcher"), ".ui.HomeActivity_vNext")
        assertTrue(FireOs.isFireOs(facts, tv.copy(home = HomeResolution(null, listOf(HomeClaimant(amazon, 950))))))
        assertTrue(FireOs.isHome(amazon.pkg) && FireOs.isHome(starter))
        assertFalse(FireOs.isHome(PlannerFixtures.stock.pkg))
    }

    @Test fun chinaMarketTclHasNoGoogleServices() {
        assertTrue(TclChina.isTclChina(facts.copy(brand = "TCL", manufacturer = "TCL", hasGms = false)))
        assertTrue(TclChina.isTclChina(facts.copy(brand = "FFalcon", manufacturer = "TCL", hasGms = false)))
        assertFalse(TclChina.isTclChina(facts.copy(brand = "TCL", manufacturer = "TCL", hasGms = true)), "an international TCL")
        assertFalse(TclChina.isTclChina(facts.copy(hasGms = false)), "another maker without Google services")
    }

    // S4: the rule stands in for a missing profile only. An exact TCL Android TV or Google TV profile
    // keeps its own behaviour; an exact TCL_CN profile, or none, gets the rule.
    @Test fun theChinaTclRuleAppliesWithoutAnExactProfileOrWithATclCnOne() {
        val cn = facts.copy(brand = "TCL", manufacturer = "TCL", hasGms = false)
        val p = PlannerFixtures.profile()
        assertTrue(TclChina.applies(cn, null))
        assertTrue(TclChina.applies(cn, p.copy(platform = Platform.TCL_CN)))
        assertFalse(TclChina.applies(cn, p.copy(platform = Platform.ANDROID_TV)))
        assertFalse(TclChina.applies(cn, p.copy(platform = Platform.GOOGLE_TV)))
        assertFalse(TclChina.applies(facts, null), "not a China-market TCL")
    }
}
