package tv.cleanyour.ui.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme

// A list group card. LOW: surface.1 with a 12% rim (lists on bg, the sheet's groups). RAISED:
// surface.2 with the 20% rim (the summary card, the Done restart card).
enum class GroupTone { LOW, RAISED }

@Composable
fun ListGroup(modifier: Modifier = Modifier, tone: GroupTone = GroupTone.LOW, content: @Composable ColumnScope.() -> Unit) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    val (fill, rim) = if (tone == GroupTone.LOW) c.surface1 to c.outline else c.surface2 to c.rim
    Column(modifier.fillMaxWidth().clip(shape).background(fill, shape).border(1.dp, rim, shape), content = content)
}

// A row: min 56 dp, 12 x 16 padding, optional leading tile, title (title.medium), subtitle
// (body.small text.tertiary, or a tinted warning line), an end value and a chevron when it opens
// something. The whole row is the touch target.
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = CytTheme.colors.textTertiary,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null && trailing == null,
    titleColor: Color = CytTheme.colors.textPrimary,
    emphasised: Boolean = false,
) {
    val c = CytTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            val style = if (emphasised) CytTheme.type.titleMedium else CytTheme.type.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp)
            Text(title, color = titleColor, style = style)
            if (subtitle != null) Text(subtitle, color = subtitleColor, style = CytTheme.type.bodySmall)
        }
        if (value != null) Text(value, color = c.textPrimary, style = CytTheme.type.bodyMedium.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"))
        trailing?.invoke()
        if (chevron) CytIcon(CytIcons.ChevronForward, null, tint = c.textTertiary, size = 18.dp)
    }
}

// An accent link row inside a card: "See the list", "Show 4 more", "+ Set up another TV".
@Composable
fun LinkRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, chevron: Boolean = true, minHeight: Dp = 48.dp) {
    val c = CytTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), color = c.accent, style = CytTheme.type.bodyMedium.copy(fontWeight = FontWeight.Medium))
        if (chevron) CytIcon(CytIcons.ChevronForward, null, tint = c.accent, size = 18.dp)
    }
}

// The M3 switch in the brand: on = accent track with a dark thumb, off = surface.3 track with a
// text.tertiary outline and thumb (mockup 08). Non-text contrast: accent on surface.2 is 6.68:1.
@Composable
fun CytSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CytTheme.colors
    Switch(
        checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.bg, checkedTrackColor = c.accent, checkedBorderColor = c.accent,
            uncheckedThumbColor = c.textTertiary, uncheckedTrackColor = c.surface3, uncheckedBorderColor = c.textTertiary,
        ),
    )
}

// "Keep debugging on" with its risk line in warning: the whole row toggles and carries the on or
// off state for TalkBack (the switch inside is only the picture of it).
@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, warning: Boolean = false, enabled: Boolean = true) {
    val c = CytTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, style = CytTheme.type.bodyLarge)
            if (subtitle != null) Text(subtitle, color = if (warning) c.warning else c.textTertiary, style = CytTheme.type.bodySmall)
        }
        CytSwitch(checked, null, Modifier.clearAndSetSemantics {}, enabled = enabled)
    }
}

// A 40 dp tile with a 20 dp icon (surface.3, shape.small) for the start of a row.
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = CytTheme.colors.textPrimary, fill: Color = CytTheme.colors.surface3) {
    Box(modifier.size(40.dp).background(fill, CytTheme.shapes.small), contentAlignment = Alignment.Center) {
        CytIcon(icon, null, tint = tint, size = 22.dp)
    }
}

// An app shown in a list: its own launcher icon read from the TV, or a neutral letter tile.
// Never another brand's logo drawn by us (DESIGN-SYSTEM 7).
@Composable
fun AppTile(name: String, modifier: Modifier = Modifier, icon: ImageBitmap? = null, size: Dp = 40.dp) {
    val c = CytTheme.colors
    Box(modifier.size(size).clip(CytTheme.shapes.small).background(c.surface3).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            val letter = name.trim().firstOrNull()?.toString()?.toUpperCase(Locale.current) ?: "?"
            Text(letter, color = c.textSecondary, style = CytTheme.type.titleMedium)
        }
    }
}

