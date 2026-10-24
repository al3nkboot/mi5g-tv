package tv.cleanyour.ui.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytMotion
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Outfit
import tv.cleanyour.ui.design.theme.ambientGlow
import tv.cleanyour.ui.design.theme.glass

// D5: the Welcome hero. The stock home screen with its advert rows fades into the clean launcher
// home screen once, over 3 s, and rests on the clean state. Reduced motion (or play = false)
// shows the rest state at once. Decorative: the title and sentence carry the meaning.
@Composable
fun WelcomeIllustration(modifier: Modifier = Modifier, play: Boolean = true, contentDescription: String? = null) {
    val reduced = CytTheme.reducedMotion
    val p = remember { Animatable(if (play && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(CytMotion.WELCOME_MS, easing = CytMotion.emphasizedDecelerate)) }
    TvFrame(modifier, contentDescription) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val u = maxWidth / 300f
            if (p.value < 1f) StockHome(u, Modifier.fade { 1f - p.value })
            CleanHome(u, Modifier.fade { p.value })
        }
    }
}

private fun Modifier.fade(alpha: () -> Float) = graphicsLayer { this.alpha = alpha(); compositingStrategy = CompositingStrategy.ModulateAlpha }

private fun tvType(size: Float, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
    TextStyle(fontFamily = Outfit, fontSize = size.sp, lineHeight = (size * 1.15f).sp, fontWeight = weight, letterSpacing = tracking.sp)

// The clean launcher home screen (mockup 01): a glow in the focused app's colour, greeting, clock,
// pills, hero name, dock. Everyday apps (YouTube focused, then Prime Video) so a first-time user
// sees their own TV, not a module (device test 2026-10-20).
@Composable
private fun CleanHome(u: Dp, modifier: Modifier) {
    Box(modifier.fillMaxSize().ambientGlow(Color(0xFFE8343D), coreAlpha = 0.30f, midAlpha = 0.09f)) {
        Text("GOOD AFTERNOON", Modifier.offset(u * 18, u * 14), color = Color.White.copy(alpha = 0.55f), style = tvType(5 * u.value, tracking = 1.2f * u.value))
        Text("2:17", Modifier.offset(u * 16, u * 20), color = Color(0xFFF5F5F7), style = tvType(22 * u.value, FontWeight.ExtraLight))
        Row(Modifier.align(Alignment.TopEnd).padding(top = u * 22, end = u * 14), horizontalArrangement = Arrangement.spacedBy(u * 5)) {
            repeat(3) { MiniPill(u) }
        }
        Text("YouTube", Modifier.offset(u * 18, u * 86), color = Color(0xFFF5F5F7), style = tvType(20 * u.value, FontWeight.Light))
        Dock(u, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MiniPill(u: Dp) {
    Box(Modifier.size(u * 30, u * 12).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(u * 6)).border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(u * 6)))
}

@Composable
private fun Dock(u: Dp, modifier: Modifier) {
    Row(
        modifier.padding(start = u * 12, end = u * 12, bottom = u * 12).fillMaxWidth().height(u * 52)
            .background(Color(0xFF111113), RoundedCornerShape(u * 12)).border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(u * 12))
            .padding(u * 6),
        horizontalArrangement = Arrangement.spacedBy(u * 7),
    ) {
        val tile = Modifier.weight(1f).fillMaxSize()
        Box(
            tile.border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(u * 8))
                .background(Brush.radialGradient(listOf(Color(0xFFE8343D), Color(0xFF8E1218))), RoundedCornerShape(u * 8)),
            contentAlignment = Alignment.Center,
        ) { Text("YouTube", color = Color.White, style = tvType(9 * u.value, FontWeight.Medium)) }
        Box(
            tile.background(Brush.linearGradient(listOf(Color(0xFF0F2A4A), Color(0xFF1A98FF))), RoundedCornerShape(u * 8)),
            contentAlignment = Alignment.Center,
        ) { Text("prime video", color = Color.White, style = tvType(7 * u.value, FontWeight.Medium)) }
        Box(tile.background(Brush.linearGradient(listOf(Color(0xFF3B3B40), Color(0xFF76767D))), RoundedCornerShape(u * 8)))
        Box(tile.background(Brush.linearGradient(listOf(Color(0xFF0B5E57), Color(0xFF1FA597))), RoundedCornerShape(u * 8)))
    }
}

