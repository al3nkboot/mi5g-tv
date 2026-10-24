package tv.cleanyour.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme

// DESIGN-SYSTEM 9 "Stopped": a surface.2 card with a grey icon and the next step. Never red, no
// red glow. What happened, what it means, what to do. busyText shows a spinner line
// ("Reconnecting..."). The way out is always a button on the card. primaryGuarded = false when the
// primary is Back, a plain OK or navigation; secondaryGuarded and tertiaryGuarded = true when that
// button writes to the TV ("Undo what we did", "Stop here" with a home section open, "Try again"),
// see rejectObscuredTouches.
@Composable
fun StopCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = CytIcons.Stopped,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    tertiaryLabel: String? = null,
    onTertiary: () -> Unit = {},
    detailsLabel: String? = null,
    onDetails: () -> Unit = {},
    busyText: String? = null,
    primaryGuarded: Boolean = true,
    secondaryGuarded: Boolean = false,
    tertiaryGuarded: Boolean = false,
) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    Column(modifier.fillMaxWidth().background(c.surface2, shape).border(1.dp, c.rim, shape).padding(20.dp)) {
        CytIcon(icon, null, tint = c.textTertiary, size = 24.dp)
        Text(title, Modifier.padding(top = 12.dp).semantics { heading() }, color = c.textPrimary, style = CytTheme.type.titleLarge)
        Text(body, Modifier.padding(top = 8.dp), color = c.textSecondary, style = CytTheme.type.bodyMedium)
        if (busyText != null) {
            Row(Modifier.padding(top = 14.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spinner()
                Text(busyText, color = c.textPrimary, style = CytTheme.type.bodyMedium)
            }
        }
        if (primaryLabel != null || secondaryLabel != null || tertiaryLabel != null) {
            ActionColumn(Modifier.padding(top = 20.dp)) {
                if (primaryLabel != null) PrimaryButton(primaryLabel, onPrimary, guardObscured = primaryGuarded)
                if (secondaryLabel != null) SecondaryButton(secondaryLabel, onSecondary, guardObscured = secondaryGuarded)
                if (tertiaryLabel != null) TertiaryButton(tertiaryLabel, onTertiary, quiet = true, guardObscured = tertiaryGuarded)
            }
        }
        if (detailsLabel != null) TertiaryButton(detailsLabel, onDetails, Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally))
    }
}

// Banner tones (DESIGN-SYSTEM 9): SUCCESS and WARNING containers, DANGER container (errors that
// are not a stop), NEUTRAL (surface.2, rim: stopped and offline lines), INFO (surface.1, the
// quiet footnote banner like "Phone and TV must be on the same Wi-Fi").
enum class BannerTone { SUCCESS, WARNING, DANGER, NEUTRAL, INFO }

// guardAction = true when the action writes to the TV ("Restore mine" on the drift banner); the
// hint after a dropped tap shows under the action (rejectObscuredTouches).
@Composable
fun Banner(
    text: AnnotatedString,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = defaultBannerIcon(tone),
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    guardAction: Boolean = false,
) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.row
    val (fill, fg, rim) = when (tone) {
        BannerTone.SUCCESS -> Triple(c.successContainer, c.onSuccessContainer, null)
        BannerTone.WARNING -> Triple(c.warningContainer, c.onWarningContainer, null)
        BannerTone.DANGER -> Triple(c.dangerContainer, c.onDangerContainer, null)
        BannerTone.NEUTRAL -> Triple(c.surface2, c.textSecondary, c.outline)
        BannerTone.INFO -> Triple(c.surface1, c.textSecondary, c.outline)
    }
    Row(
        modifier.fillMaxWidth().background(fill, shape)
            .then(if (rim != null) Modifier.border(1.dp, rim, shape) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) CytIcon(icon, null, tint = fg)
        Column(Modifier.weight(1f)) {
            Text(text, color = fg, style = CytTheme.type.bodyMedium)
            if (actionLabel != null) {
                Guarded(Modifier, guardAction) { outer, guard ->
                    Text(
                        actionLabel,
                        outer.clickable(role = Role.Button, onClick = onAction).then(guard).padding(vertical = 14.dp),
                        color = if (tone == BannerTone.NEUTRAL || tone == BannerTone.INFO) c.accent else fg,
                        style = CytTheme.type.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

@Composable
fun Banner(
    text: String, tone: BannerTone, modifier: Modifier = Modifier, icon: ImageVector? = defaultBannerIcon(tone),
    actionLabel: String? = null, onAction: () -> Unit = {}, guardAction: Boolean = false,
) = Banner(AnnotatedString(text), tone, modifier, icon, actionLabel, onAction, guardAction)

fun defaultBannerIcon(tone: BannerTone): ImageVector = when (tone) {
    BannerTone.SUCCESS -> CytIcons.Check
    BannerTone.WARNING -> CytIcons.Warning
    BannerTone.DANGER -> CytIcons.Warning
    BannerTone.NEUTRAL -> CytIcons.Stopped
    BannerTone.INFO -> CytIcons.Info
}

