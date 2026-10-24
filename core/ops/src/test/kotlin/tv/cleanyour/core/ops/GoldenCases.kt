package tv.cleanyour.core.ops

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.CurtainGroup
import tv.cleanyour.core.model.CurtainStage
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.InstallFlag
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.ProbeKind
import tv.cleanyour.core.model.ReadArg
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.model.SettingsNs
import tv.cleanyour.core.model.Sha256

// The golden corpus: one file per case under src/test/resources/golden/<name>.txt, reviewed line
// by line at gate R1. Each file is what GoldenText.render produces for the case; a change to any
// command line is a reviewed diff.
data class GoldenCase(val name: String, val title: String, val op: Op, val prior: Prior?, val variant: WatchdogVariant = WatchdogVariant.NOHUP_SETSID)

object GoldenCases {
    val launcher = Component(PackageName("com.gathin.tvhome"), ".MainActivity")
    val stock = Component(PackageName("com.google.android.tvlauncher"), ".MainActivity")
    val token = RunToken("0123456789abcdef")
    val apkSha = Sha256("a".repeat(64))
    val signer = Sha256("b".repeat(64))
    val cfgSha = Sha256("c".repeat(64))
    val apkPath = RemotePath(RemoteDir.TMP, listOf(apkSha.hex.take(40) + ".apk"))   // FILE_NAME_RE caps a segment at 64 characters
    val launcherBlob = BlobRef(apkSha, 260_000, "bundled:launcher.apk")
    val configBlob = BlobRef(cfgSha, 1234, "blobs/" + cfgSha.hex)
    val configPath = RemotePath(RemoteDir.OVERLAY, listOf("config.json"))
    val wallpaperPath = RemotePath(RemoteDir.OVERLAY, listOf("wallpaper.jpg"))

    private fun p(s: String) = PackageName(s)

    // RFC-CURTAIN-1: the curtain's receiver and one cue per stage, as the Setup plan sends them.
    val curtainReceiver = Component(PackageName("tv.cleanyour.curtain"), ".CueReceiver")
    val curtainToken = RunToken("fedcba9876543210")
    fun cue(stage: CurtainStage): Op.CurtainCue = when (stage) {
        CurtainStage.SCAN -> Op.CurtainCue(curtainReceiver, curtainToken, stage, 0, 12)
        CurtainStage.OFF -> Op.CurtainCue(curtainReceiver, curtainToken, stage, 3, 12, CurtainGroup.ADVERTS, 3, 4)
        CurtainStage.GROUP_DONE -> Op.CurtainCue(curtainReceiver, curtainToken, stage, 4, 12, CurtainGroup.ADVERTS, 4, 4)
        else -> Op.CurtainCue(curtainReceiver, curtainToken, stage, 0, 0)
    }

