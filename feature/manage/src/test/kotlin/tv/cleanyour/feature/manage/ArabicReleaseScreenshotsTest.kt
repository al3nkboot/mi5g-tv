package tv.cleanyour.feature.manage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
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
import tv.cleanyour.core.flows.ManageState
import tv.cleanyour.core.flows.RemoteStatus
import tv.cleanyour.core.flows.ScreenshotStatus
import tv.cleanyour.core.flows.SideloadStatus
import tv.cleanyour.core.flows.TvCard
import tv.cleanyour.core.flows.TvToolsState
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.ui.design.theme.CytTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h1250dp-xhdpi")
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

    private fun fakeShot(): ByteArray {
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF05060A.toInt())
        val paint = Paint().apply { color = 0xFF14998E.toInt() }
        repeat(4) { i -> canvas.drawRoundRect(RectF(70f + i * 130f, 220f, 180f + i * 130f, 320f), 16f, 16f, paint) }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    @Test fun renderManageAndTvToolsArabicScreens() {
        val outDir = File("/workspace/apps/mi5g-tv-out/screens/1.1.0").apply { mkdirs() }
        val tvId = Samples.miTvId()
        val shotBytes = fakeShot()
        val card = TvCard(
            tvId,
            "Mi TV 4A",
            "غرفة المعيشة",
            true,
            Samples.NOW_MS,
            Level.SIMPLE,
            ChipState.CONNECTED,
            shotBytes,
            23,
            listOf("شاهد (Shahid)", "TOD", "stc tv", "OSN+"),
            Samples.NOW_MS,
            DebugState.ON,
        )
        val manageState = ManageState(cards = listOf(card), selected = tvId, offline = false, reconnecting = false, loading = false)
        var currentHeight by mutableStateOf(920.dp)
        var currentScreen by mutableStateOf<@Composable () -> Unit>({ ManageScreen(manageState, {}) })

        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CytTheme(darkTheme = true, reducedMotion = true) {
                    Box(Modifier.testTag("shot").size(360.dp, currentHeight).background(CytTheme.colors.bg)) {
                        currentScreen()
                    }
                }
            }
        }

        fun record(fileName: String, height: Dp, screen: @Composable () -> Unit) {
            currentHeight = height
            currentScreen = screen
            compose.waitForIdle()
            compose.onNodeWithTag("shot").captureRoboImage(File(outDir, fileName))
        }

        record("01_manage_home_ar.png", 920.dp) {
            ManageScreen(manageState, {})
        }

        record("02_tv_tools_remote_text_ar.png", 1220.dp) {
            TvToolsScreen(
                TvToolsState(
                    tvName = "Mi TV 4A",
                    textInput = "Shahid VIP 2026",
                    remoteStatus = RemoteStatus.Sent,
                ),
                {},
                {},
            )
        }

        val fakeBlob = BlobRef(Sha256("a".repeat(64)), 24L * 1024L * 1024L, "blobs/shahid.apk")
        record("03_tv_tools_apk_ready_screenshot_ar.png", 1250.dp) {
            TvToolsScreen(
                TvToolsState(
                    tvName = "Mi TV 4A",
                    screenshotStatus = ScreenshotStatus.Captured(shotBytes),
                    sideloadStatus = SideloadStatus.Ready("shahid-tv-release.apk", fakeBlob.size, fakeBlob),
                ),
                {},
                {},
            )
        }

        record("04_tv_tools_apk_installing_ar.png", 1220.dp) {
            TvToolsScreen(
                TvToolsState(
                    tvName = "Mi TV 4A",
                    sideloadStatus = SideloadStatus.Installing("shahid-tv-release.apk", 65),
                ),
                {},
                {},
            )
        }

        record("05_tv_tools_apk_installed_ar.png", 1220.dp) {
            TvToolsScreen(
                TvToolsState(
                    tvName = "Mi TV 4A",
                    sideloadStatus = SideloadStatus.Installed("shahid-tv-release.apk"),
                ),
                {},
                {},
            )
        }
    }
}
