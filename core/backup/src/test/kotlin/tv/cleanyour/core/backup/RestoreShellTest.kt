package tv.cleanyour.core.backup

import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Tv
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.testing.fixtures.FakeClock
import tv.cleanyour.testing.fixtures.Fixtures
import tv.cleanyour.testing.fixtures.Samples
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

// The shell-interpreter test of F10 and the W6 definition of done: the restore.sh exported in the
// zip runs under a real POSIX sh against a stub adb that replays the fake TV, and puts the TV
// back to the baseline. Runs under every POSIX shell installed (dash as /bin/sh in the forge build
// container, plus bash in POSIX mode and any others found).
class RestoreShellTest {
    @get:Rule val tmp = TemporaryFolder()

    private val capture = Fixtures.miTv()
    private val props = mapOf(
        "ro.serialno" to capture.serial,
        "ro.product.device" to capture.props.getValue("ro.product.device"),
        "ro.product.model" to capture.props.getValue("ro.product.model"),
    )
    private val baseline = Scenario.baseline()
    private val changes = Scenario.changes()
    private val before = TvState.of(baseline)
    private val after = before.apply(changes)

    private fun bundle(tv: Tv = Scenario.tv(), changes: List<ChangeRecord> = this.changes): ReadBundle {
        val out = ByteArrayOutputStream()
        runBlocking { ZipRestoreBundleWriter(ShellRenderer(), FakeClock(Samples.NOW_MS + 3_600_000), "0.1.0").write(tv, baseline, changes, null, null, out) }
        return RestoreBundleReader.read(ByteArrayInputStream(out.toByteArray()))
    }

    private fun scriptFrom(b: ReadBundle, dir: File): File =
        File(dir, "restore.sh").also { it.writeBytes(b.entries.getValue(RestoreBundle.RESTORE_SH)) }

    private fun fakeTv(name: String, props: Map<String, String> = this.props, state: TvState = after): FakeTvDir =
        FakeTvDir(tmp.newFolder(name), "192.0.2.10:5555", props).also { it.write(state) }

    private fun shells(): List<List<String>> = Shells.available().also {
        if (it.isEmpty()) fail("no POSIX sh found; this test needs /bin/sh")
    }

    // Leaves the scenario's laptop files under build/restore-sample for a human read.
    @Test fun writeASampleForReview() {
        val dir = File("build/restore-sample").also { it.mkdirs() }
        bundle().entries.forEach { (name, bytes) -> File(dir, name.replace('/', '_')).writeBytes(bytes) }
        File(dir, "restore-note.txt").writeText(PlainRestoreNote().text(Scenario.tv(), changes, tv.cleanyour.testing.fixtures.MapFactsTable(), "Android TV Home", "19 October 2026"))
    }

    // Leaves what a PowerShell run of restore.ps1 needs under build/ps-harness: the scripts from a
    // read-back zip (also one made for a hostile nickname and model, and the R5-W6 M1 flow), the
    // stub adb, the fake TV after the plans (after/tv, after-m1/tv) and at the baseline (before/tv).
    // No pwsh in the build container; the files are run in a separate PowerShell container
    // (reviews/R5-W6.md).
    @Test fun writeAPowerShellHarness() {
        val dir = File("build/ps-harness").also { it.deleteRecursively(); it.mkdirs() }
        val b = bundle()
        File(dir, "restore.ps1").writeBytes(b.entries.getValue(RestoreBundle.RESTORE_PS1))
        File(dir, "restore.sh").writeBytes(b.entries.getValue(RestoreBundle.RESTORE_SH))
        val tv0 = Scenario.tv()
        val hostile = bundle(tv0.copy(nickname = "Den'; touch PWNED1; \"\$(touch PWNED2)\"`touch PWNED3` \$env:X #", facts = tv0.facts.copy(model = "M'\$(touch PWNED5)'')")))
        File(dir, "restore-hostile.ps1").writeBytes(hostile.entries.getValue(RestoreBundle.RESTORE_PS1))
        val m1 = bundle(changes = Scenario.m1Changes())
        File(dir, "restore-m1.ps1").writeBytes(m1.entries.getValue(RestoreBundle.RESTORE_PS1))
        File(dir, "restore-m1.sh").writeBytes(m1.entries.getValue(RestoreBundle.RESTORE_SH))
        FakeTvDir(File(dir, "after"), "192.0.2.10:5555", props).write(after)
        FakeTvDir(File(dir, "after-m1"), "192.0.2.10:5555", props).write(before.apply(Scenario.m1Changes()))
        FakeTvDir(File(dir, "before"), "192.0.2.10:5555", props).write(before)
        File(dir, "missing-pkg").writeText(Scenario.disabledAfter.first { it != Scenario.putBackPkg && it != Scenario.defaultStatePkg }.value)
    }

