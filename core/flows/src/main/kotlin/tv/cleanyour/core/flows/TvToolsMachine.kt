package tv.cleanyour.core.flows

import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.PACKAGE_RE
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.TvAnimationScale
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.model.TvKeyEvent
import tv.cleanyour.core.ops.Denylist
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

data class TvPerformanceStats(
    val ramUsedGb: String,
    val ramTotalGb: String,
    val ramFraction: Float,
    val storageFreeGb: String,
    val storageTotalGb: String,
    val storageUsedFraction: Float,
) {
    companion object {
        private val MEM_TOTAL_RE = Regex("^MemTotal:\\s+(\\d+)\\s+kB", RegexOption.MULTILINE)
        private val MEM_AVAIL_RE = Regex("^MemAvailable:\\s+(\\d+)\\s+kB", RegexOption.MULTILINE)
        private val MEM_FREE_RE = Regex("^MemFree:\\s+(\\d+)\\s+kB", RegexOption.MULTILINE)

        fun parse(memInfoOut: String, dfOut: String): TvPerformanceStats? {
            val totalKb = MEM_TOTAL_RE.find(memInfoOut)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            if (totalKb <= 0L) return null
            val availKb = MEM_AVAIL_RE.find(memInfoOut)?.groupValues?.get(1)?.toLongOrNull()
                ?: MEM_FREE_RE.find(memInfoOut)?.groupValues?.get(1)?.toLongOrNull()
                ?: return null
            val usedKb = (totalKb - availKb).coerceIn(0L, totalKb)

            val dfRows = dfOut.lineSequence()
                .map { it.trim().split(Regex("\\s+")) }
                .filter { it.size >= 6 && it[1].toLongOrNull() != null && it[3].toLongOrNull() != null }
                .toList()
            val dataRow = dfRows.lastOrNull { it.last() == "/data" } ?: dfRows.lastOrNull() ?: return null
            val totalDataKb = dataRow[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
            val availDataKb = dataRow[3].toLongOrNull()?.coerceIn(0L, totalDataKb) ?: return null
            val usedDataKb = (totalDataKb - availDataKb).coerceIn(0L, totalDataKb)

            fun gb(kb: Long): String = String.format(Locale.US, "%.1f", kb / (1024.0 * 1024.0))
            return TvPerformanceStats(
                ramUsedGb = gb(usedKb),
                ramTotalGb = gb(totalKb),
                ramFraction = (usedKb.toFloat() / totalKb.toFloat()).coerceIn(0f, 1f),
                storageFreeGb = gb(availDataKb),
                storageTotalGb = gb(totalDataKb),
                storageUsedFraction = (usedDataKb.toFloat() / totalDataKb.toFloat()).coerceIn(0f, 1f),
            )
        }
    }
}

sealed interface SpeedBoostStatus {
    data object Idle : SpeedBoostStatus
    data object Boosting : SpeedBoostStatus
    data class Boosted(val stoppedApps: Int) : SpeedBoostStatus
    data object Failed : SpeedBoostStatus
}

sealed interface AnimationScaleStatus {
    data object Idle : AnimationScaleStatus
    data object Applying : AnimationScaleStatus
    data class Applied(val scale: TvAnimationScale) : AnimationScaleStatus
    data object Failed : AnimationScaleStatus
}

sealed interface CastUrlStatus {
    data object Idle : CastUrlStatus
    data object Opening : CastUrlStatus
    data class Opened(val url: String) : CastUrlStatus
    data object InvalidUrl : CastUrlStatus
    data object Failed : CastUrlStatus
}

sealed interface CastMediaStatus {
    data object Idle : CastMediaStatus
    data class Casting(val fileName: String, val percent: Int) : CastMediaStatus
    data class CastDone(val fileName: String) : CastMediaStatus
    data class Failed(val fileName: String) : CastMediaStatus
}

data class TvToolsState(
    val tvName: String = "",
    val textInput: String = "",
    val remoteStatus: RemoteStatus = RemoteStatus.Idle,
    val screenshotStatus: ScreenshotStatus = ScreenshotStatus.Idle,
    val sideloadStatus: SideloadStatus = SideloadStatus.Idle,
    val performanceStats: TvPerformanceStats? = null,
    val speedBoostStatus: SpeedBoostStatus = SpeedBoostStatus.Idle,
    val animationScale: TvAnimationScale = TvAnimationScale.DEFAULT_1X,
    val animationScaleStatus: AnimationScaleStatus = AnimationScaleStatus.Idle,
    val castUrlInput: String = "",
    val castUrlStatus: CastUrlStatus = CastUrlStatus.Idle,
    val castMediaStatus: CastMediaStatus = CastMediaStatus.Idle,
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
    data object RefreshPerformance : TvToolsIntent
    data object RunSpeedBoost : TvToolsIntent
    data class SetAnimationScale(val scale: TvAnimationScale) : TvToolsIntent
    data class UpdateCastUrl(val url: String) : TvToolsIntent
    data object OpenUrlOnTv : TvToolsIntent
    data object PickCastMedia : TvToolsIntent
    data class MediaPicked(
        val fileName: String,
        val mimeType: String,
        val ext: String,
        val sizeBytes: Long,
        val blob: BlobRef,
    ) : TvToolsIntent
    data object OpenAppManager : TvToolsIntent
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
    private data class PerformanceLoaded(val stats: TvPerformanceStats?, val scale: TvAnimationScale?)
    private data class SpeedBoostUpdate(val status: SpeedBoostStatus)
    private data class AnimationScaleUpdate(val scale: TvAnimationScale, val status: AnimationScaleStatus)
    private data class CastUrlUpdate(val status: CastUrlStatus, val clearInput: Boolean = false)
    private data class CastMediaUpdate(val status: CastMediaStatus)

    init {
        scope.launch {
            d.tvs.tvs.collect { list ->
                list.firstOrNull { it.id == tvId }?.let { tv -> send(Loaded(tv.nickname)) }
            }
        }
        scope.launch { loadPerformance(liveOnly = true) }
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
        is PerformanceLoaded -> state.copy(
            performanceStats = event.stats ?: state.performanceStats,
            animationScale = event.scale ?: state.animationScale,
        )
        is SpeedBoostUpdate -> state.copy(speedBoostStatus = event.status)
        is AnimationScaleUpdate -> state.copy(
            animationScale = event.scale,
            animationScaleStatus = event.status,
        )
        is TvToolsIntent.UpdateCastUrl -> state.copy(
            castUrlInput = event.url.take(ShellRenderer.MAX_URL_LEN),
            castUrlStatus = if (state.castUrlStatus is CastUrlStatus.InvalidUrl) CastUrlStatus.Idle else state.castUrlStatus,
        )
        is CastUrlUpdate -> state.copy(
            castUrlStatus = event.status,
            castUrlInput = if (event.clearInput) "" else state.castUrlInput,
        )
        is CastMediaUpdate -> state.copy(castMediaStatus = event.status)
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
            TvToolsIntent.RefreshPerformance -> scope.launch { loadPerformance(liveOnly = false) }
            TvToolsIntent.RunSpeedBoost -> if (before.speedBoostStatus !is SpeedBoostStatus.Boosting) {
                scope.launch { runSpeedBoost() }
            }
            is TvToolsIntent.SetAnimationScale -> if (before.animationScaleStatus !is AnimationScaleStatus.Applying) {
                scope.launch { applyAnimationScale(event.scale, before.animationScale) }
            }
            TvToolsIntent.OpenUrlOnTv -> if (before.castUrlStatus !is CastUrlStatus.Opening) {
                val raw = before.castUrlInput
                val line = runCatching { ShellRenderer.openUrlOnTvLine(raw) }.getOrNull()
                if (line == null) {
                    send(CastUrlUpdate(CastUrlStatus.InvalidUrl))
                } else {
                    scope.launch { openUrlOnTv(line, raw.trim()) }
                }
            }
            TvToolsIntent.PickCastMedia -> if (before.castMediaStatus !is CastMediaStatus.Casting) {
                effect(ManageEffect.PickCastMedia)
            }
            is TvToolsIntent.MediaPicked -> scope.launch { castMedia(event) }
            TvToolsIntent.OpenAppManager -> effect(ManageEffect.Navigate("apps/${tvId.value}"))
            else -> {}
        }
    }

    private suspend fun loadPerformance(liveOnly: Boolean) {
        val session = if (liveOnly) {
            d.access.live(tvId)
        } else {
            (d.access.session(tvId) as? AccessResult.Ok)?.session
        } ?: return
        val memRes = attempt { TvShellRunner.run(session, ShellRenderer.memInfoLine()) }.getOrNull()
        val dfRes = attempt { TvShellRunner.run(session, ShellRenderer.dfDataLine()) }.getOrNull()
        val scaleRes = attempt { TvShellRunner.run(session, ShellRenderer.getWindowAnimationScaleLine()) }.getOrNull()
        val stats = if (memRes != null && memRes.exit == 0 && dfRes != null && dfRes.exit == 0) {
            TvPerformanceStats.parse(memRes.stdout, dfRes.stdout)
        } else {
            null
        }
        val scale = if (scaleRes != null && scaleRes.exit == 0) {
            TvAnimationScale.fromSetting(scaleRes.stdout)
        } else {
            null
        }
        if (stats != null || scale != null) {
            send(PerformanceLoaded(stats, scale))
        }
    }

    private suspend fun runSpeedBoost() {
        send(SpeedBoostUpdate(SpeedBoostStatus.Boosting))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(SpeedBoostUpdate(SpeedBoostStatus.Failed))
            return
        }
        val trimRes = attempt { TvShellRunner.run(session, ShellRenderer.trimCachesLine()) }.getOrNull()
        if (trimRes == null || trimRes.exit != 0) {
            send(SpeedBoostUpdate(SpeedBoostStatus.Failed))
            return
        }
        val listedRes = attempt { TvShellRunner.run(session, ShellRenderer.listThirdPartyPackagesLine()) }.getOrNull()
        val candidates = LinkedHashSet<PackageName>()
        if (listedRes != null && listedRes.exit == 0) {
            listedRes.stdout.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { PACKAGE_RE.matches(it) }
                .map { PackageName(it) }
                .filter { !ShellRenderer.isForceStopProtected(it) }
                .forEach { candidates += it }
        }
        d.snapshots.latest(tvId)?.packages?.forEach { (pkg, pkgState) ->
            if (!pkgState.system &&
                (pkgState.enabled == EnabledState.ENABLED || pkgState.enabled == EnabledState.DEFAULT) &&
                !ShellRenderer.isForceStopProtected(pkg) &&
                Denylist.staticReason(pkg.value) == null
            ) {
                candidates += pkg
            }
        }
        var stopped = 0
        for (pkg in candidates.take(25)) {
            val line = runCatching { ShellRenderer.forceStopLine(pkg) }.getOrNull() ?: continue
            val r = attempt { TvShellRunner.run(session, line) }.getOrNull()
            if (r != null && r.exit == 0) stopped++
        }
        loadPerformance(liveOnly = true)
        send(SpeedBoostUpdate(SpeedBoostStatus.Boosted(stopped)))
        effect(ManageEffect.HapticDouble)
    }

    private suspend fun applyAnimationScale(scale: TvAnimationScale, fallback: TvAnimationScale) {
        send(AnimationScaleUpdate(scale, AnimationScaleStatus.Applying))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(AnimationScaleUpdate(fallback, AnimationScaleStatus.Failed))
            return
        }
        val lines = ShellRenderer.setAnimationScaleLines(scale)
        var allOk = true
        for (line in lines) {
            val r = attempt { TvShellRunner.run(session, line) }.getOrNull()
            if (r == null || r.exit != 0) {
                allOk = false
                break
            }
        }
        if (allOk) {
            send(AnimationScaleUpdate(scale, AnimationScaleStatus.Applied(scale)))
            effect(ManageEffect.HapticLight)
        } else {
            send(AnimationScaleUpdate(fallback, AnimationScaleStatus.Failed))
        }
    }

    private suspend fun openUrlOnTv(line: tv.cleanyour.core.model.ShellLine, url: String) {
        send(CastUrlUpdate(CastUrlStatus.Opening))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(CastUrlUpdate(CastUrlStatus.Failed))
            return
        }
        val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
        val out = (res?.stdout.orEmpty() + "\n" + res?.stderr.orEmpty())
        if (res != null && res.exit == 0 && "Error:" !in out) {
            send(CastUrlUpdate(CastUrlStatus.Opened(url), clearInput = true))
            effect(ManageEffect.HapticDouble)
        } else {
            send(CastUrlUpdate(CastUrlStatus.Failed))
        }
    }

    private suspend fun castMedia(media: TvToolsIntent.MediaPicked) {
        send(CastMediaUpdate(CastMediaStatus.Casting(media.fileName, 0)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            attempt { d.blobs.delete(media.blob.sha256) }
            send(CastMediaUpdate(CastMediaStatus.Failed(media.fileName)))
            return
        }
        val cleanExt = media.ext.lowercase()
        val staging = RemotePath(RemoteDir.RESTORE, listOf("mi5g_cast.$cleanExt"))
        val total = media.sizeBytes.coerceAtLeast(1L)
        try {
            attempt { TvShellRunner.run(session, ShellRenderer.mkdirRestoreLine()) }
            attempt { TvShellRunner.run(session, ShellRenderer.mkdirDownloadLine()) }
            val pushed = attempt {
                session.push(media.blob, staging) { sent ->
                    val pct = ((sent * 85L) / total).toInt().coerceIn(0, 85)
                    send(CastMediaUpdate(CastMediaStatus.Casting(media.fileName, pct)))
                }
            }.getOrNull()
            if (pushed == null) {
                send(CastMediaUpdate(CastMediaStatus.Failed(media.fileName)))
                return
            }
            send(CastMediaUpdate(CastMediaStatus.Casting(media.fileName, 92)))
            val cpRes = attempt {
                TvShellRunner.run(session, ShellRenderer.copyCastMediaToDownloadLine(staging, cleanExt))
            }.getOrNull()
            if (cpRes == null || cpRes.exit != 0) {
                send(CastMediaUpdate(CastMediaStatus.Failed(media.fileName)))
                return
            }
            val viewRes = attempt {
                TvShellRunner.run(session, ShellRenderer.viewCastMediaLine(cleanExt, media.mimeType))
            }.getOrNull()
            val out = (viewRes?.stdout.orEmpty() + "\n" + viewRes?.stderr.orEmpty())
            if (viewRes != null && viewRes.exit == 0 && "Error:" !in out) {
                send(CastMediaUpdate(CastMediaStatus.CastDone(media.fileName)))
                effect(ManageEffect.HapticDouble)
            } else {
                send(CastMediaUpdate(CastMediaStatus.Failed(media.fileName)))
            }
        } finally {
            withContext(NonCancellable) {
                attempt { TvShellRunner.run(session, ShellRenderer.removeRestoreLine(staging)) }
                attempt { d.blobs.delete(media.blob.sha256) }
            }
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
