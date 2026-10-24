package tv.cleanyour.ui.design.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import tv.cleanyour.ui.design.R

// Outfit (SIL OFL 1.1), the launcher's own variable font, bundled from the upstream submodule.
// The licence text ships as R.raw.cyt_outfit_ofl for the Licences screen (M8).
@OptIn(ExperimentalTextApi::class)
val Outfit: FontFamily = FontFamily(
    Font(R.font.cyt_outfit, FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(R.font.cyt_outfit, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.cyt_outfit, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.cyt_outfit, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

// DESIGN-SYSTEM 3. Weights 200 and 300 only at 22 sp and above. `overline` is ALL CAPS: use the
// Overline composable, which upper-cases in the current locale. `numeral` uses tabular figures.
@Immutable
data class CytType(
    val display: TextStyle,
    val headline: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val overline: TextStyle,
    val numeral: TextStyle,
    val mono: TextStyle,
)

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun outfit(size: Int, line: Int, weight: FontWeight, tracking: Double, tnum: Boolean = false, heading: Boolean = false) = TextStyle(
    fontFamily = Outfit, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    letterSpacing = tracking.sp, lineHeightStyle = Trim,
    fontFeatureSettings = if (tnum) "tnum" else null,
    // Titles wrap into balanced lines ("things to / switch off"), never truncate.
    lineBreak = if (heading) LineBreak.Heading else LineBreak.Simple,
)

val DefaultCytType = CytType(
    display = outfit(40, 46, FontWeight.Light, -0.5, heading = true),
    headline = outfit(30, 36, FontWeight.Light, -0.25, heading = true),
    titleLarge = outfit(22, 28, FontWeight.Normal, 0.0),
    titleMedium = outfit(17, 24, FontWeight.Medium, 0.0),
    bodyLarge = outfit(16, 24, FontWeight.Normal, 0.0),
    bodyMedium = outfit(14, 20, FontWeight.Normal, 0.1),
    bodySmall = outfit(12, 16, FontWeight.Normal, 0.2),
    labelLarge = outfit(16, 20, FontWeight.Medium, 0.1),
    labelMedium = outfit(13, 16, FontWeight.Medium, 0.2),
    overline = outfit(11, 16, FontWeight.Medium, 2.0),
    numeral = outfit(64, 64, FontWeight.ExtraLight, -1.0, tnum = true),
    mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
)

fun CytType.toM3(): Typography = Typography(
    displaySmall = display, headlineMedium = headline, headlineSmall = headline,
    titleLarge = titleLarge, titleMedium = titleMedium, titleSmall = titleMedium,
    bodyLarge = bodyLarge, bodyMedium = bodyMedium, bodySmall = bodySmall,
    labelLarge = labelLarge, labelMedium = labelMedium, labelSmall = labelMedium,
)
