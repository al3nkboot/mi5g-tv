// CytBottomSheet takes the Material 3 sheet state, which is still marked experimental.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tv.cleanyour.feature.setup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.cleanyour.core.flows.Confirm
import tv.cleanyour.ui.design.component.ConfirmQuestion
import tv.cleanyour.ui.design.component.CytBottomSheet

// The question before a stop or an undo (Confirmations in :core:flows says which buttons ask).
// The intent is sent from onConfirm only; Cancel and Keep going change nothing.
@Composable
internal fun ColumnScope.ConfirmQuestionFor(confirm: Confirm, onConfirm: () -> Unit, onCancel: () -> Unit) {
    when (confirm) {
        Confirm.STOP_AND_UNDO -> ConfirmQuestion(
            stringResource(R.string.confirm_stop_title), stringResource(R.string.confirm_stop_body),
            stringResource(R.string.confirm_stop_button), onConfirm, stringResource(R.string.confirm_keep_going), onCancel,
        )
        Confirm.STOP_HERE -> ConfirmQuestion(
            stringResource(R.string.confirm_stop_here_title), stringResource(R.string.confirm_stop_here_body),
            stringResource(R.string.confirm_stop_here_button), onConfirm, stringResource(R.string.action_cancel), onCancel,
        )
        Confirm.UNDO -> ConfirmQuestion(
            stringResource(R.string.confirm_undo_title), stringResource(R.string.confirm_undo_body),
            stringResource(R.string.confirm_undo_button), onConfirm, stringResource(R.string.action_cancel), onCancel,
        )
    }
}

@Composable
internal fun ConfirmSheetFor(confirm: Confirm?, onConfirm: () -> Unit, onCancel: () -> Unit) {
    if (confirm != null) CytBottomSheet(onCancel) { ConfirmQuestionFor(confirm, onConfirm, onCancel) }
}
