package tv.cleanyour.feature.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.NoSetupActions
import tv.cleanyour.core.flows.SetupActions
import tv.cleanyour.core.flows.SetupExtras
import tv.cleanyour.core.flows.SetupIntent
import tv.cleanyour.core.flows.SetupState
import tv.cleanyour.core.flows.Sheet
import tv.cleanyour.core.model.Level
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.ui.design.theme.CytTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class FoundPermissionAndCustomiseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun customiseButtonOpensTheSheet() {
        val sent = mutableListOf<SetupIntent>()
        val actions = object : SetupActions {
            override fun intent(intent: SetupIntent) { sent += intent }
            override fun back(): Boolean = false
            override fun dismissCard() {}
        }
        compose.setContent {
            CytTheme {
                FoundScreen(
                    SetupState.Found(Samples.miTvId(), Samples.plan(emptyList()), Level.SIMPLE, null),
                    SetupExtras(unknownModel = true),
                    actions,
                )
            }
        }
        compose.onNodeWithText("Customise").performClick()
        assertEquals(listOf(SetupIntent.Open(Sheet.CUSTOMISE)), sent)
    }

    @Test fun customiseSheetWithNothingFoundDoesNotCrash() {
        compose.setContent {
            CytTheme {
                FoundScreen(
                    SetupState.Found(Samples.miTvId(), Samples.plan(emptyList()), Level.SIMPLE, Sheet.CUSTOMISE),
                    SetupExtras(unknownModel = true),
                    NoSetupActions,
                )
            }
        }
        compose.waitForIdle()
    }
}
