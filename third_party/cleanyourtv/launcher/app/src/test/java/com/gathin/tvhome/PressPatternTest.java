package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PressPatternTest {
    @Test public void singleResolvesAfterTheGap() {
        PressPattern p = new PressPattern();
        p.down(0, 0);
        assertEquals(PressPattern.NONE, p.up(80));
        assertEquals(PressPattern.NONE, p.timeout(200));
        assertEquals(PressPattern.SINGLE, p.timeout(80 + PressPattern.GAP_MS));
    }

    @Test public void doubleAndTriple() {
        PressPattern p = new PressPattern();
        p.down(0, 0); p.up(60); p.down(200, 0); p.up(260);
        assertEquals(PressPattern.DOUBLE, p.timeout(260 + PressPattern.GAP_MS));
        p.down(1000, 0); p.up(1050); p.down(1200, 0); p.up(1250); p.down(1400, 0);
        assertEquals(PressPattern.TRIPLE, p.up(1450));
        assertEquals(-1, p.deadline());
    }

    @Test public void holdWithAndWithoutRepeats() {
        PressPattern p = new PressPattern();
        p.down(0, 0);
        assertEquals(PressPattern.HOLD, p.up(700));
        p.down(1000, 0);
        assertEquals(PressPattern.NONE, p.down(1300, 1));
        assertEquals(PressPattern.HOLD, p.down(1650, 2));
        assertEquals(PressPattern.NONE, p.down(1700, 3));
        assertEquals(PressPattern.NONE, p.up(1800));
        assertEquals(-1, p.deadline());
    }

    @Test public void mapParsingKeepsOnlyKnownActions() {
        Map<String, Object> all = new HashMap<>();
        all.put("map.176.1", "settings");
        all.put("map.190.2", "app:com.teamsmart.videomanager.tv");
        all.put("map.190.H", "control_center");
        all.put("map.19.1", "rm -rf");
        all.put("map.x.1", "home");
        all.put("other", "home");
        Map<Integer, Map<String, String>> m = ButtonMap.parse(all);
        assertEquals("settings", m.get(176).get("1"));
        assertEquals("app:com.teamsmart.videomanager.tv", m.get(190).get("2"));
        assertEquals("control_center", m.get(190).get("H"));
        assertNull(m.get(19));
        assertEquals(2, m.size());
        assertTrue(ButtonMap.validAction("app:bad pkg") == null);
    }
}
