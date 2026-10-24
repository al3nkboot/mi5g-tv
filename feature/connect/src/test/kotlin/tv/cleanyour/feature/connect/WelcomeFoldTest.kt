package tv.cleanyour.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertEquals
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.discovery.DiscoverySource
import tv.cleanyour.core.discovery.FoundTv
import tv.cleanyour.core.flows.DoorPhase
import tv.cleanyour.core.flows.Families
import tv.cleanyour.core.flows.NoSetupActions
import tv.cleanyour.core.flows.SetupActions
import tv.cleanyour.core.flows.SetupIntent
import tv.cleanyour.core.flows.Sheet
import tv.cleanyour.core.flows.SetupExtras
import tv.cleanyour.core.flows.SetupState
import tv.cleanyour.ui.design.theme.CytTheme

// Emulator run, round 3: on the 360 x 720 dp phone the discovery line ("Found ..." / "No TV yet")
// sat below the fold under the three pinned actions. It is pinned with Start now, so the first view
// shows it at every height, with the promise and its body above it. 600 dp is about what that
// phone leaves between its status and navigation bars; 560 dp is a shorter phone still.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-hdpi")
class WelcomeFoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val miTv = FoundTv("192.168.1.50", "Mi TV 4A", "MiTV-AXSO0", setOf(DiscoverySource.GOOGLE_CAST), true, null, null, 0L)

    private fun welcome(state: SetupState.Welcome, heightDp: Int) = compose.setContent {
        CytTheme(darkTheme = true, reducedMotion = true) {
            Box(Modifier.size(360.dp, heightDp.dp).background(CytTheme.colors.bg)) {
                WelcomeScreen(state, SetupExtras(), NoSetupActions, onReturning = {})
            }
        }
    }

    @Test fun foundLineIsInTheFirstView() {
        welcome(SetupState.Welcome(listOf(miTv), onWifi = true, looking = false), 600)
        compose.onNodeWithText("Found Mi TV 4A").assertIsDisplayed()
        compose.onNodeWithText("Start").assertIsDisplayed()
        // The promise and its body fit above the pinned bar too (mockup 01), and Welcome has the
        // mockup's two actions only: address entry lives on S1b, the not-found card and the door.
        compose.onNodeWithText("Your TV, tidied up.").assertIsDisplayed()
        compose.onNodeWithText("Switch off the clutter", substring = true).assertIsDisplayed()
        compose.onNodeWithText("I've set up a TV before").assertIsDisplayed()
        compose.onNodeWithText("Enter the TV's address").assertDoesNotExist()
    }

    @Test fun noTvYetLineIsInTheFirstViewOnAShorterPhone() {
        welcome(SetupState.Welcome(emptyList(), onWifi = true, looking = false), 560)
        compose.onNodeWithText("No TV yet", substring = true).assertIsDisplayed()
    }

    @Test fun lookingLineIsInTheFirstView() {
        welcome(SetupState.Welcome(emptyList(), onWifi = true, looking = true), 600)
        compose.onNodeWithText("Looking for TVs", substring = true).assertIsDisplayed()
    }

    // Start went straight to the one device found: the door offers a quiet way to the address
    // sheet. A typed address does not get it (Back retypes it).
    @Test fun doorOfAFoundTvOffersNotThisTv() {
        val sent = mutableListOf<SetupIntent>()
        val actions = object : SetupActions {
            override fun intent(intent: SetupIntent) { sent += intent }
            override fun back(): Boolean = false
            override fun dismissCard() {}
        }
        val door = SetupState.OpenDoor(miTv, Families.ATV, 0, DoorPhase.STEPS, null, null, 0L)
        compose.setContent {
            CytTheme(darkTheme = true, reducedMotion = true) { DoorScreen(door, SetupExtras(tvName = "Mi TV 4A"), actions) }
        }
        compose.onNodeWithText("Not this TV? Enter the TV's address").performScrollTo().performClick()
        assertEquals(listOf<SetupIntent>(SetupIntent.Open(Sheet.ADDRESS)), sent)
    }

    @Test fun doorOfATypedAddressHasNoNotThisTv() {
        val typed = FoundTv("192.168.1.50", null, null, setOf(DiscoverySource.MANUAL), null, null, null, 0L)
        compose.setContent {
            CytTheme(darkTheme = true, reducedMotion = true) {
                DoorScreen(SetupState.OpenDoor(typed, Families.UNKNOWN, 0, DoorPhase.STEPS, null, null, 0L), SetupExtras(), NoSetupActions)
            }
        }
        compose.onNodeWithText("Not this TV? Enter the TV's address").assertDoesNotExist()
    }
}
