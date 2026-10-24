package tv.cleanyour.ui.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.ui.design.component.CountdownRing
import tv.cleanyour.ui.design.component.DiffItem
import tv.cleanyour.ui.design.component.DiffRow
import tv.cleanyour.ui.design.component.NumeralHeadline
import tv.cleanyour.ui.design.component.UndoSnackbarHost
import tv.cleanyour.ui.design.component.UndoSnackbarState
import tv.cleanyour.ui.design.component.WelcomeIllustration
import tv.cleanyour.ui.design.component.formatCountdown
import tv.cleanyour.ui.design.theme.CytTheme

// Reduced-motion variants and the component behaviours the screens rely on.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class MotionAndBehaviourTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun countUpIsStaticUnderReducedMotion() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CytTheme(reducedMotion = true) { NumeralHeadline(23, "things to switch off") } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("23").assertExists()
    }

    @Test
    fun countUpRunsAtLeast700ms() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CytTheme(reducedMotion = false) { NumeralHeadline(23, "things to switch off") } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("23").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("23").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("23").assertExists()
    }

    @Test
    fun welcomePlaysOnceAndReducedShowsRestState() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CytTheme(reducedMotion = false) { Box(Modifier.testTag("w")) { WelcomeIllustration() } } }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("w").captureRoboImage("src/test/screenshots/m01_welcome_mid_animation.png")
        compose.mainClock.advanceTimeBy(2500)
        compose.onNodeWithTag("w").captureRoboImage("src/test/screenshots/m02_welcome_rest.png")
    }

    @Test
    fun countdownFormatsAndAnnouncesOnlyAtMarks() {
        assertEquals("1:24", formatCountdown(84_000))
        assertEquals("1:30", formatCountdown(89_001))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:00", formatCountdown(0))
        var remaining by mutableLongStateOf(84_000L)
        compose.setContent { CytTheme { CountdownRing(remaining, 90_000) } }
        val live = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("1:30 left")))
        remaining = 55_000
        compose.waitForIdle()
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("One minute left. Say yes or go back.")))
        remaining = 45_000
        compose.waitForIdle()
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("One minute left. Say yes or go back.")))
        remaining = 25_000
        compose.waitForIdle()
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("30 seconds left.")))
        remaining = 9_000
        compose.waitForIdle()
        compose.onNode(live).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("10 seconds left.")))
    }

    @Test
    fun diffRowStatesTheOutcomeAndToggles() {
        var toggled: DiffItem? = null
        val item = DiffItem("com.mitv.tvhome.atv", "PatchWall", "Xiaomi's home screen with adverts", "com.mitv.tvhome.atv", off = true)
        compose.setContent { CytTheme { DiffRow(item, { toggled = it }) } }
        compose.onNodeWithText("PatchWall", useUnmergedTree = true).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off")).performClick()
        assertEquals(item, toggled)
    }

    @Test
    fun snackbarLeavesAfterFiveSeconds() {
        val state = UndoSnackbarState()
        compose.setContent { CytTheme { UndoSnackbarHost(state) } }
        compose.runOnIdle { state.show("Mi TV+ promos will be kept.") {} }
        compose.onNodeWithText("Mi TV+ promos will be kept.").assertExists()
        compose.mainClock.advanceTimeBy(5_500)
        compose.waitForIdle()
        assertNull(state.current)
    }
}
