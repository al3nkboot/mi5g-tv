package tv.cleanyour.feature.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.AppPrefsValue
import tv.cleanyour.core.flows.AppSettingsState
import tv.cleanyour.core.flows.UpdateCheck
import tv.cleanyour.ui.design.theme.CytTheme

// 2.0.2: the honest help line, GitHub's rate limit, and the "cannot install here" message.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h2600dp-xhdpi")
class UpdateItem202ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun state(u: UpdateCheck) = AppSettingsState(
        version = "2.1.0-dev", prefs = AppPrefsValue(haptics = true, largeTextFollowsSystem = true),
        launcherPkg = "com.gathin.tvhome", launcherVersion = "2.7-mi5g", launcherDigest = "9f2c41d07a6be8135c4d0e72ab19f5c3",
        keyBacking = "STRONGBOX", keyFingerprint = "AB:CD:EF:01:23:45:67:89", update = u,
    )

    @Test fun renderUpdateItem() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        val out = File("/workspace/out/mi5g-tv-2.1/screens").apply { mkdirs() }
        var u by mutableStateOf<UpdateCheck>(UpdateCheck.Idle)
        var blocked by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CytTheme(darkTheme = true, reducedMotion = true) {
                    Box(Modifier.testTag("shot").size(360.dp, 2600.dp).background(CytTheme.colors.bg)) {
                        AppSettingsScreen(state(u), {}, {}, {}, onUpdateNow = {}, updateInstallBlocked = blocked)
                    }
                }
            }
        }
        compose.onAllNodesWithText("يتحقق من آخر إصدار للتطبيق على GitHub").assertCountEquals(1)
        compose.onAllNodesWithText("وحدات التلفاز", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("shot").captureRoboImage(File(out, "phone_update_item_ar.png"))
        u = UpdateCheck.RateLimited
        compose.waitForIdle()
        compose.onAllNodesWithText("تجاوزت حد GitHub المؤقت، جرّب بعد قليل أو افتح صفحة الإصدارات").assertCountEquals(1)
        compose.onAllNodesWithText("افتح صفحة الإصدارات على GitHub").assertCountEquals(1)
        compose.onNodeWithTag("shot").captureRoboImage(File(out, "phone_update_rate_limited_ar.png"))
        u = UpdateCheck.Available("2.0.3")
        blocked = true
        compose.waitForIdle()
        compose.onAllNodesWithText("لا يمكن تثبيت التحديث على هذا الجهاز").assertCountEquals(1)
        compose.onNodeWithTag("shot").captureRoboImage(File(out, "update_install_failed_ar.png"))
    }
}
