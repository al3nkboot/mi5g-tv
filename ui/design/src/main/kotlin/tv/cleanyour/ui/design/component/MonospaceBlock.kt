package tv.cleanyour.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.theme.CytTheme

// D21 "Show commands", the S2x computer steps, the M8 pin and key mode: FontFamily.Monospace
// 12 sp on surface.1, selectable, always left to right, long lines scroll sideways instead of
// wrapping, and an optional "Copy as commands" action under it.
//
// `wrap` (the report preview): the lines wrap at the block's width instead of scrolling sideways,
// and the whole text is one Text node, so TalkBack reads it as one block and a selection can run
// across lines.
@Composable
fun MonospaceBlock(lines: List<String>, modifier: Modifier = Modifier, copyLabel: String? = null, onCopy: (() -> Unit)? = null, wrap: Boolean = false) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.small
    val style = CytTheme.type.mono.copy(textDirection = TextDirection.Ltr)
    Column(modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer(Modifier.fillMaxWidth().background(c.surface1, shape).border(1.dp, c.outline, shape)) {
                if (wrap) {
                    Text(lines.joinToString("\n"), Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), color = c.textPrimary, style = style, softWrap = true)
                } else {
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        lines.forEach { Text(it, color = c.textPrimary, style = style, softWrap = false) }
                    }
                }
            }
        }
        if (onCopy != null) {
            TertiaryButton(copyLabel ?: stringResource(R.string.cyt_copy_as_commands), onCopy, Modifier.align(Alignment.Start))
        }
    }
}
