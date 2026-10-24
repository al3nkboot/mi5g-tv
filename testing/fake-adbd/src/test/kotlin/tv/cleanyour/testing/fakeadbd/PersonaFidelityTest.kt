package tv.cleanyour.testing.fakeadbd

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The Mi TV persona answers the capture's commands with the capture's bytes (R2: fidelity).
class PersonaFidelityTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)
    private val dir = Personas.captureDir("mitv-axso0-2026-10-18")
    private fun capture(name: String) = File(dir, name).readText()

    private fun run(tv: FakeTv, line: String): String = runBlocking { String(FakeShell(tv, Faults()).run(line).stdout) }

    @Test fun getpropMatchesTheCapture() {
        val tv = Personas.miTv()
        assertEquals(capture("getprop.txt"), run(tv, "getprop"))
        assertEquals("MiTV-AXSO0\n", run(tv, "getprop ro.product.model"))
        assertEquals("dangal", tv.device)
        assertEquals("FIXTURESERIAL0001", tv.serial)
        assertEquals(28, tv.sdk)
        assertTrue(tv.adbSecure)
    }

    @Test fun packageListsMatchTheCapture() {
        val after = Personas.miTv(debloated = true)
        assertEquals(capture("packages-all.txt"), run(after, "pm list packages -f"))
        assertEquals(capture("packages-3rdparty.txt"), run(after, "pm list packages -3"))
        assertEquals(capture("packages-disabled-after.txt"), run(after, "pm list packages -d"))
        val before = Personas.miTv(debloated = false)
        assertEquals(capture("packages-disabled.txt"), run(before, "pm list packages -d"))
        assertEquals(107, before.packages.size)
    }

    @Test fun settingsAndHomeMatchTheCapture() {
        val tv = Personas.miTv()
        for (ns in listOf("global", "secure", "system")) assertEquals(capture("settings-$ns.txt"), run(tv, "settings list $ns"), ns)
        assertEquals(capture("home-activity.txt"), run(tv, "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN"))
    }

    @Test fun disablingChangesWhatTheListsSay() = runBlocking<Unit> {
        val tv = Personas.miTv()
        val shell = FakeShell(tv, Faults())
        val target = tv.packages.values.first { it.system && it.name != "android" }.name
        assertEquals("", String(shell.run("pm list packages -d").stdout))
        assertEquals(0, shell.run("'pm' 'disable-user' '--user' '0' '$target'").exit)
        assertEquals("package:$target\n", String(shell.run("pm list packages -d").stdout))
        assertEquals(0, shell.run("'pm' 'enable' '$target'").exit)
        assertEquals("", String(shell.run("pm list packages -d").stdout))
        assertTrue(tv.violations.isEmpty())
    }

    @Test fun tvIdFollowsD37() {
        val tv = Personas.miTv()
        assertEquals(sha256Hex("FIXTURESERIAL0001dangal".encodeToByteArray()).take(16), tv.tvIdHex())
        assertTrue(Personas.tclG05().synthetic)
        assertTrue(Personas.chromecast().synthetic)
        assertTrue(Personas.tclG05().tvIdHex() != tv.tvIdHex())
    }

    @Test fun bannerCarriesTheFeatures() {
        val b = Personas.miTv().banner()
        assertTrue(b.startsWith("device::ro.product.name="))
        assertTrue(b.contains("ro.product.model=MiTV-AXSO0;ro.product.device=dangal;features="))
        assertTrue(b.contains("shell_v2"))
    }
}
