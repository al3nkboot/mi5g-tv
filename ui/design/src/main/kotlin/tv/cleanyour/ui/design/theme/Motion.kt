package tv.cleanyour.ui.design.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// DESIGN-SYSTEM 6. Springs for things you move, short fades for things that appear.
object CytMotion {
    val highlight = spring<Offset>(dampingRatio = 0.58f, stiffness = 260f)
    val highlightFloat = spring<Float>(dampingRatio = 0.58f, stiffness = 260f)
    val press = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val PRESS_MS = 90
    const val PRESS_SCALE = 0.97f
    const val STEP_ENTER_MS = 320
    const val STEP_EXIT_MS = 180
    const val STEP_SLIDE_DP = 24
    const val TITLE_OUT_MS = 120
    const val TITLE_IN_MS = 200
    const val TITLE_SLIDE_DP = 12
    const val NARRATION_MS = 220
    const val NARRATION_CHECK_MS = 300
    const val LIST_FADE_MS = 160
    const val LIST_STAGGER_MS = 24
    const val LIST_STAGGER_ROWS = 8
    const val SNACKBAR_IN_MS = 200
    const val SNACKBAR_OUT_MS = 150
    const val GLOW_DIP_MS = 200
    const val GLOW_RISE_MS = 500
    const val GLOW_DIP_ALPHA = 0.35f
    const val COUNT_UP_MIN_MS = 700
    const val MORPH_MS = 400
    const val THUMBNAIL_CROSSFADE_MS = 150
    const val COUNTDOWN_SETTLE_MS = 300
    const val WAITING_PULSE_MS = 1200
    const val WAITING_PULSE_CYCLES = 3
    const val RESTART_BREATH_MS = 4000
    const val REDUCED_FADE_MS = 150
    const val WELCOME_MS = 3000
}

// "Remove animations" (ANIMATOR_DURATION_SCALE 0): springs snap, slides become 150 ms fades,
// the count-up is static, the welcome illustration shows its rest state. CytTheme provides it.
val LocalReducedMotion = staticCompositionLocalOf { false }

fun Context.isReducedMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

// A spring that snaps under reduced motion.
fun <T> cytSpring(reduced: Boolean, dampingRatio: Float = Spring.DampingRatioNoBouncy, stiffness: Float = Spring.StiffnessMedium): FiniteAnimationSpec<T> =
    if (reduced) snap() else spring(dampingRatio, stiffness)

// Shared axis X for wizard steps (forward = true slides in from the end side; RTL mirrors it).
fun <S> AnimatedContentTransitionScope<S>.stepTransition(
    forward: Boolean,
    reduced: Boolean,
    layoutDirection: LayoutDirection,
    density: Density,
): ContentTransform {
    if (reduced) {
        return fadeIn(tween(CytMotion.REDUCED_FADE_MS)) togetherWith fadeOut(tween(CytMotion.REDUCED_FADE_MS))
    }
    val px = with(density) { CytMotion.STEP_SLIDE_DP.dp.roundToPx() }
    val dir = (if (forward) 1 else -1) * (if (layoutDirection == LayoutDirection.Rtl) -1 else 1)
    val enter = slideInHorizontally(tween(CytMotion.STEP_ENTER_MS, easing = CytMotion.emphasizedDecelerate)) { dir * px } +
        fadeIn(tween(CytMotion.STEP_ENTER_MS, easing = CytMotion.emphasizedDecelerate))
    val exit = slideOutHorizontally(tween(CytMotion.STEP_EXIT_MS, easing = CytMotion.emphasizedAccelerate)) { -dir * px } +
        fadeOut(tween(CytMotion.STEP_EXIT_MS, easing = CytMotion.emphasizedAccelerate))
    return enter togetherWith exit
}

// Same screen, new title: out fade and 12 dp toward the start in 120 ms, in from 12 dp in 200 ms.
fun <S> AnimatedContentTransitionScope<S>.titleTransition(reduced: Boolean, density: Density, layoutDirection: LayoutDirection): ContentTransform {
    if (reduced) return fadeIn(tween(CytMotion.REDUCED_FADE_MS)) togetherWith fadeOut(tween(CytMotion.REDUCED_FADE_MS))
    val px = with(density) { CytMotion.TITLE_SLIDE_DP.dp.roundToPx() }
    val dir = if (layoutDirection == LayoutDirection.Rtl) -1 else 1
    return (slideInHorizontally(tween(CytMotion.TITLE_IN_MS, easing = CytMotion.emphasizedDecelerate)) { dir * px } +
        fadeIn(tween(CytMotion.TITLE_IN_MS))) togetherWith
        (slideOutHorizontally(tween(CytMotion.TITLE_OUT_MS, easing = LinearEasing)) { -dir * px } + fadeOut(tween(CytMotion.TITLE_OUT_MS)))
}


// Press: scale to 0.97 in 90 ms FastOutLinearIn, release on spring(0.6, 800). A graphicsLayer
// scale is a transform, not an offscreen layer (no alpha, no compositing strategy change).
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, reduced: Boolean = LocalReducedMotion.current): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) CytMotion.PRESS_SCALE else 1f,
        animationSpec = if (pressed) tween(CytMotion.PRESS_MS, easing = FastOutLinearInEasing) else CytMotion.press,
        label = "press",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