    val all: List<GoldenCase> = listOf(
        GoldenCase("01-disable", "Disable a package the profile lists; it was enabled", Op.Disable(p("com.miui.tv.analytics")), Prior(packageState = EnabledState.ENABLED)),
        GoldenCase("02-disable-prior-default", "Disable a package whose state was the default", Op.Disable(p("com.xiaomi.statistic")), Prior(packageState = EnabledState.DEFAULT)),
        GoldenCase("03-enable", "Put back a package we switched off", Op.Enable(p("com.google.android.katniss")), Prior(packageState = EnabledState.DISABLED_USER)),
        GoldenCase("04-default-state", "Return a package to its default state", Op.DefaultState(p("com.xiaomi.mitv.handbook")), Prior(packageState = EnabledState.ENABLED)),
        GoldenCase("05-install-replace", "Install the launcher from tmp with -r; it was absent, so the undo removes it", Op.Install(apkPath, apkSha, launcher.pkg, 1, signer, setOf(InstallFlag.REPLACE)), Prior(present = false)),
        GoldenCase("06-install-fresh", "Install the launcher from tmp, no flags, it was present (no undo)", Op.Install(apkPath, apkSha, launcher.pkg, 1, signer, emptySet()), Prior(present = true)),
        GoldenCase("07-install-stream", "Streaming install when the TV advertises cmd", Op.InstallStream(launcherBlob, apkSha, launcher.pkg, 1, signer), Prior(present = false)),
        GoldenCase("08-uninstall", "Remove an app we installed", Op.Uninstall(launcher.pkg), Prior(present = true)),
        GoldenCase("09-settings-put", "Allowlisted secure setting with a 0 or 1 shape", Op.SettingsPut(SettingKey.SCREENSAVER_ENABLED, "0"), Prior(settingValue = "1")),
        GoldenCase("10-settings-put-device-name", "device_name is the one value with a space, quoted like any token", Op.SettingsPut(SettingKey.DEVICE_NAME, "Living room TV"), Prior(settingValue = null)),
        GoldenCase("11-settings-delete", "A null value renders settings delete; the undo puts the old value back", Op.SettingsPut(SettingKey.SCREENSAVER_COMPONENTS, null), Prior(settingValue = "com.google.android.backdrop/.Backdrop")),
        GoldenCase("12-settings-put-components", "A ComponentList value", Op.SettingsPut(SettingKey.SCREENSAVER_COMPONENTS, "com.a.b/.X:com.c.d/.Y"), Prior(settingValue = null)),
        GoldenCase("13-settings-put-adb-wifi", "The session-end key: privilege SESSION_END, value 0 only", Op.SettingsPut(SettingKey.ADB_WIFI_ENABLED, "0"), Prior(settingValue = "1")),
        GoldenCase("14-set-home", "Make our launcher the HOME activity; undo is the stock launcher", Op.SetHome(launcher), Prior(home = stock)),
        GoldenCase("15-verify-home", "Resolve HOME; checked against the op's own output, no undo", Op.VerifyHome(launcher), null),
        GoldenCase("16-arm-home-watchdog-a", "The dead-man template, variant A (nohup setsid)", Op.ArmHomeWatchdog(stock, 120, token), null),
        GoldenCase("17-arm-home-watchdog-b", "The dead-man template, variant B (backgrounded subshell)", Op.ArmHomeWatchdog(stock, 120, token), null, WatchdogVariant.SUBSHELL),
        GoldenCase("18-confirm-home", "Write the token file that defuses the watchdog", Op.ConfirmHome(token), null),
        GoldenCase("19-kill-watchdog", "Kill the watchdog by its process name", Op.KillWatchdog(token), null),
        GoldenCase("20-press-home", "The HOME key", Op.PressHome, null),
        GoldenCase("21-mkdir-tmp", "Our tmp folder", Op.Mkdir(RemoteDir.TMP), null),
        GoldenCase("22-mkdir-overlay", "The launcher's external files folder", Op.Mkdir(RemoteDir.OVERLAY), null),
        GoldenCase("23-mkdir-restore", "The restore point folder", Op.Mkdir(RemoteDir.RESTORE), null),
        GoldenCase("24-push-asset", "Push config.json over sync, verified by sha256sum; undo removes it", Op.PushAsset(configBlob, configPath, cfgSha), Prior(present = false)),
        GoldenCase("25-push-asset-existing", "Push over a file that existed before us: no undo in 0.1", Op.PushAsset(configBlob, configPath, cfgSha), Prior(present = true)),
        GoldenCase("26-remove-file", "Remove a file of ours", Op.RemoveFile(wallpaperPath), null),
        GoldenCase("27-restart-home", "Two lines, force-stop then start", Op.RestartHome(launcher), null),
        GoldenCase("28-start-activity-wait", "am start -W, 180 s", Op.StartActivity(launcher, wait = true), null),
        GoldenCase("29-start-activity", "am start without waiting", Op.StartActivity(launcher, wait = false), null),
        GoldenCase("30-read-props", "getprop", Op.Read(ProbeKind.PROPS), null),
        GoldenCase("31-read-packages", "Every package with its path", Op.Read(ProbeKind.PACKAGES), null),
        GoldenCase("32-read-disabled", "Disabled packages", Op.Read(ProbeKind.DISABLED), null),
        GoldenCase("33-read-home", "Resolve HOME, brief", Op.Read(ProbeKind.HOME), null),
        GoldenCase("34-read-home-focus", "The resumed activity, for the live check", Op.Read(ProbeKind.HOME_FOCUS), null),
        GoldenCase("35-read-tif-inputs", "TV inputs", Op.Read(ProbeKind.TIF_INPUTS), null),
        GoldenCase("36-read-cec", "CEC devices", Op.Read(ProbeKind.CEC), null),
        GoldenCase("37-read-settings-secure", "A settings namespace", Op.Read(ProbeKind.SETTINGS, ReadArg.Ns(SettingsNs.SECURE)), null),
        GoldenCase("38-read-ime", "The active keyboard", Op.Read(ProbeKind.IME), null),
        GoldenCase("39-read-screensaver", "The screensaver components", Op.Read(ProbeKind.SCREENSAVER), null),
        GoldenCase("40-read-screencap", "Screenshot to tmp then a sync pull, 2 MiB cap", Op.Read(ProbeKind.SCREENCAP), null),
        GoldenCase("41-read-sha256-of-file", "sha256sum of a file of ours", Op.Read(ProbeKind.SHA256_OF_FILE, ReadArg.Path(apkPath)), null),
        GoldenCase("42-read-sha256-of-installed", "The installed APK's path (the prober hashes it)", Op.Read(ProbeKind.SHA256_OF_INSTALLED, ReadArg.Pkg(launcher.pkg)), null),
        GoldenCase("43-read-package-dump", "dumpsys of one package", Op.Read(ProbeKind.PACKAGE_DUMP, ReadArg.Pkg(launcher.pkg)), null),
        GoldenCase("44-read-persistent", "The packages section of dumpsys package", Op.Read(ProbeKind.PERSISTENT), null),
        GoldenCase("45-read-df-data", "Free space on /data", Op.Read(ProbeKind.DF_DATA), null),
        GoldenCase("46-read-watchdog-alive", "pgrep for the watchdog of this run", Op.Read(ProbeKind.WATCHDOG_ALIVE, ReadArg.Token(token)), null),
        GoldenCase("47-read-boot-id", "The kernel boot id", Op.Read(ProbeKind.BOOT_ID), null),
        GoldenCase("48-read-boot-completed", "sys.boot_completed", Op.Read(ProbeKind.BOOT_COMPLETED), null),
        GoldenCase("49-read-launcher-dir", "The launcher's external files folder listing", Op.Read(ProbeKind.LAUNCHER_DIR), null),
        GoldenCase("50-settle", "A phone-side wait: no line", Op.Settle(15), null),
        GoldenCase("51-reboot", "Reboot: shown as a line, dispatched to TvSession.reboot", Op.Reboot, null),
        GoldenCase("52-debug-off", "Debugging off: the last op of a session", Op.DebugOff, null),
        GoldenCase("53-cue-scan", "Curtain cue: the scan, with the plan's switch-off total", cue(CurtainStage.SCAN), null),
        GoldenCase("54-cue-restore-saved", "Curtain cue: the restore point is saved", cue(CurtainStage.RESTORE_SAVED), null),
        GoldenCase("55-cue-off", "Curtain cue: one switch-off, third of twelve, third of four adverts", cue(CurtainStage.OFF), null),
        GoldenCase("56-cue-group-done", "Curtain cue: a group is done", cue(CurtainStage.GROUP_DONE), null),
        GoldenCase("57-cue-install", "Curtain cue: the new home screen is being installed", cue(CurtainStage.INSTALL), null),
        GoldenCase("58-cue-installed", "Curtain cue: the new home screen is installed", cue(CurtainStage.INSTALLED), null),
        GoldenCase("59-cue-finale", "Curtain cue: the finale, before Home switches over", cue(CurtainStage.FINALE), null),
    )
}

