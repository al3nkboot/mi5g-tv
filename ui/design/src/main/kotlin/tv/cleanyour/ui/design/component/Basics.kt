package tv.cleanyour.ui.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.theme.CytTheme

// A 20 dp icon in rows and chips, 24 dp in the top bar (DESIGN-SYSTEM 7). Icons pair with a text
// label, so contentDescription is null unless the icon stands alone.
@Composable
fun CytIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = CytTheme.colors.textPrimary, size: Dp = 20.dp) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}

// A 48 dp icon button (back, close, help) that always carries a content description.
@Composable
fun CytIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = CytTheme.colors.textPrimary) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(icon, contentDescription, Modifier.size(24.dp), tint = tint)
    }
}

// The small ring used for "doing" states, in chips, rows and buttons. Static in screenshot tests.
@Composable
fun Spinner(modifier: Modifier = Modifier, size: Dp = 16.dp, color: Color = CytTheme.colors.textPrimary, strokeWidth: Dp = 2.dp) {
    CircularProgressIndicator(
        modifier = modifier.size(size), color = color, strokeWidth = strokeWidth,
        trackColor = color.copy(alpha = 0.2f),
    )
}

// Section label, ALL CAPS in the current locale, tracking 2 sp ("2 OF 5", "WHAT IT CAN DO ON THE TV").
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = CytTheme.colors.textTertiary) {
    Text(text.toUpperCase(Locale.current), modifier, color = color, style = CytTheme.type.overline)
}

// The quiet step counter on screens 2 to 4 (D33): "2 of 5", or "4 of 5 · Look at the TV".
@Composable
fun StepCounter(current: Int, total: Int, modifier: Modifier = Modifier, extra: String? = null) {
    val text = if (extra == null) stringResource(R.string.cyt_step_counter, current, total)
    else stringResource(R.string.cyt_step_counter_extra, current, total, extra)
    Overline(text, modifier)
}

enum class TitleSize { DISPLAY, HEADLINE }

// The one big light title of a screen, marked as a heading for TalkBack. Titles wrap, never truncate.
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, size: TitleSize = TitleSize.HEADLINE, color: Color = CytTheme.colors.textPrimary) {
    val style = if (size == TitleSize.DISPLAY) CytTheme.type.display else CytTheme.type.headline
    Text(text, modifier.semantics { heading() }, color = color, style = style)
}

// The one supporting sentence under a title (body.large, text.secondary).
@Composable
fun Lead(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyLarge)
}

@Composable
fun Lead(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(text, modifier, color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyLarge)
}

// "Tick Always allow, then choose OK.": the named parts in text.primary, the rest as it is.
@Composable
fun rememberEmphasis(text: String, vararg emphasised: String): AnnotatedString {
    val color = CytTheme.colors.textPrimary
    val parts = emphasised.toList()
    return remember(text, parts, color) {
        buildAnnotatedString {
            append(text)
            for (p in parts) {
                if (p.isEmpty()) continue
                var i = text.indexOf(p)
                while (i >= 0) {
                    addStyle(SpanStyle(color = color), i, i + p.length)
                    i = text.indexOf(p, i + p.length)
                }
            }
        }
    }
}

// Package names, key digests and fingerprints read left to right in every locale (DESIGN-SYSTEM 10):
// wrap them in a first-strong-isolate pair so they never reorder inside RTL text.
fun ltr(s: String): String = buildString { append(LRI); append(s); append(PDI) }

private val LRI = 0x2066.toChar() // LEFT-TO-RIGHT ISOLATE
private val PDI = 0x2069.toChar() // POP DIRECTIONAL ISOLATE

// Text forced left to right (package names, digests, commands).
@Composable
fun LtrText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CytTheme.type.bodySmall,
    color: Color = CytTheme.colors.textTertiary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(ltr(text), modifier, color = color, style = style.copy(textDirection = TextDirection.Ltr), maxLines = maxLines, overflow = overflow)
}

// The 1 px divider between rows inside a card (outline, white 12% on dark).
@Composable
fun ListDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(CytTheme.colors.outline))
}

@Composable
internal fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) { content() }
}
