package tv.cleanyour.testing.fakeadbd

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellParserTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)
    private fun argv(line: String) = ShellParser.parse(line).commands.map { it.argv }

    @Test fun singleQuotedTokens() {
        assertEquals(listOf(listOf("pm", "disable-user", "--user", "0", "com.x.y")), argv("'pm' 'disable-user' '--user' '0' 'com.x.y'"))
        assertEquals(listOf(listOf("echo", "a b", "c")), argv("echo 'a b' c"))
        assertEquals(listOf(listOf("echo", "it's")), argv("echo \"it's\""))
        assertEquals(listOf(listOf("echo", "a;b")), argv("echo 'a;b'"))
    }

    @Test fun separatorsAndJoiners() {
        val p = ShellParser.parse("getprop a; echo ---\ngetprop b && echo ok || echo no")
        assertEquals(5, p.commands.size)
        assertTrue(p.chained)
        assertEquals(listOf(null, ";", ";", "&&", "||"), p.commands.map { it.joiner })
        assertFalse(ShellParser.parse("echo one").chained)
    }

    @Test fun redirectionsAreDropped() {
        assertEquals(listOf(listOf("cmd", "x")), argv("cmd x >/dev/null 2>&1"))
        assertEquals(listOf(listOf("cmd", "x")), argv("cmd x > /dev/null"))
        assertEquals(listOf(listOf("ls", "/sdcard")), argv("ls /sdcard 2>/dev/null"))
    }

    @Test fun backgroundCountsAsChained() {
        val p = ShellParser.parse("nohup setsid sh -c 'sleep 1' >/dev/null 2>&1 &")
        assertTrue(p.chained)
        assertTrue(p.commands.single().background)
    }

    @Test fun unsupportedSyntaxIsReported() {
        assertNotNull(ShellParser.parse("echo a | cat").unsupported)
        assertNotNull(ShellParser.parse("echo `id`").unsupported)
        assertNotNull(ShellParser.parse("echo $(id)").unsupported)
        assertNotNull(ShellParser.parse("echo 'open").unsupported)
        assertNull(ShellParser.parse("echo \$HOME").unsupported)
    }

    @Test fun interpreterRefusesChainedPmWrites() = runBlocking<Unit> {
        val tv = Personas.tclG05()
        val shell = FakeShell(tv, Faults())
        val r = shell.run("pm disable-user --user 0 com.tcl.browser; pm disable-user --user 0 com.tcl.tv")
        assertEquals(126, r.exit)
        assertEquals(1, tv.violations.size)
        assertEquals("enabled", tv.packages.getValue("com.tcl.browser").state)
        val ok = shell.run("'pm' 'disable-user' '--user' '0' 'com.tcl.browser'")
        assertEquals(0, ok.exit)
        assertEquals("Package com.tcl.browser new state: disabled-user\n", String(ok.stdout))
        assertEquals("disabled-user", tv.packages.getValue("com.tcl.browser").state)
        // Chained reads are fine (D40 probe scripts).
        val reads = shell.run("getprop ro.product.model; echo ---; pm list packages -d")
        assertEquals(0, reads.exit)
        assertEquals("Smart TV\n---\npackage:com.tcl.browser\n", String(reads.stdout))
        assertEquals(1, tv.violations.size)
    }

    @Test fun vocabularyChangesState() = runBlocking<Unit> {
        val tv = Personas.chromecast()
        val shell = FakeShell(tv, Faults())
        assertEquals("1\n", String(shell.run("settings get global adb_enabled").stdout))
        shell.run("settings put secure screensaver_enabled 0")
        assertEquals("0\n", String(shell.run("settings get secure screensaver_enabled").stdout))
        assertEquals("Deleted 1 rows\n", String(shell.run("settings delete secure screensaver_enabled").stdout))
        assertEquals("null\n", String(shell.run("settings get secure screensaver_enabled").stdout))
        assertEquals(Effect.AdbOff, shell.run("settings put global adb_enabled 0").effect)
        assertEquals(Effect.Reboot, shell.run("reboot").effect)
        shell.run("mkdir -p /sdcard/cleanyourtv")
        tv.files["/sdcard/cleanyourtv/a.txt"] = "hi".encodeToByteArray()
        assertEquals("hi", String(shell.run("cat /sdcard/cleanyourtv/a.txt").stdout))
        assertTrue(String(shell.run("sha256sum '/sdcard/cleanyourtv/a.txt'").stdout).startsWith(sha256Hex("hi".encodeToByteArray()) + "  "))
        assertEquals(1, shell.run("cat /sdcard/none").exit)
        assertEquals("Success\n", String(shell.run("cmd package set-home-activity --user 0 com.netflix.ninja/.MainActivity").stdout))
        assertTrue(String(shell.run("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN").stdout).endsWith("com.netflix.ninja/.MainActivity\n"))
        assertEquals(1, shell.run("cmd package set-home-activity com.nope/.X").exit)
        assertEquals(255, shell.run("am start -W -n com.nope/.X").exit)
        assertEquals(0, shell.run("am start -W -n com.netflix.ninja/.MainActivity").exit)
        assertEquals(listOf("com.netflix.ninja/.MainActivity"), tv.startedActivities)
        assertEquals(tv.bootId + "\n", String(shell.run("cat /proc/sys/kernel/random/boot_id").stdout))
        assertEquals(0, shell.run("pm path android").exit)
        assertEquals(1, shell.run("pm path com.nope").exit)
    }

    @Test fun installsComeFromTheCatalog() = runBlocking<Unit> {
        val tv = Personas.tclG05()
        val shell = FakeShell(tv, Faults())
        val apk = ByteArray(1000) { 3 }
        tv.files["/data/local/tmp/cleanyourtv/x.apk"] = apk
        assertTrue(String(shell.run("pm install -r --user 0 /data/local/tmp/cleanyourtv/x.apk").stdout).startsWith("Failure [INSTALL_PARSE_FAILED_NOT_APK"))
        tv.apkCatalog[sha256Hex(apk)] = FakePackage("com.gathin.tvhome", "/data/app/com.gathin.tvhome-1/base.apk", versionCode = 7, signer = "ab".repeat(32))
        assertEquals("Success\n", String(shell.run("pm install -r --user 0 /data/local/tmp/cleanyourtv/x.apk").stdout))
        assertEquals(7, tv.packages.getValue("com.gathin.tvhome").versionCode)
        assertTrue(String(shell.run("dumpsys package com.gathin.tvhome").stdout).contains("versionCode=7"))
        assertEquals("Success\n", String(shell.run("pm uninstall com.gathin.tvhome").stdout))
        assertEquals(1, shell.run("pm uninstall com.tcl.tv").exit)
        tv.installFailure = "INSTALL_FAILED_INSUFFICIENT_STORAGE"
        tv.apkCatalog[sha256Hex(apk)] = FakePackage("com.gathin.tvhome", "/data/app/x/base.apk")
        assertEquals("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]\n", String(shell.run("pm install -r /data/local/tmp/cleanyourtv/x.apk").stdout))
    }

    @Test fun handlersComeFirst() = runBlocking<Unit> {
        val tv = Personas.tclG05()
        tv.handlers += { argv, _ -> if (argv.firstOrNull() == "dumpsys" && argv.getOrNull(1) == "hdmi_control") ExecOutcome("custom\n") else null }
        val shell = FakeShell(tv, Faults())
        assertEquals("custom\n", String(shell.run("dumpsys hdmi_control").stdout))
    }
}
