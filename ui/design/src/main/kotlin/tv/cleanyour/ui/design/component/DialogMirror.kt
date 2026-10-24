package tv.cleanyour.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tv.cleanyour.ui.design.R
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Outfit

// TV art is a picture of the TV, not app UI: it keeps the TV's own colours in both themes, is
// drawn left to right (the TV prints English), ignores the phone's font scale, and is hidden from
// TalkBack unless a description is given (the text around it carries the meaning).
@Composable
private fun TvArt(contentDescription: String?, modifier: Modifier, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    Box(modifier.clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription }) {
        CompositionLocalProvider(LocalDensity provides Density(d.density, 1f), LocalLayoutDirection provides LayoutDirection.Ltr) { content() }
    }
}

private val TvWhite = Color(0xFFF5F5F7)
private val TvGrey = Color(0xFFA1A1A6)
private val TvAccent = Color(0xFF8C9BFF)

// The TV: black screen, a 1 dp rim, a 6 dp bezel in surface.1 and a hairline outside, and the
// stand bar below (the launcher's look, mockups 01 and 03). The screen is 16:9 by default.
@Composable
fun TvFrame(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    showStand: Boolean = true,
    aspect: Float = 300f / 176f,
    screen: @Composable BoxScope.() -> Unit,
) {
    // The stand is the one part of the picture that sits on the phone's background, so it takes
    // the background's contrast: white on dark, black on light.
    val standColor = (if (CytTheme.colors.isDark) Color.White else Color.Black).copy(alpha = 0.22f)
    TvArt(contentDescription, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.padding(7.dp).fillMaxWidth().aspectRatio(aspect)
                    .drawBehind {
                        val r = 14.dp.toPx()
                        val o1 = 6.dp.toPx()
                        drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(-o1 - 1f, -o1 - 1f), Size(size.width + 2 * (o1 + 1f), size.height + 2 * (o1 + 1f)), CornerRadius(r + o1 + 1f))
                        drawRoundRect(Color(0xFF0E0E10), Offset(-o1, -o1), Size(size.width + 2 * o1, size.height + 2 * o1), CornerRadius(r + o1))
                    }
                    .background(Color.Black, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                    .padding(1.dp),
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black, shape), content = screen)
            }
            if (showStand) Box(Modifier.padding(top = 14.dp).size(84.dp, 4.dp).background(standColor, RoundedCornerShape(2.dp)))
        }
    }
}

// Screen S2c: the TV's "Allow USB debugging?" dialog, mirrored, with the fingerprint's last bytes
// highlighted (the phone shows the same tail in CodeMatchCard). fingerprint "3A:91:...:7F:2A".
@Composable
fun AdbAllowMirror(fingerprint: String, modifier: Modifier = Modifier, highlightBytes: Int = 2, contentDescription: String? = null) {
    val parts = fingerprint.split(":")
    val head = parts.dropLast(highlightBytes).joinToString(":")
    val tail = parts.takeLast(highlightBytes).joinToString(":")
    TvFrame(modifier, contentDescription) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF141416), Color(0xFF08080A)))))
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.72f).background(Color(0xFF26262A), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.cyt_mirror_allow_title), color = TvWhite, style = tvText(10, FontWeight.Medium))
            Text(stringResource(R.string.cyt_mirror_allow_body), Modifier.padding(top = 5.dp), color = TvGrey, style = tvText(6.5))
            Text(
                buildAnnotatedString {
                    append(if (head.isEmpty()) "" else "$head:")
                    withStyle(SpanStyle(color = TvWhite, background = TvAccent.copy(alpha = 0.28f))) { append(tail) }
                },
                color = TvGrey, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 6.sp, lineHeight = 9.sp, textDirection = TextDirection.Ltr),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(TvAccent, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(CytIcons.Check, null, Modifier.size(8.dp), tint = Color.Black)
                }
                Text(stringResource(R.string.cyt_mirror_allow_always), color = TvWhite, style = tvText(7))
            }
            Row(Modifier.align(Alignment.End).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.cyt_mirror_cancel), Modifier.padding(4.dp), color = TvGrey, style = tvText(7.5, FontWeight.Medium, 0.6))
                Text(
                    stringResource(R.string.cyt_mirror_ok),
                    Modifier.drawBehind {
                        drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(-4f, -4f), Size(size.width + 8f, size.height + 8f), CornerRadius(size.height), style = Stroke(4f))
                    }.background(TvWhite, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.Black, style = tvText(7.5, FontWeight.Medium, 0.6),
                )
            }
        }
    }
}

// Screen M3c: the launcher's card prompt, mirrored (upstream card.md step 4): "New card from
// Retro", the body, the hint, package and the 12-hex key, "Keep hidden" (focused) and "Show card".
@Composable
fun CardPromptMirror(appName: String, packageName: String, keyHex: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    TvFrame(modifier, contentDescription) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF3A0E0E), Color.Black), center = Offset(80f, 60f), radius = 600f)))
        Text("2:04", Modifier.padding(start = 14.dp, top = 12.dp), color = TvWhite.copy(alpha = 0.8f), style = tvText(16, FontWeight.ExtraLight))
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(0.78f)
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2A2E), Color(0xFF151517))), RoundedCornerShape(10.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.cyt_mirror_card_title, appName), color = TvWhite, style = tvText(10))
            Text(stringResource(R.string.cyt_mirror_card_body, appName), Modifier.padding(top = 4.dp), color = TvWhite.copy(alpha = 0.85f), style = tvText(6.5))
            Text(stringResource(R.string.cyt_mirror_card_later), Modifier.padding(top = 4.dp), color = TvGrey, style = tvText(5.5))
            Text(
                buildAnnotatedString {
                    append(ltr(packageName))
                    append(", key ")
                    withStyle(SpanStyle(color = TvWhite, background = TvAccent.copy(alpha = 0.28f))) { append(ltr(keyHex)) }
                },
                Modifier.padding(top = 3.dp), color = TvGrey, style = tvText(5.5).copy(textDirection = TextDirection.Ltr),
            )
            Row(Modifier.align(Alignment.End).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.cyt_mirror_keep_hidden), Modifier.background(TvWhite, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp), color = Color(0xFF555558), style = tvText(7))
                Text(stringResource(R.string.cyt_mirror_show_card), Modifier.background(Color(0xFF3A3A3E), RoundedCornerShape(50)).border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp), color = TvWhite, style = tvText(7))
            }
        }
    }
}

private fun tvText(size: Number, weight: FontWeight = FontWeight.Normal, tracking: Number = 0) =
    TextStyle(fontFamily = Outfit, fontSize = size.toFloat().sp, lineHeight = (size.toFloat() * 1.35f).sp, fontWeight = weight, letterSpacing = tracking.toFloat().sp)

// "The code on the TV ends in 7F:2A / That is this phone." and the 12-hex key ritual (D1): a
// surface.1 card with the label and caption at the start and the code in monospace at the end,
// always left to right.
@Composable
fun CodeMatchCard(label: String, caption: String?, code: String, modifier: Modifier = Modifier) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    Row(
        modifier.fillMaxWidth().background(c.surface1, shape).border(1.dp, c.outline, shape).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = c.textSecondary, style = CytTheme.type.bodyMedium)
            if (caption != null) Text(caption, Modifier.padding(top = 2.dp), color = c.textTertiary, style = CytTheme.type.bodySmall)
        }
        LtrText(code, Modifier.widthIn(max = 200.dp), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 1.sp, fontFeatureSettings = "tnum"), color = c.textPrimary)
    }
}
