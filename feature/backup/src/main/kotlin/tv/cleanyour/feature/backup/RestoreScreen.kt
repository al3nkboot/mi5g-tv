@file:OptIn(ExperimentalMaterial3Api::class)

package tv.cleanyour.feature.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import tv.cleanyour.core.flows.RestorePhase
import tv.cleanyour.core.flows.RestorePointIntent
import tv.cleanyour.core.flows.RestorePointState
import tv.cleanyour.core.flows.UndoPreview
import tv.cleanyour.ui.design.component.ActionColumn
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CheckLine
import tv.cleanyour.ui.design.component.CheckState
import tv.cleanyour.ui.design.component.ChoiceList
import tv.cleanyour.ui.design.component.ChoiceOption
import tv.cleanyour.ui.design.component.CytBottomSheet
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.DestructiveButton
import tv.cleanyour.ui.design.component.Lead
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.NarrationLine
import tv.cleanyour.ui.design.component.NarrationState
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ProgressNarration
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.SheetHeader
import tv.cleanyour.ui.design.component.Spinner
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.theme.CytTheme
import tv.cleanyour.ui.design.theme.Glow

fun restoreDate(ms: Long, locale: java.util.Locale): String = SimpleDateFormat("d MMM, HH:mm", locale).format(Date(ms))

// M6 Restore point (UX 4.13, F10, DESIGN-SYSTEM 8.1): the preview always comes first; the one
// outlined danger button sits only inside its confirmation sheet; the export is a SAF zip. Each
// sheet keeps its body in an internal *Content composable, so the screenshot tests can draw it.
@Composable
fun RestoreScreen(state: RestorePointState, onIntent: (RestorePointIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val baseline = state.baselineAtMs?.let { restoreDate(it, locale) }
    var factory by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        when (val p = state.phase) {
            is RestorePhase.Working -> {
                // The last line is the one running; the ones before it are done.
                val lines = p.lines.mapIndexed { i, text ->
                    NarrationLine("$i", text, if (i == p.lines.lastIndex) NarrationState.DOING else NarrationState.DONE)
                }
                ProgressNarration(stringResource(if (p.kind == "factory") R.string.restore_factory_working else R.string.restore_working_title),
                    p.percent, lines, Modifier.padding(top = 20.dp))
                return@ScreenScaffold
            }
            else -> ScreenTitle(stringResource(R.string.restore_title), Modifier.padding(top = 20.dp))
        }
        when (val p = state.phase) {
            is RestorePhase.Finished -> {
                if (p.kind == "factory") {
                    Banner(stringResource(R.string.restore_factory_done), BannerTone.SUCCESS, Modifier.padding(top = 16.dp))
                } else {
                    Banner(stringResource(R.string.restore_done_title), BannerTone.SUCCESS, Modifier.padding(top = 16.dp))
                    Body(msg(R.string.restore_done_body, "date" to restoreDate(p.atMs, locale)), Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp), color = CytTheme.colors.textPrimary)
                    // Navigation into the wizard, whose own "Make it clean" is the guarded action (R5-W8 O1).
                    PrimaryButton(stringResource(R.string.restore_clean_again), { onIntent(RestorePointIntent.CleanAgain) }, Modifier.padding(top = 16.dp), guardObscured = false)
                }
            }
            RestorePhase.Failed -> Banner(stringResource(R.string.restore_read_failed), BannerTone.WARNING, Modifier.padding(top = 16.dp))
            else -> {}
        }
        Lead(if (baseline != null) msg(R.string.restore_current, "date" to baseline) else stringResource(R.string.restore_none), Modifier.padding(top = 12.dp))
        ListGroup(Modifier.padding(top = 20.dp, bottom = 24.dp)) {
            ListRow(stringResource(R.string.restore_row_undo), subtitle = baseline?.let { msg(R.string.restore_row_undo_sub, "date" to it) },
                onClick = if (baseline != null) ({ onIntent(RestorePointIntent.PreviewUndo) }) else null)
            ListDivider()
            ListRow(stringResource(R.string.restore_row_save), subtitle = stringResource(R.string.restore_row_save_sub),
                trailing = if (state.exporting) ({ Spinner() }) else null,
                onClick = if (baseline != null && !state.exporting) ({ onIntent(RestorePointIntent.SaveCopy) }) else null)
            if (state.googleTv) {
                ListDivider()
                ListRow(stringResource(R.string.restore_row_factory), subtitle = stringResource(R.string.restore_row_factory_sub), onClick = { factory = true })
            }
        }
    }
    val phase = state.phase
    if (phase is RestorePhase.Previewing) CytBottomSheet({ onIntent(RestorePointIntent.Cancel) }) { PreviewLoadingContent() }
    if (phase is RestorePhase.Preview) {
        CytBottomSheet({ onIntent(RestorePointIntent.Cancel) }) {
            if (!phase.confirming) PreviewContent(phase.preview, baseline.orEmpty(), onIntent) else ConfirmContent(onIntent)
        }
    }
    if (factory) CytBottomSheet({ factory = false }) {
        FactoryContent({ onIntent(RestorePointIntent.BeforeFactoryReset); factory = false }, { factory = false })
    }
}

