package tv.cleanyour.feature.modules

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.InstallStep
import tv.cleanyour.core.flows.ModuleRow
import tv.cleanyour.core.flows.ModulesIntent
import tv.cleanyour.core.flows.ModulesState
import tv.cleanyour.core.flows.UpdateConsent
import tv.cleanyour.core.model.PermissionStatement
import tv.cleanyour.core.model.Tier
import tv.cleanyour.ui.design.theme.CytTheme

// R5-W8 O1 as ruled: removing a module and allowing an update drop a tap through another app's
// overlay; Cancel, Skip, Add (it opens the list) and Done (it leaves the screen) never do.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1600dp-xhdpi")
class GuardedActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun removeIsGuardedAndCancelIsNot() {
        val hits = mutableListOf<String>()
        compose.showColumn { RemoveContent("Retro", "Mi TV 4A", { hits += "remove" }, { hits += "cancel" }) }
        compose.tap("Remove", OBSCURED)
        compose.tap("Cancel", OBSCURED)
        assertEquals(listOf("cancel"), hits)
    }

    @Test fun allowingAnUpdateIsGuardedAndSkippingIsNot() {
        val hits = mutableListOf<ModulesIntent>()
        val consent = UpdateConsent("stremio", "Stremio shelf", "0.2.0", listOf(PermissionStatement("android.permission.RECORD_AUDIO", "Listen")))
        compose.showColumn { ConsentContent(consent) { hits += it } }
        compose.tap("Update", OBSCURED)
        compose.tap("Skip this version", OBSCURED)
        assertEquals(listOf<ModulesIntent>(ModulesIntent.SkipUpdate), hits)
    }

    // RFC-CARDS-1: "Show its card" sends the look to the TV, so it drops a tap through an overlay too.
    @Test fun showItsCardIsGuarded() {
        val hits = mutableListOf<ModulesIntent>()
        val retro = ModuleRow("retro", "Retro", "NES games", 1L, "MIT", Tier.COMMUNITY, null, installed = true, card = null, updateTo = null)
        compose.setContent {
            CytTheme(reducedMotion = true) { InstalledScreen(ModulesState(tvName = "Mi TV 4A", installed = listOf(retro), cardsOff = true), { hits += it }, {}, {}) }
        }
        compose.waitForIdle()
        compose.tap("Show its card", OBSCURED)
        assertEquals(emptyList<ModulesIntent>(), hits)
        compose.tap("Show its card", 0)
        assertEquals(listOf<ModulesIntent>(ModulesIntent.ShowCard("retro")), hits)
    }

    @Test fun addAndDoneAreNavigationAndNotGuarded() {
        var adds = 0
        val hits = mutableListOf<ModulesIntent>()
        compose.setContent {
            CytTheme(reducedMotion = true) {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { InstalledScreen(ModulesState(tvName = "Mi TV 4A"), {}, { adds++ }, {}) }
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { InstallScreen(InstallStep.Shown("Retro"), ModulesState(tvName = "Mi TV 4A"), { hits += it }) }
                }
            }
        }
        compose.waitForIdle()
        compose.tap("Add", OBSCURED)
        compose.tap("Done", OBSCURED)
        assertEquals(1, adds)
        assertEquals(listOf<ModulesIntent>(ModulesIntent.DismissInstall), hits)
    }
}
