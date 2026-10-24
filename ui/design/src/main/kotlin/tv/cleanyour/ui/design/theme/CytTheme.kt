package tv.cleanyour.ui.design.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalCytColors = staticCompositionLocalOf { DarkCytColors }
val LocalCytType = staticCompositionLocalOf { DefaultCytType }
val LocalCytShapes = staticCompositionLocalOf { CytShapes() }

// The app theme. Dark is the default and the brand (DESIGN-SYSTEM 2); W8 passes
// isSystemInDarkTheme() or a setting. reducedMotion null reads ANIMATOR_DURATION_SCALE.
@Composable
fun CytTheme(
    darkTheme: Boolean = true,
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkCytColors else LightCytColors
    val type = DefaultCytType
    val shapes = remember { CytShapes() }
    val context = LocalContext.current
    val reduced = reducedMotion ?: remember(context) { context.isReducedMotion() }
    CompositionLocalProvider(
        LocalCytColors provides colors,
        LocalCytType provides type,
        LocalCytShapes provides shapes,
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(colorScheme = colors.toM3(), typography = type.toM3(), shapes = shapes.toM3()) {
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}

// Components read tokens through CytTheme.colors, CytTheme.type and so on.
object CytTheme {
    val colors: CytColors
        @Composable @ReadOnlyComposable get() = LocalCytColors.current
    val type: CytType
        @Composable @ReadOnlyComposable get() = LocalCytType.current
    val shapes: CytShapes
        @Composable @ReadOnlyComposable get() = LocalCytShapes.current
    val glass: GlassStyle
        @Composable @ReadOnlyComposable get() = LocalCytColors.current.glassStyle()
    val highlight: GlassStyle
        @Composable @ReadOnlyComposable get() = LocalCytColors.current.highlightStyle()
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}
