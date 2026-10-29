package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.PACKAGE_RE
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.ops.Denylist
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.probe.TvShellRunner

// 1.6 item 8: «تطبيقاتي» (installed by the user) and «المصنع» (factory/system) are separate lists.
enum class TvAppFilter { USER, SYSTEM }

enum class TvAppActionKind {
    LAUNCH,
    FORCE_STOP,
    RESTRICT_BG,
    DISABLE_APP,
    ENABLE_APP,
    CLEAR_DATA,
    UNINSTALL,
    PULL_APK,
}

data class TvInstalledApp(
    val pkg: PackageName,
    val name: String,
    val system: Boolean,
    val enabled: Boolean = true,
    // 1.6 item 8: protected streaming apps (Shahid, TOD, stc tv, YouTube...) open and stop, never freeze or go.
    val protectedStreaming: Boolean = ProtectedStreaming.isProtected(pkg),
    val canForceStop: Boolean = !ShellRenderer.isForceStopProtected(pkg),
    val canClearData: Boolean = !ShellRenderer.isForceStopProtected(pkg) && Denylist.staticReason(pkg.value) == null,
    val canDisable: Boolean = !protectedStreaming && !ShellRenderer.isForceStopProtected(pkg) && Denylist.staticReason(pkg.value) == null,
    val canUninstall: Boolean = !protectedStreaming && !ShellRenderer.isForceStopProtected(pkg) && Denylist.staticReason(pkg.value) == null,
)

sealed interface TvAppActionStatus {
    data object Idle : TvAppActionStatus
    data class Working(val pkg: PackageName, val appName: String, val kind: TvAppActionKind) : TvAppActionStatus
    data class Done(val pkg: PackageName, val appName: String, val kind: TvAppActionKind) : TvAppActionStatus
    data class Failed(val pkg: PackageName, val appName: String, val kind: TvAppActionKind) : TvAppActionStatus
}

data class TvAppManagerState(
    val tvName: String = "",
    val loading: Boolean = false,
    val searchQuery: String = "",
    val filter: TvAppFilter = TvAppFilter.USER,
    val apps: List<TvInstalledApp> = emptyList(),
    val selectedApp: TvInstalledApp? = null,
    val confirmingUninstall: Boolean = false,
    // 1.6 item 3: a permanent uninstall needs this TV's restore point and the typed word «احذف».
    val uninstallWord: String = "",
    val hasRestorePoint: Boolean = false,
    val actionStatus: TvAppActionStatus = TvAppActionStatus.Idle,
) {
    val filteredApps: List<TvInstalledApp>
        get() = apps.filter { app ->
            (app.system == (filter == TvAppFilter.SYSTEM)) &&
                (searchQuery.isBlank() ||
                    app.name.contains(searchQuery, ignoreCase = true) ||
                    app.pkg.value.contains(searchQuery, ignoreCase = true))
        }
    val uninstallUnlocked: Boolean get() = uninstallUnlocked(uninstallWord, hasRestorePoint)
}

sealed interface TvAppManagerIntent {
    data class UpdateSearch(val query: String) : TvAppManagerIntent
    data class SetFilter(val filter: TvAppFilter) : TvAppManagerIntent
    data object Refresh : TvAppManagerIntent
    data class SelectApp(val app: TvInstalledApp) : TvAppManagerIntent
    data object DismissApp : TvAppManagerIntent
    data class LaunchApp(val pkg: PackageName) : TvAppManagerIntent
    data class ForceStopApp(val pkg: PackageName) : TvAppManagerIntent
    data class RestrictBackgroundApp(val pkg: PackageName) : TvAppManagerIntent
    data class DisableApp(val pkg: PackageName) : TvAppManagerIntent
    data class EnableApp(val pkg: PackageName) : TvAppManagerIntent
    data class ClearAppData(val pkg: PackageName) : TvAppManagerIntent
    data object AskUninstall : TvAppManagerIntent
    data object CancelUninstall : TvAppManagerIntent
    data class UpdateUninstallWord(val word: String) : TvAppManagerIntent
    data class UninstallApp(val pkg: PackageName) : TvAppManagerIntent
    data class PullApk(val pkg: PackageName) : TvAppManagerIntent
}

class TvAppManagerMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
) : Machine<TvAppManagerState, TvAppManagerIntent, ManageEffect>(TvAppManagerState(loading = true), scope) {

    private data class TvLoaded(val tvName: String)
    private data class AppsLoaded(val apps: List<TvInstalledApp>, val hasRestorePoint: Boolean = false)
    private data object LoadingStarted
    private data class ActionUpdate(
        val status: TvAppActionStatus,
        val removedPkg: PackageName? = null,
        val toggledPkg: Pair<PackageName, Boolean>? = null,
        val closeSheet: Boolean = false,
    )

    init {
        scope.launch {
            d.tvs.tvs.collect { list ->
                list.firstOrNull { it.id == tvId }?.let { tv -> send(TvLoaded(tv.nickname)) }
            }
        }
        scope.launch { loadApps(liveOnly = true) }
    }

    override fun reduce(state: TvAppManagerState, event: Any): TvAppManagerState = when (event) {
        is TvLoaded -> state.copy(tvName = event.tvName)
        LoadingStarted -> state.copy(loading = true)
        is AppsLoaded -> {
            val updatedSelected = state.selectedApp?.let { sel -> event.apps.firstOrNull { it.pkg == sel.pkg } }
            state.copy(
                loading = false,
                apps = event.apps,
                selectedApp = updatedSelected,
                hasRestorePoint = event.hasRestorePoint,
            )
        }
        is TvAppManagerIntent.UpdateSearch -> state.copy(searchQuery = event.query.take(120))
        is TvAppManagerIntent.SetFilter -> state.copy(filter = event.filter)
        is TvAppManagerIntent.SelectApp -> state.copy(
            selectedApp = event.app,
            confirmingUninstall = false,
            actionStatus = TvAppActionStatus.Idle,
        )
        TvAppManagerIntent.DismissApp -> state.copy(
            selectedApp = null,
            confirmingUninstall = false,
        )
        TvAppManagerIntent.AskUninstall -> state.copy(confirmingUninstall = true, uninstallWord = "")
        TvAppManagerIntent.CancelUninstall -> state.copy(confirmingUninstall = false, uninstallWord = "")
        is TvAppManagerIntent.UpdateUninstallWord -> state.copy(uninstallWord = event.word.take(20))
        is ActionUpdate -> {
            var nextApps = if (event.removedPkg != null) state.apps.filterNot { it.pkg == event.removedPkg } else state.apps
            if (event.toggledPkg != null) {
                val (targetPkg, newEnabled) = event.toggledPkg
                nextApps = nextApps.map { if (it.pkg == targetPkg) it.copy(enabled = newEnabled) else it }
            }
            val nextSelected = when {
                event.closeSheet || event.removedPkg != null -> null
                event.toggledPkg != null && state.selectedApp?.pkg == event.toggledPkg.first ->
                    state.selectedApp.copy(enabled = event.toggledPkg.second)
                else -> state.selectedApp
            }
            state.copy(
                apps = nextApps,
                selectedApp = nextSelected,
                confirmingUninstall = false,
                actionStatus = event.status,
            )
        }
        else -> state
    }

    override fun handle(event: Any, before: TvAppManagerState, after: TvAppManagerState) {
        val busy = before.actionStatus is TvAppActionStatus.Working
        when (event) {
            TvAppManagerIntent.Refresh -> scope.launch {
                send(LoadingStarted)
                loadApps(liveOnly = false)
            }
            is TvAppManagerIntent.LaunchApp -> if (!busy) scope.launch { launchApp(event.pkg) }
            is TvAppManagerIntent.ForceStopApp -> if (!busy) scope.launch { forceStopApp(event.pkg) }
            is TvAppManagerIntent.RestrictBackgroundApp -> if (!busy) scope.launch { restrictBackgroundApp(event.pkg) }
            is TvAppManagerIntent.DisableApp -> if (!busy && !ProtectedStreaming.isProtected(event.pkg)) scope.launch { disableApp(event.pkg) }
            is TvAppManagerIntent.EnableApp -> if (!busy) scope.launch { enableApp(event.pkg) }
            is TvAppManagerIntent.ClearAppData -> if (!busy) scope.launch { clearAppData(event.pkg) }
            is TvAppManagerIntent.UninstallApp -> if (!busy && before.uninstallUnlocked) scope.launch { uninstallApp(event.pkg) }
            is TvAppManagerIntent.PullApk -> if (!busy) scope.launch { pullApk(event.pkg) }
            else -> {}
        }
    }

    private fun appNameOf(pkg: PackageName): String =
        state.value.apps.firstOrNull { it.pkg == pkg }?.name ?: d.nameOf(pkg)

    private suspend fun loadApps(liveOnly: Boolean) {
        val byPkg = LinkedHashMap<PackageName, TvInstalledApp>()
        val snap = d.snapshots.latest(tvId)
        val switchedOffByUs = d.ledger.switchedOffByUs(tvId).toSet()
        snap?.packages?.forEach { (pkg, pkgState) ->
            if (pkg.value == Denylist.FRAMEWORK) return@forEach
            val enabled = pkg !in switchedOffByUs &&
                (pkgState.enabled == EnabledState.ENABLED || pkgState.enabled == EnabledState.DEFAULT)
            byPkg[pkg] = TvInstalledApp(
                pkg = pkg,
                name = d.nameOf(pkg, pkgState.label),
                system = pkgState.system,
                enabled = enabled,
            )
        }
        val session = if (liveOnly) {
            d.access.live(tvId)
        } else {
            (d.access.session(tvId) as? AccessResult.Ok)?.session
        }
        if (session != null) {
            val thirdPartyRes = attempt { TvShellRunner.run(session, ShellRenderer.listThirdPartyPackagesLine()) }.getOrNull()
            val systemRes = attempt { TvShellRunner.run(session, ShellRenderer.listSystemPackagesLine()) }.getOrNull()
            val disabledRes = attempt { TvShellRunner.run(session, ShellRenderer.listDisabledPackagesLine()) }.getOrNull()
            val disabledPkgs = if (disabledRes != null && disabledRes.exit == 0) {
                disabledRes.stdout.lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .toSet()
            } else {
                emptySet()
            }
            if (systemRes != null && systemRes.exit == 0) {
                systemRes.stdout.lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { PACKAGE_RE.matches(it) && it != Denylist.FRAMEWORK }
                    .forEach { raw ->
                        val pkg = PackageName(raw)
                        val existing = byPkg[pkg]
                        byPkg[pkg] = TvInstalledApp(
                            pkg = pkg,
                            name = existing?.name ?: d.nameOf(pkg),
                            system = true,
                            enabled = if (disabledPkgs.isNotEmpty()) raw !in disabledPkgs else (existing?.enabled ?: true),
                        )
                    }
            }
            if (thirdPartyRes != null && thirdPartyRes.exit == 0) {
                thirdPartyRes.stdout.lineSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .filter { PACKAGE_RE.matches(it) && it != Denylist.FRAMEWORK }
                    .forEach { raw ->
                        val pkg = PackageName(raw)
                        val existing = byPkg[pkg]
                        byPkg[pkg] = TvInstalledApp(
                            pkg = pkg,
                            name = existing?.name ?: d.nameOf(pkg),
                            system = false,
                            enabled = if (disabledPkgs.isNotEmpty()) raw !in disabledPkgs else (existing?.enabled ?: true),
                        )
                    }
            }
        }
        val sorted = byPkg.values.sortedWith(
            compareBy<TvInstalledApp> { it.system }
                .thenBy { !it.enabled }
                .thenBy { it.name.lowercase() },
        )
        send(AppsLoaded(sorted, hasRestorePoint = snap != null))
    }

    private suspend fun launchApp(pkg: PackageName) {
        val name = appNameOf(pkg)
        send(ActionUpdate(TvAppActionStatus.Working(pkg, name, TvAppActionKind.LAUNCH)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        val line = runCatching { ShellRenderer.launchPackageLine(pkg) }.getOrNull()
        if (session == null || line == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.LAUNCH)))
            return
        }
        // For bundled Mi5G TV modules or Leanback apps, try MainActivity / Leanback first, then standard LAUNCHER monkey
        if (pkg.value.startsWith("com.midroid.mi5gtv.")) {
            val mainLine = runCatching { ShellRenderer.launchMainActivityLine(pkg) }.getOrNull()
            if (mainLine != null) {
                val r0 = attempt { TvShellRunner.run(session, mainLine) }.getOrNull()
                if (r0 != null && r0.exit == 0 && "Error:" !in (r0.stdout + r0.stderr)) {
                    send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.LAUNCH)))
                    effect(ManageEffect.HapticLight)
                    return
                }
            }
        }
        val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
        if (res != null && res.exit == 0 && "No activities found" !in (res.stdout + res.stderr)) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.LAUNCH)))
            effect(ManageEffect.HapticLight)
            return
        }
        val leanbackLine = runCatching { ShellRenderer.launchLeanbackPackageLine(pkg) }.getOrNull()
        val lbRes = if (leanbackLine != null) attempt { TvShellRunner.run(session, leanbackLine) }.getOrNull() else null
        if (lbRes != null && lbRes.exit == 0) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.LAUNCH)))
            effect(ManageEffect.HapticLight)
        } else {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.LAUNCH)))
        }
    }

    private suspend fun forceStopApp(pkg: PackageName) {
        val name = appNameOf(pkg)
        val line = runCatching { ShellRenderer.forceStopLine(pkg) }.getOrNull()
        if (line == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.FORCE_STOP)))
            return
        }
        send(ActionUpdate(TvAppActionStatus.Working(pkg, name, TvAppActionKind.FORCE_STOP)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.FORCE_STOP)))
            return
        }
        val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
        if (res != null && res.exit == 0) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.FORCE_STOP)))
            effect(ManageEffect.HapticDouble)
        } else {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.FORCE_STOP)))
        }
    }

    private suspend fun restrictBackgroundApp(pkg: PackageName) {
        val name = appNameOf(pkg)
        val lines = runCatching { ShellRenderer.restrictBackgroundLines(pkg) }.getOrNull()
        if (lines == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.RESTRICT_BG)))
            return
        }
        send(ActionUpdate(TvAppActionStatus.Working(pkg, name, TvAppActionKind.RESTRICT_BG)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.RESTRICT_BG)))
            return
        }
        var ok = false
        for (l in lines) {
            val r = attempt { TvShellRunner.run(session, l) }.getOrNull()
            if (r != null && r.exit == 0) ok = true
        }
        runCatching { ShellRenderer.forceStopLine(pkg) }.getOrNull()?.let { fs ->
            attempt { TvShellRunner.run(session, fs) }
        }
        if (ok) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.RESTRICT_BG)))
            effect(ManageEffect.HapticDouble)
        } else {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.RESTRICT_BG)))
        }
    }

    private suspend fun disableApp(pkg: PackageName) {
        val name = appNameOf(pkg)
        val line = runCatching { ShellRenderer.disableUserPackageLine(pkg) }.getOrNull()
        if (line == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.DISABLE_APP)))
            return
        }
        send(ActionUpdate(TvAppActionStatus.Working(pkg, name, TvAppActionKind.DISABLE_APP)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.DISABLE_APP)))
            return
        }
        val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
        val out = (res?.stdout.orEmpty() + "\n" + res?.stderr.orEmpty())
        if (res != null && res.exit == 0 && "disabled" in out.lowercase()) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.DISABLE_APP), toggledPkg = pkg to false))
            effect(ManageEffect.HapticDouble)
        } else {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.DISABLE_APP)))
        }
    }

    private suspend fun enableApp(pkg: PackageName) {
        val name = appNameOf(pkg)
        val lines = runCatching { ShellRenderer.enableUserPackageLines(pkg) }.getOrNull()
        if (lines == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.ENABLE_APP)))
            return
        }
        send(ActionUpdate(TvAppActionStatus.Working(pkg, name, TvAppActionKind.ENABLE_APP)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.ENABLE_APP)))
            return
        }
        var ok = false
        for (l in lines) {
            val r = attempt { TvShellRunner.run(session, l) }.getOrNull()
            if (r != null && r.exit == 0) ok = true
        }
        if (ok) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.ENABLE_APP), toggledPkg = pkg to true))
            effect(ManageEffect.HapticDouble)
        } else {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.ENABLE_APP)))
        }
    }

    private suspend fun clearAppData(pkg: PackageName) {
        val name = appNameOf(pkg)
        val line = runCatching { ShellRenderer.clearPackageDataLine(pkg) }.getOrNull()
        if (line == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.CLEAR_DATA)))
            return
        }
        send(ActionUpdate(TvAppActionStatus.Working(pkg, name, TvAppActionKind.CLEAR_DATA)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.CLEAR_DATA)))
            return
        }
        val res = attempt { TvShellRunner.run(session, line) }.getOrNull()
        val out = (res?.stdout.orEmpty() + "\n" + res?.stderr.orEmpty())
        if (res != null && res.exit == 0 && "Failed" !in out) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.CLEAR_DATA)))
            effect(ManageEffect.HapticDouble)
        } else {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.CLEAR_DATA)))
        }
    }

    private suspend fun uninstallApp(pkg: PackageName) {
        val app = state.value.apps.firstOrNull { it.pkg == pkg }
        val name = app?.name ?: d.nameOf(pkg)
        if ((app != null && !app.canUninstall) || ProtectedStreaming.isProtected(pkg)) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.UNINSTALL)))
            return
        }
        val stdLine = runCatching { ShellRenderer.uninstallUserPackageLine(pkg) }.getOrNull()
        val keepLine = runCatching { ShellRenderer.uninstallKeepDataUserPackageLine(pkg) }.getOrNull()
        if (stdLine == null || keepLine == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.UNINSTALL)))
            return
        }
        send(ActionUpdate(TvAppActionStatus.Working(pkg, name, TvAppActionKind.UNINSTALL)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.UNINSTALL)))
            return
        }
        // Try standard --user 0 uninstall first; for preloaded system apps fallback to -k --user 0
        val primaryLine = if (app?.system == true) keepLine else stdLine
        val fallbackLine = if (app?.system == true) stdLine else keepLine
        var res = attempt { TvShellRunner.run(session, primaryLine) }.getOrNull()
        var out = (res?.stdout.orEmpty() + "\n" + res?.stderr.orEmpty())
        if (res == null || res.exit != 0 || "Success" !in out) {
            res = attempt { TvShellRunner.run(session, fallbackLine) }.getOrNull()
            out = (res?.stdout.orEmpty() + "\n" + res?.stderr.orEmpty())
        }
        if (res != null && res.exit == 0 && "Success" in out) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.UNINSTALL), removedPkg = pkg, closeSheet = true))
            effect(ManageEffect.HapticDouble)
        } else {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.UNINSTALL)))
        }
    }

    private suspend fun pullApk(pkg: PackageName) {
        val name = appNameOf(pkg)
        val pathLine = runCatching { ShellRenderer.pmPathLine(pkg) }.getOrNull()
        if (pathLine == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.PULL_APK)))
            return
        }
        send(ActionUpdate(TvAppActionStatus.Working(pkg, name, TvAppActionKind.PULL_APK)))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session
        if (session == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.PULL_APK)))
            return
        }
        val pathRes = attempt { TvShellRunner.run(session, pathLine) }.getOrNull()
        val remoteApkPath = if (pathRes != null && pathRes.exit == 0) {
            ShellRenderer.parsePmPathOutput(pathRes.stdout)
        } else {
            null
        }
        if (remoteApkPath == null) {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.PULL_APK)))
            return
        }
        val tmpApk = RemotePath(RemoteDir.TMP, listOf("pulled-app.apk"))
        val apkBytes = try {
            attempt { TvShellRunner.run(session, ShellRenderer.mkdirTmpLine()) }
            val cpLine = runCatching { ShellRenderer.copyRemoteApkToTmpLine(remoteApkPath, tmpApk) }.getOrNull()
            val cpRes = if (cpLine != null) attempt { TvShellRunner.run(session, cpLine, timeoutMs = 60_000L) }.getOrNull() else null
            val pulled = if (cpRes != null && cpRes.exit == 0) {
                attempt { session.pull(tmpApk, ShellRenderer.MAX_APK_BYTES) }.getOrNull()
            } else {
                null
            }
            if (pulled != null && pulled.isNotEmpty()) {
                pulled
            } else {
                val catLine = runCatching { ShellRenderer.catRemoteApkLine(remoteApkPath) }.getOrNull()
                val catRes = if (catLine != null) {
                    attempt { TvShellRunner.run(session, catLine, timeoutMs = 60_000L, stdoutCap = 16 * 1024 * 1024) }.getOrNull()
                } else {
                    null
                }
                if (catRes != null && catRes.exit == 0 && catRes.stdout.isNotEmpty()) {
                    catRes.stdout.toByteArray(Charsets.ISO_8859_1)
                } else {
                    null
                }
            }
        } finally {
            withContext(NonCancellable) {
                attempt { TvShellRunner.run(session, ShellRenderer.removeTmpLine(tmpApk)) }
            }
        }
        if (apkBytes != null && apkBytes.isNotEmpty()) {
            send(ActionUpdate(TvAppActionStatus.Done(pkg, name, TvAppActionKind.PULL_APK)))
            effect(ManageEffect.HapticDouble)
            effect(ManageEffect.ShareApk(apkBytes, "${pkg.value}.apk"))
        } else {
            send(ActionUpdate(TvAppActionStatus.Failed(pkg, name, TvAppActionKind.PULL_APK)))
        }
    }
}
