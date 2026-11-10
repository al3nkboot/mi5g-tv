package tv.cleanyour.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.ChipState
import tv.cleanyour.core.flows.ManageIntent
import tv.cleanyour.core.flows.ManageState
import tv.cleanyour.core.flows.TvCard
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.Level
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.ui.design.theme.CytTheme
import kotlin.test.assertEquals

// 2.2.1: where "Skip cleanup" lands, a connected TV that was never cleaned; the clean is still on it.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h1400dp-xhdpi")
class SkipCleanLandingScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private var prevLocale: Locale = Locale.getDefault()

    @Before fun setUpLocale() {
        prevLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreLocale() { Locale.setDefault(prevLocale) }

    @Test fun homeAfterSkipStillOffersTheClean() {
        val tvId = Samples.miTvId()
        val card = TvCard(tvId, "Android TV", null, false, Samples.NOW_MS, Level.SIMPLE, ChipState.CONNECTED, null, 0,
            emptyList(), null, DebugState.ON)
        val state = ManageState(cards = listOf(card), selected = tvId, offline = false, reconnecting = false, loading = false)
        val intents = mutableListOf<ManageIntent>()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CytTheme(darkTheme = true, reducedMotion = true) {
                    Box(Modifier.testTag("shot").size(360.dp, 1400.dp).background(CytTheme.colors.bg)) { ManageScreen(state, { intents += it }) }
                }
            }
        }
        compose.waitForIdle()
        val out = File("/workspace/mi5g-tv-shots/v221").apply { mkdirs() }
        compose.onNodeWithTag("shot").captureRoboImage(File(out, "03_home_after_skip_ar.png"))
        compose.onNodeWithText("ابدأ التنظيف الآمن").performScrollTo()
        compose.onNodeWithText("ابدأ التنظيف الآمن").performClick()
        compose.waitForIdle()
        assertEquals(ManageIntent.CleanAgain, intents.lastOrNull())
    }
}
