package tv.cleanyour.feature.backup

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.RestorePhase
import tv.cleanyour.core.flows.RestorePointIntent
import tv.cleanyour.core.flows.RestorePointState
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.ui.design.theme.CytTheme

// R5-W8 O1 as ruled: "Put everything back" and "Switch the wizard back on" drop a tap through
// another app's overlay; Cancel and the way back into the wizard never do.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1600dp-xhdpi")
class GuardedActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun putEverythingBackIsGuardedAndCancelIsNot() {
        val hits = mutableListOf<RestorePointIntent>()
        compose.showColumn { ConfirmContent { hits += it } }
        compose.tap("Put everything back", OBSCURED)
        compose.tap("Cancel", OBSCURED)
        assertEquals(listOf<RestorePointIntent>(RestorePointIntent.Cancel), hits)
        compose.tap("Put everything back", 0)
        assertEquals(listOf(RestorePointIntent.Cancel, RestorePointIntent.PutEverythingBack), hits)
    }

    @Test fun theFactoryResetSwitchIsGuarded() {
        val hits = mutableListOf<String>()
        compose.showColumn { FactoryContent({ hits += "confirm" }, { hits += "cancel" }) }
        compose.tap("Switch the wizard back on", OBSCURED)
        compose.tap("Cancel", OBSCURED)
        assertEquals(listOf("cancel"), hits)
    }

    @Test fun cleaningAgainIsNavigationAndNotGuarded() {
        val hits = mutableListOf<RestorePointIntent>()
        val state = RestorePointState("Mi TV 4A", Samples.NOW_MS, Samples.NOW_MS, phase = RestorePhase.Finished("undo", Samples.NOW_MS))
        compose.setContent { CytTheme(reducedMotion = true) { RestoreScreen(state, { hits += it }, {}) } }
        compose.waitForIdle()
        compose.tap("Clean it up again", OBSCURED)
        assertEquals(listOf<RestorePointIntent>(RestorePointIntent.CleanAgain), hits)
    }
}
