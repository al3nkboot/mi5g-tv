package tv.cleanyour.ui.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytMotion
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.glass

// DESIGN-SYSTEM 5.1 "Glass highlight": one glass shape that slides between the options of a
// single-choice list on spring(0.58, 260), drawn behind the rows' content and only on the
// selected option, with the 3-ring outer glow. Under reduced motion it snaps.
// The unselected rows' plates (surface.1 with the outline) are drawn here too, in a layer under
// the glass, so the glass slides over every row on its way instead of passing behind the plate
// the row it left gets back (device test 2026-10-20). Rows themselves draw no background.
@Composable
fun HighlightColumn(
    count: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    gap: Dp = 10.dp,
    row: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    val reduced = CytTheme.reducedMotion
    val bounds = remember(count) { mutableStateListOf<Pair<Float, Float>?>().apply { repeat(count) { add(null) } } }
    val pos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val height = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = bounds.getOrNull(selectedIndex)
    LaunchedEffect(selectedIndex, target, reduced) {
        val t = target ?: return@LaunchedEffect
        if (!placed || reduced) {
            pos.snapTo(Offset(0f, t.first))
            height.snapTo(t.second)
            placed = true
        } else {
            coroutineScope {
                launch { pos.animateTo(Offset(0f, t.first), CytMotion.highlight) }
                launch { height.animateTo(t.second, CytMotion.highlightFloat) }
            }
        }
    }
    val density = LocalDensity.current
    val c = CytTheme.colors
    val shape = CytTheme.shapes.row
    Box(modifier.fillMaxWidth()) {
        for (i in 0 until count) key(i) {
            val plate by animateFloatAsState(if (i == selectedIndex) 0f else 1f, tween(if (reduced) 0 else 200), label = "plate")
            val b = bounds[i]
            if (b != null) {
                Box(
                    Modifier.offset { IntOffset(0, b.first.roundToInt()) }.fillMaxWidth()
                        .height(with(density) { b.second.toDp() })
                        .graphicsLayer { alpha = plate }
                        .background(c.surface1, shape).border(1.dp, c.outline, shape),
                )
            }
        }
        if (placed && selectedIndex in 0 until count) {
            Box(
                Modifier.offset { IntOffset(0, pos.value.y.roundToInt()) }.fillMaxWidth()
                    .height(with(density) { height.value.toDp() })
                    .glass(CytTheme.shapes.row, CytTheme.highlight),
            )
        }
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (i in 0 until count) {
                Box(Modifier.fillMaxWidth().onPlaced { bounds[i] = it.positionInParent().y to it.size.height.toFloat() }) {
                    row(i, i == selectedIndex)
                }
            }
        }
    }
}

@Immutable
data class ChoiceOption(val key: String, val title: String, val subtitle: String? = null, val icon: ImageVector? = CytIcons.Tv)

// Screen S1b "Which TV?": rows "Mi TV 4A / Living room · Android TV 9". Unselected rows sit on
// surface.1; the selected one is under the glass highlight with a check and text.primary.
@Composable
fun ChoiceList(options: List<ChoiceOption>, selectedKey: String?, onSelect: (ChoiceOption) -> Unit, modifier: Modifier = Modifier) {
    val selected = options.indexOfFirst { it.key == selectedKey }
    HighlightColumn(options.size, selected, modifier) { i, isSelected ->
        val o = options[i]
        ChoiceRow(o, isSelected) { onSelect(o) }
    }
}

@Composable
private fun ChoiceRow(option: ChoiceOption, selected: Boolean, onClick: () -> Unit) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.row
    val reduced = CytTheme.reducedMotion
    val titleColor by animateColorAsState(if (selected) c.textPrimary else c.textSecondary, tween(if (reduced) 0 else 200), label = "choiceTitle")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (option.icon != null) {
            IconTile(option.icon, tint = if (selected) c.textPrimary else c.textSecondary, fill = if (selected) c.textPrimary.copy(alpha = 0.10f) else c.surface3)
        }
        Column(Modifier.weight(1f)) {
            Text(option.title, color = titleColor, style = CytTheme.type.titleMedium)
            if (option.subtitle != null) Text(option.subtitle, color = if (selected) c.textSecondary else c.textTertiary, style = CytTheme.type.bodySmall)
        }
        if (selected) CytIcon(CytIcons.Check, null, tint = c.textPrimary)
    }
}

// A launcher theme: id, name, a one-line description and the colours its preview tile shows.
@Immutable
data class ThemeOption(val id: String, val name: String, val description: String, val swatch: List<Color>)

// The preview colours of the three launcher themes by id (background, glow, then dock tiles): a
// picture of the TV's theme (D-W7-5), not phone UI colours. Shared by S3a and M2 so the two
// pickers look the same (R4-W8). Unknown ids get the Default picture.
fun themeSwatch(id: String): List<Color> = when (id) {
    "aurora" -> listOf(Color(0xFF041018), Color(0xFF1FA597), Color(0xFF0B5E57), Color(0xFF1D4FBF))
    "six-columns" -> listOf(Color.Black, Color(0xFFB9C2CC), Color(0xFF3B3B40), Color(0xFF76767D))
    else -> listOf(Color.Black, Color(0xFF5B6CFF), Color(0xFF2A2A2E), Color(0xFF3A86E8))
}

// The theme picker (S3a "Look", M2): three rows with a small preview of the home screen in the
// theme's colours, the name and one line; the glass highlight springs to the chosen one.
// guardObscured = true where a tap applies the theme to the TV at once (M2); the hint after a
// dropped tap shows under the list (rejectObscuredTouches).
@Composable
fun ThemePicker(options: List<ThemeOption>, selectedId: String?, onSelect: (ThemeOption) -> Unit, modifier: Modifier = Modifier, guardObscured: Boolean = false) {
    val selected = options.indexOfFirst { it.id == selectedId }
    Guarded(modifier, guardObscured) { outer, guard ->
        HighlightColumn(options.size, selected, outer) { i, isSelected ->
            val o = options[i]
            val c = CytTheme.colors
            val shape = CytTheme.shapes.row
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape)
                    .selectable(isSelected, role = Role.RadioButton) { onSelect(o) }
                    .then(guard)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeSwatch(o.swatch)
                Column(Modifier.weight(1f)) {
                    Text(o.name, color = if (isSelected) c.textPrimary else c.textSecondary, style = CytTheme.type.titleMedium)
                    Text(o.description, color = if (isSelected) c.textSecondary else c.textTertiary, style = CytTheme.type.bodySmall)
                }
                if (isSelected) CytIcon(CytIcons.Check, null, tint = c.textPrimary)
            }
        }
    }
}

// A tiny home screen: the theme's background with a glow and a dock of four tiles.
@Composable
private fun ThemeSwatch(colors: List<Color>) {
    val bg = colors.getOrElse(0) { Color.Black }
    val glow = colors.getOrElse(1) { Color(0xFF5B6CFF) }
    val tiles = colors.drop(2).ifEmpty { listOf(Color(0xFF2A2A2E)) }
    Box(
        Modifier.size(64.dp, 40.dp).clip(CytTheme.shapes.small)
            .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), bg), center = Offset(18f, 14f), radius = 140f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CytTheme.shapes.small),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(4) { k -> Box(Modifier.size(11.dp, 8.dp).background(tiles[k % tiles.size], androidx.compose.foundation.shape.RoundedCornerShape(2.dp))) }
        }
    }
}
