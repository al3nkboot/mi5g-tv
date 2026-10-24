package tv.cleanyour.feature.connect

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h780dp-xhdpi")
class ArabicReleaseScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private var prevLocale: Locale = Locale.getDefault()

    @Before fun setUpLocale() {
        prevLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreLocale() {
        Locale.setDefault(prevLocale)
    }

    @Test fun renderWelcomeArabic() {
        val outDir = File("/workspace/apps/mi5g-tv-out/screens/1.0.0").apply { mkdirs() }
        val welcome = ConnectShots.all.first { it.name == "welcome_found_one" }
        compose.setContent { Rendered(welcome, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "01_connect_welcome.png"))
    }

    @Test fun renderStepsArabic() {
        val outDir = File("/workspace/apps/mi5g-tv-out/screens/1.0.0").apply { mkdirs() }
        val steps = ConnectShots.all.first { it.name == "door_steps_atv" }
        compose.setContent { Rendered(steps, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "02_connect_steps.png"))
    }
}
