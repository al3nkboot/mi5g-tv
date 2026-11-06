package tv.cleanyour.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import tv.cleanyour.ui.design.theme.CytTheme

// 2.0 M1 review shots: Arabic, RTL, dark, a 411 dp phone. Not goldens; they land in m1.shots.dir.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-xhdpi")
class M1ScreensTest {
    @get:Rule val compose = createComposeRule()
    private val prev = Locale.getDefault()
    private val dir = File(System.getProperty("m1.shots.dir") ?: "build/m1-shots").apply { mkdirs() }

    @Before fun ar() { Locale.setDefault(Locale.forLanguageTag("ar")); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restore() { Locale.setDefault(prev) }

    private fun shoot(shotName: String, file: String, height: Int = 891, before: () -> Unit = {}) {
        val shot = ManageShots.all.first { it.name == shotName }
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { CytTheme(darkTheme = true, reducedMotion = true) { Box(Modifier.testTag("m1").size(411.dp, height.dp).background(CytTheme.colors.bg)) { shot.content() } } } }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        compose.onNodeWithTag("m1").captureRoboImage(File(dir, file))
    }

    @Test fun cardSizeSetting() = shoot("appsettings_idle", "card_size_setting.png", height = 1500)
    @Test fun quickStartGuide() = shoot("quick_start_guide", "quick_start_guide.png", height = 700)
    @Test fun unreachableTv() = shoot("manage_offline", "unreachable_tv_error.png")
}
