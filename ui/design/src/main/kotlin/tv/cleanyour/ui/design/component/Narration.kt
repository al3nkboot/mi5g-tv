package tv.cleanyour.ui.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytMotion
import tv.cleanyour.ui.design.theme.CytTheme

// DESIGN-SYSTEM 8.7. Leading state icon: spinner (doing), check (done, success), small hollow dot
// (upcoming, text.tertiary), dash (skipped). No animated ellipsis.
enum class NarrationState { DOING, DONE, UPCOMING, SKIPPED }

@Immutable
data class NarrationLine(val key: String, val text: String, val state: NarrationState)

// The 4 dp accent progress bar on a surface.3 track.
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    val reduced = CytTheme.reducedMotion
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (reduced) 0 else 300), label = "progress")
    Box(
        modifier.fillMaxWidth().height(4.dp).clip(CytTheme.shapes.pill).background(c.surface3)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(p).height(4.dp).clip(CytTheme.shapes.pill).background(c.accent))
    }
}

// Title with the percentage, the bar, then up to 6 lines; older finished lines collapse into
// "3 earlier steps". Under it the footnote and the quiet "Stop and undo". Announced politely.
@Composable
fun ProgressNarration(
    title: String,
    percent: Int,
    lines: List<NarrationLine>,
    modifier: Modifier = Modifier,
    footnote: String? = null,
    stopLabel: String? = null,
    onStop: () -> Unit = {},
    maxVisible: Int = 6,
) {
    val c = CytTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenTitle(title, Modifier.weight(1f))
            Text(
                stringResource(R.string.cyt_percent, percent.coerceIn(0, 100)),
                Modifier.padding(bottom = 6.dp), color = c.textSecondary,
                style = CytTheme.type.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
        ProgressBar(percent / 100f, Modifier.padding(top = 12.dp, bottom = 18.dp))
        val overflow = (lines.size - maxVisible).coerceAtLeast(0)
        val collapsed = lines.take(overflow).takeWhile { it.state == NarrationState.DONE || it.state == NarrationState.SKIPPED }.size
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (collapsed > 0) {
                NarrationRow(NarrationLine("earlier", pluralStringResource(R.plurals.cyt_earlier_steps, collapsed, collapsed), NarrationState.DONE), animate = false)
            }
            for (line in lines.drop(collapsed)) key(line.key) { NarrationRow(line, animate = true) }
        }
        if (footnote != null) Text(footnote, Modifier.padding(top = 20.dp), color = c.textTertiary, style = CytTheme.type.bodySmall)
        if (stopLabel != null) TertiaryButton(stopLabel, onStop, Modifier.padding(top = 4.dp).offset(x = (-16).dp), quiet = true)
    }
}

// One narration line: fade and 8 dp up in 220 ms when it first appears (ModulateAlpha, so no
// offscreen layer); static under reduced motion.
@Composable
fun NarrationRow(line: NarrationLine, modifier: Modifier = Modifier, animate: Boolean = true) {
    val c = CytTheme.colors
    val reduced = CytTheme.reducedMotion
    val appear = remember { Animatable(if (animate && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(CytMotion.NARRATION_MS, easing = CytMotion.emphasizedDecelerate)) }
    val dy = with(LocalDensity.current) { 8.dp.toPx() }
    val stateWord = stringResource(
        when (line.state) {
            NarrationState.DOING -> R.string.cyt_state_doing
            NarrationState.DONE -> R.string.cyt_state_done
            NarrationState.UPCOMING -> R.string.cyt_state_upcoming
            NarrationState.SKIPPED -> R.string.cyt_state_skipped
        },
    )
    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * dy; compositingStrategy = CompositingStrategy.ModulateAlpha }
            .semantics(mergeDescendants = true) { stateDescription = stateWord },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(20.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            when (line.state) {
                NarrationState.DOING -> Spinner()
                NarrationState.DONE -> CytIcon(CytIcons.Check, null, tint = c.success)
                NarrationState.SKIPPED -> CytIcon(CytIcons.Dash, null, tint = c.textTertiary)
                NarrationState.UPCOMING -> Canvas(Modifier.size(6.dp)) {
                    drawCircle(c.surface3)
                    drawCircle(c.textTertiary, size.minDimension / 2 - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                }
            }
        }
        val color = if (line.state == NarrationState.UPCOMING || line.state == NarrationState.SKIPPED) c.textTertiary else c.textPrimary
        Text(line.text, Modifier.weight(1f), color = color, style = CytTheme.type.bodyMedium)
    }
}

