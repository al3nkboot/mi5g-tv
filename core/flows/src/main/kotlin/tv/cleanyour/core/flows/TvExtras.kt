package tv.cleanyour.core.flows

import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.ops.TellyFeatures
import tv.cleanyour.core.ops.TellyShell
import tv.cleanyour.core.probe.TvShellRunner
import tv.cleanyour.core.session.TvSession

data class MonitorView(
    val temp: String?,
    val net: String?,
    val apps: List<String>,
    val connected: List<String>,
)

data class ChangeView(val id: Long, val label: String, val pkg: String, val canUndo: Boolean)

data class InstallPart(val name: String, val sizeBytes: Long, val blob: BlobRef)

internal data class SideloadProgress(val status: SideloadStatus)

internal data class ExtrasLoaded(
    val monitor: MonitorView? = null,
    val files: List<String>? = null,
    val folder: String? = null,
    val filesNote: String? = null,
    val dock: List<String>? = null,
    val changes: List<ChangeView>? = null,
    val adbKeys: List<String>? = null,
    val adbNote: String? = null,
    val screenNote: String? = null,
    val busy: Boolean? = null,
    val confirmKeys: Boolean? = null,
)

internal fun reduceExtras(state: TvToolsState, event: Any): TvToolsState? = when (event) {
    is SideloadProgress -> state.copy(sideloadStatus = event.status)
    is ExtrasLoaded -> state.copy(
        monitor = event.monitor ?: state.monitor,
        files = event.files ?: state.files,
        filesFolder = event.folder ?: state.filesFolder,
        filesNote = event.filesNote ?: state.filesNote,
        dock = event.dock ?: state.dock,
        changes = event.changes ?: state.changes,
        adbKeys = event.adbKeys ?: state.adbKeys,
        adbNote = event.adbNote ?: state.adbNote,
        screenNote = event.screenNote ?: state.screenNote,
        extrasBusy = event.busy ?: state.extrasBusy,
        confirmKeys = event.confirmKeys ?: state.confirmKeys,
    )
    is TvToolsIntent.UpdateDockInput -> state.copy(dockInput = event.text.take(120))
    else -> null
}

internal object TellyActions {
    private val JAR = RemotePath(RemoteDir.TMP, listOf("picture-off.jar"))
    private val KEYS = RemotePath(RemoteDir.TMP, listOf("kept-keys.txt"))
    private val STAGE = RemotePath(RemoteDir.TMP, listOf("stage.bin"))

    fun wants(event: Any): Boolean = event is TvToolsIntent && event::class.simpleName?.let {
        it in SET
    } == true

    private val SET = setOf(
        "RefreshExtras", "RefreshMonitor", "RefreshFiles", "SetFilesFolder", "PickSendFile", "SendFile",
        "PullSharedFile", "AddDock", "RemoveDock", "LaunchDock", "RestartTv", "SetPicture",
        "RefreshKeys", "AskRemoveOtherKeys", "ConfirmRemoveOtherKeys", "UndoChange", "RestoreOurChanges",
        "ConfirmInstall",
    )