// The readable form of an op and of a rendering, shared by the golden files and the test messages.
object GoldenText {
    fun describe(op: Op): String = when (op) {
        is Op.Disable -> "Disable pkg=${op.pkg.value}"
        is Op.Enable -> "Enable pkg=${op.pkg.value}"
        is Op.DefaultState -> "DefaultState pkg=${op.pkg.value}"
        is Op.Install -> "Install apk=${op.apk.absolute} sha256=${op.sha256.hex} pkg=${op.pkg.value} versionCode=${op.versionCode} signer=${op.signer.hex} flags=${op.flags.sortedBy { it.name }.joinToString(",") { it.name }}"
        is Op.InstallStream -> "InstallStream blob=${blob(op.blob)} sha256=${op.sha256.hex} pkg=${op.pkg.value} versionCode=${op.versionCode} signer=${op.signer.hex}"
        is Op.Uninstall -> "Uninstall pkg=${op.pkg.value}"
        is Op.SettingsPut -> "SettingsPut key=${op.key.name} value=${op.value ?: "null"}"
        is Op.SetHome -> "SetHome component=${op.component.flat}"
        is Op.VerifyHome -> "VerifyHome expected=${op.expected.flat}"
        is Op.ArmHomeWatchdog -> "ArmHomeWatchdog stock=${op.stock.flat} seconds=${op.seconds} token=${op.token.value}"
        is Op.ConfirmHome -> "ConfirmHome token=${op.token.value}"
        is Op.KillWatchdog -> "KillWatchdog token=${op.token.value}"
        Op.PressHome -> "PressHome"
        is Op.Mkdir -> "Mkdir dir=${op.dir.name}"
        is Op.PushAsset -> "PushAsset src=${blob(op.src)} dest=${op.dest.absolute} sha256=${op.sha256.hex}"
        is Op.RemoveFile -> "RemoveFile path=${op.path.absolute}"
        is Op.RestartHome -> "RestartHome component=${op.component.flat}"
        is Op.StartActivity -> "StartActivity component=${op.component.flat} wait=${op.wait}"
        is Op.Read -> "Read kind=${op.kind.name}" + when (val a = op.arg) {
            null -> ""
            is ReadArg.Pkg -> " pkg=${a.pkg.value}"
            is ReadArg.Path -> " path=${a.path.absolute}"
            is ReadArg.Token -> " token=${a.token.value}"
            is ReadArg.Ns -> " ns=${a.ns.name}"
        }
        is Op.Settle -> "Settle seconds=${op.seconds}"
        Op.Reboot -> "Reboot"
        Op.DebugOff -> "DebugOff"
        is Op.CurtainCue -> "CurtainCue receiver=${op.receiver.flat} token=${op.token.value} stage=${op.stage.name} count=${op.count} total=${op.total} group=${op.group.name} groupCount=${op.groupCount} groupTotal=${op.groupTotal}"
    }

