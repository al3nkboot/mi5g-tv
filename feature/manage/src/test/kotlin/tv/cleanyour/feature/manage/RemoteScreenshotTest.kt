package tv.cleanyour.feature.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.*
import tv.cleanyour.ui.design.theme.CytTheme
import android.content.res.Configuration
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-xhdpi")
class RemoteScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun captureArabicRemoteVariants() {
        val directory = File(System.getProperty("remote.shots.dir") ?: "build/remote-shots").apply { mkdirs() }
        val shots = listOf(
            Triple("01_remote_ar_dark.png", RemoteState(tvName = "Mi TV", connection = RemoteConnection.Connected), false),
            Triple("02_trackpad_ar.png", RemoteState(tvName = "Mi TV", connection = RemoteConnection.Connected, mode = RemoteMode.Trackpad), false),
            Triple("03_keyboard_ar.png", RemoteState(tvName = "Mi TV", connection = RemoteConnection.Connected, mode = RemoteMode.Keyboard, textInput = "Hello مرحبا", unicodeWarning = true), false),
            Triple("04_apps_favorites_ar.png", RemoteState(tvName = "Mi TV", connection = RemoteConnection.Connected, tab = RemoteTab.Apps, installedApps = listOf(RemoteApp("com.netflix.ninja", "Netflix"), RemoteApp("com.google.android.youtube.tv", "YouTube"), RemoteApp("net.mbc.shahidTV", "Shahid"), RemoteApp("com.teamsmart.videomanager.tv", "SmartTube"), RemoteApp("com.disney.disneyplus", "Disney Plus")), favorites = setOf("com.netflix.ninja", "net.mbc.shahidTV")), false),
            Triple("05_remote_en.png", RemoteState(tvName = "Mi TV", connection = RemoteConnection.Connected), true),
            Triple("06_more_sheet_ar.png", RemoteState(tvName = "Mi TV", connection = RemoteConnection.Connected, showMoreSheet = true), false),
            Triple("07_not_connected_ar.png", RemoteState(tvName = "Mi TV", connection = RemoteConnection.NotConnected), false),
        )
        var current by mutableStateOf(shots.first().second)
        var english by mutableStateOf(false)
        compose.setContent {
            CytTheme(darkTheme = true, reducedMotion = true) {
                val config = Configuration(LocalConfiguration.current).apply { setLocale(if (english) Locale.ENGLISH else Locale.forLanguageTag("ar")); setLayoutDirection(if (english) Locale.ENGLISH else Locale.forLanguageTag("ar")) }
                val ctx = androidx.compose.ui.platform.LocalContext.current.createConfigurationContext(config)
                CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides ctx, LocalConfiguration provides config, LocalLayoutDirection provides if (english) LayoutDirection.Ltr else LayoutDirection.Rtl) {
                    Box(Modifier.testTag("remote-shot").size(411.dp, 891.dp)) { RemoteScreen(current, {}, {}) }
                }
            }
        }
        shots.forEach { (name, state, useEnglish) ->
            current = state
            english = useEnglish
            compose.waitForIdle()
            compose.onNodeWithTag("remote-shot").captureRoboImage(File(directory, name))
        }
    }
}
