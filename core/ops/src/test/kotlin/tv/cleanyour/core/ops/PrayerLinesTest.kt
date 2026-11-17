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

    // 2.4 M5: the phone's adhan recordings go from the staging folder into the module's own files.
    @Test fun soundLinesMoveTheRecordingIntoTheModule() {
        val fajr = ShellRenderer.prayerSoundLines(fajr = true).map { it.text }
        assertTrue(fajr[1] == "'cp' '/sdcard/cleanyourtv/mi5g_adhan_fajr.audio' '/sdcard/Android/data/com.midroid.mi5gtv.prayer/files/adhan_fajr.audio'", fajr[1])
        assertTrue(fajr.last() == "'rm' '-f' '/sdcard/cleanyourtv/mi5g_adhan_fajr.audio'")
        val main = ShellRenderer.prayerSoundLines(fajr = false).map { it.text }
        assertTrue("adhan_custom.audio" in main[1])
        assertEquals("/sdcard/cleanyourtv/mi5g_adhan.audio", ShellRenderer.prayerSoundStage(false).absolute)
        assertTrue(main.all { it.all { c -> c in ' '..'~' } })
    }

    // 2.4 M6: Mi5G Score's grants for the kick-off banner and the pinned corner score.
    @Test fun scoreGrantLinesAllowOverlayAlarmsAndNotifications() {
        val l = ShellRenderer.scoreGrantLines().map { it.text }
        assertEquals(listOf(
            "'appops' 'set' 'com.midroid.mi5gtv.matches' 'SYSTEM_ALERT_WINDOW' 'allow'",
            "'appops' 'set' 'com.midroid.mi5gtv.matches' 'SCHEDULE_EXACT_ALARM' 'allow'",
            "'pm' 'grant' 'com.midroid.mi5gtv.matches' 'android.permission.POST_NOTIFICATIONS'",
        ), l)
        assertTrue(l.all { line -> line.all { it.code < 128 } })
    }
}
