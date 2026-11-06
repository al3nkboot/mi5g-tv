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
}
