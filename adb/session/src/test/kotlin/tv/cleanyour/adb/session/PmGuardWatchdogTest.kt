package tv.cleanyour.adb.session

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// INTEGRATION-PLAN T4: the HOME watchdog (D14, SECURITY 3.6) is the one chained pm line the
// session admits, and only in exactly W3's two template shapes. The goldens are copies of W3's
// (see the first line of each resource file).
class PmGuardWatchdogTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    private fun golden(name: String): String {
        val text = javaClass.getResource("/watchdog/$name")?.readText() ?: error("missing resource $name")
        // W3's newer goldens put a preparatory `rm` line first; the watchdog is the `sh -c` line.
        return text.lines().filter { it.startsWith("line: ") }.map { it.removePrefix("line: ") }.single { "sh -c '" in it }
    }

    private val a by lazy { golden("16-arm-home-watchdog-a.txt") }
    private val b by lazy { golden("17-arm-home-watchdog-b.txt") }

    @Test fun bothTemplateShapesAreAdmitted() {
        for (line in listOf(a, b)) {
            assertTrue(PmGuard.WATCHDOG_RE.matches(line), line)
            assertNull(PmGuard.refusal(line), line)
        }
        // Other valid values fill the same shapes.
        val other = a.replace("sleep 120", "sleep 300").replace("0123456789abcdef", "fedcba9876543210")
            .replace("com.google.android.tvlauncher/.MainActivity", "com.mitv.tvhome.atv/com.mitv.tvhome.MainActivity")
            .replace("--user 0 com.google.android.tvlauncher;", "--user 0 com.mitv.tvhome.atv;")
        assertNull(PmGuard.refusal(other), other)
    }

    @Test fun oneCharacterMutationsAreRefused() {
        for ((name, line) in listOf("a" to a, "b" to b)) {
            val mutations = listOf(
                "extra ; at the end" to "$line;",
                "extra ; inside the body" to line.replaceFirst("&& exit 0;", "&& exit 0;;"),
                "extra ; after sleep" to line.replaceFirst("sleep 120 & wait;", "sleep 120 & wait;;"),
                "a quote in the body" to line.replaceFirst("sleep 120", "sleep 12'0"),
                "a quote before the name" to line.replaceFirst(" cleanyourtv-rollback-", " 'cleanyourtv-rollback-"),
                "changed token file path" to line.replaceFirst("/data/local/tmp/cleanyourtv/home-ok-", "/data/local/tmp/cleanyourtw/home-ok-"),
                "changed log path" to line.replaceFirst("/watchdog-", "/watchdoh-"),
                "token differs in one place" to line.replaceFirst("rollback-0123456789abcdef", "rollback-0123456789abcdee"),
                "another package in set-home-activity" to line.replaceFirst("--user 0 com.google.android.tvlauncher/", "--user 0 com.google.android.tvlauncheR/"),
                "a pipe" to line.replaceFirst("2>&1", "2>|1"),
                "too few digits of seconds" to line.replaceFirst("sleep 120", "sleep 1"),
            )
            for ((what, mutated) in mutations) {
                assertTrue(mutated != line, "$name: $what did not change the line")
                assertNotNull(PmGuard.refusal(mutated), "$name: $what was admitted: $mutated")
            }
        }
        // The two shapes do not mix.
        assertEquals(b, "( " + a.removePrefix("nohup setsid ") + " )")
        assertNotNull(PmGuard.refusal("nohup setsid $b"))
        assertNotNull(PmGuard.refusal("$a )"))
    }

    @Test fun plainChainedPmWritesAreRefused() {
        assertNotNull(PmGuard.refusal("pm disable-user --user 0 com.example.app; pm enable --user 0 com.example.app"))
        assertNotNull(PmGuard.refusal("pm disable-user --user 0 com.example.app && pm enable --user 0 com.example.app"))
        assertNotNull(PmGuard.refusal("sh -c 'pm enable --user 0 com.example.app; reboot'"))
        assertNull(PmGuard.refusal("pm disable-user --user 0 com.example.app"))
        assertNull(PmGuard.refusal("getprop; getprop ro.product.model"))
    }

    // End to end on the fake: the session sends the watchdog, a mutated one never leaves the phone.
    @Test fun theSessionRunsTheWatchdogAndRefusesAMutation() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        for (text in listOf(a, b)) assertEquals(0, s.exec(line(text)).exit, text)
        assertEquals(2, rig.tv.armedScripts.size)
        assertTrue(rig.tv.violations.isEmpty())
        val e = assertFailsWith<AdbException> { s.exec(line("$a;")) }
        assertIs<AdbError.Policy.GuardFailed>(e.error)
        assertTrue(rig.adbd.connections.single().openedServices.none { it.endsWith("&;") })
    }
}
