package tv.cleanyour.feature.manage

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import tv.cleanyour.core.flows.TvToolsIntent
import tv.cleanyour.core.flows.TvToolsState
import tv.cleanyour.ui.design.component.ActionColumn
import tv.cleanyour.ui.design.component.CytTextField
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.component.TertiaryButton
import tv.cleanyour.ui.design.theme.CytTheme

@Composable
internal fun TellyExtrasSections(state: TvToolsState, onIntent: (TvToolsIntent) -> Unit) {
    val ar = Locale.getDefault().language == "ar"
    fun t(arText: String, en: String) = if (ar) arText else en

    SectionLabel(t("مراقبة أوضح", "A clearer look"))
    Body(
        t("الحرارة وسرعة الشبكة واستهلاك التطبيقات وما هو موصول. القراءة من الاتصال الحالي فقط.", "Temperature, network speed, app use, and what is connected. Read from the current connection only."),
        Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
    )
    state.monitor?.let { m ->
        ListGroup {
            m.temp?.let { ListRow(t("الحرارة: $it", "Temperature: $it")) }
            m.net?.let { ListRow(t("الشبكة: $it", "Network: $it")) }
            m.apps.forEach { ListRow(it) }
            if (m.connected.isEmpty()) ListRow(t("لا أجهزة ظاهرة", "Nothing plugged in is listed"))
            else m.connected.forEach { ListRow(it) }
        }
    }
    SecondaryButton(t("تحديث المراقبة", "Refresh monitor"), { onIntent(TvToolsIntent.RefreshMonitor) }, Modifier.padding(top = 8.dp))

    SectionLabel(t("ملفات Download وMovies", "Download and Movies"))
    Body(
        t("إرسال واستقبال داخل هذين المجلدين فقط. لا يُستبدل ملف موجود.", "Send and receive inside these two folders only. An existing file is not overwritten."),
        Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
    )
    ActionColumn {
        SecondaryButton(t("التنزيلات", "Download"), { onIntent(TvToolsIntent.SetFilesFolder("download")) })
        SecondaryButton(t("الأفلام", "Movies"), { onIntent(TvToolsIntent.SetFilesFolder("movies")) })
        PrimaryButton(t("إرسال ملف", "Send a file"), { onIntent(TvToolsIntent.PickSendFile(state.filesFolder)) })
        SecondaryButton(t("تحديث القائمة", "Refresh list"), { onIntent(TvToolsIntent.RefreshFiles) })
    }
    state.filesNote?.takeIf { it.isNotBlank() }?.let {
        Body(it, Modifier.padding(top = 8.dp, start = 4.dp), color = CytTheme.colors.textSecondary)
    }
    if (state.files.isNotEmpty()) {
        ListGroup {
            state.files.take(12).forEach { name ->
                ListRow(name, onClick = { onIntent(TvToolsIntent.PullSharedFile(state.filesFolder, name)) })
            }
        }
    }

    SectionLabel(t("ريموت أكمل", "A fuller remote"))
    Body(
        t("رصيف حتى أربعة تطبيقات، إعادة تشغيل، وإطفاء الصورة مع بقاء الصوت على أندرويد 13 أو أقدم.", "A dock of up to four apps, restart, and picture off with audio on Android 13 or older."),
        Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
    )
    CytTextField(
        value = state.dockInput,
        onValueChange = { onIntent(TvToolsIntent.UpdateDockInput(it)) },
        label = t("اسم الحزمة", "Package name"),
    )
    ActionColumn(Modifier.padding(top = 8.dp)) {
        SecondaryButton(t("إضافة للرصيف", "Add to dock"), { onIntent(TvToolsIntent.AddDock(state.dockInput)) })
        SecondaryButton(t("إعادة تشغيل التلفاز", "Restart the TV"), { onIntent(TvToolsIntent.RestartTv) })
        SecondaryButton(t("إطفاء الصورة", "Picture off"), { onIntent(TvToolsIntent.SetPicture(false)) })
        SecondaryButton(t("إعادة الصورة", "Picture on"), { onIntent(TvToolsIntent.SetPicture(true)) })
    }
    state.dock.forEach { pkg ->
        ActionColumn(Modifier.padding(top = 8.dp)) {
            PrimaryButton(pkg, { onIntent(TvToolsIntent.LaunchDock(pkg)) })
            TertiaryButton(t("إزالة", "Remove"), { onIntent(TvToolsIntent.RemoveDock(pkg)) })
        }
    }
    state.screenNote?.let {
        Body(it, Modifier.padding(top = 8.dp, start = 4.dp), color = CytTheme.colors.textSecondary)
    }

    SectionLabel(t("سجل التغيير والتراجع", "Change log"))
    Body(
        t("التراجع يعيد ما أوقفه هذا التطبيق فقط. تطبيقات المصنع تُعطَّل ولا تُحذف.", "Undo turns back on only what this app switched off. Factory apps are disabled, not removed."),
        Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
    )
    SecondaryButton(t("استعادة ما أوقفناه", "Restore what we switched off"), { onIntent(TvToolsIntent.RestoreOurChanges) })
    state.changes.take(8).forEach { change ->
        ListGroup {
            ListRow(change.label + "  " + change.pkg)
        }
        if (change.canUndo) {
            TertiaryButton(t("تراجع", "Undo"), { onIntent(TvToolsIntent.UndoChange(change.id)) })
        }
    }

    SectionLabel(t("من له صلاحية ADB", "Who has ADB access"))
    Body(
        t("القائمة من الاتصال الحالي. حذف الآخرين لا يفتح نافذة سماح ولا يتصل بتلفاز آخر.", "The list is from the current connection. Removing others does not open Allow and does not connect a different TV."),
        Modifier.padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
    )
    SecondaryButton(t("عرض المفاتيح", "Show keys"), { onIntent(TvToolsIntent.RefreshKeys) })
    state.adbKeys.forEach { ListGroup { ListRow(it) } }
    state.adbNote?.takeIf { it.isNotBlank() }?.let {
        Body(it, Modifier.padding(top = 8.dp, start = 4.dp), color = CytTheme.colors.textSecondary)
    }
    if (!state.confirmKeys) {
        TertiaryButton(t("حذف المفاتيح الأخرى", "Remove other keys"), { onIntent(TvToolsIntent.AskRemoveOtherKeys) }, Modifier.padding(top = 8.dp))
    } else {
        Body(t("سيبقى مفتاح هذا الهاتف فقط إن سمح التلفاز.", "Only this phone's key stays, if the TV allows the write."), Modifier.padding(top = 8.dp))
        PrimaryButton(t("تأكيد الحذف", "Confirm removal"), { onIntent(TvToolsIntent.ConfirmRemoveOtherKeys) })
    }
}
