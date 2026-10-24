package tv.cleanyour.ui.design.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// DESIGN-SYSTEM 4: a 4 dp base and the fixed layout distances.
object CytSpacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s56 = 56.dp

    val gutter = 20.dp            // screen side gutter
    val gutterNarrow = 16.dp      // under 360 dp wide
    val titleTop = 32.dp          // title block below the top bar
    val titleGap = 12.dp          // title to sentence
    val primaryAboveInset = 16.dp // primary action above the gesture inset
    val secondaryAboveInset = 8.dp
    val touchTarget = 48.dp
    val buttonHeight = 56.dp
    val chipHeight = 32.dp
    val topBarHeight = 56.dp
    val cardPadding = 20.dp
    val rowMinHeight = 56.dp
    val rim = 1.dp

    fun gutterFor(screenWidth: Dp): Dp = if (screenWidth < 360.dp) gutterNarrow else gutter
}
