package tv.cleanyour.ui.design.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.theme.CytTheme

// The four renderings every component gets (W7 definition of done).
enum class Variant(val dark: Boolean, val fontScale: Float, val rtl: Boolean) {
    DARK(true, 1f, false),
    LIGHT(false, 1f, false),
    FONT200(true, 2f, false),
    RTL(true, 1f, true),
}

// A named component state. Screens are 360 x 780 dp like the mockups; components are 360 dp wide
// on the theme background with a 16 dp margin, as tall as they need.
class Sample(val name: String, val screen: Boolean = false, val reducedMotion: Boolean = false, val content: @Composable () -> Unit) {
    override fun toString() = name
}

const val SHOT_TAG = "shot"

@Composable
fun Rendered(sample: Sample, variant: Variant, width: Dp = 360.dp) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, variant.fontScale),
        LocalLayoutDirection provides if (variant.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        CytTheme(darkTheme = variant.dark, reducedMotion = sample.reducedMotion) {
            val bg = CytTheme.colors.bg
            if (sample.screen) {
                Box(Modifier.testTag(SHOT_TAG).size(width, 780.dp).background(bg)) { sample.content() }
            } else {
                Box(Modifier.testTag(SHOT_TAG).width(width).background(bg).padding(16.dp)) { sample.content() }
            }
        }
    }
}