// A dock editor row (S3b): the app tile, the name and the controls at the end (move up, move down,
// take out; or the plus of "Other apps on the TV"). The controls sit beside the name while the
// name's longest word fits next to them; otherwise they drop under the name (R4-W8), so an app
// name never breaks mid-word ("YouTu / be") at 200% font and "Prime Video" keeps one line at 100%.
// The whole row is the touch target when onClick is given (the plus rows); the controls carry
// their own labels.
@Composable
fun DockRow(
    name: String,
    modifier: Modifier = Modifier,
    tile: @Composable () -> Unit = { AppTile(name) },
    onClick: (() -> Unit)? = null,
    controls: (@Composable () -> Unit)? = null,
) {
    val c = CytTheme.colors
    val style = CytTheme.type.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp)
    Layout(
        contents = listOf(tile, { Text(name, color = c.textPrimary, style = style) }, { if (controls != null) controls() }),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { (tileM, nameM, controlsM), constraints ->
        val gap = 14.dp.roundToPx()
        val stackGap = 4.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tile = tileM.first().measure(loose)
        val ctrl = controlsM.firstOrNull()?.measure(loose)?.takeIf { it.width > 0 }
        val afterTile = (constraints.maxWidth - tile.width - gap).coerceAtLeast(0)
        val nameM1 = nameM.first()
        val beside = ctrl == null || nameM1.minIntrinsicWidth(constraints.maxHeight) + gap + ctrl.width <= afterTile
        val nameWidth = if (beside && ctrl != null) afterTile - ctrl.width - gap else afterTile
        val name = nameM1.measure(Constraints(maxWidth = nameWidth.coerceAtLeast(0)))
        val firstLine = maxOf(tile.height, name.height, if (beside) ctrl?.height ?: 0 else 0)
        val height = if (beside || ctrl == null) firstLine else firstLine + stackGap + ctrl.height
        layout(constraints.maxWidth, maxOf(height, constraints.minHeight)) {
            val top = (maxOf(height, constraints.minHeight) - height) / 2
            tile.placeRelative(0, top + (firstLine - tile.height) / 2)
            name.placeRelative(tile.width + gap, top + (firstLine - name.height) / 2)
            if (ctrl != null) {
                if (beside) ctrl.placeRelative(constraints.maxWidth - ctrl.width, top + (firstLine - ctrl.height) / 2)
                else ctrl.placeRelative(constraints.maxWidth - ctrl.width, top + firstLine + stackGap)
            }
        }
    }
}

// The safety line with the shield: "A restore point is saved first, so everything can be put back."
@Composable
fun SafetyLine(text: String, modifier: Modifier = Modifier, icon: ImageVector = CytIcons.Shield) {
    val c = CytTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        CytIcon(icon, null, tint = c.textSecondary)
        Text(text, Modifier.weight(1f), color = c.textSecondary, style = CytTheme.type.bodyMedium)
    }
}

// D4: one compact text row with a trailing accent action: "Home screen: Netflix, YouTube, ...  Change".
@Composable
fun TextActionRow(text: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = c.textSecondary, style = CytTheme.type.bodyMedium)
        TertiaryButton(actionLabel, onAction)
    }
}

// A checklist line. DONE: success check. NONE: hollow circle in text.tertiary (a capability the
// module does not have). PENDING: a spinner. The text carries the meaning.
enum class CheckState { DONE, NONE, PENDING }

@Composable
fun CheckLine(text: String, modifier: Modifier = Modifier, state: CheckState = CheckState.DONE, emphasised: Boolean = false) {
    val c = CytTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (state) {
                CheckState.DONE -> CytIcon(CytIcons.Check, null, tint = if (emphasised) c.textPrimary else c.success)
                CheckState.NONE -> CytIcon(CytIcons.Circle, null, tint = c.textTertiary)
                CheckState.PENDING -> Spinner()
            }
        }
        Text(text, Modifier.weight(1f), color = if (emphasised) c.textPrimary else c.textSecondary, style = CytTheme.type.bodyMedium)
    }
}

// A neutral fact chip ("Community", "Open source", "4 MB", the level chip "Simple"): 28 dp,
// surface.2 with rim, text.secondary. Never colour alone: "Verified" would differ by icon and word.
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.pill
    Row(
        modifier.heightIn(min = 28.dp).background(c.surface2, shape).border(1.dp, c.rim, shape).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) CytIcon(icon, null, tint = c.textSecondary, size = 14.dp)
        Text(text, color = c.textSecondary, style = CytTheme.type.bodySmall.copy(fontWeight = FontWeight.Medium))
    }
}
