package tv.cleanyour.core.probe

import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.testing.fixtures.FakeClock
import tv.cleanyour.testing.fixtures.FakeTvSession
import tv.cleanyour.testing.fixtures.Fixtures
import tv.cleanyour.testing.fixtures.Recording

// The reference TV behind FakeTvSession: W1's capture for what it recorded, the synthesized
// dumps in src/test/resources/probe/mitv for the rest (see the README there), joined the way the
// batched scripts print them.
object ProbeFixtures {
    fun res(path: String): String = ProbeFixtures::class.java.getResource("/probe/$path")!!.readText()

    private val GREP = Regex("^(Packages:|Hidden system packages:)|^ +(Package \\[|codePath=|versionCode=|pkgFlags=|User 0: )")
    fun grepped(dump: String) = dump.lines().filter { GREP.containsMatchIn(it) }.joinToString("\n", postfix = "\n")

    private fun mark(name: String) = "${ProbeScripts.MARK} $name\n"
    private fun nl(s: String) = if (s.endsWith("\n")) s else s + "\n"

    val overlayConfig = "{\n  \"background_color\": \"#070A14\",\n  \"wallpaper\": true\n}\n".encodeToByteArray()

    fun miTvRecording(): Recording {
        val c = Fixtures.miTv()
        return Recording.miTv(c)
            .on(ProbeScripts.FACTS.text, nl(c.getpropText) + mark("meminfo") + res("mitv/meminfo.txt") + mark("gms") + res("mitv/gms-path.txt"))
            .on(ProbeScripts.PACKAGE_FLAGS.text, grepped(res("mitv/dumpsys-package-packages.txt")))
            .on(ProbeScripts.HOME.text, res("mitv/query-home.txt") + mark("resolved") + c.homeActivityText)
            .on(ProbeScripts.INPUTS.text, res("mitv/tv_input.txt") + mark("hdmi") + res("mitv/hdmi_control.txt"))
            .on(ProbeScripts.SETTINGS.text, nl(c.settingsSecureText) + mark("global") + c.settingsGlobalText)
            .on(ProbeScripts.HOME_FOCUS.text, res("mitv/activities.txt"))
            .on(ProbeScripts.FREE_DATA.text, res("mitv/df.txt"))
            .on(ProbeScripts.BOOT_ID.text, "3b1f6c2e-0d4a-4f7e-9a51-6c0e2b7d9f10\n")
    }

    fun miTvSession(overlay: Boolean = true): FakeTvSession = FakeTvSession(recording = miTvRecording()).also {
        if (overlay) it.files[RemoteDir.OVERLAY.path + "/config.json"] = overlayConfig
    }

    fun clock() = FakeClock()
}
