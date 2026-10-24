package tv.cleanyour.feature.manage

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.engine.PauseReason
import tv.cleanyour.core.flows.AppConfirm
import tv.cleanyour.core.flows.AppSettingsIntent
import tv.cleanyour.core.flows.LeftoversIntent
import tv.cleanyour.core.flows.LeftoversState
import tv.cleanyour.core.flows.OpenWrite
import tv.cleanyour.core.flows.OpenWriteAction
import tv.cleanyour.core.flows.TvSettingsIntent
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.planner.PlanKind
import tv.cleanyour.testing.fixtures.Samples

// R5-W8 O1 as ruled: on these screens a tap through another app's overlay never confirms, applies
// or forgets (it shows the :ui:design hint instead), and never blocks Cancel, Stop here or
// navigation.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1600dp-xhdpi")
class GuardedActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun open(kind: PlanKind) = OpenWrite(Samples.miTvId(), "Mi TV 4A", PlanId("01JAZ0000000000000000000"), kind, PauseReason.CONNECTION_LOST)

    @Test fun theConfirmationsDropObscuredTapsAndCancelStillWorks() {
        val hits = mutableListOf<Any>()
        compose.showColumn {
            ForgetContent("Mi TV 4A") { hits += it }
            ConfirmContent(AppConfirm.DELETE_EVERYTHING, working = false) { hits += it }
        }
        compose.tap("Forget this TV", OBSCURED)
        compose.tap("Delete everything", OBSCURED)
        assertEquals(emptyList<Any>(), hits)
        compose.tap("Forget this TV", 0)
        compose.tap("Delete everything", 0)
        assertEquals(listOf<Any>(TvSettingsIntent.Forget, AppSettingsIntent.DeleteEverything), hits)
    }

    @Test fun cancelIsNeverGuarded() {
        val hits = mutableListOf<Any>()
        compose.showColumn { ConfirmContent(AppConfirm.RESET_KEY, working = false) { hits += it } }
        compose.tap("Cancel", OBSCURED)
        assertEquals(listOf<Any>(AppSettingsIntent.CancelConfirm), hits)
    }

    @Test fun carryOnIsGuardedAndStopHereIsNot() {
        val hits = mutableListOf<OpenWriteAction>()
        compose.showColumn { OpenWriteContent(open(PlanKind.SWITCH_OFF), { hits += it }, {}, {}) }
        compose.tap("Carry on", OBSCURED)
        compose.tap("Stop here", OBSCURED)
        // Stop here asks first: nothing is sent until the question is answered.
        assertEquals(emptyList<OpenWriteAction>(), hits)
        compose.onNodeWithText("Stop here?").assertExists()
        compose.tap("Stop here", OBSCURED)
        assertEquals(emptyList<OpenWriteAction>(), hits)
        compose.tap("Stop here", 0)
        assertEquals(listOf(OpenWriteAction.STOP_HERE), hits)
    }

    // Undo asks first; Cancel is never guarded and sends nothing.
    @Test fun undoWhatWeDidAsksFirstAndCancelSendsNothing() {
        val hits = mutableListOf<OpenWriteAction>()
        compose.showColumn { OpenWriteContent(open(PlanKind.SWITCH_OFF), { hits += it }, {}, {}) }
        compose.tap("Undo what we did", 0)
        compose.onNodeWithText("Undo the changes on the TV?").assertExists()
        compose.tap("Cancel", OBSCURED)
        assertEquals(emptyList<OpenWriteAction>(), hits)
        compose.tap("Undo what we did", 0)
        compose.tap("Undo the changes", 0)
        assertEquals(listOf(OpenWriteAction.UNDO), hits)
    }

    @Test fun openingTheSetupIsNavigationAndNotGuarded() {
        var opened = 0
        compose.showColumn { OpenWriteContent(open(PlanKind.SETUP), {}, { opened++ }, {}) }
        compose.tap("Open the setup", OBSCURED)
        assertEquals(1, opened)
    }

    @Test fun switchingLeftoversOffIsGuarded() {
        val hits = mutableListOf<LeftoversIntent>()
        compose.setContent {
            tv.cleanyour.ui.design.theme.CytTheme(reducedMotion = true) {
                LeftoversScreen(LeftoversState.Found(listOf("PatchWall"), Samples.plan(emptyList())), { hits += it }, {})
            }
        }
        compose.waitForIdle()
        compose.tap("Switch off", OBSCURED)
        compose.tap("Not now", OBSCURED)
        assertEquals(listOf<LeftoversIntent>(LeftoversIntent.NotNow), hits)
    }
}
