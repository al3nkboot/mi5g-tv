package com.midroid.mi5gtv.prayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EngineTest {
 @Test public void asrMethodChangesOnlyAsrAndDefaultsToShafi() {
  Settings shafi=Settings.parse("{}"),hanafi=Settings.parse("{\"hanafi\":true}");assertFalse(shafi.hanafi);assertTrue(hanafi.hanafi);
  ZonedDateTime[] s=Engine.day(shafi,LocalDate.of(2026,11,5)),h=Engine.day(hanafi,LocalDate.of(2026,11,5));
  assertTrue(h[Engine.ASR].isAfter(s[Engine.ASR]));for(int i=0;i<Engine.COUNT;i++)if(i!=Engine.ASR)assertEquals(s[i],h[i]);
 }

    static Settings city(String id) throws Exception {
        String json = new String(EngineTest.class.getClassLoader().getResourceAsStream("cities.json") == null
                ? java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/assets/cities.json"))
                : EngineTest.class.getClassLoader().getResourceAsStream("cities.json").readAllBytes(), StandardCharsets.UTF_8);
        JSONArray a = new JSONArray(json);
        for (int i = 0; i < a.length(); i++) {
            JSONObject c = a.getJSONObject(i);
            if (c.getString("id").equals(id)) return Settings.parse("{\"city\":" + c + "}");
        }
        throw new AssertionError(id);
    }

    /** The Oman method against the Ministry's 2026 calendar (same check as the phone app): every time within 1 min. */
    @Test public void omanMethodMatchesTheMinistryCalendar() throws Exception {
        int checked = 0, worst = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                EngineTest.class.getClassLoader().getResourceAsStream("mara_2026.csv"), StandardCharsets.UTF_8))) {
            String line;
            java.util.Map<String, Settings> cache = new java.util.HashMap<>();
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("city_id")) continue;
                String[] f = line.split(",");
                Settings s = cache.get(f[0]);
                if (s == null) { s = city(f[0]); cache.put(f[0], s); }
                ZonedDateTime[] d = Engine.day(s, LocalDate.parse(f[1]));
                for (int i = 0; i < Engine.COUNT; i++) {
                    LocalTime want = LocalTime.parse(f[2 + i]);
                    int diff = (int) Math.abs(Duration.between(want, d[i].toLocalTime()).toMinutes());
                    worst = Math.max(worst, diff);
                    checked++;
                }
            }
        }
        assertTrue(checked > 10000);
        assertTrue("worst " + worst, worst <= 1);
    }

    @Test public void nextRollsOverToTomorrowsFajr() throws Exception {
        Settings s = city("muscat");
        ZonedDateTime late = ZonedDateTime.of(2026, 11, 3, 23, 30, 0, 0, ZoneId.of("Asia/Muscat"));
        Engine.Next n = Engine.next(s, late);
        assertEquals(Engine.FAJR, n.prayer);
        assertEquals(4, n.at.getDayOfMonth());
        assertEquals("00:00:05", Engine.countdown(5));
    }

    @Test public void settingsRejectBadValuesAndKeepForcedAdhanOnByDefault() {
        Settings d = Settings.parse("");
        assertTrue(d.forced);
        Settings s = Settings.parse("{\"method\":\"rm -rf\",\"pre_minutes\":999,\"city\":{\"lat\":200,\"lon\":1,\"zone\":\"Asia/Muscat\"},"
                + "\"overlay\":{\"on\":true,\"corner\":\"x\",\"opacity\":5},\"forced_adhan\":false}");
        assertEquals("OMAN", s.method);
        assertEquals(60, s.preMinutes);
        assertEquals(23.58413, s.lat, 1e-6);
        assertTrue(s.overlay);
        assertEquals("top_end", s.overlayCorner);
        assertEquals(30, s.overlayOpacity);
        assertFalse(s.forced);
        assertEquals("Muscat default", "Asia/Muscat", Settings.parse("not json").zone);
    }

    @Test public void planHasPreAdhanAndFridayKahfOnlyOnFriday() throws Exception {
        Settings s = city("muscat");
        ZonedDateTime thu = ZonedDateTime.of(2026, 11, 5, 8, 0, 0, 0, ZoneId.of("Asia/Muscat"));
        List<Plan.Alarm> p = Plan.upcoming(s, thu);
        assertTrue(p.stream().anyMatch(a -> a.kind == Plan.KIND_PRE));
        Plan.Alarm kahf = p.stream().filter(a -> a.kind == Plan.KIND_KAHF).findFirst().orElseThrow(AssertionError::new);
        assertEquals(DayOfWeek.FRIDAY, kahf.at.getDayOfWeek());
        assertEquals(10, kahf.at.getHour());
        for (int i = 1; i < p.size(); i++) assertFalse(p.get(i).at.isBefore(p.get(i - 1).at));
        assertTrue(p.stream().noneMatch(a -> a.kind == Plan.KIND_PRAYER && a.prayer == Engine.SUNRISE));
    }

    @Test public void forcedVolumeAndOverlayLabel() throws Exception {
        assertEquals(11, AdhanService.forcedIndex(15, 70));
        assertEquals(2, AdhanService.forcedIndex(15, 1)); // clamped to 10%
        assertEquals(1, AdhanService.forcedIndex(7, 10));
        Settings s = city("muscat");
        String l = OverlayService.label(s, ZonedDateTime.of(2026, 11, 3, 23, 30, 0, 0, ZoneId.of("Asia/Muscat")));
        assertTrue(l, l.startsWith("الفجر"));
    }

    @Test public void radioKeepsOnlyNamedHttpStreams() {
        List<RadioActivity.Station> st = RadioActivity.parse("{\"radios\":[{\"name\":\"a\",\"url\":\"https://x/1\"},"
                + "{\"name\":\"\",\"url\":\"https://x/2\"},{\"name\":\"b\",\"url\":\"file:///etc\"}]}");
        assertEquals(1, st.size());
        assertEquals(0, RadioActivity.parse("garbage").size());
    }
    @Test public void bannerHexDecodes() {
        assertEquals("تنبيه وقت الصلاة", BannerReceiver.decode("d8aad986d8a8d98ad98720d988d982d8aa20d8a7d984d8b5d984d8a7d8a9"));
        assertNull(BannerReceiver.decode("zz"));
        assertNull(BannerReceiver.decode("abc"));
        assertNull(BannerReceiver.decode(null));
        assertNull(BannerReceiver.decode("0a0d"));
    }
}
