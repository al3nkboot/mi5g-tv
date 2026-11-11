package tv.cleanyour.feature.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import tv.cleanyour.core.flows.SetupActions
import tv.cleanyour.core.flows.SetupExtras
import tv.cleanyour.core.flows.SetupIntent
import tv.cleanyour.core.flows.SetupState
import tv.cleanyour.core.flows.StopAction
import tv.cleanyour.core.flows.StopCard
import tv.cleanyour.core.flows.StopCase
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.planner.GroupSummary
import tv.cleanyour.core.planner.PlanSummary
import tv.cleanyour.core.planner.RowSummary
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.assertEquals

// 2.3 item 4 (cleanyourtv-app 0.4.8 to 0.5.0-beta1): the choices line on an empty plan (#1222), the
// third way out after saying no (#3760), the low storage card (#2851) and the chooser card (#3301).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h780dp-xhdpi")
class Setup23ScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private var prevLocale: Locale = Locale.getDefault()
    private val outDir = File("/workspace/mi5g-tv-shots/v230").apply { mkdirs() }

    private class Recording : SetupActions {
        val intents = mutableListOf<SetupIntent>()
        override fun intent(intent: SetupIntent) { intents += intent }
        override fun back() = false
        override fun dismissCard() {}
    }

    @Before fun setUpLocale() {
        prevLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreLocale() { Locale.setDefault(prevLocale) }

    private val extras = SetupExtras(tvName = "Freebox", chip = ChipState.CONNECTED, canSkipClean = true)

    @Test fun anEmptyPlanWithRowsToChooseSaysSo() {
        val rows = listOf(RowSummary(PackageName("fr.tf1.mytf1"), "TF1+", "Streaming app", off = false, protected = false, whyProtected = null))
        val summary = PlanSummary(listOf(GroupSummary("streaming", "Streaming", 0, 1, rows)), emptyList(), emptyList(), homeScreenPossible = false, replacesLauncher = false)
        val state = SetupState.Found(Samples.miTvId(), Samples.plan(emptyList()).copy(summary = summary), Level.SIMPLE, null)
        compose.setContent { Rendered(Shot("found_choices") { SetupFlow(state, extras, Recording(), nowMs = { Samples.NOW_MS }) }, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithText("تركنا بعض التطبيقات تعمل لتختار أنت. تجدها في «تخصيص».").assertIsDisplayed()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "01_found_choices_ar.png"))
    }

    @Test fun saidNoOffersKeepingTheTvsOwnHome() {
        val card = StopCard(StopCase.HOME_USER_SAID_NO, "عادت الشاشة الرئيسية القديمة",
            "تمت إعادتها. هل كانت هناك مشكلة، أم أردت إلقاء نظرة أولًا؟", "حاول مرة أخرى", "بدا أن هناك مشكلة", null,
            "الإبقاء على الشاشة الرئيسية الأصلية للتلفاز")
        val actions = Recording()
        compose.setContent { Rendered(Shot("said_no") { SetupFlow(SetupState.Stopped(Samples.miTvId(), card), extras, actions) }, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "02_said_no_keep_own_ar.png"))
        compose.onNodeWithText("الإبقاء على الشاشة الرئيسية الأصلية للتلفاز").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(SetupIntent.StopAction(StopAction.TERTIARY), actions.intents.last())
    }

    @Test fun lowStorageCard() {
        val card = StopCard(StopCase.INSTALL_FAILED, "تعذّر تثبيت الشاشة الرئيسية",
            "مساحة التلفاز شبه ممتلئة (المتاح 120 م.ب)، لذلك لم نتمكن من تثبيت الشاشة الرئيسية الجديدة. تبقى الشاشة الرئيسية الأصلية للتلفاز، ويبقى ما تم من التنظيف. فرّغ بعض المساحة من الإعدادات > التخزين، ثم جرّب الشاشة الرئيسية من جديد من قسم الإدارة.",
            "حسنًا", null, null)
        compose.setContent { Rendered(Shot("low_storage") { SetupFlow(SetupState.Stopped(Samples.miTvId(), card), extras, Recording()) }, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "03_low_storage_ar.png"))
    }

    @Test fun chooserCardNamesTheirApps() {
        val card = StopCard(StopCase.HOME_OTHER_LAUNCHER, "تطبيق شاشة رئيسية آخر",
            "توجد على التلفاز تطبيقات شاشة رئيسية أخرى (Projectivy, ATV Launcher)، فسأل أندرويد أي شاشة رئيسية يستخدم ولم يكتمل التبديل. أعدنا الشاشة الرئيسية الأصلية للتلفاز. احذفها من التلفاز ثم حاول مرة أخرى.",
            "استخدام الشاشة الرئيسية الجديدة", "الإبقاء على الشاشة الرئيسية القديمة", null)
        compose.setContent { Rendered(Shot("chooser") { SetupFlow(SetupState.Stopped(Samples.miTvId(), card), extras, Recording()) }, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithText("Projectivy", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "04_chooser_names_apps_ar.png"))
    }
}
