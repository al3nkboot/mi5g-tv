package tv.cleanyour.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.icon.CytIcons
import tv.cleanyour.ui.design.theme.CytTheme

// DESIGN-SYSTEM 8.2: upcoming (text.tertiary, no rim), current (rim outline.strong), done (success
// check, collapses to one line), stopped (surface.2 with a grey icon, never red).
enum class StepState { UPCOMING, CURRENT, DONE, STOPPED }

// A step card: surface.2, shape.medium, rim, 20 dp padding, optional text-free illustration on
// top, overline (step label), title.large, body.medium in text.secondary, trailing status.
// trailing overrides the state's default (a spinner while the step is running, for example).
@Composable
fun StepCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    state: StepState = StepState.CURRENT,
    illustration: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    val rimColor = when (state) {
        StepState.CURRENT -> c.outlineStrong
        StepState.UPCOMING -> null
        else -> c.rim
    }
    val base = modifier.fillMaxWidth().background(c.surface2, shape)
        .then(if (rimColor != null) Modifier.border(1.dp, rimColor, shape) else Modifier)
    if (state == StepState.DONE) {
        Row(base.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CytIcon(CytIcons.Check, null, tint = c.success)
            Text(title, Modifier.weight(1f), color = c.textSecondary, style = CytTheme.type.titleMedium, maxLines = 2)
            trailing?.invoke()
        }
        return
    }
    val titleColor = if (state == StepState.UPCOMING) c.textTertiary else c.textPrimary
    val bodyColor = if (state == StepState.UPCOMING) c.textTertiary else c.textSecondary
    Column(base.padding(20.dp)) {
        if (illustration != null) {
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) { illustration() }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                if (overline != null) Overline(overline, Modifier.padding(bottom = 6.dp))
                Text(title, color = titleColor, style = CytTheme.type.titleLarge)
                Text(body, Modifier.padding(top = 6.dp), color = bodyColor, style = CytTheme.type.bodyMedium)
            }
            when {
                trailing != null -> trailing()
                state == StepState.STOPPED -> CytIcon(CytIcons.Stopped, null, tint = c.textTertiary, size = 24.dp)
                else -> Unit
            }
        }
    }
}

// Pager dots under the step cards: the current dot is text.primary and wider, the others text.tertiary.
@Composable
fun PagerDots(count: Int, selected: Int, modifier: Modifier = Modifier, description: String? = null) {
    val c = CytTheme.colors
    Row(
        modifier.then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == selected
            Box(
                Modifier.size(width = if (on) 16.dp else 6.dp, height = 6.dp)
                    .background(if (on) c.textPrimary else c.textTertiary.copy(alpha = 0.6f), CircleShape),
            )
        }
    }
}
