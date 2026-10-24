package tv.cleanyour.ui.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.theme.CytMotion
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.glass

// D42 and DESIGN-SYSTEM 8.3. The state word is always present: the dot is never the only cue.
enum class TvStatus { CONNECTED, WAITING, WORKING, DEBUGGING_OFF, OFFLINE }

@Composable
fun TvStatus.word(): String = stringResource(
    when (this) {
        TvStatus.CONNECTED -> R.string.cyt_status_connected
        TvStatus.WAITING -> R.string.cyt_status_waiting
        TvStatus.WORKING -> R.string.cyt_status_working
        TvStatus.DEBUGGING_OFF -> R.string.cyt_status_debugging_off
        TvStatus.OFFLINE -> R.string.cyt_status_offline
    },
)

// The leading mark of a glass chip.
enum class ChipMark { DOT_SUCCESS, DOT_WARNING, DOT_HOLLOW, SPINNER, NONE }

// "Mi TV 4A · Connected": a 32 dp glass pill, centred in the top bar once a TV is chosen. Waiting
// pulses 3 times (1.2 s each) and then holds still. Tap opens the TV details sheet.
@Composable
fun TvStatusChip(
    tvName: String,
    status: TvStatus,
    modifier: Modifier = Modifier,
    stateWord: String = status.word(),
    onClick: (() -> Unit)? = null,
) {
    val mark = when (status) {
        TvStatus.CONNECTED -> ChipMark.DOT_SUCCESS
        TvStatus.WAITING -> ChipMark.DOT_WARNING
        TvStatus.WORKING -> ChipMark.SPINNER
        TvStatus.DEBUGGING_OFF, TvStatus.OFFLINE -> ChipMark.DOT_HOLLOW
    }
    GlassChip(
        text = tvName, mark = mark, modifier = modifier, secondary = stateWord, separator = true, onClick = onClick,
        pulse = status == TvStatus.WAITING, singleLine = true,
    )
}

// The live discovery line on Welcome: "Looking for TVs on your Wi-Fi..." with a spinner, then
// "Found Mi TV 4A (Living room)" with the success dot. A polite live region.
@Composable
fun DiscoveryChip(text: String, found: Boolean, modifier: Modifier = Modifier, secondary: String? = null) {
    GlassChip(
        text = text, mark = if (found) ChipMark.DOT_SUCCESS else ChipMark.SPINNER, modifier = modifier,
        secondary = secondary, separator = false, liveRegion = true,
    )
}

// The general glass chip (32 dp, label.medium): a leading mark or icon, the text, an optional
// secondary word in text.secondary. singleLine (the top bar chip): the text shortens with an
// ellipsis when the width runs out at large font sizes and the secondary word is never cut, since
// the state word is the information; otherwise the chip wraps and grows.
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    mark: ChipMark = ChipMark.NONE,
    icon: ImageVector? = null,
    secondary: String? = null,
    separator: Boolean = false,
    onClick: (() -> Unit)? = null,
    liveRegion: Boolean = false,
    pulse: Boolean = false,
    singleLine: Boolean = false,
) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.pill
    Row(
        modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .heightIn(min = 32.dp)
            .glass(shape, CytTheme.glass)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (liveRegion) Modifier.semantics(mergeDescendants = true) { this.liveRegion = LiveRegionMode.Polite } else Modifier.semantics(mergeDescendants = true) {})
            .padding(start = if (mark != ChipMark.NONE || icon != null) 12.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) CytIcon(icon, null, size = 16.dp)
        ChipMarkView(mark, pulse)
        val style = CytTheme.type.labelMedium
        if (singleLine) {
            Text(text, Modifier.weight(1f, fill = false), color = c.textPrimary, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(text, color = c.textPrimary, style = style)
        }
        if (secondary != null) {
            if (separator) Text("·", color = c.textTertiary, style = style)
            Text(secondary, color = c.textSecondary, style = style, maxLines = if (singleLine) 1 else Int.MAX_VALUE, softWrap = !singleLine)
        }
    }
}

@Composable
private fun ChipMarkView(mark: ChipMark, pulse: Boolean) {
    val c = CytTheme.colors
    when (mark) {
        ChipMark.NONE -> Unit
        ChipMark.SPINNER -> Spinner(size = 12.dp, strokeWidth = 1.5.dp)
        else -> {
            val color = when (mark) {
                ChipMark.DOT_SUCCESS -> c.success
                ChipMark.DOT_WARNING -> c.warning
                else -> c.textTertiary
            }
            val reduced = CytTheme.reducedMotion
            val halo = remember { Animatable(if (mark == ChipMark.DOT_WARNING) 1f else 0f) }
            LaunchedEffect(mark, pulse, reduced) {
                if (pulse && !reduced) {
                    repeat(CytMotion.WAITING_PULSE_CYCLES) {
                        halo.animateTo(0f, tween(CytMotion.WAITING_PULSE_MS / 2))
                        halo.animateTo(1f, tween(CytMotion.WAITING_PULSE_MS / 2))
                    }
                }
            }
            Canvas(Modifier.size(8.dp)) {
                val r = size.minDimension / 2
                if (mark == ChipMark.DOT_HOLLOW) {
                    drawCircle(color, r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                } else {
                    if (mark == ChipMark.DOT_WARNING) {
                        val h = halo.value
                        if (h > 0f) drawCircle(color.copy(alpha = 0.18f * h), r + 3.dp.toPx(), Offset(r, r))
                    }
                    drawCircle(color, r)
                }
            }
        }
    }
}