    @Test fun theScenarioChangesTheTv() {
        // guards the test itself: the fake TV after the plans differs from the baseline in every way restore.sh must undo
        assertTrue(after.packages != before.packages && after.home != before.home && after.settings != before.settings && after.files.isNotEmpty())
    }

    @Test fun restoreShPutsTheBaselineBackInEveryShell() {
        val b = bundle()
        println("restore.sh shells: " + shells().joinToString { it.joinToString(" ") })
        for ((i, shell) in shells().withIndex()) {
            val tv = fakeTv("sh$i")
            val script = scriptFrom(b, tv.root)
            val r = tv.run(shell, script, listOf("--remove-apps"))
            assertEquals(0, r.exit, "$shell\n$r")
            assertEquals(before, tv.read(), "$shell left the TV different from the baseline\n$r")
            assertIdentityCheckedFirst(tv.lines(), shell)
            assertTrue(r.out.contains("failed") && !r.out.contains("FAILED"), r.toString())
        }
    }

    // R5-W6 M1: setup, "TV's own home screen", then "remove the new one". The script finishes with
    // exit 0 at the baseline: no step fails on the removed launcher, and the TV has an enabled home
    // screen at every step (the stock launcher is switched on before HOME moves).
    @Test fun afterTheNewHomeScreenWasRemovedTheScriptStillEndsAtTheBaseline() {
        val m1 = Scenario.m1Changes()
        val b = bundle(changes = m1)
        for ((i, shell) in shells().withIndex()) {
            val tv = fakeTv("m1-$i", state = before.apply(m1))
            val r = tv.run(shell, scriptFrom(b, tv.root), listOf("--remove-apps"))
            assertEquals(0, r.exit, "$shell\n$r")
            assertEquals(before, tv.read(), "$shell left the TV different from the baseline\n$r")
            assertTrue(r.out.contains(", 0 failed, 0 kept, 0 not possible from here."), r.out)
            assertTrue(tv.lines().none { Scenario.launcher.pkg.value in it }, tv.lines().joinToString("\n"))
        }
    }

    @Test fun withoutRemoveAppsOurHomeScreenStaysInstalled() {
        val tv = fakeTv("keep")
        val r = tv.run(listOf("/bin/sh"), scriptFrom(bundle(), tv.root))
        assertEquals(0, r.exit, r.toString())
        val expected = before.copy(packages = before.packages + (Scenario.launcher.pkg.value to "ENABLED"))
        assertEquals(expected, tv.read())
        assertTrue(r.out.contains("kept     Remove the app: com.gathin.tvhome"), r.out)
    }

    @Test fun refusesAnotherTvUnlessAllowed() {
        val other = props + ("ro.serialno" to "OTHERSERIAL0002")
        val tv = fakeTv("other", other)
        val script = scriptFrom(bundle(), tv.root)
        val r = tv.run(listOf("/bin/sh"), script, listOf("--remove-apps"))
        assertEquals(3, r.exit, r.toString())
        assertTrue(r.err.contains("--allow-other-tv") && r.err.contains("Nothing was changed"), r.err)
        assertEquals(after, tv.read(), "a refused run must not change the TV")
        assertTrue(tv.lines().all { it.startsWith("getprop ") }, tv.lines().toString())

        val forced = tv.run(listOf("/bin/sh"), script, listOf("--remove-apps", "--allow-other-tv"))
        assertEquals(0, forced.exit, forced.toString())
        assertEquals(before, tv.read())
        assertTrue(forced.err.contains("Careful"), forced.err)
    }

    @Test fun refusesWhenNoHashToolCanCheckTheTv() {
        val tv = fakeTv("nohash")
        val tools = File(tv.root, "tools").also { it.mkdirs() }
        for (t in listOf("tr", "cut", "cat", "grep", "rm", "mkdir")) {
            val src = Shells.which(t) ?: fail("$t not on PATH")
            Files.createSymbolicLink(File(tools, t).toPath(), src.toPath())
        }
        File(tv.bin, "adb").copyTo(File(tools, "adb")).setExecutable(true)
        val r = tv.run(listOf("/bin/sh"), scriptFrom(bundle(), tv.root), pathOverride = tools.absolutePath)
        assertEquals(3, r.exit, r.toString())
        assertTrue(r.err.contains("no sha256sum, shasum or openssl"), r.err)
        assertEquals(after, tv.read())
    }

