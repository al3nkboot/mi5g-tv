package tv.cleanyour.testing.fixtures

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// No device identifier may enter the repo through a capture (W1 row, W6 "no serial or Wi-Fi data").
class RedactionTest {
    private val mac = Regex("\\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b")
    private val privateIp = Regex("\\b(10\\.\\d{1,3}|172\\.(1[6-9]|2\\d|3[01])|192\\.168|100\\.(6[4-9]|[7-9]\\d|1[01]\\d|12[0-7]))\\.\\d{1,3}\\.\\d{1,3}\\b")

    private fun files(): List<File> = Fixtures.conformanceDir.walkTopDown().filter { it.isFile }.toList()

    @Test fun conformanceFixturesCarryNoIdentifiers() {
        val problems = mutableListOf<String>()
        for (f in files()) {
            f.readLines().forEachIndexed { i, line ->
                val where = "${f.relativeTo(Fixtures.fixturesDir)}:${i + 1}"
                mac.findAll(line).filter { it.value != "00:00:00:00:00:00" }.forEach { problems += "$where MAC" }
                if (privateIp.containsMatchIn(line)) problems += "$where private IPv4"
                if (Regex("ssid", RegexOption.IGNORE_CASE).containsMatchIn(line) && !line.endsWith("=REDACTED")) problems += "$where SSID"
                Regex("^\\[(ro\\.serialno|ro\\.boot\\.serialno)\\]: \\[(.*)\\]$").matchEntire(line)?.let {
                    if (it.groupValues[2] != "FIXTURESERIAL0001") problems += "$where serial"
                }
                if (line.startsWith("android_id=") && line != "android_id=0000000000000000") problems += "$where android_id"
            }
        }
        assertEquals(emptyList(), problems)
        assertTrue(files().isNotEmpty())
    }

    @Test fun captureRestoreScriptIsNotImported() {
        assertTrue(files().none { it.name == "restore.sh" })
    }
}
