package tv.cleanyour.ui.design.component

import android.os.Build
import android.view.MotionEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.theme.CytMotion
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.glass
import tv.cleanyour.ui.design.theme.pressScale

// DESIGN-SYSTEM 8.1. All pills are full width inside the gutter unless the caller says otherwise,
// 56 dp tall at least, and grow in height at 200% font (labels wrap to two lines at most).

// Obscured-touch guard (SECURITY 4.3 and P8, D35, spec 7.3 item 6): a touch that reaches the window
// through another app's overlay is dropped, the Compose form of filterTouchesWhenObscured. The rule
// (R4-W8): every control whose tap starts a write to the TV is guarded, whatever it looks like.
// That is the primary pill, the destructive pill, the small card action, the CountdownConfirm
// "Yes", and, where the caller says so, a secondary or tertiary button ("Undo what we did"), a
// banner action ("Restore mine") and the theme picker on Manage. Never on Back, Cancel, close or
// navigation (R5-W8 O1): with a screen dimmer running the person must always have a way out, and
// the hint under a guarded control says why nothing happened. Place it after `clickable`, so it
// sees the events first and the click never fires. onRejected runs once per dropped tap (on its
// down event).
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.rejectObscuredTouches(onRejected: () -> Unit = {}): Modifier = pointerInteropFilter { e ->
    val obscured = isObscured(e)
    if (obscured && e.actionMasked == MotionEvent.ACTION_DOWN) onRejected()
    obscured   // true consumes (drops) the event, false lets the button see it
}

internal fun isObscured(e: MotionEvent): Boolean =
    (e.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0 ||
        (Build.VERSION.SDK_INT >= 29 && (e.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0)

// How long the hint stays under a guarded button after a dropped tap.
internal const val OBSCURED_HINT_MS = 6_000L

// A guarded action plus its hint: after a dropped tap, one calm line under the button says why
// nothing happened (polite live region, so TalkBack reads it once). The caller's modifier sits on
// the outer column, so ColumnScope and RowScope modifiers from the caller still apply.
@Composable
internal fun Guarded(
    modifier: Modifier,
    guard: Boolean,
    content: @Composable (outer: Modifier, guard: Modifier) -> Unit,
) {
    if (!guard) {
        content(modifier, Modifier)
        return
    }
    var drops by remember { mutableIntStateOf(0) }
    var showing by remember { mutableStateOf(false) }
    // Each dropped tap restarts the timer.
    LaunchedEffect(drops) {
        if (drops == 0) return@LaunchedEffect
        delay(OBSCURED_HINT_MS)
        showing = false
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        content(Modifier, Modifier.rejectObscuredTouches { drops++; showing = true })
        if (showing) {
            Text(
                stringResource(R.string.cyt_obscured_hint),
                Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = CytTheme.colors.textSecondary, style = CytTheme.type.bodySmall, textAlign = TextAlign.Center,
            )
        }
    }
}

// Primary: the white pill (black in light). Loading: a 20 dp ring plus the verb (pass the verb as
// text), width unchanged. Disabled: surface.3 with text.tertiary. guardObscured: drops touches
// through an overlay (see rejectObscuredTouches); pass false only for a primary that is Back, OK to
// a plain message, or navigation.
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    guardObscured: Boolean = true,
) = Guarded(modifier, guardObscured) { outer, guard -> PrimaryPill(text, onClick, outer, guard, enabled, loading) }

@Composable
private fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier, guard: Modifier, enabled: Boolean, loading: Boolean) {
    val c = CytTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = when {
        !enabled -> c.surface3
        pressed -> c.primaryPressed
        else -> c.primary
    }
    val content = if (enabled) c.onPrimary else c.textTertiary
    PillBase(
        modifier = modifier.pressScale(source).clip(CytTheme.shapes.pill).background(fill, CytTheme.shapes.pill),
        onClick = onClick, enabled = enabled && !loading, source = source,
        stateDescription = null, guard = guard,
    ) {
        if (loading) {
            Spinner(size = 20.dp, color = content)
            Box(Modifier.padding(start = 10.dp))
        }
        PillLabel(text, content)
    }
}

// Secondary: a glass pill, same height, text.primary ("No, put it back", "I don't see it on the TV").
// guardObscured = true when the secondary writes to the TV ("Undo what we did" on a stop card).
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, guardObscured: Boolean = false) =
    Guarded(modifier, guardObscured) { outer, guard ->
        val c = CytTheme.colors
        val source = remember { MutableInteractionSource() }
        PillBase(
            modifier = outer.pressScale(source).glass(CytTheme.shapes.pill, CytTheme.glass).clip(CytTheme.shapes.pill),
            onClick = onClick, enabled = enabled, source = source, stateDescription = null, guard = guard,
        ) { PillLabel(text, if (enabled) c.textPrimary else c.textTertiary) }
    }

