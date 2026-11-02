package com.midroid.mi5gtv.matches;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Parsers run on recorded ESPN responses (Premier League, 1 Nov 2026). Robolectric for org.json. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EspnTest {
    static String res(String n) throws Exception {
        try (InputStream in = EspnTest.class.getClassLoader().getResourceAsStream(n)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test public void standingsSortedWithPoints() throws Exception {
        List<Espn.Row> rows = Espn.standings(res("standings.json"));
        assertEquals(20, rows.size());
        assertEquals("1", rows.get(0).rank);
        assertEquals("20", rows.get(19).rank);
        assertFalse(rows.get(0).points.isEmpty());
    }

    @Test public void keyEventsKeepOnlyGoalsCardsSubs() throws Exception {
        List<String> ev = Espn.keyEvents(res("summary.json"));
        assertFalse(ev.isEmpty());
        for (String e : ev) assertTrue(e, e.contains("هدف") || e.contains("بطاقة") || e.contains("تبديل") || e.contains("ركلة"));
    }

    @Test public void statsPaired() throws Exception {
        List<String[]> s = Espn.stats(res("summary.json"));
        assertTrue(s.size() >= 5);
        for (String[] r : s) assertEquals(3, r.length);
    }

    @Test public void eventTypes() {
        assertEquals("⚽ هدف", Espn.eventAr("Goal - Header"));
        assertEquals("⚽ هدف عكسي", Espn.eventAr("Own Goal"));
        assertEquals("🟨 بطاقة صفراء", Espn.eventAr("Yellow Card"));
        assertNull(Espn.eventAr("Kickoff"));
        assertNull(Espn.eventAr("Start Delay"));
    }

    @Test public void garbageIsEmpty() {
        assertTrue(Espn.standings("{}").isEmpty());
        assertTrue(Espn.keyEvents("nope").isEmpty());
        assertTrue(Espn.stats("{\"boxscore\":{}}").isEmpty());
    }
}
