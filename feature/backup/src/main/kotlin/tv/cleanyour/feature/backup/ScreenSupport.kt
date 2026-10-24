package tv.cleanyour.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.component.Overline
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.theme.CytTheme

// Copy: ICU MessageFormat with named arguments (strings.xml rule).
@Composable
internal fun msg(id: Int, vararg args: Pair<String, Any>): String {
    val pattern = stringResource(id)
    if (args.isEmpty()) return pattern
    val locale = LocalConfiguration.current.locales[0]
    return remember(pattern, locale, args.toList()) {
        android.icu.text.MessageFormat(pattern, locale).format(args.toMap())
    }
}

// The live status line with a spinner ("Waiting for the TV..."), as mockup 03 draws it. A polite
// live region, so TalkBack reads each change.
@Composable
internal fun LiveLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 20.dp, start = 4.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spinner()
        Text(text, color = CytTheme.colors.textPrimary, style = CytTheme.type.bodyMedium)
    }
}

// A supporting paragraph in body.medium, text.secondary unless the caller says otherwise.
@Composable
internal fun Body(text: String, modifier: Modifier = Modifier, color: Color = CytTheme.colors.textSecondary) {
    Text(text, modifier, color = color, style = CytTheme.type.bodyMedium)
}

// A group label above a list card ("ABOUT", "ADVERTS AND SPONSORED ROWS"), spaced as the mockups.
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Overline(text, modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
}