    fun launch(
        event: Any,
        before: TvToolsState,
        d: ManageDeps,
        tvId: TvId,
        scope: CoroutineScope,
        phoneKey: () -> String?,
        pictureJar: () -> ByteArray?,
        send: (Any) -> Unit,
        effect: (ManageEffect) -> Unit,
    ) {
        when (event) {
            TvToolsIntent.RefreshExtras -> scope.launch { loadSaved(d, tvId, send) }
            TvToolsIntent.RefreshMonitor -> scope.launch { monitor(d, tvId, send) }
            TvToolsIntent.RefreshFiles -> scope.launch { files(d, tvId, before.filesFolder, send) }
            is TvToolsIntent.SetFilesFolder -> scope.launch { files(d, tvId, event.folder, send) }
            is TvToolsIntent.PickSendFile -> effect(ManageEffect.PickSendFile(event.folder))
            is TvToolsIntent.SendFile -> scope.launch { sendFile(d, tvId, event, send) }
            is TvToolsIntent.PullSharedFile -> scope.launch { pullFile(d, tvId, event.folder, event.name, send, effect) }
            is TvToolsIntent.AddDock -> scope.launch { addDock(d, tvId, event.pkg, send) }
            is TvToolsIntent.RemoveDock -> scope.launch { removeDock(d, tvId, event.pkg, send) }
            is TvToolsIntent.LaunchDock -> scope.launch { launchDock(d, tvId, event.pkg, send) }
            TvToolsIntent.RestartTv -> scope.launch { restart(d, tvId, send) }
            is TvToolsIntent.SetPicture -> scope.launch { picture(d, tvId, event.on, pictureJar, send) }
            TvToolsIntent.RefreshKeys -> scope.launch { keys(d, tvId, phoneKey, send) }
            TvToolsIntent.AskRemoveOtherKeys -> send(ExtrasLoaded(confirmKeys = true))
            TvToolsIntent.ConfirmRemoveOtherKeys -> scope.launch { removeOtherKeys(d, tvId, phoneKey, send) }
            is TvToolsIntent.UndoChange -> scope.launch { undo(d, tvId, event.id, send) }
            TvToolsIntent.RestoreOurChanges -> scope.launch { restore(d, tvId, send) }
            else -> {}
        }
    }

    suspend fun loadSaved(d: ManageDeps, tvId: TvId, send: (Any) -> Unit) {
        val log = readLog(d, tvId)
        val dock = readDock(d, tvId)
        send(ExtrasLoaded(dock = dock, changes = views(log)))
    }

    private suspend fun monitor(d: ManageDeps, tvId: TvId, send: (Any) -> Unit) {
        val session = openSession(d, tvId) ?: run {
            send(ExtrasLoaded(filesNote = null, screenNote = note("لا يوجد اتصال بهذا التلفاز المحفوظ. لم يُفتح اتصال جديد.", "No connection to this saved TV. No new connection was opened.")))
            return
        }
        send(ExtrasLoaded(busy = true))
        val thermal = out(session, TellyShell.dumpsysThermalLine())
        val cpu = out(session, TellyShell.dumpsysCpuLine(), cap = ShellRenderer.CAP_DUMP)
        val mem = out(session, TellyShell.dumpsysMemLine(), cap = ShellRenderer.CAP_DUMP)
        val usb = out(session, TellyShell.dumpsysUsbLine())
        val bt = out(session, TellyShell.dumpsysBluetoothLine(), cap = ShellRenderer.CAP_LIST)
        val first = TellyFeatures.parseNetBytes(out(session, TellyShell.procNetDevLine()))
        delay(1000)
        val second = TellyFeatures.parseNetBytes(out(session, TellyShell.procNetDevLine()))
        val speed = if (first != null && second != null) TellyFeatures.netSpeed(first, second, 1000) else null
        val apps = (TellyFeatures.parseCpuApps(cpu) + TellyFeatures.parseMemApps(mem)).distinct().take(8)
        send(ExtrasLoaded(
            monitor = MonitorView(TellyFeatures.parseThermal(thermal), speed, apps, TellyFeatures.parseConnected(usb, bt)),
            busy = false,
        ))
    }

    private suspend fun files(d: ManageDeps, tvId: TvId, folderId: String, send: (Any) -> Unit) {
        val path = TellyFeatures.folderPath(folderId) ?: return
        val session = openSession(d, tvId) ?: run {
            send(ExtrasLoaded(folder = folderId, filesNote = note("لا يوجد اتصال بهذا التلفاز المحفوظ.", "No connection to this saved TV.")))
            return
        }
        attempt { TvShellRunner.run(session, TellyShell.mkdirSharedFolderLine(path)) }
        val listed = out(session, TellyShell.listSharedFolderLine(path))
        send(ExtrasLoaded(folder = folderId, files = TellyFeatures.parseNames(listed), filesNote = ""))
    }

