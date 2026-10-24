package tv.cleanyour.core.ops

import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.testing.fixtures.Fixtures
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

// Every verb's rendering against its reviewed golden file (SECURITY 6.1). A missing or differing
// file fails with the full actual text so the diff can be reviewed, never silently rewritten.
class GoldenTest {
    private val dir = File(Fixtures.repoRoot, "core/ops/src/test/resources/golden")

    @Test fun everyCaseHasAGoldenFile() {
        val names = GoldenCases.all.map { it.name }
        assertEquals(names.distinct(), names, "duplicate golden case names")
        val files = dir.listFiles().orEmpty().filter { it.name.endsWith(".txt") && it.name != "planhash.txt" }.map { it.name.removeSuffix(".txt") }.sorted()
        assertEquals(names.sorted(), files, "golden files on disk must be exactly the cases")
    }

    @Test fun everyRenderingMatchesItsGoldenFile() {
        val failures = mutableListOf<String>()
        for (c in GoldenCases.all) {
            val actual = GoldenText.render(c, ShellRenderer(c.variant))
            val file = File(dir, c.name + ".txt")
            val expected = if (file.isFile) file.readText() else null
            if (expected != actual) failures += "---- ${c.name} (${if (expected == null) "missing" else "differs"}); actual:\n$actual" +
                (expected?.let { "---- expected:\n$it" } ?: "")
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test fun everyGoldenLineTokenizesBackToItsArgv() {
        for (c in GoldenCases.all) {
            val r = ShellRenderer(c.variant).render(c.op)
            for (line in r.lines) {
                if (c.op is Op.ArmHomeWatchdog && line === r.lines.last()) continue   // the one multi-command template, checked by WatchdogTest
                ShellTokens.assertLine(line.text)
                assertTrue(ShellTokens.tokenize(line.text).isNotEmpty(), c.name)
            }
        }
    }

    // The plan hash input of 5.2 (R0: 5), pinned: lines then "\n", a transfer as one sync line.
    @Test fun planHashIsPinned() {
        val r = ShellRenderer()
        val ops = listOf(
            Op.Disable(PackageName("com.miui.tv.analytics")),
            Op.PushAsset(GoldenCases.configBlob, GoldenCases.configPath, GoldenCases.cfgSha),
            Op.RestartHome(GoldenCases.launcher),
            Op.Read(tv.cleanyour.core.model.ProbeKind.SCREENCAP),
            Op.Settle(15),
            Op.InstallStream(GoldenCases.launcherBlob, GoldenCases.apkSha, GoldenCases.launcher.pkg, 1, GoldenCases.signer),
        )
        val rendered = ops.map { r.render(it) }
        val input = "'pm' 'disable-user' '--user' '0' 'com.miui.tv.analytics'\n" +
            "sync:/sdcard/Android/data/com.gathin.tvhome/files/config.json ${GoldenCases.cfgSha.hex}\n" +
            "'am' 'force-stop' 'com.gathin.tvhome'\n" +
            "'am' 'start' '-n' 'com.gathin.tvhome/.MainActivity'\n" +
            "'screencap' '-p' '/data/local/tmp/cleanyourtv/screen.png'\n" +
            "sync:/data/local/tmp/cleanyourtv/screen.png 2097152\n" +
            "sync:bundled:launcher.apk 260000\n"
        assertEquals(ShellRenderer.sha256Of(input.encodeToByteArray()), r.planHash(rendered))
        assertEquals(File(dir, "planhash.txt").takeIf { it.isFile }?.readText()?.trim(), r.planHash(rendered).hex, "core/ops/src/test/resources/golden/planhash.txt")
        assertEquals(ShellRenderer.sha256Of(ByteArray(0)), r.planHash(emptyList()), "the empty plan hashes the empty string")
    }

    // The golden text helpers themselves: a change here changes every file, so pin them too.
    @Test fun describeAndPriorAreStable() {
        assertEquals("SettingsPut key=DEVICE_NAME value=null", GoldenText.describe(Op.SettingsPut(SettingKey.DEVICE_NAME, null)))
        assertEquals("packageState=ENABLED present=false", GoldenText.prior(Prior(packageState = tv.cleanyour.core.model.EnabledState.ENABLED, present = false)))
        assertEquals("none", GoldenText.prior(null))
        assertEquals("empty", GoldenText.prior(Prior()))
    }

    @Test fun fakeLinesNeverLookLikeRealOnes() {
        // D-W1-10: the fixtures' FakeOpRenderer must never produce a line the real renderer could.
        val fake = tv.cleanyour.testing.fixtures.FakeOpRenderer().render(Op.Disable(PackageName("com.a.b"))).lines.single()
        assertTrue(fake.text.startsWith("fake "))
        assertTrue(runCatching { ShellTokens.assertLine(fake.text) }.isFailure, "a fake line fails the real line assertion")
    }
}
