package tv.cleanyour.feature.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import tv.cleanyour.ui.design.component.SheetSurface
import tv.cleanyour.ui.design.theme.CytTheme

// The screen-test harness of this module (T8): every state in the four renderings of W7 (dark,
// light, 200% font, right to left), 360 dp wide like the mockups, with the ATF checks on. Images
// land in src/test/screenshots (record with recordRoborazziDebug, compare with
// verifyRoborazziDebug). Rendered at xhdpi for the checks, saved at half scale to keep the
// goldens small.

enum class Variant(val dark: Boolean, val fontScale: Float, val rtl: Boolean) {
    DARK(true, 1f, false),
    LIGHT(false, 1f, false),
    FONT200(true, 2f, false),
    RTL(true, 1f, true),
}

class Shot(val name: String, val height: Dp = 780.dp, val content: @Composable () -> Unit) {
    override fun toString() = name
}

const val SHOT_TAG = "shot"

// Nothing is suppressed. A suppression here needs a reason next to it.
private val atfSuppressions: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything())

@Composable
private fun Rendered(shot: Shot, variant: Variant) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, variant.fontScale),
        LocalLayoutDirection provides if (variant.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        CytTheme(darkTheme = variant.dark, reducedMotion = true) {
            Box(Modifier.testTag(SHOT_TAG).size(360.dp, shot.height).background(CytTheme.colors.bg)) { shot.content() }
        }
    }
}

@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.shoot(shot: Shot, variant: Variant) {
    setContent { Rendered(shot, variant) }
    waitForIdle()
    val node = onNodeWithTag(SHOT_TAG)
    node.captureRoboImage(
        "src/test/screenshots/${shot.name}_${variant.name.lowercase()}.png",
        roborazziOptions = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5)),
    )
    node.checkRoboAccessibility(
        RoborazziATFAccessibilityCheckOptions(
            checker = RoborazziATFAccessibilityChecker(suppressions = atfSuppressions),
            failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
        ),
    )
}

// A sheet as the modal shows it: the screen under the scrim, hidden from accessibility like the
// modal hides it, and the sheet body on SheetSurface at the bottom.
@Composable
fun SheetOver(screen: @Composable () -> Unit, sheet: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().clearAndSetSemantics {}) { screen() }
        Box(Modifier.fillMaxSize().background(CytTheme.colors.scrim).clearAndSetSemantics {})
        SheetSurface(Modifier.align(Alignment.BottomCenter).padding(top = 72.dp).verticalScroll(rememberScrollState()), content = sheet)
    }
}
