package tv.cleanyour.feature.launcher

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
import tv.cleanyour.core.flows.AppChoice
import tv.cleanyour.core.flows.HomeBlock
import tv.cleanyour.core.flows.HomeScreenState
import tv.cleanyour.core.flows.NotOnTv
import tv.cleanyour.core.flows.ThemeOption
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.ui.design.theme.CytTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h920dp-xhdpi")
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

    @Test fun renderHomeArabicScreens() {
        val outDir = File("/workspace/apps/mi5g-tv-out/screens/1.1.1").apply { mkdirs() }
        val themes = listOf(
            ThemeOption("default", "الافتراضي", "خلفية سوداء مع أربعة تطبيقات في الأسفل."),
            ThemeOption("aurora", "شفق (Aurora)", "خلفية زرقاء داكنة مع توهج هادئ خلف التطبيقات."),
            ThemeOption("six-columns", "ستة أعمدة", "ستة تطبيقات في الصف ليتسع المزيد على الشاشة."),
        )
        val dock = listOf(
            AppChoice(PackageName("net.mbc.shahid.tv"), "شاهد (Shahid)"),
            AppChoice(PackageName("tv.tod.android"), "TOD"),
            AppChoice(PackageName("com.intigral.jawwytv"), "stc tv"),
            AppChoice(PackageName("com.google.android.youtube.tv"), "يوتيوب"),
        )
        val notOnTvState = HomeScreenState(
            tvName = "AndroidTV",
            themes = themes,
            homeScreenOurs = false,
            notOnTv = NotOnTv(
                reason = HomeBlock.HOME_CHANGED,
                brand = "AndroidTV",
                unlisted = true,
                setupAgain = false,
                helpAddTv = false,
            ),
        )
        val installedState = HomeScreenState(
            tvName = "AndroidTV",
            dock = dock,
            apps = dock,
            themeId = "aurora",
            themes = themes,
            stock = Samples.stockLauncher,
            homeScreenOurs = true,
            tvSideSettings = true,
            notOnTv = null,
        )

        var currentHeight by mutableStateOf(820.dp)
        var currentScreen by mutableStateOf<@Composable () -> Unit>({ HomeScreen(notOnTvState, {}, {}) })

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

        record("01_home_not_on_tv_install_button_ar.png", 820.dp) {
            HomeScreen(notOnTvState, {}, {})
        }
        record("02_home_installed_manage_ar.png", 820.dp) {
            HomeScreen(installedState, {}, {})
        }
    }
}
