package tv.cleanyour.testing.fixtures

import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.probe.ProbeScripts
import tv.cleanyour.core.session.TvIdentity
import java.io.File

// The forge emulator session's TV, read-only probe output (redacted), see
// fixtures/conformance/emu-atv28-2026-10-20/README.md. recording() answers the probe's batched
// scripts the way the TV printed them, so AdbProber over a FakeTvSession reads the same snapshot the
// app read on the emulator.
class EmulatorCapture(val dir: File) {
    private fun read(name: String) = File(dir, name).readText()
    private fun nl(s: String) = if (s.isEmpty() || s.endsWith("\n")) s else s + "\n"
    private fun mark(name: String) = "${ProbeScripts.MARK} $name\n"

    val props: Map<String, String> by lazy {
        read("getprop.txt").lines().mapNotNull { PROP_LINE.matchEntire(it) }.associate { it.groupValues[1] to it.groupValues[2] }
    }

    // D37: first 16 hex of sha256(ro.serialno + ro.product.device).
    fun tvId(): TvId = TvId(sha256Of((props.getValue("ro.serialno") + props.getValue("ro.product.device")).encodeToByteArray()).hex.take(16))

    fun identity(): TvIdentity = TvIdentity(
        tvId(), props.getValue("ro.serialno").takeLast(4), props.getValue("ro.product.device"), props.getValue("ro.product.model"),
        props.getValue("ro.build.version.sdk").toInt(), props["ro.adb.secure"] == "1", MiTvCapture.ANDROID_9_ADB_FEATURES, MiTvCapture.MAX_PAYLOAD,
    )

    fun recording(): Recording = Recording()
        .on(ProbeScripts.FACTS.text, nl(read("getprop.txt")) + mark("meminfo") + nl(read("meminfo.txt")) + mark("gms") + read("gms-path.txt"))
        .on(ProbeScripts.PACKAGES.text, read("packages-all.txt"))
        .on(ProbeScripts.DISABLED.text, read("packages-disabled.txt"))
        .on(ProbeScripts.PACKAGE_FLAGS.text, read("dumpsys-package-packages.txt"))
        .on(ProbeScripts.HOME.text, nl(read("query-home.txt")) + mark("resolved") + read("home-activity.txt"))
        .on(ProbeScripts.LAUNCHABLE.text, nl(read("query-leanback.txt")) + mark("launcher") + read("query-launcher.txt"))
        .on(ProbeScripts.INPUTS.text, nl(read("tv_input.txt")) + mark("hdmi") + read("hdmi_control.txt"))
        .on(ProbeScripts.SETTINGS.text, nl(read("settings-secure.txt")) + mark("global") + read("settings-global.txt"))

    fun session(): FakeTvSession = FakeTvSession(identity = identity(), recording = recording())

    companion object {
        private val PROP_LINE = Regex("^\\[([^\\]]+)\\]: \\[(.*)\\]$")
    }
}
