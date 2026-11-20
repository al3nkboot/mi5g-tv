package tv.cleanyour.feature.manage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.MINI_STORE
import tv.cleanyour.core.flows.TvToolsIntent
import tv.cleanyour.ui.design.component.CytTextField
import tv.cleanyour.ui.design.component.GlassChip
import tv.cleanyour.ui.design.component.SmallGlassButton
import tv.cleanyour.ui.design.theme.CytTheme

// 2.4 M3, phone companion: the mini store (backlog 17) and a live message or drawing on the TV (backlog 32).

/** Well-known legal TV apps; each opens its Play Store page on the TV, where the person installs it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MiniStoreSection(enabled: Boolean, onIntent: (TvToolsIntent) -> Unit) {
    SectionLabel(stringResource(R.string.tvtools_store_section))
    Body(stringResource(R.string.tvtools_store_note), Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MINI_STORE.forEach { app ->
            GlassChip(text = app.name, onClick = { if (enabled) onIntent(TvToolsIntent.OpenStorePage(app.pkg)) })
        }
    }
}

/** A short message and/or a finger drawing, shown on the TV at once by Mi5G Home; "Take it off" closes it. */
@Composable
internal fun LiveBoardSection(enabled: Boolean, onIntent: (TvToolsIntent) -> Unit) {
    SectionLabel(stringResource(R.string.tvtools_live_section))
    Body(stringResource(R.string.tvtools_live_note), Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp))
    var text by rememberSaveable { mutableStateOf("") }
    val strokes = remember { mutableStateListOf<List<Pair<Float, Float>>>() }
    var current by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    CytTextField(value = text, onValueChange = { text = it.take(120) }, label = stringResource(R.string.tvtools_live_text_label))
    val gold = CytTheme.colors.warning
    val padCd = stringResource(R.string.tvtools_live_pad_cd)
    // A 16:9 pad like the TV screen; drawn in gold on dark, as the TV shows it.
    Canvas(
        Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0B1F1E)).border(1.dp, CytTheme.colors.outline, RoundedCornerShape(16.dp))
            .testTag("live_pad").semantics { contentDescription = padCd }
            .pointerInput(Unit) {
                fun norm(o: Offset) = (o.x / size.width).coerceIn(0f, 1f) to (o.y / size.height).coerceIn(0f, 1f)
                detectDragGestures(
                    onDragStart = { current = listOf(norm(it)) },
                    onDragEnd = { if (current.isNotEmpty()) strokes.add(current); current = emptyList() },
                    onDragCancel = { if (current.isNotEmpty()) strokes.add(current); current = emptyList() },
                ) { change, _ -> current = current + norm(change.position) }
            },
    ) {
        val style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        (strokes + listOf(current)).forEach { s ->
            if (s.isEmpty()) return@forEach
            val path = Path()
            s.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * size.width, y * size.height) else path.lineTo(x * size.width, y * size.height) }
            if (s.size == 1) path.lineTo(s[0].first * size.width + 1f, s[0].second * size.height)
            drawPath(path, gold, style = style)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallGlassButton(
            stringResource(R.string.tvtools_live_send),
            { onIntent(TvToolsIntent.SendLiveBoard(strokes.toList(), text)) },
            enabled = enabled && (strokes.isNotEmpty() || text.isNotBlank()),
            guardObscured = false,
        )
        SmallGlassButton(stringResource(R.string.tvtools_live_clear_pad), { strokes.clear(); current = emptyList() }, enabled = strokes.isNotEmpty(), guardObscured = false)
        SmallGlassButton(stringResource(R.string.tvtools_live_close), { onIntent(TvToolsIntent.CloseLiveBoard) }, enabled = enabled, guardObscured = false)
    }
}
