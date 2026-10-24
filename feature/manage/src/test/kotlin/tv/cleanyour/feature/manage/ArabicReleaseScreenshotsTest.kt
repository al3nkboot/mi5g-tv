package tv.cleanyour.feature.manage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import tv.cleanyour.core.flows.TvCard
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.Level
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.ui.design.theme.CytTheme

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

    @Test fun renderManageArabicScreen() {
        val outDir = File("/workspace/apps/mi5g-tv-out/screens/1.0.0").apply { mkdirs() }
        val tvId = Samples.miTvId()
        val card = TvCard(
            tvId,
            "Mi TV 4A",
            "غرفة المعيشة",
            true,
            Samples.NOW_MS,
            Level.SIMPLE,
            ChipState.CONNECTED,
            fakeShot(),
            23,
            listOf("Netflix", "YouTube", "Prime Video", "البث المباشر"),
            Samples.NOW_MS,
            DebugState.ON,
        )
        val state = ManageState(cards = listOf(card), selected = tvId, offline = false, reconnecting = false, loading = false)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CytTheme(darkTheme = true, reducedMotion = true) {
                    Box(Modifier.testTag("shot").size(360.dp, 780.dp).background(CytTheme.colors.bg)) {
                        ManageScreen(state, {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("shot").captureRoboImage(File(outDir, "04_manage_home.png"))
    }
}