// Tertiary: a 48 dp text button in accent ("Customise", "I've set up a TV before"). Quiet: in
// text.secondary ("Stop and undo": always present, never hidden, never red). guardObscured = true
// when the tertiary writes to the TV ("Undo what we did" as the third button of a stop card).
@Composable
fun TertiaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, quiet: Boolean = false, guardObscured: Boolean = false) =
    Guarded(modifier, guardObscured) { outer, guard ->
        val c = CytTheme.colors
        val color = when {
            !enabled -> c.textTertiary
            quiet -> c.textSecondary
            else -> c.accent
        }
        val source = remember { MutableInteractionSource() }
        Box(
            outer
                .heightIn(min = 48.dp)
                .clip(CytTheme.shapes.pill)
                .clickable(source, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
                .then(guard)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = color, style = CytTheme.type.labelLarge, textAlign = TextAlign.Center)
        }
    }

// The one destructive action in the app, "Put everything back", inside its confirmation sheet only:
// an outlined pill, 1.5 dp danger stroke, danger label, no fill. Never animated into place.
@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    Guarded(modifier, guard = true) { outer, guard ->
        val c = CytTheme.colors
        val source = remember { MutableInteractionSource() }
        PillBase(
            modifier = outer.clip(CytTheme.shapes.pill).border(1.5.dp, if (enabled) c.danger else c.textTertiary, CytTheme.shapes.pill),
            onClick = onClick, enabled = enabled, source = source, stateDescription = null, guard = guard,
        ) { PillLabel(text, if (enabled) c.danger else c.textTertiary) }
    }

// A compact glass pill for an action inside a card ("Restart"): 36 dp visible, 48 dp touch target.
// It acts on the TV, so it is guarded like the primary pill.
@Composable
fun SmallGlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, guardObscured: Boolean = true) =
    Guarded(modifier, guardObscured) { outer, guard -> SmallGlassPill(text, onClick, outer, guard, enabled) }

@Composable
private fun SmallGlassPill(text: String, onClick: () -> Unit, modifier: Modifier, guard: Modifier, enabled: Boolean) {
    val c = CytTheme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .pressScale(source)
            .heightIn(min = 36.dp)
            .glass(CytTheme.shapes.pill, CytTheme.glass)
            .clip(CytTheme.shapes.pill)
            .clickable(source, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .then(guard)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) c.textPrimary else c.textTertiary, style = CytTheme.type.labelMedium.copy(fontSize = CytTheme.type.bodyMedium.fontSize))
    }
}

// "Make it clean" morphs into the progress bar (DESIGN-SYSTEM 6, 400 ms emphasized): while
// progress is null this is the primary pill; once progress is set it collapses to the 4 dp accent
// bar at the same place. Under reduced motion the swap is immediate.
@Composable
fun MorphingPrimaryButton(text: String, onClick: () -> Unit, progress: Float?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = CytTheme.colors
    val reduced = CytTheme.reducedMotion
    val morphing = progress != null
    val spec = tween<androidx.compose.ui.unit.Dp>(if (reduced) 0 else CytMotion.MORPH_MS, easing = CytMotion.emphasizedDecelerate)
    val height by animateDpAsState(if (morphing) 4.dp else 56.dp, spec, label = "morphHeight")
    val labelAlpha by animateFloatAsState(if (morphing) 0f else 1f, tween(if (reduced) 0 else CytMotion.MORPH_MS / 2), label = "morphLabel")
    if (!morphing && height >= 55.dp) {
        PrimaryButton(text, onClick, modifier, enabled)
        return
    }
    val p = (progress ?: 0f).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(height).clip(CytTheme.shapes.pill)
                .background(if (morphing && height < 6.dp) c.surface3 else c.primary),
        ) {
            if (morphing && height < 6.dp) {
                Box(Modifier.fillMaxWidth(p).height(height).clip(CytTheme.shapes.pill).background(c.accent))
            }
        }
        if (labelAlpha > 0f) {
            Text(
                text, color = c.onPrimary, style = CytTheme.type.labelLarge,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha; compositingStrategy = CompositingStrategy.ModulateAlpha },
            )
        }
    }
}

// The bottom action column: primary on top, secondary or tertiary 8 dp below (DESIGN-SYSTEM 4).
@Composable
fun ActionColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}

@Composable
private fun PillBase(
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean,
    source: MutableInteractionSource,
    stateDescription: String?,
    guard: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(source, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .then(guard)
            .then(if (stateDescription != null) Modifier.semantics { this.stateDescription = stateDescription } else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun PillLabel(text: String, color: Color) {
    Text(text, color = color, style = CytTheme.type.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
}