    fun blob(b: BlobRef) = "${b.sha256.hex}:${b.size}:${b.path}"

    fun prior(p: Prior?): String {
        if (p == null) return "none"
        val parts = listOfNotNull(
            p.packageState?.let { "packageState=${it.name}" },
            p.settingValue?.let { "settingValue=$it" },
            p.home?.let { "home=${it.flat}" },
            p.overlay?.let { "overlay=${it.size} bytes" },
            p.present?.let { "present=$it" },
        )
        return if (parts.isEmpty()) "empty" else parts.joinToString(" ")
    }

    fun transfer(t: Transfer?): String = when (t) {
        null -> "none"
        is Transfer.Push -> "sync:${t.dest.absolute} ${t.src.sha256.hex}"
        is Transfer.Pull -> "sync:${t.src.absolute} ${t.maxBytes}"
        is Transfer.StreamInstall -> "sync:${t.src.path} ${t.size}"
    }

    // The golden file text for a case, rendered with the given renderer.
    fun render(c: GoldenCase, renderer: OpRenderer): String {
        val r = renderer.render(c.op)
        val sb = StringBuilder()
        sb.append("# ").append(c.title).append('\n')
        sb.append("op: ").append(describe(c.op)).append('\n')
        sb.append("privilege: ").append(r.privilege.name).append('\n')
        sb.append("timeout_ms: ").append(r.timeoutMs).append('\n')
        sb.append("stdout_cap: ").append(r.stdoutCap).append('\n')
        if (r.lines.isEmpty()) sb.append("line: none\n") else r.lines.forEach { sb.append("line: ").append(it.text).append('\n') }
        sb.append("transfer: ").append(transfer(r.transfer)).append('\n')
        val v = r.verify
        if (v == null) {
            sb.append("verify: none\n")
        } else {
            sb.append("verify: ").append(describe(v)).append('\n')
            renderer.render(v).lines.forEach { sb.append("verify_line: ").append(it.text).append('\n') }
        }
        sb.append("prior: ").append(prior(c.prior)).append('\n')
        val inverse = renderer.inverse(c.op, c.prior ?: Prior())
        if (inverse == null) {
            sb.append("inverse: none\n")
        } else {
            sb.append("inverse: ").append(describe(inverse)).append('\n')
            renderer.render(inverse).lines.forEach { sb.append("inverse_line: ").append(it.text).append('\n') }
        }
        return sb.toString()
    }
}
