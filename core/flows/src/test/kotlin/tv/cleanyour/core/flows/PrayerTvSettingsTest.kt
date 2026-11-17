package tv.cleanyour.core.flows

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrayerTvSettingsTest {
    @org.junit.Test fun asrDefaultsToShafiAndSerializesBothMethods() {
        kotlin.test.assertTrue(PrayerTvSettings().toJson().contains("\"hanafi\":false"))
        kotlin.test.assertTrue(PrayerTvSettings(hanafi = true).toJson().contains("\"hanafi\":true"))
    }

    @Test fun defaultsAreMuscatOmanForcedAdhan() {
        val j = PrayerTvSettings().toJson()
        assertTrue("\"id\":\"muscat\"" in j)
        assertTrue("\"method\":\"OMAN\"" in j)
        assertTrue("\"forced_adhan\":true" in j)
        assertTrue("\"kahf\":{\"on\":true" in j)
    }

    @Test fun valuesAreClampedAndQuoted() {
        val j = PrayerTvSettings(forcedVolume = 500, preMinutes = -3, overlay = true, corner = OverlayCorner.BOTTOM_START,
            city = PrayerCity("x\"y", "ع", 1.0, 2.0, "Asia/Muscat", "")).toJson()
        assertTrue("\"forced_volume\":100" in j)
        assertTrue("\"pre_minutes\":0" in j)
        assertTrue("\"corner\":\"bottom_start\"" in j)
        assertTrue("\"id\":\"x\\\"y\"" in j)
    }

    @Test fun cyclingWraps() {
        assertEquals(0, PrayerTvSettings.PRE_MINUTES.after(30))
        assertEquals(PrayerMethod.UMM_AL_QURA, PrayerMethod.entries.after(PrayerMethod.OMAN))
        val c = PrayerCities.ALL
        assertEquals(c.last(), c.asReversed().after(c.first()))
    }

    @Test fun phoneCitiesMatchTheTvModule() {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        val json = File(dir, "tv/prayer/src/main/assets/cities.json").readText()
        val ids = Regex("\"id\": \"([a-z0-9_]+)\"").findAll(json).map { it.groupValues[1] }.toList()
        assertEquals(ids, PrayerCities.ALL.map { it.id })
    }

    // 2.4 M5: per-prayer alerts and offsets, the adhan sounds and Keep Alive.
    @Test fun perPrayerAlertsOffsetsAndSoundsSerialize() {
        val d = PrayerTvSettings().toJson()
        assertTrue("\"adjust\":[0,0,0,0,0,0]" in d, d)
        assertTrue("\"modes\":[2,0,2,2,2,2]" in d, d)
        assertTrue("\"adhan_sound\":\"default\",\"fajr_sound\":\"same\"" in d, d)
        assertTrue("\"keep_alive\":true" in d, d)
        assertTrue(d.endsWith("}"))
        val s = PrayerTvSettings(offsets = listOf(3, 0, 0, -2, 0, 99), adhanSound = PrayerSound.CUSTOM, fajrSound = FajrSound.CUSTOM, keepAlive = false)
        val j = s.toJson()
        assertTrue("\"adjust\":[3,0,0,-2,0,10]" in j, j)   // clamped to ±10
        assertTrue("\"adhan_sound\":\"custom\",\"fajr_sound\":\"custom\"" in j)
        assertTrue("\"keep_alive\":false" in j)
        assertTrue(s.needsAdhanFile && s.needsFajrFile)
        assertTrue(!PrayerTvSettings().needsAdhanFile && !PrayerTvSettings().needsFajrFile)
    }

    @Test fun alertsCycleAndSunriseNeverGetsTheAdhan() {
        var s = PrayerTvSettings()
        assertEquals(PrayerAlert.ADHAN, s.alertAt(0))
        s = s.withNextAlert(0)
        assertEquals(PrayerAlert.OFF, s.alertAt(0))
        s = s.withNextAlert(0).withNextAlert(0)
        assertEquals(PrayerAlert.ADHAN, s.alertAt(0))
        assertEquals(PrayerAlert.OFF, s.alertAt(1))
        s = s.withNextAlert(1)
        assertEquals(PrayerAlert.BANNER, s.alertAt(1))
        s = s.withNextAlert(1)
        assertEquals(PrayerAlert.OFF, s.alertAt(1))
        assertEquals(PrayerAlert.BANNER, PrayerTvSettings(alerts = List(6) { PrayerAlert.ADHAN }).alertAt(1))
        assertTrue("\"modes\":[2,1,2,2,2,2]" in PrayerTvSettings(alerts = List(6) { PrayerAlert.ADHAN }).toJson())
    }

    @Test fun offsetsCycleThroughPlusThenMinus() {
        var s = PrayerTvSettings()
        val seen = mutableListOf<Int>()
        repeat(PrayerTvSettings.OFFSETS.size) { s = s.withNextOffset(3); seen += s.offsetAt(3) }
        assertEquals(PrayerTvSettings.OFFSETS.drop(1) + 0, seen)
        assertEquals(0, s.offsetAt(0))
        assertEquals(0, PrayerTvSettings(offsets = listOf(1)).offsetAt(4))   // short list from an old save
    }
}
