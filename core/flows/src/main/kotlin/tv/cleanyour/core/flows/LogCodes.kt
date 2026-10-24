package tv.cleanyour.core.flows

import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.probe.ProbeException
import tv.cleanyour.core.spec.ProfileResolution

// Stable words for the diagnostics log (issue #2: the log said "(f3)", the R8 name of
// AdbError.NonZeroExit). Class names are never logged: R8 renames them in a release build. Each
// code is fixed here, so a report from any build reads the same. Only fixed words, exit codes, phase
// names and package or component names reach a line; an error's own text may carry ids, so it never
// does, and a TV's stderr goes through clean() first.
object LogCodes {
    private val PHASE = Regex("^[a-z_]{1,16}$")
    private val INSTALL_CODE = Regex("^[A-Z0-9_]{1,48}$")

    fun of(e: AdbError): String = when (e) {
        AdbError.Transport.Unreachable -> "unreachable"
        AdbError.Transport.Refused -> "refused"
        is AdbError.Transport.Timeout -> if (PHASE.matches(e.phase)) "timeout(${e.phase})" else "timeout"
        AdbError.Transport.Closed -> "lost"
        is AdbError.Transport.Protocol -> "protocol"
        AdbError.Transport.NoShellV2 -> "no_shell_v2"
        AdbError.Auth.Denied -> "auth_denied"
        AdbError.Auth.PendingTooLong -> "auth_pending"
        is AdbError.Auth.TrustChanged -> "trust_changed"
        is AdbError.Device.InstallFailed -> if (INSTALL_CODE.matches(e.code)) "install_failed(${e.code})" else "install_failed"
        AdbError.Device.NotEnoughSpace -> "no_space"
        AdbError.Device.InstallRefusedByFirmware -> "install_refused"
        AdbError.Device.Busy -> "busy"
        is AdbError.NonZeroExit -> "nonzero_exit(${e.exit})"
        is AdbError.Policy.ProtectedPackage -> "protected"
        is AdbError.Policy.GuardFailed -> "guard_failed"
        is AdbError.Policy.Stale -> "stale"
        is AdbError.Policy.WrongTv -> "wrong_tv"
    }

    fun of(t: Throwable): String = when (t) {
        is AdbException -> of(t.error)
        is ProbeException -> "probe_failed(${t.stage.name.lowercase()})"
        else -> "unexpected"
    }

    // What an op is, by its kind and the package or component it acts on. Never a run token, a
    // checksum or a file the phone holds.
    fun op(op: Op): String = when (op) {
        is Op.Disable -> "Disable ${op.pkg.value}"
        is Op.Enable -> "Enable ${op.pkg.value}"
        is Op.DefaultState -> "DefaultState ${op.pkg.value}"
        is Op.Install -> "Install ${op.pkg.value}"
        is Op.InstallStream -> "Install ${op.pkg.value}"
        is Op.Uninstall -> "Uninstall ${op.pkg.value}"
        is Op.SettingsPut -> "SettingsPut ${op.key.key}"
        is Op.SetHome -> "SetHome ${op.component.flat}"
        is Op.VerifyHome -> "VerifyHome ${op.expected.flat}"
        is Op.ArmHomeWatchdog -> "ArmHomeWatchdog ${op.stock.flat}"
        is Op.ConfirmHome -> "ConfirmHome"
        is Op.KillWatchdog -> "KillWatchdog"
        Op.PressHome -> "PressHome"
        is Op.Mkdir -> "Mkdir ${op.dir.name.lowercase()}"
        is Op.PushAsset -> "PushAsset ${op.dest.segments.last()}"
        is Op.RemoveFile -> "RemoveFile ${op.path.segments.last()}"
        is Op.RestartHome -> "RestartHome ${op.component.flat}"
        is Op.StartActivity -> "StartActivity ${op.component.flat}"
        is Op.Read -> "Read ${op.kind.name.lowercase()}"
        is Op.Settle -> "Settle"
        Op.Reboot -> "Reboot"
        Op.DebugOff -> "DebugOff"
        is Op.CurtainCue -> "CurtainCue ${op.stage.name.lowercase()}"
    }

    // How the profile resolved: "exact <id>", "brand set <Brand>" or "none".
    fun profile(res: ProfileResolution?): String = when (res) {
        is ProfileResolution.Exact -> "exact ${res.profile.id}"
        is ProfileResolution.BrandSureSet -> "brand set ${clean(res.brand, 40)}"
        is ProfileResolution.None, null -> "none"
    }

    // "tv Hisense 55A6H (HiTV-MT9602) sdk 30": the keys a profile matches on. Model names and device
    // codenames are fine to log; the serial is not, and is never read here.
    fun tv(f: DeviceFacts): String = "tv ${clean(f.brand, 40)} ${clean(f.model, 60)} (${clean(f.device, 60)}) sdk ${f.sdk}"

    // One line of printable text from the TV (stderr, a model name), at most `max` characters, with
    // addresses, MAC addresses, 16 hex ids and long blobs masked. The app's redactor runs over every
    // log line as well; this keeps the flows' own output safe in tests and in the TV report.
    fun clean(text: String, max: Int = 160): String =
        Scrub.text(text.map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }.joinToString("")
            .replace(Regex(" {2,}"), " ").trim()).take(max)
}

// Defence in depth for anything the TV told us: none of the report's or the log's fields carry these
// by construction, but a TV's model or stderr is free text.
internal object Scrub {
    // 0.3.3 D3 (issue #10): the same address rule as the app's redactor: four octets of 0 to 255, not
    // glued to a letter, a digit or a dot (a sentence's last dot still masks), so a Xiaomi build
    // string such as "V816.0.11.0.UZFAABX" in a fingerprint stays whole.
    private const val OCTET = "(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)"
    private val IPV4 = Regex("(?<![A-Za-z0-9.])$OCTET(?:\\.$OCTET){3}(?![A-Za-z0-9]|\\.[A-Za-z0-9])")
    private val IPV6 = Regex("\\b(?:[0-9A-Fa-f]{1,4}:){3,7}[0-9A-Fa-f]{1,4}\\b")
    private val MAC = Regex("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b")
    private val TV_ID = Regex("\\b[0-9a-f]{16}\\b")
    private val BLOB = Regex("[A-Za-z0-9+_=-]{40,}")
    private val SERIAL = Regex("(?i)(serial(?:no)?|ro\\.serialno|ro\\.boot\\.serialno)([\"'\\]\\s:=\\[]+)([A-Za-z0-9]{4,})")
    private val SSID = Regex("(?i)(ssid|wifi[ _-]?name)([\"'\\s:=]+)([^,;\\s\"']+)")
    private val CODE = Regex("(?<![\\w.])\\d{6}(?![\\w.])")

    fun text(s: String, codes: Boolean = true): String = s
        .replace(MAC, "[mac]")
        .replace(IPV6, "[address]")
        .replace(IPV4, "[address]")
        .replace(SERIAL) { "${it.groupValues[1]}${it.groupValues[2]}[serial]" }
        .replace(SSID) { "${it.groupValues[1]}${it.groupValues[2]}[wifi]" }
        .replace(TV_ID, "[id]")
        .replace(BLOB, "[blob]")
        .let { if (codes) it.replace(CODE, "[code]") else it }
}