    @Test fun aSerialIsPassedToEveryAdbCall() {
        val tv = fakeTv("serial")
        val script = scriptFrom(bundle(), tv.root)
        val r = tv.run(listOf("/bin/sh"), script, listOf("-s", "192.0.2.10:5555", "--remove-apps"))
        assertEquals(0, r.exit, r.toString())
        assertTrue(tv.calls().all { it.startsWith("adb <-s> <192.0.2.10:5555> ") }, tv.calls().joinToString("\n"))
        assertEquals(before, tv.read())

        val wrong = fakeTv("wrongserial")
        val w = wrong.run(listOf("/bin/sh"), scriptFrom(bundle(), wrong.root), listOf("-s", "emulator-5554"))
        assertEquals(2, w.exit, w.toString())
        assertEquals(after, wrong.read())
    }

    @Test fun hostConnectsFirstOnPort5555() {
        val tv = fakeTv("host")
        val r = tv.run(listOf("/bin/sh"), scriptFrom(bundle(), tv.root), listOf("--host", "192.0.2.10", "--remove-apps"))
        assertEquals(0, r.exit, r.toString())
        val calls = tv.calls()
        assertEquals("adb <connect> <192.0.2.10:5555>", calls.first())
        assertTrue(calls.drop(1).all { it.startsWith("adb <-s> <192.0.2.10:5555> ") }, calls.joinToString("\n"))
        assertEquals(before, tv.read())
    }

    @Test fun dryRunPrintsTheCommandsAndTouchesNothing() {
        val b = bundle()
        val tv = fakeTv("dry")
        val r = tv.run(listOf("/bin/sh"), scriptFrom(b, tv.root), listOf("--dry-run"))
        assertEquals(0, r.exit, r.toString())
        assertTrue(tv.calls().isEmpty(), "a dry run must not call adb")
        val commands = b.entries.getValue(RestoreBundle.COMMANDS).decodeToString().lines().filter { it.isNotEmpty() && !it.startsWith("#") }
        assertEquals(commands, r.out.lines().filter { it.isNotEmpty() && !it.startsWith("#") })
        assertTrue(r.out.contains("# kept (needs --remove-apps): 'pm' 'uninstall' '--user' '0' 'com.gathin.tvhome'"), r.out)
    }

    @Test fun aFailedStepIsReportedAndTheRestStillRun() {
        val missing = Scenario.disabledAfter.first { it != Scenario.putBackPkg && it != Scenario.defaultStatePkg }.value
        val tv = fakeTv("fail", state = after.copy(packages = after.packages - missing))
        val r = tv.run(listOf("/bin/sh"), scriptFrom(bundle(), tv.root), listOf("--remove-apps"))
        assertEquals(1, r.exit, r.toString())
        assertTrue(r.out.contains("FAILED   Switch back on: $missing"), r.out)
        assertTrue(r.out.contains("Unknown package: $missing"), r.out)
        assertEquals(before.copy(packages = before.packages - missing), tv.read())
    }

    @Test fun aHostileNicknameAndModelStayInert() {
        val hostile = "Den'; touch PWNED1; echo \"\$(touch PWNED2)\"`touch PWNED3`\n touch PWNED4 #"
        val tv0 = Scenario.tv()
        val tv = tv0.copy(nickname = hostile, facts = tv0.facts.copy(model = "M'\$(touch PWNED5)"))
        val b = bundle(tv)
        val dir = fakeTv("hostile")
        val script = scriptFrom(b, dir.root)
        for (shell in shells()) {
            val syntax = dir.run(shell + "-n", script)
            assertEquals(0, syntax.exit, "$shell -n\n$syntax")
        }
        val r = dir.run(listOf("/bin/sh"), script, listOf("--remove-apps"))
        assertEquals(0, r.exit, r.toString())
        assertEquals(before, dir.read())
        val pwned = dir.root.walkTopDown().filter { it.name.startsWith("PWNED") }.toList()
        assertTrue(pwned.isEmpty(), "free text ran as code: $pwned")
        val text = script.readText()
        assertFalse(text.lines().any { it.trimStart().startsWith("touch") }, "a nickname broke out of its line")
    }

    // Before the first write the script reads the identity, and only reads.
    private fun assertIdentityCheckedFirst(lines: List<String>, shell: List<String>) {
        val firstWrite = lines.indexOfFirst { !it.startsWith("getprop ") }
        val reads = lines.take(firstWrite)
        assertEquals(listOf("getprop 'ro.serialno'", "getprop 'ro.product.device'", "getprop 'ro.product.model'"), reads, "$shell: $lines")
    }
}
