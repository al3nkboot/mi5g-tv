package tv.cleanyour.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.ui.design.theme.CytTheme

// 2.0 M4 review shots (Samsung / LG helper): Arabic, RTL, dark phone. Land in build/m4-shots.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-xhdpi")
class M4SmartTvShotsTest {
    @get:Rule val compose = createComposeRule()
    private val prev = Locale.getDefault()
    private val dir = File("build/m4-shots").apply { mkdirs() }

    @Before fun ar() { Locale.setDefault(Locale.forLanguageTag("ar")) }
    @After fun restore() { Locale.setDefault(prev) }

    private fun shoot(tab: SmartTvTab, file: String) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CytTheme(darkTheme = true, reducedMotion = true) {
                    Column(Modifier.testTag("s").size(411.dp, 891.dp).background(CytTheme.colors.bg).padding(16.dp)) {
                        SmartTvHelperContent(tab) {}
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("s").captureRoboImage(File(dir, file))
    }

    @Test fun remote() = shoot(SmartTvTab.REMOTE, "smarttv-remote.png")
    @Test fun dns() = shoot(SmartTvTab.DNS, "smarttv-dns.png")
    @Test fun ads() = shoot(SmartTvTab.ADS, "smarttv-ads.png")
}
