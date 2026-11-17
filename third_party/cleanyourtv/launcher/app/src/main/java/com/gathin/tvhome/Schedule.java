package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 2.4 M4 (backlog 34, CleanYourTV 3.5 idea, own code): a weekly sleep and wake schedule, one optional time of each
 * per weekday (Monday first), set on the phone's arrange page. At a sleep time the TV shows a one-minute notice with
 * Cancel focused, then sleeps through the Home-key helper (as the sleep timer does). Waking is best effort: many
 * TVs keep their panel off in standby whatever an app asks.
 */
final class Schedule {
    static final String KEY = "mi5g_week_schedule";
    static final String ON = "mi5g_week_schedule_on";

    final int[] sleep = new int[7], wake = new int[7];   // minute of day, or -1

    Schedule() { java.util.Arrays.fill(sleep, -1); java.util.Arrays.fill(wake, -1); }

    static Schedule parse(String json) {
        Schedule s = new Schedule();
        try {
            JSONObject o = new JSONObject(json);
            JSONArray sl = o.optJSONArray("sleep"), wk = o.optJSONArray("wake");
            for (int i = 0; i < 7; i++) {
                if (sl != null) s.sleep[i] = Reminders.minuteOf(sl.optString(i, ""));
                if (wk != null) s.wake[i] = Reminders.minuteOf(wk.optString(i, ""));
            }
        } catch (Exception ignored) { }
        return s;
    }

    String toJson() {
        JSONArray sl = new JSONArray(), wk = new JSONArray();
        for (int i = 0; i < 7; i++) { sl.put(hhmm(sleep[i])); wk.put(hhmm(wake[i])); }
        try { return new JSONObject().put("sleep", sl).put("wake", wk).toString(); } catch (Exception e) { return "{}"; }
    }

    static String hhmm(int m) { return m < 0 ? "" : String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60); }

    boolean empty() {
        for (int i = 0; i < 7; i++) if (sleep[i] >= 0 || wake[i] >= 0) return false;
        return true;
    }

    static Schedule load(Context ctx) { return parse(LauncherSwitches.prefs(ctx).getString(KEY, "{}")); }
    static boolean on(Context ctx) { return LauncherSwitches.prefs(ctx).getBoolean(ON, false); }

    static void save(Context ctx, Schedule s, boolean on) {
        LauncherSwitches.prefs(ctx).edit().putString(KEY, s.toJson()).putBoolean(ON, on).apply();
        arm(ctx);
    }

    static void setOn(Context ctx, boolean on) { LauncherSwitches.prefs(ctx).edit().putBoolean(ON, on).apply(); arm(ctx); }

    /** The next sleep (true) or wake (false) after now as {time, isSleep}; null when the week is empty. */
    Object[] next(LocalDateTime now) {
        Object[] best = null;
        for (int d = 0; d <= 7; d++) {
            java.time.LocalDate day = now.toLocalDate().plusDays(d);
            int i = day.getDayOfWeek().getValue() - 1;
            for (int k = 0; k < 2; k++) {
                int m = k == 0 ? sleep[i] : wake[i];
                if (m < 0) continue;
                LocalDateTime at = day.atTime(LocalTime.of(m / 60, m % 60));
                if (at.isAfter(now) && (best == null || at.isBefore((LocalDateTime) best[0]))) best = new Object[]{at, k == 0};
            }
            if (best != null) break;
        }
        return best;
    }

    /** Arms one alarm for the next sleep or wake; none while the schedule is off. */
    static void arm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 42, new Intent(ctx, ReminderReceiver.class).setAction(ReminderReceiver.SCHEDULE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!on(ctx)) return;
        Object[] n = load(ctx).next(LocalDateTime.now());
        if (n == null) return;
        long at = ((LocalDateTime) n[0]).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        Reminders.setAlarm(am, at, pi);
    }
}
