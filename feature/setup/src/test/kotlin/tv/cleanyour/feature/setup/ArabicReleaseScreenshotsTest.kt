package tv.cleanyour.feature.setup

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
import tv.cleanyour.core.flows.ChipState
import tv.cleanyour.core.flows.NoSetupActions
import tv.cleanyour.core.flows.SetupExtras
import tv.cleanyour.core.flows.SetupState
import tv.cleanyour.core.flows.ThemeOption
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.planner.GroupSummary
import tv.cleanyour.core.planner.PlanSummary
import tv.cleanyour.core.planner.RowSummary
import tv.cleanyour.testing.fixtures.Samples

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

    @Test fun renderSetupArabicScreen() {
        val outDir = File("/workspace/apps/mi5g-tv-out/screens/1.0.0").apply { mkdirs() }
        val tvId = Samples.miTvId()
        val summary = PlanSummary(
            groups = listOf(
                GroupSummary("ads", "الإعلانات والصفوف الممولة", off = 5, total = 6, rows = listOf(
                    RowSummary(PackageName("com.mitv.tvhome.atv"), "PatchWall", "الشاشة الرئيسية مع الإعلانات", true, false, null),
                )),
                GroupSummary("tracking", "التتبع والتحليلات", off = 7, total = 7, rows = emptyList()),
                GroupSummary("nobody", "تطبيقات لم يطلبها أحد", off = 8, total = 8, rows = emptyList()),
                GroupSummary("brand", "إضافات Xiaomi", off = 2, total = 2, rows = emptyList()),
            ),
            keepNames = listOf("Netflix", "YouTube", "Prime Video"),
            dock = listOf("Netflix", "YouTube", "Prime Video", "البث المباشر"),
            homeScreenPossible = true,
            replacesLauncher = false,
        )
        val plan = Samples.plan(emptyList()).copy(summary = summary)
        val state = SetupState.Found(tvId, plan, Level.SIMPLE, null)
        val extras = SetupExtras(
            tvName = "Mi TV 4A",
            chip = ChipState.CONNECTED,
            themes = listOf(ThemeOption("default", "الافتراضي", "أسود")),
        )
        val shot = Shot("arabic_found_simple") { SetupFlow(state, extras, NoSetupActions, nowMs = { Samples.NOW_MS }) }
        compose.setContent { Rendered(shot, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "03_setup_plan.png"))
    }
}