    private suspend fun sendFile(d: ManageDeps, tvId: TvId, event: TvToolsIntent.SendFile, send: (Any) -> Unit) {
        val path = TellyFeatures.folderPath(event.folder) ?: return
        val session = openSession(d, tvId) ?: return
        send(ExtrasLoaded(busy = true, folder = event.folder))
        try {
            attempt { TvShellRunner.run(session, ShellRenderer.mkdirTmpLine()) }
            attempt { TvShellRunner.run(session, TellyShell.mkdirSharedFolderLine(path)) }
            val listed = TellyFeatures.parseNames(out(session, TellyShell.listSharedFolderLine(path))).toSet()
            val name = TellyFeatures.uniqueName(event.fileName, listed)
            val pushed = attempt { session.push(event.blob, STAGE) {} }.getOrNull()
            val copied = if (pushed != null) {
                attempt { TvShellRunner.run(session, TellyShell.copyToSharedFolderLine(STAGE, path, name)) }.getOrNull()
            } else null
            if (copied != null && copied.exit == 0) {
                send(ExtrasLoaded(busy = false, filesNote = note("حُفظ في المجلد باسم $name.", "Saved in the folder as $name.")))
                files(d, tvId, event.folder, send)
            } else {
                send(ExtrasLoaded(busy = false, filesNote = note("لم يُنسخ الملف. لم يُستبدل شيء.", "The file was not copied. Nothing was replaced.")))
            }
        } finally {
            withContext(NonCancellable) {
                attempt { TvShellRunner.run(session, ShellRenderer.removeTmpLine(STAGE)) }
                attempt { d.blobs.delete(event.blob.sha256) }
            }
        }
    }

    private suspend fun pullFile(
        d: ManageDeps,
        tvId: TvId,
        folderId: String,
        name: String,
        send: (Any) -> Unit,
        effect: (ManageEffect) -> Unit,
    ) {
        val path = TellyFeatures.folderPath(folderId) ?: return
        if (TellyFeatures.parseNames(name) != listOf(name)) return
        val session = openSession(d, tvId) ?: return
        try {
            attempt { TvShellRunner.run(session, ShellRenderer.mkdirTmpLine()) }
            val copied = attempt { TvShellRunner.run(session, TellyShell.copyFromSharedFolderLine(path, name, STAGE)) }.getOrNull()
            val bytes = if (copied != null && copied.exit == 0) attempt { session.pull(STAGE, ShellRenderer.MAX_APK_BYTES) }.getOrNull() else null
            if (bytes != null && bytes.isNotEmpty()) {
                effect(ManageEffect.SaveFile(bytes, TellyFeatures.safeFileName(name), mime(name)))
                send(ExtrasLoaded(filesNote = note("نُسخ الملف إلى الهاتف.", "Copied to the phone.")))
            } else {
                send(ExtrasLoaded(filesNote = note("تعذر سحب الملف.", "Could not copy the file.")))
            }
        } finally {
            withContext(NonCancellable) { attempt { TvShellRunner.run(session, ShellRenderer.removeTmpLine(STAGE)) } }
        }
    }

    private suspend fun addDock(d: ManageDeps, tvId: TvId, pkg: String, send: (Any) -> Unit) {
        val next = TellyFeatures.addDock(readDock(d, tvId), pkg) ?: return
        writeDock(d, tvId, next)
        send(ExtrasLoaded(dock = next))
    }

    private suspend fun removeDock(d: ManageDeps, tvId: TvId, pkg: String, send: (Any) -> Unit) {
        val next = readDock(d, tvId).filterNot { it == pkg }
        writeDock(d, tvId, next)
        send(ExtrasLoaded(dock = next))
    }

    private suspend fun launchDock(d: ManageDeps, tvId: TvId, pkg: String, send: (Any) -> Unit) {
        val name = runCatching { PackageName(pkg) }.getOrNull() ?: return
        val session = openSession(d, tvId) ?: return
        val line = runCatching { ShellRenderer.launchLeanbackPackageLine(name) }.getOrNull() ?: return
        val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
        if (res == null || res.exit != 0) {
            val fallback = runCatching { ShellRenderer.launchPackageLine(name) }.getOrNull()
            if (fallback != null) attempt { TvShellRunner.run(session, fallback) }
        }
        send(ExtrasLoaded())
    }

