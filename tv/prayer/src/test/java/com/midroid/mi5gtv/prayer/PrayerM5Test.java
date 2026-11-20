package com.midroid.mi5gtv.prayer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** 2.4 M5: per-prayer offsets and alerts from the phone, the adhan recordings, Keep Alive. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrayerM5Test {
    private static final ZoneId MCT = ZoneId.of("Asia/Muscat");

    @Test public void phoneOffsetsAndAlertsMoveTheTimes() throws Exception {
        Settings base = EngineTest.city("muscat");
        String city = "\"city\":{\"id\":\"muscat\",\"ar\":\"مسقط\",\"lat\":23.58413,\"lon\":58.40778,\"zone\":\"Asia/Muscat\"}";
        Settings s = Settings.parse("{" + city + ",\"adjust\":[2,0,0,-3,0,10],\"modes\":[2,2,1,2,0,2]}");
        assertArrayEquals(new int[]{2, 0, 0, -3, 0, 10}, s.adjust);
        assertArrayEquals(new int[]{2, 1, 1, 2, 0, 2}, s.modes);   // sunrise never gets the adhan
        LocalDate d = LocalDate.of(2026, 11, 18);
        ZonedDateTime[] a = Engine.day(base, d), b = Engine.day(s, d);
        assertEquals(Duration.ofMinutes(2), Duration.between(a[Engine.FAJR], b[Engine.FAJR]));
        assertEquals(Duration.ofMinutes(-3), Duration.between(a[Engine.ASR], b[Engine.ASR]));
        assertEquals(Duration.ofMinutes(10), Duration.between(a[Engine.ISHA], b[Engine.ISHA]));
        // Maghrib off: no alarm for it at all.
        List<Plan.Alarm> p = Plan.upcoming(s, ZonedDateTime.of(2026, 11, 18, 8, 0, 0, 0, MCT));
        assertTrue(p.stream().noneMatch(x -> x.prayer == Engine.MAGHRIB && x.kind != Plan.KIND_KAHF && x.kind != Plan.KIND_KEEP));
    }

    @Test public void phoneJsonOfOlderVersionsKeepsDefaults() {
        Settings s = Settings.parse("{\"adjust\":[1,2],\"modes\":[9,9,9,9,9,9],\"adhan_sound\":\"http://x\",\"fajr_sound\":\"rm\"}");
        assertArrayEquals(new int[6], s.adjust);              // wrong length ignored
        assertArrayEquals(new int[]{2, 1, 2, 2, 2, 2}, s.modes); // clamped to adhan, sunrise to a banner
        assertEquals("default", s.adhanSound);
        assertEquals("same", s.fajrSound);
        assertTrue(s.keepAlive);
        assertTrue("forced adhan stays on by default", Settings.parse("{}").forced);
    }

    @Test public void separateFajrAdhanPicksItsOwnFile() throws Exception {
        File dir = Files.createTempDirectory("prayer").toFile();
        File main = new File(dir, Settings.SOUND_MAIN), fajr = new File(dir, Settings.SOUND_FAJR);
        Settings same = Settings.parse("{\"adhan_sound\":\"custom\",\"fajr_sound\":\"same\"}");
        assertNull("no file yet: built-in", same.soundFile(dir, Engine.DHUHR));
        Files.write(main.toPath(), new byte[]{1, 2, 3});
        assertEquals(main, same.soundFile(dir, Engine.DHUHR));
        assertEquals(main, same.soundFile(dir, Engine.FAJR));
        Settings ownFajr = Settings.parse("{\"adhan_sound\":\"custom\",\"fajr_sound\":\"custom\"}");
        assertNull("Fajr file missing: built-in", ownFajr.soundFile(dir, Engine.FAJR));
        Files.write(fajr.toPath(), new byte[]{4});
        assertEquals(fajr, ownFajr.soundFile(dir, Engine.FAJR));
        assertEquals(main, ownFajr.soundFile(dir, Engine.ISHA));
        Settings builtInFajr = Settings.parse("{\"adhan_sound\":\"custom\",\"fajr_sound\":\"default\"}");
        assertNull(builtInFajr.soundFile(dir, Engine.FAJR));
        assertEquals(main, builtInFajr.soundFile(dir, Engine.ASR));
        assertNull(Settings.parse("{}").soundFile(dir, Engine.ASR));
        assertNull(same.soundFile(null, Engine.ASR));
    }

    @Test public void keepAliveAddsAWatchdogAlarm() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 18, 8, 0, 0, 0, MCT);
        List<Plan.Alarm> on = Plan.upcoming(EngineTest.city("muscat"), now);
        Plan.Alarm keep = on.stream().filter(a -> a.kind == Plan.KIND_KEEP).findFirst().orElseThrow(AssertionError::new);
        assertEquals(now.plusHours(Plan.KEEP_HOURS), keep.at);
        assertTrue(keep.code() < Plan.CODES);
        Settings off = Settings.parse("{\"keep_alive\":false}");
        assertTrue(Plan.upcoming(off, now).stream().noneMatch(a -> a.kind == Plan.KIND_KEEP));
    }

    @Test public void keepAlivePingReArmsAtMostOnceAMinute() {
        assertTrue(KeepAliveReceiver.due(60_000L, 0L));
        assertFalse(KeepAliveReceiver.due(59_999L, 0L));
        KeepAliveReceiver.resetForTests();
        Context ctx = RuntimeEnvironment.getApplication();
        new KeepAliveReceiver().onReceive(ctx, new Intent("other"));
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        assertEquals(0, Shadows.shadowOf(am).getScheduledAlarms().size());
        new KeepAliveReceiver().onReceive(ctx, new Intent(KeepAliveReceiver.ACTION));
        assertTrue(Shadows.shadowOf(am).getScheduledAlarms().size() > 0);
    }
}
