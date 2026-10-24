package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.model.TvKeyEvent
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.probe.TvShellRunner
import tv.cleanyour.core.session.InstallOutcome

sealed interface RemoteStatus {
    data object Idle : RemoteStatus
    data object Sending : RemoteStatus
    data object Sent : RemoteStatus
    data object Failed : RemoteStatus
}

sealed interface ScreenshotStatus {
    data object Idle : ScreenshotStatus
    data object Capturing : ScreenshotStatus
    data class Captured(val png: ByteArray) : ScreenshotStatus
    data object Failed : ScreenshotStatus
}

sealed interface SideloadStatus {
    data object Idle : SideloadStatus
    data class Ready(val fileName: String, val sizeBytes: Long, val blob: BlobRef) : SideloadStatus
    data class Installing(val fileName: String, val percent: Int) : SideloadStatus
    data class Installed(val fileName: String) : SideloadStatus
    data class Failed(val fileName: String, val detail: String? = null) : SideloadStatus
}

data class TvToolsState(
    val tvName: String = "",
    val textInput: String = "",
    val remoteStatus: RemoteStatus = RemoteStatus.Idle,
    val screenshotStatus: ScreenshotStatus = ScreenshotStatus.Idle,
    val sideloadStatus: SideloadStatus = SideloadStatus.Idle,
)

sealed interface TvToolsIntent {
    data class PressKey(val key: TvKeyEvent) : TvToolsIntent
    data object OpenTvSettings : TvToolsIntent
    data class UpdateText(val text: String) : TvToolsIntent
    data object SendText : TvToolsIntent
    data object TakeScreenshot : TvToolsIntent
    data object ShareScreenshot : TvToolsIntent
    data object PickApk : TvToolsIntent
    data class ApkPicked(val fileName: String, val sizeBytes: Long, val blob: BlobRef) : TvToolsIntent
    data object ClearApk : TvToolsIntent
    data object InstallApk : TvToolsIntent
}

class TvToolsMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
) : Machine<TvToolsState, TvToolsIntent, ManageEffect>(TvToolsState(), scope) {

    private data class Loaded(val tvName: String)
    private data class RemoteUpdate(val status: RemoteStatus, val clearText: Boolean = false)
    private data class ScreenshotUpdate(val status: ScreenshotStatus)
    private data class SideloadUpdate(val status: SideloadStatus)

    init {
        scope.launch {
            d.tvs.tvs.collect { list ->
                list.firstOrNull { it.id == tvId }?.let { tv -> send(Loaded(tv.nickname)) }
            }
        }
    }

    override fun reduce(state: TvToolsState, event: Any): TvToolsState = when (event) {
        is Loaded -> state.copy(tvName = event.tvName)
        is TvToolsIntent.UpdateText -> state.copy(textInput = event.text.take(ShellRenderer.MAX_INPUT_TEXT_LEN))
        is RemoteUpdate -> state.copy(
            remoteStatus = event.status,
            textInput = if (event.clearText) "" else state.textInput,
        )
        is ScreenshotUpdate -> state.copy(screenshotStatus = event.status)
        is TvToolsIntent.ApkPicked -> state.copy(
            sideloadStatus = SideloadStatus.Ready(event.fileName, event.sizeBytes, event.blob),
        )
        TvToolsIntent.ClearApk -> state.copy(sideloadStatus = SideloadStatus.Idle)
        is SideloadUpdate -> state.copy(sideloadStatus = event.status)
        else -> state
    }

    override fun handle(event: Any, before: TvToolsState, after: TvToolsState) {
        when (event) {
            is TvToolsIntent.PressKey -> scope.launch { runRemoteLine(ShellRenderer.keyEventLine(event.key), clearText = false) }
            TvToolsIntent.OpenTvSettings -> scope.launch { runRemoteLine(ShellRenderer.openSettingsLine(), clearText = false) }
            TvToolsIntent.SendText -> {
                val raw = before.textInput
                if (raw.isNotBlank()) scope.launch {
                    val line = runCatching { ShellRenderer.inputTextLine(raw) }.getOrNull()
                    if (line == null) {
                        send(RemoteUpdate(RemoteStatus.Failed))
                    } else {
                        runRemoteLine(line, clearText = true)
                    }
                }
            }
            TvToolsIntent.TakeScreenshot -> if (before.screenshotStatus !is ScreenshotStatus.Capturing) {
                scope.launch { captureScreenshot() }
            }
            TvToolsIntent.ShareScreenshot -> {
                val captured = before.screenshotStatus as? ScreenshotStatus.Captured ?: return
                effect(ManageEffect.SharePng(captured.png, "mi5g-tv-screenshot-${d.clock.nowMs()}.png"))
            }
            TvToolsIntent.PickApk -> if (before.sideloadStatus !is SideloadStatus.Installing) {
                effect(ManageEffect.PickApk)
            }
            TvToolsIntent.ClearApk -> {
                val prev = before.sideloadStatus as? SideloadStatus.Ready
                if (prev != null) scope.launch { attempt { d.blobs.delete(prev.blob.sha256) } }
            }
            TvToolsIntent.InstallApk -> {
                val ready = before.sideloadStatus as? SideloadStatus.Ready
                if (ready != null) scope.launch { installApk(ready) }
            }
            else -> {}
        }
    }

    private suspend fun runRemoteLine(line: tv.cleanyour.core.model.ShellLine, clearText: Boolean) {
        send(RemoteUpdate(RemoteStatus.Sending))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(RemoteUpdate(RemoteStatus.Failed))
            return
        }
        val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
        if (res != null && res.exit == 0) {
            send(RemoteUpdate(RemoteStatus.Sent, clearText = clearText))
            effect(if (clearText) ManageEffect.HapticDouble else ManageEffect.HapticLight)
        } else {
            send(RemoteUpdate(RemoteStatus.Failed))
        }
    }

    private suspend fun captureScreenshot() {
        send(ScreenshotUpdate(ScreenshotStatus.Capturing))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(ScreenshotUpdate(ScreenshotStatus.Failed))
            return
        }
        val png = attempt { d.prober.screenshot(session) }.getOrNull()
        if (png != null) {
            send(ScreenshotUpdate(ScreenshotStatus.Captured(png)))
            effect(ManageEffect.HapticDouble)
            effect(ManageEffect.SharePng(png, "mi5g-tv-screenshot-${d.clock.nowMs()}.png"))
        } else {
            send(ScreenshotUpdate(ScreenshotStatus.Failed))
        }
    }

    private suspend fun installApk(ready: SideloadStatus.Ready) {
        send(SideloadUpdate(SideloadStatus.Installing(ready.fileName, 0)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(SideloadUpdate(SideloadStatus.Failed(ready.fileName)))
            return
        }
        d.sessionEnd.touched(tvId)
        val total = ready.sizeBytes.coerceAtLeast(1L)
        try {
            if ("cmd" in session.identity.features) {
                val outcome = attempt {
                    session.installStream(ready.blob, ready.sizeBytes) { sent ->
                        val pct = ((sent * 95L) / total).toInt().coerceIn(0, 95)
                        send(SideloadUpdate(SideloadStatus.Installing(ready.fileName, pct)))
                    }
                }.getOrNull()
                when (outcome) {
                    InstallOutcome.Installed -> {
                        send(SideloadUpdate(SideloadStatus.Installed(ready.fileName)))
                        effect(ManageEffect.HapticDouble)
                    }
                    is InstallOutcome.Failed -> {
                        send(SideloadUpdate(SideloadStatus.Failed(ready.fileName, outcome.code)))
                    }
                    null -> send(SideloadUpdate(SideloadStatus.Failed(ready.fileName)))
                }
            } else {
                val remoteApk = RemotePath(RemoteDir.TMP, listOf(ready.blob.sha256.hex.take(40) + ".apk"))
                try {
                    attempt { TvShellRunner.run(session, ShellRenderer.mkdirTmpLine()) }
                    val pushed = attempt {
                        session.push(ready.blob, remoteApk) { sent ->
                            val pct = ((sent * 80L) / total).toInt().coerceIn(0, 80)
                            send(SideloadUpdate(SideloadStatus.Installing(ready.fileName, pct)))
                        }
                    }.getOrNull()
                    if (pushed == null) {
                        send(SideloadUpdate(SideloadStatus.Failed(ready.fileName)))
                        return
                    }
                    send(SideloadUpdate(SideloadStatus.Installing(ready.fileName, 90)))
                    val res = attempt {
                        TvShellRunner.run(session, ShellRenderer.pmInstallLine(remoteApk), timeoutMs = ShellRenderer.T_INSTALL)
                    }.getOrNull()
                    val out = (res?.stdout.orEmpty() + "\n" + res?.stderr.orEmpty())
                    if (res != null && res.exit == 0 && "Success" in out) {
                        send(SideloadUpdate(SideloadStatus.Installed(ready.fileName)))
                        effect(ManageEffect.HapticDouble)
                    } else {
                        send(SideloadUpdate(SideloadStatus.Failed(ready.fileName)))
                    }
                } finally {
                    withContext(NonCancellable) {
                        attempt { TvShellRunner.run(session, ShellRenderer.removeTmpLine(remoteApk)) }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                attempt { d.blobs.delete(ready.blob.sha256) }
            }
        }
    }
}