    private suspend fun restart(d: ManageDeps, tvId: TvId, send: (Any) -> Unit) {
        val session = openSession(d, tvId) ?: run {
            send(ExtrasLoaded(screenNote = note("لا يوجد اتصال. لم تُعد تشغيل تلفاز آخر.", "No connection. A different TV was not restarted.")))
            return
        }
        val direct = attempt { session.reboot() }.isSuccess
        if (!direct) attempt { TvShellRunner.run(session, TellyShell.svcRebootLine()) }
        send(ExtrasLoaded(screenNote = note("أُرسلت إعادة التشغيل إلى هذا التلفاز.", "Restart was sent to this TV.")))
    }

    private suspend fun picture(
        d: ManageDeps,
        tvId: TvId,
        on: Boolean,
        pictureJar: () -> ByteArray?,
        send: (Any) -> Unit,
    ) {
        val session = openSession(d, tvId) ?: return
        val refusal = TellyFeatures.screenOffRefusal(session.identity.sdk, arabic())
        if (refusal != null) {
            send(ExtrasLoaded(screenNote = refusal))
            return
        }
        val jar = pictureJar()
        if (jar == null || jar.isEmpty()) {
            send(ExtrasLoaded(screenNote = note("أداة إطفاء الصورة غير موجودة. لم يُرسل أمر.", "The picture-off helper is missing. No command was sent.")))
            return
        }
        val blob = blobOf(d, jar) ?: return
        try {
            attempt { TvShellRunner.run(session, ShellRenderer.mkdirTmpLine()) }
            val pushed = attempt { session.push(blob, JAR) {} }.getOrNull()
            if (pushed == null) {
                send(ExtrasLoaded(screenNote = note("تعذر إرسال الأداة. لم تتغير الصورة.", "The helper was not sent. The picture was not changed.")))
                return
            }
            val res = attempt { TvShellRunner.run(session, TellyShell.pictureOffLine(JAR, on), timeoutMs = 20_000) }.getOrNull()
            val ok = res != null && res.exit == 0 && res.stdout.contains("1")
            send(ExtrasLoaded(screenNote = if (ok) {
                if (on) note("عادت الصورة.", "The picture is back.")
                else note("أُطفئت الصورة. الصوت يبقى، والاتصال يبقى.", "The picture is off. Audio and the connection stay.")
            } else note("التلفاز رفض إطفاء الصورة. لم يُغلق الجهاز.", "The TV refused to turn the picture off. It was not shut down.")))
        } finally {
            withContext(NonCancellable) {
                attempt { TvShellRunner.run(session, ShellRenderer.removeTmpLine(JAR)) }
                attempt { d.blobs.delete(blob.sha256) }
            }
        }
    }

    private suspend fun keys(d: ManageDeps, tvId: TvId, phoneKey: () -> String?, send: (Any) -> Unit) {
        val session = openSession(d, tvId) ?: run {
            send(ExtrasLoaded(adbNote = note("لا يوجد اتصال بهذا التلفاز. لم تُفتح نافذة سماح.", "No connection to this TV. No Allow dialog was opened.")))
            return
        }
        val listed = TellyFeatures.parseKeys(out(session, TellyShell.dumpsysAdbLine(), cap = ShellRenderer.CAP_DUMP), phoneKey())
        if (listed == null) {
            send(ExtrasLoaded(adbKeys = emptyList(), adbNote = note("التلفاز لا يعرض مفاتيح ADB. لم يُحذف شيء.", "The TV will not list ADB keys. Nothing was removed.")))
            return
        }
        send(ExtrasLoaded(
            adbKeys = listed.map { c ->
                val who = c.name ?: c.fingerprint
                if (c.ours) if (arabic()) "$who — هذا الهاتف" else "$who — this phone" else who
            },
            adbNote = if (listed.any { it.ours }) "" else note("هذا الهاتف غير ظاهر في القائمة. لن نحذف مفاتيح.", "This phone is not in the list. No keys will be removed."),
            confirmKeys = false,
        ))
    }