// The stock home screen: a loud banner and rows of sponsored tiles (no real brands).
@Composable
private fun StockHome(u: Dp, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(Color(0xFF101014)).padding(u * 10), verticalArrangement = Arrangement.spacedBy(u * 6)) {
        Box(Modifier.fillMaxWidth().height(u * 58).clip(RoundedCornerShape(u * 6)).background(Brush.linearGradient(listOf(Color(0xFFE0562B), Color(0xFFB0247A)))))
        val rows = listOf(
            listOf(Color(0xFF2E6BD8), Color(0xFFD8A12E), Color(0xFF2EB87A), Color(0xFFD8452E), Color(0xFF8A2ED8)),
            listOf(Color(0xFFD82E8A), Color(0xFF2EC7D8), Color(0xFFD8D02E), Color(0xFF5A5A62), Color(0xFF2ED85E)),
        )
        for (r in rows) {
            Row(Modifier.fillMaxWidth().height(u * 36), horizontalArrangement = Arrangement.spacedBy(u * 5)) {
                r.forEach { Box(Modifier.weight(1f).fillMaxSize().background(it.copy(alpha = 0.85f), RoundedCornerShape(u * 4))) }
            }
        }
    }
}

// A text-free TV settings menu for the "Let the phone in" step cards: a list of rows with one
// under the glass highlight, an optional switch on it (on or off), and an optional count of taps
// shown as dots (the seven taps on Build). Decorative.
@Composable
fun TvMenuArt(highlight: Int, modifier: Modifier = Modifier, rows: Int = 5, toggleOn: Boolean? = null, taps: Int = 0) {
    TvFrame(modifier, showStand = false) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
            val u = maxWidth / 300f
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = u * 14).width(u * 150).fillMaxSize().padding(vertical = u * 14),
                verticalArrangement = Arrangement.spacedBy(u * 6),
            ) {
                Box(Modifier.width(u * 60).height(u * 6).background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(u * 3)))
                for (i in 0 until rows) {
                    val on = i == highlight
                    Row(
                        Modifier.fillMaxWidth().height(u * 20)
                            .then(if (on) Modifier.glass(RoundedCornerShape(u * 6), CytTheme.highlight) else Modifier)
                            .padding(horizontal = u * 8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f).height(u * 4).padding(end = u * (20 + i * 9 % 30)).background(Color.White.copy(alpha = if (on) 0.85f else 0.3f), RoundedCornerShape(u * 2)))
                        if (on && toggleOn != null) {
                            Box(
                                Modifier.size(u * 18, u * 10).background(if (toggleOn) Color(0xFF8C9BFF) else Color(0xFF2A2A2E), CircleShape),
                                contentAlignment = if (toggleOn) Alignment.CenterEnd else Alignment.CenterStart,
                            ) { Box(Modifier.padding(u * 2).size(u * 6).background(if (toggleOn) Color.Black else Color(0xFF98989E), CircleShape)) }
                        }
                        if (on && taps > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(u * 2)) { repeat(taps) { Box(Modifier.size(u * 3).background(Color(0xFF8C9BFF), CircleShape)) } }
                        }
                    }
                }
            }
        }
    }
}

// D31: the Done visual. A 56 dp glass circle with the highlight rim and rings, and the check.
@Composable
fun GlassCheckMark(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val c = CytTheme.colors
    Box(modifier.size(size).glass(CircleShape, CytTheme.highlight).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Icon(CytIcons.Check, null, Modifier.size(size / 2), tint = c.textPrimary)
    }
}

// The restart test wait (UX 6.2): a TV glyph whose glow breathes (4 s cycle, 60% to 100%), the one
// allowed breathing animation. It stops (breathing = false) the moment the TV answers.
@Composable
fun RestartGlyph(breathing: Boolean, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    val reduced = CytTheme.reducedMotion
    val alpha = if (breathing && !reduced) {
        val t = rememberInfiniteTransition(label = "breath")
        val a by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(CytMotion.RESTART_BREATH_MS / 2, easing = LinearEasing), RepeatMode.Reverse), label = "breathAlpha")
        a
    } else 1f
    Box(
        modifier.size(96.dp).ambientGlow(c.glowTv, coreAlpha = 0.5f, midAlpha = 0.18f) { alpha }.clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) { Icon(CytIcons.Tv, null, Modifier.size(40.dp), tint = c.textPrimary) }
}
