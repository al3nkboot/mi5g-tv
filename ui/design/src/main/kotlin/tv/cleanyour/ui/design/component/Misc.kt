package tv.cleanyour.ui.design.component

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.theme.CytTheme

// The three haptic patterns of UX 6 and DESIGN-SYSTEM 6 only: light tick (progress, selection),
// double tap (success, connected, done), firm single (look at the TV). No error haptic.
@Stable
class CytHaptics internal constructor(private val view: View, private val enabled: Boolean) {
    fun light() { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun double() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        else view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    fun firm() { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

// enabled follows the app setting "Haptics" (M8).
@Composable
fun rememberCytHaptics(enabled: Boolean = true): CytHaptics {
    val view = LocalView.current
    return remember(view, enabled) { CytHaptics(view, enabled) }
}

// A text field in shape.small (S1c address, M7 name and room). error replaces the supporting line.
@Composable
fun CytTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    val c = CytTheme.colors
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, isError = error != null, singleLine = singleLine,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = CytTheme.shapes.small, textStyle = CytTheme.type.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
            focusedContainerColor = c.surface1, unfocusedContainerColor = c.surface1,
            focusedBorderColor = c.accent, unfocusedBorderColor = c.outlineStrong,
            focusedLabelColor = c.accent, unfocusedLabelColor = c.textSecondary,
            cursorColor = c.accent, errorBorderColor = c.danger, errorLabelColor = c.danger,
            errorSupportingTextColor = c.danger, errorContainerColor = c.surface1,
            focusedSupportingTextColor = c.textTertiary, unfocusedSupportingTextColor = c.textTertiary,
        ),
    )
}

// M1: the TV card on Manage: the last thumbnail, name and room, the state line ("Clean. Checked
// 19 Oct.") and the level chip ("Simple"). Tapping refreshes the thumbnail when connected.
@Composable
fun TvCard(
    name: String,
    statusLine: String,
    thumbnail: ImageBitmap?,
    thumbnailDescription: String,
    modifier: Modifier = Modifier,
    level: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = CytTheme.colors
    val shape = CytTheme.shapes.medium
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.rim, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TvThumbnail(thumbnail, thumbnailDescription, label = null)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(name, color = c.textPrimary, style = CytTheme.type.titleMedium)
                Text(statusLine, color = c.textSecondary, style = CytTheme.type.bodyMedium)
            }
            if (level != null) InfoChip(level)
        }
    }
}

