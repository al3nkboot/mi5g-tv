package tv.cleanyour.feature.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.flows.ChipState
import tv.cleanyour.core.flows.SetupActions
import tv.cleanyour.core.flows.SetupExtras
import tv.cleanyour.core.flows.SetupIntent
import tv.cleanyour.core.flows.SetupState
import tv.cleanyour.core.flows.StopAction
import tv.cleanyour.core.flows.StopCard
import tv.cleanyour.core.flows.StopCase
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.ui.design.theme.CytTheme

// Stop and undo ask first (owner, device test): the Working screen's "Stop and undo" and a stop
// card's "Stop here" and "Undo what we did" send nothing to the machine until the question is
// answered yes; Carry on acts at once.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class StopAsksFirstTest {
    @get:Rule val compose = createComposeRule()

    private val sent = mutableListOf<SetupIntent>()
    private val actions = object : SetupActions {
        override fun intent(intent: SetupIntent) { sent += intent }
        override fun back(): Boolean = false
        override fun dismissCard() {}
    }
    private val extras = SetupExtras(tvName = "Mi TV 4A", chip = ChipState.WORKING)

    @Test fun stopAndUndoAsksBeforeTheRunStops() {
        val run = RunState.Running(Samples.plan(emptyList()), step = 1, narration = emptyList(), percent = 40, thumbnail = null)
        compose.setContent { CytTheme(reducedMotion = true) { WorkingScreen(SetupState.Working(Samples.miTvId(), run, null), extras, actions) } }
        compose.onNodeWithText("Stop and undo").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<SetupIntent>(), sent)
        compose.onNodeWithText("Stop now?").assertExists()
        compose.onNodeWithText("Keep going").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<SetupIntent>(), sent)
        compose.onNodeWithText("Stop and undo").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Stop and undo")[1].performClick()
        compose.waitForIdle()
        assertEquals(listOf<SetupIntent>(SetupIntent.StopAndUndo), sent)
    }

    @Test fun aStopCardAsksBeforeUndoAndStopHereButCarryOnActsAtOnce() {
        val card = StopCard(StopCase.CONNECTION_LOST, "The TV stopped answering", "Usually it's back in a few seconds.", "Carry on with the rest",
            "Stop here", planId = null, tertiary = "Undo what we did")
        compose.setContent { CytTheme(reducedMotion = true) { StoppedScreen(SetupState.Stopped(Samples.miTvId(), card), extras, actions) } }
        compose.onNodeWithText("Undo what we did").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<SetupIntent>(), sent)
        compose.onNodeWithText("Undo the changes on the TV?").assertExists()
        compose.onNodeWithText("Undo the changes").performClick()
        compose.waitForIdle()
        assertEquals(listOf<SetupIntent>(SetupIntent.StopAction(StopAction.TERTIARY)), sent)
        sent.clear()
        compose.onNodeWithText("Carry on with the rest").performClick()
        compose.waitForIdle()
        assertEquals(listOf<SetupIntent>(SetupIntent.StopAction(StopAction.PRIMARY)), sent)
    }
}
