package tv.cleanyour.ui.design.shots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.checkRoboAccessibility
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Every component and the eight mockup screens, in dark, light, 200% font and RTL. Images land in
// src/test/screenshots (record with recordRoborazziDebug, compare with verifyRoborazziDebug).
// The ATF checks (touch targets, contrast, labels) run on every rendering and fail
// the test on any ERROR result.
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class ComponentScreenshotTest(private val sample: Sample, private val variant: Variant) {

    @get:Rule
    val compose = createComposeRule()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun shot() {
        compose.setContent { Rendered(sample, variant) }
        compose.waitForIdle()
        val node = compose.onNodeWithTag(SHOT_TAG)
        // Rendered at xhdpi for the checks, saved at half scale like every feature module (R4-W8).
        node.captureRoboImage(
            "src/test/screenshots/${sample.name}_${variant.name.lowercase()}.png",
            roborazziOptions = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)),
        )
        run {
            node.checkRoboAccessibility(
                RoborazziATFAccessibilityCheckOptions(
                    checker = RoborazziATFAccessibilityChecker(suppressions = AtfSuppressions.matcher),
                    failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
                ),
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun params(): List<Array<Any>> = Samples.all.flatMap { s -> Variant.entries.map { v -> arrayOf<Any>(s, v) } }
    }
}
