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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.FajrSound
import tv.cleanyour.core.flows.PrayerAlert
import tv.cleanyour.core.flows.PrayerSound
import tv.cleanyour.core.flows.PrayerTvSettings
import tv.cleanyour.core.flows.TvToolsIntent
import tv.cleanyour.core.flows.TvToolsState
import tv.cleanyour.ui.design.theme.CytTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 2.4 M5 review shots (Arabic, RTL, dark, 411 dp): the prayer module section with per-prayer alerts and offsets
// and the adhan sounds. Not goldens; they land in build/m5-shots.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-xhdpi")
class M5PrayerScreensTest {
    @get:Rule val compose = createComposeRule()
    private val prev = Locale.getDefault()
    private val dir = File(System.getProperty("m5.shots.dir") ?: "build/m5-shots").apply { mkdirs() }

    @Before fun ar() { Locale.setDefault(Locale.forLanguageTag("ar")) }
    @After fun restore() { Locale.setDefault(prev) }

    @Test fun prayerSectionShowsEachPrayerAndTheSounds() {
        val sent = mutableListOf<TvToolsIntent>()
        val state = TvToolsState(
            tvName = "Mi TV 4A",
            prayer = PrayerTvSettings(
                offsets = listOf(2, 0, 0, -3, 0, 5),
                alerts = listOf(PrayerAlert.ADHAN, PrayerAlert.BANNER, PrayerAlert.ADHAN, PrayerAlert.ADHAN, PrayerAlert.OFF, PrayerAlert.ADHAN),
                adhanSound = PrayerSound.DEFAULT,
                fajrSound = FajrSound.SAME,
            ),
        )
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CytTheme(darkTheme = true, reducedMotion = true) {
                    Box(Modifier.testTag("m5").size(411.dp, 891.dp).background(CytTheme.colors.bg)) {
                        TvToolsScreen(state, { sent += it }, {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("الفجر: أذان").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("m5").captureRoboImage(File(dir, "prayer_each_prayer_ar.png"))
        compose.onNodeWithText("الفجر: أذان").performClick()
        val update = sent.filterIsInstance<TvToolsIntent.UpdatePrayer>().last()
        assertEquals(PrayerAlert.OFF, update.settings.alertAt(0))
        compose.onNodeWithText("+2 دقيقة").performClick()
        assertEquals(3, sent.filterIsInstance<TvToolsIntent.UpdatePrayer>().last().settings.offsetAt(0))
        compose.onNodeWithText("🌅 أذان الفجر: مثل باقي الصلوات").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("m5").captureRoboImage(File(dir, "prayer_adhan_sounds_ar.png"))
        compose.onNodeWithText("🔈 الأذان: المرفق").performClick()
        assertTrue(TvToolsIntent.PickPrayerSound(fajr = false) in sent)
    }
}