    private suspend fun removeOtherKeys(d: ManageDeps, tvId: TvId, phoneKey: () -> String?, send: (Any) -> Unit) {
        val session = openSession(d, tvId) ?: return
        val listed = TellyFeatures.parseKeys(out(session, TellyShell.dumpsysAdbLine(), cap = ShellRenderer.CAP_DUMP), phoneKey())
        val kept = listed?.let(TellyFeatures::keptKeyFile)
        if (kept == null) {
            send(ExtrasLoaded(confirmKeys = false, adbNote = note("لن نحذف المفاتيح لأن مفتاح هذا الهاتف غير معروف. لم يُرسل أمر.", "Keys were not removed because this phone's key is unknown. No command was sent.")))
            return
        }
        val blob = blobOf(d, kept.toByteArray()) ?: return
        try {
            attempt { TvShellRunner.run(session, ShellRenderer.mkdirTmpLine()) }
            val pushed = attempt { session.push(blob, KEYS) {} }.getOrNull()
            val copied = if (pushed != null) attempt { TvShellRunner.run(session, TellyShell.copyKeptKeysLine(KEYS)) }.getOrNull() else null
            if (copied != null && copied.exit == 0) {
                send(ExtrasLoaded(confirmKeys = false, adbNote = note("بقيت مفاتيح هذا الهاتف فقط، إن سمح التلفاز بالكتابة.", "Only this phone's keys remain, if the TV allowed the write.")))
                keys(d, tvId, phoneKey, send)
            } else {
                send(ExtrasLoaded(confirmKeys = false, adbNote = note("التلفاز رفض تعديل ملف المفاتيح. لم يُمسح تفويض هذا الهاتف.", "The TV refused to change the key file. This phone's authorisation was not cleared.")))
            }
        } finally {
            withContext(NonCancellable) {
                attempt { TvShellRunner.run(session, ShellRenderer.removeTmpLine(KEYS)) }
                attempt { d.blobs.delete(blob.sha256) }
            }
        }
    }

