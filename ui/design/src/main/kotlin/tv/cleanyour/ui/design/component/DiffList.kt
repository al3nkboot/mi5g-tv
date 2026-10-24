package tv.cleanyour.ui.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytMotion
import tv.cleanyour.ui.design.theme.CytTheme

// DESIGN-SYSTEM 8.4, D2. Two densities: the summary card on screen 3 and the full list in the
// Customise sheet. The trailing control is the Off / Kept pill, never a switch.

@Immutable
data class SummaryGroup(val key: String, val name: String, val count: Int)

@Immutable
data class DiffItem(
    val key: String,          // the package name; lists are keyed by it
    val name: String,         // friendly name
    val description: String,  // one plain-words line
    val packageName: String?, // shown only at Custom level (never in Simple)
    val off: Boolean,
    // Part of another change, not a choice (the TV's own home screen inside the home screen
    // switch): the pill shows its state and the row has nothing to flip.
    val fixed: Boolean = false,
)

@Immutable
data class DiffGroup(val key: String, val title: String, val items: List<DiffItem>) {
    val offCount: Int get() = items.count { it.off }
}

// The "23" + "things to switch off" block. Counts up from 0 over at least 700 ms the first time
// (and when the count grows as probe groups arrive); static under reduced motion.
@Composable
fun NumeralHeadline(count: Int, headline: String, modifier: Modifier = Modifier, animate: Boolean = true) {
    val c = CytTheme.colors
    val reduced = CytTheme.reducedMotion
    val shown = remember { Animatable(if (animate && !reduced) 0f else count.toFloat()) }
    LaunchedEffect(count, reduced, animate) {
        if (!animate || reduced) shown.snapTo(count.toFloat())
        else shown.animateTo(count.toFloat(), tween(CytMotion.COUNT_UP_MIN_MS, easing = CytMotion.emphasizedDecelerate))
    }
    Row(
        modifier.semantics(mergeDescendants = true) { heading(); contentDescription = "$count $headline" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(shown.value.toInt().toString(), color = c.textPrimary, style = CytTheme.type.numeral)
        Text(headline, Modifier.weight(1f, fill = false).padding(bottom = 4.dp), color = c.textPrimary, style = CytTheme.type.headline)
    }
}

// Summary density: one raised card, one row per group with a right-aligned tabular count, then
// the accent "See the list" row.
@Composable
fun PlanSummaryCard(groups: List<SummaryGroup>, seeListLabel: String, onSeeList: () -> Unit, modifier: Modifier = Modifier) {
    ListGroup(modifier, tone = GroupTone.RAISED) {
        groups.forEachIndexed { i, g ->
            if (i > 0) ListDivider()
            ListRow(title = g.name, value = g.count.toString())
        }
        ListDivider()
        LinkRow(seeListLabel, onSeeList)
    }
}

// The Off / Kept pill: "Off" (surface.3, rim, minus glyph, text.primary) and "Kept" (accent 14%
// fill, accent 50% rim, check glyph, accent text). It states the outcome in a word. Visual only:
// the whole row is the touch target (DiffRow).
@Composable
fun OffKeptPill(off: Boolean, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.pill
    val fill = if (off) c.surface3 else c.accent.copy(alpha = 0.14f)
    val rim = if (off) c.rim else c.accent.copy(alpha = 0.5f)
    val fg = if (off) c.textPrimary else c.accent
    Row(
        modifier.heightIn(min = 32.dp).widthIn(min = 68.dp).background(fill, shape).border(1.dp, rim, shape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        CytIcon(if (off) CytIcons.Minus else CytIcons.Check, null, tint = fg, size = 14.dp)
        Text(stringResource(if (off) R.string.cyt_pill_off else R.string.cyt_pill_kept), color = fg, style = CytTheme.type.labelMedium)
    }
}

// One row of the full list: friendly name, the plain-words line, the package in grey when
// showPackageName (Custom level), and the pill. onToggle null = protected (no pill); a fixed item
// shows its pill and cannot be flipped.
@Composable
fun DiffRow(item: DiffItem, onToggle: ((DiffItem) -> Unit)?, modifier: Modifier = Modifier, showPackageName: Boolean = false) {
    val c = CytTheme.colors
    val offWord = stringResource(R.string.cyt_pill_off)
    val keptWord = stringResource(R.string.cyt_pill_kept)
    val actionLabel = stringResource(if (item.off) R.string.cyt_action_keep else R.string.cyt_action_switch_off)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(
                if (onToggle != null && !item.fixed) Modifier
                    .clickable(role = Role.Button, onClickLabel = actionLabel) { onToggle(item) }
                    .semantics(mergeDescendants = true) { stateDescription = if (item.off) offWord else keptWord }
                else if (item.fixed) Modifier.semantics(mergeDescendants = true) { stateDescription = if (item.off) offWord else keptWord }
                else Modifier.semantics(mergeDescendants = true) {},
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, color = c.textPrimary, style = CytTheme.type.titleMedium.copy(fontSize = 16.sp))
            Text(item.description, color = c.textTertiary, style = CytTheme.type.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (showPackageName && item.packageName != null) {
                // One line; a long package keeps its start and end ("com.mitv…home.atv").
                LtrText(item.packageName, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
        if (onToggle != null || item.fixed) OffKeptPill(item.off, Modifier.clearAndSetSemantics {})
    }
}

// The overline header of a group in the full list: "ADVERTS AND SPONSORED ROWS · 5 OF 6".
@Composable
fun DiffGroupHeader(title: String, offCount: Int, total: Int, modifier: Modifier = Modifier) {
    Overline(
        stringResource(R.string.cyt_group_count, title, offCount, total),
        modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp).semantics { heading() },
    )
}

// A group of the full list: its header and a card with the first visibleCount rows, then
// "Show 4 more" while rows are hidden. The first 8 rows fade in with a 24 ms stagger (160 ms
// each, ModulateAlpha so nothing renders offscreen); none under reduced motion.
// With onCollapse the header is a 48 dp button with a chevron that folds the group back into the
// collapsed card (device test 2026-10-20: an opened group had no way to close).
@Composable
fun DiffGroupSection(
    group: DiffGroup,
    onToggle: (DiffItem) -> Unit,
    modifier: Modifier = Modifier,
    showPackageNames: Boolean = false,
    visibleCount: Int = group.items.size,
    onShowMore: (() -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (onCollapse == null) {
            DiffGroupHeader(group.title, group.offCount, group.items.size)
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(onClickLabel = stringResource(R.string.cyt_close_group), role = Role.Button, onClick = onCollapse),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiffGroupHeader(group.title, group.offCount, group.items.size, Modifier.weight(1f).padding(top = 8.dp))
                CytIcon(CytIcons.ChevronUp, null, Modifier.padding(end = 4.dp, bottom = 8.dp), tint = CytTheme.colors.textTertiary, size = 18.dp)
            }
        }
        ListGroup {
            val shown = group.items.take(visibleCount.coerceAtLeast(0))
            shown.forEachIndexed { i, item ->
                if (i > 0) ListDivider()
                androidx.compose.runtime.key(item.key) {
                    DiffRow(item, onToggle, Modifier.listAppear(i), showPackageName = showPackageNames)
                }
            }
            val hidden = group.items.size - shown.size
            if (hidden > 0 && onShowMore != null) {
                ListDivider()
                LinkRow(pluralStringResource(R.plurals.cyt_show_more, hidden, hidden), onShowMore, chevron = false, minHeight = 48.dp)
            }
        }
    }
}

// Collapsed groups in one card: "Tracking and analytics   7 of 7  v". Tapping expands the group.
@Composable
fun CollapsedGroupsCard(groups: List<DiffGroup>, onExpand: (DiffGroup) -> Unit, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    ListGroup(modifier) {
        groups.forEachIndexed { i, g ->
            if (i > 0) ListDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onExpand(g) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(g.title, Modifier.weight(1f), color = c.textPrimary, style = CytTheme.type.bodyMedium)
                Text(stringResource(R.string.cyt_count_of, g.offCount, g.items.size), color = c.textSecondary, style = CytTheme.type.bodyMedium.copy(fontFeatureSettings = "tnum"))
                CytIcon(CytIcons.ChevronDown, null, tint = c.textTertiary, size = 18.dp)
            }
        }
    }
}

// The protected group: "We keep these" with a "Why?" link and rows without pills.
@Composable
fun ProtectedGroupSection(title: String, items: List<DiffItem>, onWhy: () -> Unit, modifier: Modifier = Modifier, showPackageNames: Boolean = false) {
    val c = CytTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Overline(title, Modifier.weight(1f).semantics { heading() })
            TertiaryButton(stringResource(R.string.cyt_why), onWhy)
        }
        ListGroup {
            items.forEachIndexed { i, item ->
                if (i > 0) ListDivider()
                DiffRow(item, null, showPackageName = showPackageNames)
            }
        }
    }
}

// The list-appear fade for the first 8 rows (DESIGN-SYSTEM 6). Rows past 8 appear at once.
@Composable
fun Modifier.listAppear(index: Int): Modifier {
    val reduced = CytTheme.reducedMotion
    if (reduced || index >= CytMotion.LIST_STAGGER_ROWS) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(CytMotion.LIST_FADE_MS, delayMillis = index * CytMotion.LIST_STAGGER_MS)) }
    return this.graphicsLayer { alpha = a.value; compositingStrategy = CompositingStrategy.ModulateAlpha }
}

