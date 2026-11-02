package tv.cleanyour.core.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrayerLinesTest {
    @Test fun arabicBannerTravelsAsUtf8Hex() {
        val line = ShellRenderer.arabicBannerLine("تنبيه وقت الصلاة").text
        val hex = Regex("'--es' 'hex' '([0-9a-f]+)'").find(line)!!.groupValues[1]
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals("تنبيه وقت الصلاة", String(bytes, Charsets.UTF_8))
        assertTrue("'-p' 'com.midroid.mi5gtv.prayer'" in line)
        assertFailsWith<IllegalArgumentException> { ShellRenderer.arabicBannerLine("  ") }
    }

    @Test fun asciiDetection() {
        assertTrue(ShellRenderer.isAsciiBanner("Dinner is ready"))
        assertTrue(!ShellRenderer.isAsciiBanner("تنبيه وقت الصلاة"))
    }

    @Test fun settingsLinesCopyGrantAndReload() {
        val l = ShellRenderer.prayerSettingsLines().map { it.text }
        assertTrue(l[1].startsWith("'cp' '/sdcard/cleanyourtv/mi5g_prayer.json' '/sdcard/Android/data/com.midroid.mi5gtv.prayer/files/settings.json'"))
        assertTrue(l.any { "SYSTEM_ALERT_WINDOW" in it && "allow" in it })
        assertTrue(l.any { "deviceidle" in it })
        assertTrue(l.any { "com.midroid.mi5gtv.prayer.RELOAD" in it })
        assertTrue(l.last().startsWith("'rm' '-f'"))
        assertTrue(l.none { "uninstall" in it || "disable" in it })
    }
}
