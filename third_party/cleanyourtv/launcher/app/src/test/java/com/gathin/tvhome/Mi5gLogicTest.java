package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Mi5gLogicTest {
    @Test public void sleepCyclesAndWrapsToOff() {
        assertEquals(15, ControlCenterActivity.nextSleep(0));
        assertEquals(30, ControlCenterActivity.nextSleep(15));
        assertEquals(60, ControlCenterActivity.nextSleep(42));
        assertEquals(0, ControlCenterActivity.nextSleep(120));
    }

    @Test public void clockDriftStaysOnScreen() {
        for (float f : new float[]{0f, 0.5f, 0.999f}) {
            int[] o = ClockActivity.offset(1920, 1080, 600, 300, f, f);
            assertTrue(o[0] >= 0 && o[0] + 600 <= 1920);
            assertTrue(o[1] >= 0 && o[1] + 300 <= 1080);
        }
        int[] tiny = ClockActivity.offset(100, 100, 600, 300, 0.7f, 0.7f);
        assertEquals(0, tiny[0]);
        assertEquals(0, tiny[1]);
    }

    @Test public void navigationKeysCannotBeMapped() {
        assertTrue(ButtonMapperActivity.isNavigation(android.view.KeyEvent.KEYCODE_DPAD_CENTER));
        assertTrue(!ButtonMapperActivity.isNavigation(android.view.KeyEvent.KEYCODE_SETTINGS));
    }
}
