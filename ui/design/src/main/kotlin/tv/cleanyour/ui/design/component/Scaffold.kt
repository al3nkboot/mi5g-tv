package tv.cleanyour.ui.design.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytSpacing
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow
import tv.cleanyour.ui.design.theme.animatedAmbientGlow

// The top bar: 56 dp (taller only when the centre needs it at large font sizes), a 48 dp back
// button at the start (or an empty slot), the centre (the TV status chip or the step counter)
// and an optional trailing 48 dp action.
@Composable
fun CytTopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    center: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = CytSpacing.topBarHeight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (onBack != null) CytIconButton(CytIcons.Back, stringResource(R.string.cyt_back), onBack)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center?.invoke() }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing?.invoke() }
    }
}

// One screen: black bg (or light bg), the context glow drawn on the window background and under
// the status bar, the top bar, a scrolling content column inside the gutter, and the bottom actions
// pinned 16 dp above the gesture inset. Content never jumps while an action runs: the bottom slot
// keeps its place. While the content can scroll further, its last 28 dp fade into the background
// (R4-W8): a card or a line cut by the pinned actions reads as "more below", not as a mistake.
// Anything that must stay above the primary button (the S3 safety line) goes in the bottom bar.
@Composable
fun ScreenScaffold(
    glow: Glow,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CytTheme.colors
    BoxWithConstraints(modifier.fillMaxSize().background(c.bg).animatedAmbientGlow(glow)) {
        val gutter = CytSpacing.gutterFor(maxWidth)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            topBar()
            val scroll = rememberScrollState()
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .then(if (scrollable) Modifier.scrollEdgeFade(scroll, c.bg).verticalScroll(scroll) else Modifier)
                    .padding(horizontal = gutter),
                content = content,
            )
            if (bottomBar != null) {
                Column(Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 12.dp, bottom = CytSpacing.primaryAboveInset)) {
                    bottomBar()
                }
            }
        }
        overlay()
    }
}

// The bottom fade of a scrolling column: 28 dp from transparent to the background, drawn only
// while there is more below. Goes before verticalScroll in the chain so it draws in the viewport.
// One brush per size (drawWithCache), one rect per frame, no offscreen layer (DESIGN-SYSTEM 5).
private fun Modifier.scrollEdgeFade(scroll: ScrollState, bg: Color): Modifier = drawWithCache {
    val fade = 28.dp.toPx()
    val top = size.height - fade
    val brush = Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg), startY = top, endY = size.height)
    onDrawWithContent {
        drawContent()
        if (scroll.canScrollForward) drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, fade))
    }
}

// A vertical gap in the 4 dp scale.
@Composable
fun Gap(height: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(height))