    suspend fun noteChange(d: ManageDeps, tvId: TvId, session: TvSession?, disable: Boolean, pkg: PackageName, label: String) {
        val log = readLog(d, tvId)
        if (!log.hasBaseline() && session != null) {
            val disabled = out(session, ShellRenderer.listDisabledPackagesLine()).lineSequence()
                .map { it.trim().removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            log.setBaseline(disabled)
        } else if (!log.hasBaseline()) {
            log.setBaseline(emptySet())
        }
        val action = if (disable) TellyFeatures.ChangeAction.DISABLE else TellyFeatures.ChangeAction.ENABLE
        log.record(d.clock.nowMs(), action, pkg.value, label)
        if (!disable) log.openDisable(pkg.value)?.let { log.markUndone(it.id, d.clock.nowMs()) }
        writeLog(d, tvId, log)
    }

    private suspend fun undo(d: ManageDeps, tvId: TvId, id: Long, send: (Any) -> Unit) {
        val log = readLog(d, tvId)
        val entry = log.recent().firstOrNull { it.id == id && it.undoneAt == null && it.action == TellyFeatures.ChangeAction.DISABLE } ?: return
        val pkg = runCatching { PackageName(entry.pkg) }.getOrNull() ?: return
        val session = openSession(d, tvId) ?: return
        val lines = runCatching { ShellRenderer.enableUserPackageLines(pkg) }.getOrNull() ?: return
        var ok = false
        for (line in lines) {
            if (TellyFeatures.usesUserZeroUninstall(line.text)) return
            val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
            if (res != null && res.exit == 0) ok = true
        }
        if (ok) {
            log.markUndone(id, d.clock.nowMs())
            log.record(d.clock.nowMs(), TellyFeatures.ChangeAction.ENABLE, entry.pkg, entry.label)
            writeLog(d, tvId, log)
            send(ExtrasLoaded(changes = views(log), screenNote = note("أُعيد تفعيل ${entry.label}.", "${entry.label} was switched back on.")))
        }
    }

    private suspend fun restore(d: ManageDeps, tvId: TvId, send: (Any) -> Unit) {
        val log = readLog(d, tvId)
        val session = openSession(d, tvId) ?: return
        for (pkg in log.restorable()) {
            val name = runCatching { PackageName(pkg) }.getOrNull() ?: continue
            val lines = runCatching { ShellRenderer.enableUserPackageLines(name) }.getOrNull() ?: continue
            var ok = false
            for (line in lines) {
                if (TellyFeatures.usesUserZeroUninstall(line.text)) continue
                val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
                if (res != null && res.exit == 0) ok = true
            }
            if (ok) {
                log.openDisable(pkg)?.let { log.markUndone(it.id, d.clock.nowMs()) }
                log.record(d.clock.nowMs(), TellyFeatures.ChangeAction.ENABLE, pkg, pkg)
            }
        }
        writeLog(d, tvId, log)
        send(ExtrasLoaded(changes = views(log), screenNote = note("أُعيد تشغيل ما أوقفه هذا التطبيق فقط.", "Only what this app switched off was turned back on.")))
    }

    suspend fun reviewInstall(d: ManageDeps, tvId: TvId, ready: SideloadStatus.Ready, send: (Any) -> Unit): Boolean {
        val pkg = ready.packageName ?: return false
        val session = openSession(d, tvId) ?: return false
        val name = runCatching { PackageName(pkg) }.getOrNull() ?: return false
        val dump = out(session, TellyShell.dumpsysPackageLine(name), cap = ShellRenderer.CAP_DUMP)
        val installed = TellyFeatures.parseInstalledVersion(dump, pkg)
        val kind = TellyFeatures.installKind(ready.versionCode, installed)
        send(SideloadProgress(SideloadStatus.NeedsConfirm(ready, TellyFeatures.installMessage(kind, pkg, arabic()), kind == TellyFeatures.InstallKind.OLDER)))
        return true
    }

    suspend fun installParts(d: ManageDeps, tvId: TvId, ready: SideloadStatus.Ready, downgrade: Boolean, send: (Any) -> Unit, effect: (ManageEffect) -> Unit): Boolean {
        val session = openSession(d, tvId) ?: run {
            send(SideloadProgress(SideloadStatus.Failed(ready.fileName)))
            return true
        }
        val parts = ready.parts.ifEmpty { listOf(InstallPart("base.apk", ready.sizeBytes, ready.blob)) }
        if (parts.size == 1 && !downgrade) return false
        send(SideloadProgress(SideloadStatus.Installing(ready.fileName, 5)))
        var sessionId: String? = null
        try {
            attempt { TvShellRunner.run(session, ShellRenderer.mkdirTmpLine()) }
            if (parts.size == 1) {
                val remote = RemotePath(RemoteDir.TMP, listOf("one.apk"))
                val pushed = attempt { session.push(parts[0].blob, remote) {} }.getOrNull()
                val res = if (pushed != null) {
                    attempt { TvShellRunner.run(session, TellyShell.pmInstallReplaceLine(remote, downgrade), timeoutMs = ShellRenderer.T_INSTALL) }.getOrNull()
                } else null
                withContext(NonCancellable) { attempt { TvShellRunner.run(session, ShellRenderer.removeTmpLine(remote)) } }
                val out = res?.stdout.orEmpty() + res?.stderr.orEmpty()
                if (res != null && res.exit == 0 && "Success" in out) {
                    send(SideloadProgress(SideloadStatus.Installed(ready.fileName)))
                    effect(ManageEffect.HapticDouble)
                } else send(SideloadProgress(SideloadStatus.Failed(ready.fileName)))
                return true
            }
            val total = parts.sumOf { it.sizeBytes }.coerceAtLeast(1L)
            val created = attempt {
                TvShellRunner.run(session, TellyShell.installCreateLine(total, downgrade), timeoutMs = ShellRenderer.T_PM)
            }.getOrNull()
            sessionId = created?.let { TellyShell.parseInstallSession(it.stdout + it.stderr) }
            if (sessionId == null) {
                send(SideloadProgress(SideloadStatus.Failed(ready.fileName, "session")))
                return true
            }
            parts.forEachIndexed { index, part ->
                val remote = RemotePath(RemoteDir.TMP, listOf("s$index.apk"))
                val pushed = attempt { session.push(part.blob, remote) {} }.getOrNull()
                val wrote = if (pushed != null) {
                    attempt {
                        TvShellRunner.run(session, TellyShell.installWriteLine(sessionId!!, part.sizeBytes, part.name, remote), timeoutMs = ShellRenderer.T_INSTALL)
                    }.getOrNull()
                } else null
                withContext(NonCancellable) { attempt { TvShellRunner.run(session, ShellRenderer.removeTmpLine(remote)) } }
                if (wrote == null || wrote.exit != 0 || "Success" !in (wrote.stdout + wrote.stderr)) {
                    send(SideloadProgress(SideloadStatus.Failed(ready.fileName, "write")))
                    return true
                }
                send(SideloadProgress(SideloadStatus.Installing(ready.fileName, (20 + index * 60 / parts.size).coerceIn(0, 90))))
            }
            val committed = attempt {
                TvShellRunner.run(session, TellyShell.installCommitLine(sessionId!!), timeoutMs = ShellRenderer.T_INSTALL)
            }.getOrNull()
            val out = committed?.stdout.orEmpty() + committed?.stderr.orEmpty()
            if (committed != null && committed.exit == 0 && "Success" in out) {
                sessionId = null
                send(SideloadProgress(SideloadStatus.Installed(ready.fileName)))
                effect(ManageEffect.HapticDouble)
            } else send(SideloadProgress(SideloadStatus.Failed(ready.fileName)))
        } finally {
            val id = sessionId
            if (id != null) withContext(NonCancellable) { attempt { TvShellRunner.run(session, TellyShell.installAbandonLine(id)) } }
            withContext(NonCancellable) {
                (ready.parts.map { it.blob.sha256 } + ready.blob.sha256).distinct().forEach { attempt { d.blobs.delete(it) } }
            }
        }
        return true
    }

    private fun views(log: TellyFeatures.ChangeLog): List<ChangeView> = log.recent().map {
        ChangeView(it.id, it.label, it.pkg, it.undoneAt == null && it.action == TellyFeatures.ChangeAction.DISABLE)
    }

    private suspend fun readLog(d: ManageDeps, tvId: TvId): TellyFeatures.ChangeLog =
        TellyFeatures.ChangeLog(d.documents.read(logPath(tvId)) { it.toString(Charsets.UTF_8) }.orEmpty())

    private suspend fun writeLog(d: ManageDeps, tvId: TvId, log: TellyFeatures.ChangeLog) {
        d.documents.write(logPath(tvId), log.encode().toByteArray())
    }

    private suspend fun readDock(d: ManageDeps, tvId: TvId): List<String> =
        d.documents.read(dockPath(tvId)) { it.toString(Charsets.UTF_8) }.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(TellyFeatures.MAX_DOCK).toList()

    private suspend fun writeDock(d: ManageDeps, tvId: TvId, dock: List<String>) {
        d.documents.write(dockPath(tvId), dock.joinToString("\n").toByteArray())
    }

    private fun logPath(tvId: TvId) = "extras-${tvId.value}-log"
    private fun dockPath(tvId: TvId) = "extras-${tvId.value}-dock"

    private suspend fun openSession(d: ManageDeps, tvId: TvId): TvSession? = (d.access.session(tvId) as? AccessResult.Ok)?.session

    private suspend fun out(session: TvSession, line: tv.cleanyour.core.model.ShellLine, cap: Int = ShellRenderer.CAP_DEFAULT): String {
        val res = attempt { TvShellRunner.run(session, line, stdoutCap = cap) }.getOrNull() ?: return ""
        return res.stdout
    }

    private suspend fun blobOf(d: ManageDeps, bytes: ByteArray): BlobRef? = attempt {
        val sha = Sha256(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        d.blobs.put(sha, bytes.inputStream(), bytes.size.toLong())
    }.getOrNull()

    private fun mime(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "mp4", "mkv" -> "video/*"
        "jpg", "jpeg", "png" -> "image/*"
        "apk" -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }

    private fun arabic() = java.util.Locale.getDefault().language == "ar"
    private fun note(ar: String, en: String) = if (arabic()) ar else en
}
