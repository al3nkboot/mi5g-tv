package tv.cleanyour.ui.design.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytMotion
import tv.cleanyour.ui.design.theme.CytTheme

// "1:24" from milliseconds left, rounded up so the ring never shows 0:00 before the end.
fun formatCountdown(remainingMs: Long): String {
    val s = ((remainingMs.coerceAtLeast(0) + 999) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

// The 64 dp ring (DESIGN-SYSTEM 8.6): track surface.3, progress accent, 5 dp, round caps, m:ss in
// the middle. It shows the time left, driven by real elapsed time from the machine (remainingMs),
// linear between updates. It never speeds up and never changes colour. stopped = "Yes" was tapped:
// a 300 ms settle to full. RTL runs the ring the other way. TalkBack hears it at 60, 30 and 10 s
// only, through a polite live region, never every second.
@Composable
fun CountdownRing(remainingMs: Long, totalMs: Long, modifier: Modifier = Modifier, stopped: Boolean = false, updateIntervalMs: Int = 1000) {
    val c = CytTheme.colors
    val target = if (stopped) 1f else (remainingMs.toFloat() / totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        target,
        if (stopped) tween(CytMotion.COUNTDOWN_SETTLE_MS, easing = CytMotion.emphasizedDecelerate) else tween(updateIntervalMs, easing = LinearEasing),
        label = "countdown",
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val seconds = ((remainingMs + 999) / 1000).toInt()
    // The spoken text changes only at the 60, 30 and 10 s marks, so the polite live region speaks
    // three times at most; above 60 s it names the whole window once.
    val spoken = when {
        seconds <= 10 -> stringResource(R.string.cyt_countdown_10)
        seconds <= 30 -> stringResource(R.string.cyt_countdown_30)
        seconds <= 60 -> stringResource(R.string.cyt_countdown_60)
        else -> stringResource(R.string.cyt_countdown_left, formatCountdown(totalMs))
    }
    val time = formatCountdown(remainingMs)
    // The ring grows with the font (up to 1.6x) so the time inside never touches the stroke.
    val ringSize = 64.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    Box(
        modifier.size(ringSize).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ringSize)) {
            val sw = 5.dp.toPx()
            val inset = sw / 2 + 1.5.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(c.surface3, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            val sweep = 360f * fraction
            if (sweep > 0f) {
                drawArc(c.accent, -90f, if (rtl) -sweep else sweep, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Text(time, Modifier.clearAndSetSemantics {}, color = c.textPrimary, style = CytTheme.type.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"))
    }
}

// The new home screen check (DESIGN-SYSTEM 8.6, screen S4b): overline, question, live TV
// thumbnail, the sentence naming what should be visible, the Home check card, the ring with the
// timeout sentence, then "Yes, I see it" and "No, put it back", which are the first two focus
// stops after the heading. Nothing moves except the ring, the time and the thumbnail crossfade.
// Put it below the top bar (with the status chip) in a ScreenScaffold without a bottomBar. The
// ring and its sentence stay pinned above the buttons with them (R4-W8): at 200% font the picture
// and the sentence scroll, the time left and what happens at zero never leave the screen.
@Composable
fun CountdownConfirm(
    overline: String,
    title: String,
    thumbnail: ImageBitmap?,
    thumbnailDescription: String,
    sentence: String,
    instruction: String,
    liveCheck: String,
    liveCheckPassed: Boolean,
    remainingMs: Long,
    totalMs: Long,
    timeoutSentence: String,
    yesLabel: String,
    noLabel: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
    stopped: Boolean = false,
    noThumbnailSentence: String? = null,
) {
    val c = CytTheme.colors
    Column(modifier.fillMaxSize().semantics { isTraversalGroup = true }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Overline(overline, Modifier.padding(top = 8.dp))
            ScreenTitle(title, Modifier.padding(top = 8.dp).semantics { traversalIndex = -3f })
            TvThumbnail(thumbnail, thumbnailDescription, Modifier.padding(top = 16.dp))
            Text(
                if (thumbnail == null && noThumbnailSentence != null) noThumbnailSentence else sentence,
                Modifier.padding(top = 16.dp), color = c.textSecondary, style = CytTheme.type.bodyMedium,
            )
            ListGroup(Modifier.padding(top = 14.dp)) {
                Text(instruction, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), color = c.textPrimary, style = CytTheme.type.bodyMedium)
                ListDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (liveCheckPassed) CytIcon(CytIcons.Check, null, tint = c.success) else Spinner()
                    Text(liveCheck, color = if (liveCheckPassed) c.onSuccessContainer else c.textSecondary, style = CytTheme.type.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CountdownRing(remainingMs, totalMs, stopped = stopped)
            Text(timeoutSentence, Modifier.weight(1f), color = c.textSecondary, style = CytTheme.type.bodySmall)
        }
        ActionColumn(Modifier.padding(top = 16.dp, bottom = 16.dp)) {
            PrimaryButton(yesLabel, onYes, Modifier.semantics { traversalIndex = -2f })
            SecondaryButton(noLabel, onNo, Modifier.semantics { traversalIndex = -1f })
        }
    }
}
