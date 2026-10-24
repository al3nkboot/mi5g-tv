package tv.cleanyour.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.ambientGlow

// Module accents (Retro #E8A33D) are used only for that module's glow and hero card: never for
// text, never for buttons (DESIGN-SYSTEM 2.1).

// M3a: a module in the list: surface.2 card with the module's accent glow in its corner, its
// launcher-icon tile (or letter tile), name, one line, and neutral fact chips ("Community").
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModuleCard(
    name: String,
    summary: String,
    chips: List<String>,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageBitmap? = null,
) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.surface2)
            .ambientGlow(accent, coreAlpha = c.glowCoreAlpha * 0.9f, midAlpha = c.glowMidAlpha)
            .border(1.dp, c.rim, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppTile(name, icon = icon, size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(name, color = c.textPrimary, style = CytTheme.type.titleMedium)
            Text(summary, Modifier.padding(top = 2.dp), color = c.textSecondary, style = CytTheme.type.bodyMedium)
            if (chips.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { InfoChip(it) }
                }
            }
        }
    }
}

// M3b: the module's hero card (mockup 07): 150 dp, shape.large, a gradient in the module's
// accent with faint scanlines and a glow, the name in light 28 sp, an overline tagline, and the
// "Its card on your TV" badge. art draws the module's own mark (top start). Decorative.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModuleHero(
    name: String,
    tagline: String,
    accent: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    contentDescription: String? = null,
    art: (@Composable BoxScope.() -> Unit)? = null,
) {
    val shape = CytTheme.shapes.large
    val dark = lerp(Color.Black, accent, 0.25f)
    val mid = lerp(Color.Black, accent, 0.46f)
    val light = lerp(Color.Black, accent, 0.60f)
    Box(
        modifier.fillMaxWidth().heightIn(min = 150.dp).clip(shape)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .drawWithCache {
                val base = Brush.linearGradient(listOf(dark, mid, light), start = Offset.Zero, end = Offset(size.width, size.height))
                val glow = Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0f)), center = Offset(size.width * 0.8f, size.height * 0.3f), radius = size.width * 0.6f)
                val lineColor = Color.Black.copy(alpha = 0.22f)
                val step = 3.dp.toPx()
                val line = 1.dp.toPx()
                onDrawBehind {
                    drawRect(base)
                    var y = 0f
                    while (y < size.height) { drawRect(lineColor, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, line)); y += step }
                    drawRect(glow)
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape),
    ) {
        if (art != null) Box(Modifier.padding(20.dp).align(Alignment.TopStart), content = art)
        // Name and tagline share the bottom line with the badge; when the two do not fit side by
        // side (200% font) the badge drops under them instead of overlapping, and the card grows.
        FlowRow(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.padding(end = 12.dp, bottom = 2.dp)) {
                Text(name, color = Color(0xFFF5F5F7), style = CytTheme.type.titleLarge.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light))
                Overline(tagline, Modifier.padding(top = 2.dp), color = Color.White.copy(alpha = 0.8f))
            }
            if (badge != null) {
                Text(
                    badge,
                    Modifier.background(Color.Black.copy(alpha = 0.45f), CytTheme.shapes.small).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Color(0xFFF5F5F7), style = CytTheme.type.bodySmall.copy(fontSize = 11.sp),
                )
            }
        }
    }
}

