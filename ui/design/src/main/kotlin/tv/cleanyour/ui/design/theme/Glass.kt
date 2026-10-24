package tv.cleanyour.ui.design.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

// DESIGN-SYSTEM 5.1. Glass is three draws in one drawBehind: fill gradient, specular over the top
// 40%, 1 dp rim. No offscreen layer, no graphicsLayer alpha, no clip: the specular is a gradient
// that ends transparent at 40% of the height (TileMode.Clamp keeps it transparent below), and the
// rim is the same shape inset by half the stroke. Brushes and outlines are built once per size.
@Immutable
data class GlassStyle(
    val fillTop: Color,
    val fillBottom: Color,
    val specular: Color,
    val rim: Color,
    val rimWidth: Dp = 1.dp,
    val specularFraction: Float = 0.4f,
    // The highlight's 3-ring outer glow (2, 4 and 6 dp at 12%, 6% and 4%); null on plain glass.
    val ring: Color? = null,
)

fun CytColors.glassStyle(): GlassStyle = GlassStyle(glassFillTop, glassFillBottom, glassSpecular, rim)

fun CytColors.highlightStyle(): GlassStyle = GlassStyle(
    fillTop = highlightFillTop, fillBottom = highlightFillBottom, specular = highlightSpecular,
    rim = highlightRim, rimWidth = 1.5.dp, specularFraction = 0.45f, ring = highlightRing,
)

internal class GlassGeometry(
    val outline: Outline,
    val fill: Brush,
    val specular: Brush,
    val rimOutline: Outline,
    val rimInset: Float,
    val rimStroke: Stroke,
    val rim: Color,
    val rings: List<Ring>,
) {
    class Ring(val outline: Outline, val outset: Float, val stroke: Stroke, val color: Color)
}

private val RingAlphas = floatArrayOf(0.20f, 0.10f, 0.04f) // composite of the 12/6/4% stack, inner to outer

internal fun buildGlass(size: Size, layoutDirection: LayoutDirection, density: Density, shape: Shape, style: GlassStyle): GlassGeometry {
    val outline = shape.createOutline(size, layoutDirection, density)
    val sw = with(density) { style.rimWidth.toPx() }
    val rimOutline = shape.createOutline(Size((size.width - sw).coerceAtLeast(0f), (size.height - sw).coerceAtLeast(0f)), layoutDirection, density)
    val fill = Brush.verticalGradient(listOf(style.fillTop, style.fillBottom), startY = 0f, endY = size.height)
    val specular = Brush.verticalGradient(
        listOf(style.specular, style.specular.copy(alpha = 0f)), startY = 0f, endY = size.height * style.specularFraction,
    )
    val rings = style.ring?.let { c ->
        val band = with(density) { 2.dp.toPx() }
        (1..3).map { k ->
            val outset = band * (2 * k - 1) / 2f
            GlassGeometry.Ring(
                outline = shape.createOutline(Size(size.width + 2 * outset, size.height + 2 * outset), layoutDirection, density),
                outset = outset, stroke = Stroke(band), color = c.copy(alpha = RingAlphas[k - 1]),
            )
        }
    }.orEmpty()
    return GlassGeometry(outline, fill, specular, rimOutline, sw / 2f, Stroke(sw), style.rim, rings)
}

internal fun DrawScope.drawGlass(g: GlassGeometry) {
    for (r in g.rings) translate(-r.outset, -r.outset) { drawOutline(r.outline, r.color, style = r.stroke) }
    drawOutline(g.outline, g.fill)
    drawOutline(g.outline, g.specular)
    translate(g.rimInset, g.rimInset) { drawOutline(g.rimOutline, g.rim, style = g.rimStroke) }
}

fun Modifier.glass(shape: Shape, style: GlassStyle): Modifier = drawWithCache {
    val g = buildGlass(size, layoutDirection, this, shape, style)
    onDrawBehind { drawGlass(g) }
}

// DESIGN-SYSTEM 5.2 as drawn in the approved mockups: one radial gradient on the window
// background, centre (32% x, 26% y, capped for tall content), core alpha 24% (light 14%), 10% at
// about half the radius, transparent at the edge. `alpha` is read in the draw lambda, so the
// colour dip animates without recomposition or layout.
fun Modifier.ambientGlow(
    color: Color,
    core: Color = color,
    coreAlpha: Float = 0.24f,
    midAlpha: Float = 0.10f,
    alpha: () -> Float = { 1f },
): Modifier = drawWithCache {
    val w = size.width
    val center = Offset(w * 0.32f, min(size.height * 0.26f, w * 0.56f))
    val brush = Brush.radialGradient(
        0f to core.copy(alpha = coreAlpha),
        0.52f to color.copy(alpha = midAlpha),
        1f to color.copy(alpha = 0f),
        center = center, radius = w * 1.08f,
    )
    onDrawBehind {
        val a = alpha()
        if (a > 0f) drawRect(brush, alpha = a)
    }
}

// The screen's ambient glow, by context (DESIGN-SYSTEM 5.2): brand on welcome, TV while working
// with the TV, the module accent on a module screen, Done (D31) the brand glow with a success core.
// Failures keep the glow they had: there is no red glow.
@Immutable
sealed interface Glow {
    data object Brand : Glow
    data object Tv : Glow
    data object Done : Glow
    data object None : Glow
    data class Module(val accent: Color) : Glow
}

internal fun Glow.colors(c: CytColors): Pair<Color, Color>? = when (this) {
    Glow.Brand -> c.glowBrand to c.glowBrand
    Glow.Tv -> c.glowTv to c.glowTv
    Glow.Done -> c.glowBrand to c.success
    Glow.None -> null
    is Glow.Module -> accent to accent
}

// The glow with the launcher's dip on a context change: alpha to 35% in 200 ms, swap, back to
// 100% in 500 ms. Static otherwise. Under reduced motion the colour swaps at once.
@Composable
fun Modifier.animatedAmbientGlow(glow: Glow): Modifier {
    val c = CytTheme.colors
    val reduced = LocalReducedMotion.current
    var shown by remember { mutableStateOf(glow) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(glow, reduced) {
        if (glow == shown) return@LaunchedEffect
        if (reduced) {
            shown = glow
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(CytMotion.GLOW_DIP_ALPHA, tween(CytMotion.GLOW_DIP_MS))
            shown = glow
            alpha.animateTo(1f, tween(CytMotion.GLOW_RISE_MS))
        }
    }
    val pair = shown.colors(c) ?: return this
    val core = if (shown == Glow.Done) c.glowCoreAlpha * 0.75f else c.glowCoreAlpha
    val mid = if (shown == Glow.Done) c.glowMidAlpha * 1.2f else c.glowMidAlpha
    return this.ambientGlow(pair.first, pair.second, core, mid) { alpha.value }
}
