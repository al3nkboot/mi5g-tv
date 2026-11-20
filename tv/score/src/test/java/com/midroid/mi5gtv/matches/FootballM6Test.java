package com.midroid.mi5gtv.matches;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Intent;
import android.widget.TextView;
import org.robolectric.RuntimeEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Mi5G 2.4 (M6): kick-off times, prayer clashes, tournaments window, reminders, pinned score card. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FootballM6Test {
    static final TimeZone RIYADH = TimeZone.getTimeZone("Asia/Riyadh");
    // 2026-11-18 17:00 UTC = 20:00 Riyadh.
    static final long KICKOFF = Football.kickoffMs("2026-11-18T17:00Z");
    static final List<String[]> TIMES = Arrays.asList(
            new String[]{"الفجر", "04:58"}, new String[]{"الشروق", "06:16"}, new String[]{"الظهر", "11:42"},
            new String[]{"العصر", "14:50"}, new String[]{"المغرب", "17:09"}, new String[]{"العشاء", "20:39"});

    Application app;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        TimeZone.setDefault(RIYADH);
    }

    @Test public void kickoffParsesBothEspnForms() {
        assertEquals(1795021200000L, KICKOFF);
        assertEquals(KICKOFF, Football.kickoffMs("2026-11-18T17:00:00Z"));
        assertEquals(-1, Football.kickoffMs(""));
        assertEquals(-1, Football.kickoffMs("soon"));
    }

    @Test public void remindTenMinutesBeforeOrRightAwayOrNever() {
        assertEquals(KICKOFF - 600_000L, Football.remindAt(KICKOFF, KICKOFF - 3_600_000L));
        assertEquals(KICKOFF - 299_000L, Football.remindAt(KICKOFF, KICKOFF - 300_000L));
        assertEquals(-1, Football.remindAt(KICKOFF, KICKOFF + 1));
        assertEquals(-1, Football.remindAt(-1, 0));
    }

    @Test public void clashesFindIshaInsideTheMatchAndSkipSunrise() {
        List<String[]> hits = Football.clashes(KICKOFF, TIMES, RIYADH);
        assertEquals(1, hits.size());
        assertEquals("العشاء", hits.get(0)[0]);
        assertEquals("20:39", hits.get(0)[1]);
        // Maghrib 17:09 just before a 17:15 kick-off counts (10-minute lead); sunrise never does.
        long k2 = Football.kickoffMs("2026-11-18T14:15Z");
        assertEquals("المغرب", Football.clashes(k2, TIMES, RIYADH).get(0)[0]);
        long morning = Football.kickoffMs("2026-11-18T03:10Z");
        assertTrue(Football.clashes(morning, TIMES, RIYADH).isEmpty());
        assertTrue(Football.clashes(KICKOFF, new ArrayList<String[]>(), RIYADH).isEmpty());
        assertTrue(Football.clashes(KICKOFF, Arrays.<String[]>asList(new String[]{"x", "25:99"}), RIYADH).isEmpty());
    }

    @Test public void clashNoteIsReadable() {
        String note = MainActivity.clashNote(app, KICKOFF, TIMES);
        assertTrue(note, note.contains("العشاء") && note.contains("20:39"));
        assertNull(MainActivity.clashNote(app, KICKOFF, new ArrayList<String[]>()));
    }

    @Test public void tournamentWindowAndSlugs() {
        assertTrue(Football.inTournamentWindow(KICKOFF, KICKOFF - 86_400_000L * 10));
        assertTrue(Football.inTournamentWindow(KICKOFF, KICKOFF + 3_600_000L * 30));
        assertFalse(Football.inTournamentWindow(KICKOFF, KICKOFF - 86_400_000L * 40));
        assertFalse(Football.inTournamentWindow(KICKOFF, KICKOFF + 86_400_000L * 3));
        for (String[] t : Football.TOURNAMENTS) assertTrue(t[2], t[2].matches("[a-z0-9.]+"));
    }

    @Test public void liveLineFromSummary() throws Exception {
        Football.Live l = Football.fromSummary(EspnTest.res("summary-live.json"));
        assertNotNull(l);
        assertEquals("in", l.state);
        assertEquals("الهلال 2 - 1 النصر  67'", l.line());
        assertNull(Football.fromSummary("{}"));
        assertEquals("A × B", new Football.Live("A", "B", "0", "0", "pre", "").line());
        assertEquals("A 1 - 1 B  ✔", new Football.Live("A", "B", "1", "1", "post", "90'").line());
    }

    @Test public void remindersRoundTripAndPrune() {
        List<Reminders.Item> items = Arrays.asList(
                new Reminders.Item("1", "ksa.1", "الهلال", "النصر", "دوري روشن السعودي", KICKOFF),
                new Reminders.Item("2", "eng.1", "a", "b", "c", KICKOFF - 86_400_000L),
                new Reminders.Item("bad", "eng.1", "a", "b", "c", KICKOFF));
        List<Reminders.Item> back = Reminders.decode(Reminders.encode(items));
        assertEquals(2, back.size());
        assertEquals("الهلال × النصر", back.get(0).title());
        assertEquals(1, Reminders.prune(back, KICKOFF - 3_600_000L).size());
        assertTrue(Reminders.decode("not json").isEmpty());
    }

    @Test public void addArmsAnAlarmAndRemoveCancelsIt() {
        long kickoff = System.currentTimeMillis() + 3_600_000L;
        assertTrue(Reminders.add(app, new Reminders.Item("77", "ksa.1", "الهلال", "النصر", "دوري روشن السعودي", kickoff)));
        assertTrue(Reminders.has(app, "77"));
        AlarmManager am = app.getSystemService(AlarmManager.class);
        assertEquals(kickoff - 600_000L, shadowOf(am).getNextScheduledAlarm().triggerAtTime);
        assertFalse(Reminders.add(app, new Reminders.Item("78", "ksa.1", "a", "b", "c", System.currentTimeMillis() - 1)));
        Reminders.remove(app, "77");
        assertFalse(Reminders.has(app, "77"));
        assertNull(shadowOf(am).getNextScheduledAlarm());
    }

    @Test public void alarmWithoutOverlayGrantPostsANotification() {
        long kickoff = System.currentTimeMillis() + 300_000L;
        Reminders.add(app, new Reminders.Item("90", "ksa.1", "الهلال", "النصر", "دوري روشن السعودي", kickoff));
        new ReminderReceiver().onReceive(app, new Intent(ReminderReceiver.ACTION).putExtra("id", "90"));
        assertFalse(Reminders.has(app, "90"));
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        assertEquals(1, shadowOf(nm).getAllNotifications().size());
        String text = shadowOf(nm).getAllNotifications().get(0).extras.getString("android.text");
        assertTrue(text, text.contains("الهلال × النصر"));
    }

    @Test public void bootReArmsWhatIsStillAhead() {
        long kickoff = System.currentTimeMillis() + 7_200_000L;
        Reminders.add(app, new Reminders.Item("55", "ksa.1", "a", "b", "c", kickoff));
        AlarmManager am = app.getSystemService(AlarmManager.class);
        am.cancel(shadowOf(am).getNextScheduledAlarm().operation);
        new ReminderReceiver().onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertEquals(kickoff - 600_000L, shadowOf(am).getNextScheduledAlarm().triggerAtTime);
    }

    @Test public void pinNeedsTheOverlayGrantAndValidIds() {
        assertFalse(CornerService.pin(app, "ksa.1", "401", "x", "a × b"));
        assertEquals("", CornerService.pinned(app));
        shadowOf(app).grantPermissions(android.Manifest.permission.SYSTEM_ALERT_WINDOW);
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true);
        assertFalse(CornerService.pin(app, "ksa.1; rm", "401", "x", "a × b"));
        assertTrue(CornerService.pin(app, "ksa.1", "401", "دوري روشن السعودي", "الهلال × النصر"));
        assertEquals("401", CornerService.pinned(app));
        Intent started = shadowOf(app).getNextStartedService();
        assertEquals(CornerService.PIN, started.getAction());
        CornerService.unpin(app);
        assertEquals("", CornerService.pinned(app));
        org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false);
    }

    @Test public void cardShowsTheLine() {
        TextView line = CornerService.card(app, "⚽ الهلال 2 - 1 النصر  67'", "دوري روشن السعودي").findViewWithTag("line");
        assertEquals("⚽ الهلال 2 - 1 النصر  67'", line.getText().toString());
    }
}
