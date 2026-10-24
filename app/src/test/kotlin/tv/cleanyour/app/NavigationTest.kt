package tv.cleanyour.app

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The emulator run: Back from "I've set up a TV before" left the app instead of returning to Welcome.
class NavigationTest {
    @Test fun backFromIveSetUpATvBeforeReturnsToWelcome() {
        val stack = mutableListOf<NavKey>(Dest.Setup())
        assertTrue(applyRoute(stack, "returning", noTvs = true))
        assertEquals(listOf<NavKey>(Dest.Setup(), Dest.Manage(null)), stack)
        assertTrue(applyRoute(stack, "back", noTvs = true))   // system Back pops the same way
        assertEquals(listOf<NavKey>(Dest.Setup()), stack)
        assertFalse(applyRoute(stack, "back", noTvs = true))  // Back on Welcome leaves the app
    }

    @Test fun manageAfterTheWizardStillReplacesTheStack() {
        val stack = mutableListOf<NavKey>(Dest.Setup(), Dest.Setup("0123456789abcdef"))
        applyRoute(stack, "manage/0123456789abcdef", noTvs = false)
        assertEquals(listOf<NavKey>(Dest.Manage("0123456789abcdef")), stack)
        applyRoute(stack, "modules/0123456789abcdef", noTvs = false, addOns = true)
        assertEquals(Dest.Modules("0123456789abcdef"), stack.last())
        applyRoute(stack, "welcome", noTvs = true)
        assertEquals(listOf<NavKey>(Dest.Setup()), stack)
    }

    // STORES-PLAN 1: without add-ons (the Play build) the modules route opens nothing.
    @Test fun theModulesRouteNeedsAddOns() {
        val stack = mutableListOf<NavKey>(Dest.Manage("0123456789abcdef"))
        assertTrue(applyRoute(stack, "modules/0123456789abcdef", noTvs = false, addOns = false))
        assertEquals(listOf<NavKey>(Dest.Manage("0123456789abcdef")), stack)
        // The default is this build's own rule.
        applyRoute(stack, "modules/0123456789abcdef", noTvs = false)
        assertEquals(if (Store.rules.addOns) 2 else 1, stack.size)
    }

    // 0.3.2 B1, B2: the report preview opens on top and Back returns to the screen that asked.
    @Test fun theReportRoutesOpenThePreviewOnTop() {
        val stack = mutableListOf<NavKey>(Dest.Manage("0123456789abcdef"))
        assertTrue(applyRoute(stack, "report/tv/0123456789abcdef", noTvs = false))
        assertEquals(Dest.Report("tv", "0123456789abcdef"), stack.last())
        applyRoute(stack, "back", noTvs = false)
        applyRoute(stack, "report/problem/0123456789abcdef", noTvs = false)
        assertEquals(Dest.Report("problem", "0123456789abcdef"), stack.last())
        applyRoute(stack, "back", noTvs = false)
        applyRoute(stack, "report/problem", noTvs = false)   // Settings: no TV named
        assertEquals(Dest.Report("problem", null), stack.last())
        applyRoute(stack, "back", noTvs = false)
        // A TV report needs its TV, and an unknown kind opens nothing.
        applyRoute(stack, "report/tv", noTvs = false)
        applyRoute(stack, "report/zip/0123456789abcdef", noTvs = false)
        assertEquals(listOf<NavKey>(Dest.Manage("0123456789abcdef")), stack)
    }

    // What a report says the phone is: the maker once, then the model.
    @Test fun thePhoneNameNamesTheMakerOnce() {
        assertEquals("Samsung SM-S921B", AppGraph.phoneName("samsung", "SM-S921B"))
        assertEquals("Google Pixel 7", AppGraph.phoneName("Google", "Pixel 7"))
        assertEquals("Google Pixel 7", AppGraph.phoneName("google", "Google Pixel 7"))   // not "Google Google Pixel 7"
        assertEquals("SM-S921B", AppGraph.phoneName("", "SM-S921B"))
    }
}
