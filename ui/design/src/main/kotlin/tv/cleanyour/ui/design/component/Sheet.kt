package tv.cleanyour.ui.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import tv.cleanyour.ui.design.theme.CytTheme

// DESIGN-SYSTEM 8.8. The drag handle: 32 x 4 dp, white 36% (outline.strong).
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier) {
    Box(modifier.padding(top = 12.dp, bottom = 6.dp).size(width = 32.dp, height = 4.dp).background(CytTheme.colors.outlineStrong, CytTheme.shapes.pill))
}

// The Material 3 modal sheet in the brand: surface.2, shape.large top corners, the 64% scrim, the
// handle, 20 dp side padding. Used for Customise and See the list (D3), the dock picker, the
// address entry, the TV details and the confirmations. No true blur in 0.1.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CytBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = CytTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // A tall sheet stops below the status bar plus a strip of scrim, so the screen behind and
        // its top bar still show as in mockup 08 (round 3: on the emulator it ran under the status
        // bar icons).
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = SHEET_TOP_GAP).sheetTopRim(c.rim),
        sheetState = sheetState,
        shape = CytTheme.shapes.sheet,
        containerColor = c.surface2,
        contentColor = c.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = c.scrim,
        dragHandle = { SheetDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), content = content)
    }
}

// The sheet's look without the modal window, for inline use and screenshot tests: the same
// surface, corners, rim and handle.
@Composable
fun SheetSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = CytTheme.colors
    Column(
        modifier.fillMaxWidth().clip(CytTheme.shapes.sheet).background(c.surface2).sheetTopRim(c.rim),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SheetDragHandle()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), content = content)
    }
}

// Sheet title (title.large) with an optional live total on the end ("22 of 23 off", with the
// number in text.primary) and one line of reassurance under it. When the title and the total do
// not fit side by side (200% font), the total moves under the title instead of breaking a word.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetHeader(title: String, modifier: Modifier = Modifier, trailing: AnnotatedString? = null, subtitle: String? = null) {
    val c = CytTheme.colors
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Text(title, Modifier.padding(end = 12.dp).semantics { heading() }, color = c.textPrimary, style = CytTheme.type.titleLarge)
            if (trailing != null) {
                Text(trailing, color = c.textSecondary, style = CytTheme.type.bodyMedium.copy(fontFeatureSettings = "tnum"))
            }
        }
        if (subtitle != null) Text(subtitle, Modifier.padding(top = 4.dp), color = c.textSecondary, style = CytTheme.type.bodyMedium)
    }
}

// The 1 dp highlight line along the top edge of the sheet (inset box-shadow in the mockup), drawn
// over the sheet surface so it shows on the modal sheet as well as on SheetSurface.
private fun Modifier.sheetTopRim(color: Color): Modifier = this.drawWithContent {
    drawContent()
    drawLine(color, Offset(28.dp.toPx(), 0.5f), Offset(size.width - 28.dp.toPx(), 0.5f), strokeWidth = 1f)
}

// The scrim strip a full-height sheet leaves under the status bar (mockup 08).
private val SHEET_TOP_GAP = 48.dp

// A question before a run stops or TV changes are undone (owner, first device test): the title
// asks ("Stop now?"), one calm sentence says what happens and that the TV stays safe, the verb is
// the primary and drops a tap through another app's overlay (rejectObscuredTouches), and the way
// back ("Keep going", "Cancel") never needs that guard. The screen sends its intent only from
// onConfirm, so nothing reaches the TV before the answer.
@Composable
fun ColumnScope.ConfirmQuestion(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, cancelLabel: String, onCancel: () -> Unit) {
    SheetHeader(title)
    Text(body, Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), color = CytTheme.colors.textSecondary, style = CytTheme.type.bodyMedium)
    ActionColumn(Modifier.padding(top = 20.dp)) {
        PrimaryButton(confirmLabel, onConfirm, guardObscured = true)
        TertiaryButton(cancelLabel, onCancel, quiet = true)
    }
}

// ConfirmQuestion in its own modal sheet; dismissing it is the same as Cancel.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, cancelLabel: String, onCancel: () -> Unit) {
    CytBottomSheet(onCancel) { ConfirmQuestion(title, body, confirmLabel, onConfirm, cancelLabel, onCancel) }
}