@Composable
internal fun ColumnScope.PreviewLoadingContent() {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { Spinner(size = 24.dp) }
}

// Undo everything, step one: what comes back, and whether the new home screen stays installed.
@Composable
internal fun ColumnScope.PreviewContent(pv: UndoPreview, baseline: String, onIntent: (RestorePointIntent) -> Unit) {
    SheetHeader(msg(R.string.restore_preview_title, "date" to baseline))
    Column(Modifier.padding(top = 16.dp, start = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckLine(msg(R.string.restore_preview_things, "count" to pv.thingsBack), state = CheckState.NONE)
        pv.stockName?.let { CheckLine(msg(R.string.restore_preview_home, "name" to it), state = CheckState.NONE) }
        if (pv.wizardBack) CheckLine(stringResource(R.string.restore_preview_wizard), state = CheckState.NONE)
    }
    SectionLabel(stringResource(R.string.restore_launcher_title))
    ChoiceList(
        listOf(
            ChoiceOption("keep", stringResource(R.string.restore_launcher_keep), icon = null),
            ChoiceOption("remove", stringResource(R.string.restore_launcher_remove), stringResource(R.string.restore_launcher_remove_note), icon = null),
        ),
        if (pv.removeLauncher) "remove" else "keep",
        { onIntent(RestorePointIntent.RemoveLauncher(it.key == "remove")) },
    )
    SecondaryButton(stringResource(R.string.restore_preview_button), { onIntent(RestorePointIntent.Confirm) }, Modifier.padding(top = 20.dp))
}

// Undo everything, step two: the one red button in the app (UX 4.13), outlined, inside its own sheet.
@Composable
internal fun ColumnScope.ConfirmContent(onIntent: (RestorePointIntent) -> Unit) {
    SheetHeader(stringResource(R.string.restore_confirm_title))
    Body(stringResource(R.string.restore_confirm_body), Modifier.padding(top = 8.dp))
    ActionColumn(Modifier.padding(top = 20.dp)) {
        DestructiveButton(stringResource(R.string.restore_confirm_button), { onIntent(RestorePointIntent.PutEverythingBack) })
        TertiaryButton(stringResource(R.string.action_cancel), { onIntent(RestorePointIntent.Cancel) }, quiet = true)
    }
}

@Composable
internal fun ColumnScope.FactoryContent(onConfirm: () -> Unit, onCancel: () -> Unit) {
    SheetHeader(stringResource(R.string.restore_factory_title))
    Body(stringResource(R.string.restore_factory_body), Modifier.padding(top = 8.dp))
    ActionColumn(Modifier.padding(top = 20.dp)) {
        PrimaryButton(stringResource(R.string.restore_factory_button), onConfirm)
        TertiaryButton(stringResource(R.string.action_cancel), onCancel, quiet = true)
    }
}
