package tv.cleanyour.feature.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import tv.cleanyour.core.flows.StopCard
import tv.cleanyour.core.flows.StopCase
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.planner.PlanSummary
import tv.cleanyour.core.probe.ProbeStage
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.assertEquals

// 2.2.1: "Skip cleanup" on S3 (the user's report: an unlisted TV with nothing sure to switch off),
// on the probe, and on a failed read; tapping it asks the machine for SkipClean.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-rSA-ldrtl-w360dp-h780dp-xhdpi")
class SkipCleanScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private var prevLocale: Locale = Locale.getDefault()
    private val outDir = File("/workspace/mi5g-tv-shots/v221").apply { mkdirs() }

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

    private val extras = SetupExtras(tvName = "Android TV", chip = ChipState.CONNECTED, unknownModel = true, canSkipClean = true)

    @Test fun foundOffersSkipAndTappingItSkips() {
        val summary = PlanSummary(groups = emptyList(), keepNames = emptyList(), dock = emptyList(), homeScreenPossible = false, replacesLauncher = false)
        val state = SetupState.Found(Samples.miTvId(), Samples.plan(emptyList()).copy(summary = summary), Level.SIMPLE, null)
        val actions = Recording()
        val shot = Shot("found_skip") { SetupFlow(state, extras, actions, nowMs = { Samples.NOW_MS }) }
        compose.setContent { Rendered(shot, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithText("تخطّي التنظيف").assertIsDisplayed()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "01_found_skip_clean_ar.png"))
        compose.onNodeWithText("تخطّي التنظيف").performClick()
        compose.waitForIdle()
        assertEquals(listOf<SetupIntent>(SetupIntent.SkipClean), actions.intents)
    }

    @Test fun noSkipBeforeTheTvIsSaved() {
        val summary = PlanSummary(groups = emptyList(), keepNames = emptyList(), dock = emptyList(), homeScreenPossible = false, replacesLauncher = false)
        val state = SetupState.Found(Samples.miTvId(), Samples.plan(emptyList()).copy(summary = summary), Level.SIMPLE, null)
        compose.setContent { Rendered(Shot("found_noskip") { SetupFlow(state, extras.copy(canSkipClean = false), Recording()) }, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithText("تخطّي التنظيف").assertDoesNotExist()
    }

    @Test fun aFailedReadOffersSkip() {
        val card = StopCard(StopCase.PROBE_FAILED, "تعذّرت قراءة محتويات التلفاز",
            "توقف التلفاز عن الاستجابة أثناء قراءته. لم يتغير أي شيء. تأكد من أنه ما زال قيد التشغيل، ثم حاول مرة أخرى.",
            "حاول مرة أخرى", "رجوع", null)
        val actions = Recording()
        compose.setContent { Rendered(Shot("stop_skip") { SetupFlow(SetupState.Stopped(Samples.miTvId(), card), extras, actions) }, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithTag(SHOT_TAG).captureRoboImage(File(outDir, "02_probe_failed_skip_clean_ar.png"))
        compose.onNodeWithText("تخطّي التنظيف").performClick()
        compose.waitForIdle()
        assertEquals(listOf<SetupIntent>(SetupIntent.SkipClean), actions.intents)
    }

    @Test fun theProbeOffersSkipForASavedTv() {
        compose.setContent { Rendered(Shot("probe_skip") { SetupFlow(SetupState.Probing(Samples.miTvId(), ProbeStage.entries[1], null), extras, Recording()) }, Variant.RTL) }
        compose.waitForIdle()
        compose.onNodeWithText("تخطّي التنظيف").assertIsDisplayed()
    }
}
