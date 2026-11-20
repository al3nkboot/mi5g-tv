package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 2.4 M4 (backlog 30, UrTV idea, own code): timed task reminders typed on the phone's arrange page. Each has a
 * time of day and the weekdays it repeats on; a reminder that falls on an adhan (from 2 minutes before a prayer
 * time to 20 minutes after it, as the Mi5G prayer module reports today's times) waits until that has passed.
 * At most {@link #MAX} reminders, text at most {@link #MAX_TEXT} characters.
 */
final class Reminders {
    static final String KEY = "mi5g_reminders";
    static final int MAX = 12, MAX_TEXT = ScreensaverMessage.MAX;
    static final int BEFORE_ADHAN = 2, AFTER_ADHAN = 20;
    /** Every day: bit (dayOfWeek - 1), Monday = bit 0. */
    static final int EVERY_DAY = 0x7F;

    static final class Item {
        final String text;
        final int minute;    // of the day, 0..1439
        final int days;      // weekday bits
        final boolean on;
        Item(String text, int minute, int days, boolean on) { this.text = text; this.minute = minute; this.days = days; this.on = on; }
        JSONObject json() throws Exception { return new JSONObject().put("text", text).put("minute", minute).put("days", days).put("on", on); }
    }

    private Reminders() {}

    static List<Item> load(Context ctx) { return parse(LauncherSwitches.prefs(ctx).getString(KEY, "[]")); }

    /** The valid reminders of a JSON array (bad entries dropped, at most MAX). */
    static List<Item> parse(String json) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length() && out.size() < MAX; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String text = ScreensaverMessage.clean(o.optString("text"));
                if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
                int minute = o.optInt("minute", -1), days = o.optInt("days", EVERY_DAY) & EVERY_DAY;
                if (text.isEmpty() || minute < 0 || minute >= 1440 || days == 0) continue;
                out.add(new Item(text, minute, days, o.optBoolean("on", true)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String toJson(List<Item> items) {
        JSONArray a = new JSONArray();
        try { for (Item i : items) a.put(i.json()); } catch (Exception ignored) { }
        return a.toString();
    }

    static void save(Context ctx, List<Item> items) {
        LauncherSwitches.prefs(ctx).edit().putString(KEY, toJson(items)).apply();
        schedule(ctx);
    }

    /** The minute a reminder at `minute` really shows, after any adhan window it falls in. */
    static int afterAdhan(int minute, List<Integer> prayers) {
        int m = minute;
        for (int pass = 0; pass < 6; pass++) {
            boolean moved = false;
            for (int p : prayers) {
                if (m >= p - BEFORE_ADHAN && m < p + AFTER_ADHAN) { m = p + AFTER_ADHAN; moved = true; }
            }
            if (!moved) break;
        }
        return m;
    }

    /** The next time any enabled reminder is due after `now`, with the reminder; null when none. */
    static Object[] next(List<Item> items, LocalDateTime now) {
        Object[] best = null;
        for (Item i : items) {
            if (!i.on) continue;
            for (int d = 0; d <= 7; d++) {
                LocalDateTime at = now.toLocalDate().plusDays(d).atTime(LocalTime.of(i.minute / 60, i.minute % 60));
                if (!at.isAfter(now)) continue;
                if ((i.days & bit(at.getDayOfWeek())) == 0) continue;
                if (best == null || at.isBefore((LocalDateTime) best[0])) best = new Object[]{at, i};
                break;
            }
        }
        return best;
    }

    static int bit(DayOfWeek d) { return 1 << (d.getValue() - 1); }

    /** Today's prayer times in minutes from the Mi5G prayer module, or empty without it. */
    static List<Integer> prayerMinutes(Context ctx) {
        List<Integer> out = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(CityScreen.PRAYER_TODAY, null, null, null, null)) {
            if (c == null) return out;
            int t = c.getColumnIndex("time");
            while (t >= 0 && c.moveToNext()) {
                int m = minuteOf(c.getString(t));
                if (m >= 0) out.add(m);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** "05:12", "17:40" or Arabic-Indic digits, to minutes of the day; -1 when it isn't a time. */
    static int minuteOf(String s) {
        if (s == null) return -1;
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch >= '\u0660' && ch <= '\u0669') b.append((char) ('0' + ch - '\u0660'));
            else if (ch >= '0' && ch <= '9' || ch == ':') b.append(ch);
        }
        String[] p = b.toString().split(":");
        if (p.length < 2 || p[0].isEmpty() || p[1].isEmpty()) return -1;
        try {
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            return h < 24 && m < 60 ? h * 60 + m : -1;
        } catch (NumberFormatException e) { return -1; }
    }

    /** The Home line for the next reminder today ("Next reminder 18:30: Take the medicine"), or "". */
    static String homeLine(Context ctx, LocalDateTime now) {
        Object[] n = next(load(ctx), now);
        if (n == null || !((LocalDateTime) n[0]).toLocalDate().equals(now.toLocalDate())) return "";
        Item i = (Item) n[1];
        return ctx.getString(R.string.mi5g_reminder_next, String.format(java.util.Locale.ROOT, "%02d:%02d", i.minute / 60, i.minute % 60), i.text);
    }

    /** Arms one alarm for the next reminder (ReminderReceiver re-arms after each one). */
    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 41, new Intent(ctx, ReminderReceiver.class).setAction(ReminderReceiver.REMIND),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        Object[] n = next(load(ctx), LocalDateTime.now());
        if (n == null) return;
        long at = ((LocalDateTime) n[0]).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        setAlarm(am, at, pi);
    }

    static void setAlarm(AlarmManager am, long at, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
