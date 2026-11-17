package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * 2.4 M7 (backlog 2): apps ordered by when they are used. Each time Home opens an app it counts that open in one
 * of four parts of the day; with the switch on, Home and All apps put the apps used most at this time of day
 * first (the dock stays where it is). Counts stay on the TV, older opens fade so habits can change, and nothing
 * is read from other apps.
 */
final class TimeOfDay {
    private TimeOfDay() {}

    static final String KEY = "mi5g_time_of_day_order";
    private static final String PREFS = "time_of_day";
    /** Morning 05–12, afternoon 12–17, evening 17–22, night 22–05. */
    static final int MORNING = 0, AFTERNOON = 1, EVENING = 2, NIGHT = 3;
    /** Every open adds 10 points; all of a slot's points shrink by a tenth on each open in that slot. */
    static final int STEP = 10;

    static int slot(int hour) {
        if (hour >= 5 && hour < 12) return MORNING;
        if (hour >= 12 && hour < 17) return AFTERNOON;
        if (hour >= 17 && hour < 22) return EVENING;
        return NIGHT;
    }

    static int slotNow() { return slot(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)); }

    static boolean on(Context ctx) { return LauncherSwitches.prefs(ctx).getBoolean(KEY, false); }

    static void setOn(Context ctx, boolean on) { LauncherSwitches.prefs(ctx).edit().putBoolean(KEY, on).apply(); }

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static String key(int slot, String pkg) { return slot + ":" + pkg; }

    /** Notes one open now (called with LastOpened.record). */
    static void record(Context ctx, String pkg) { record(ctx, pkg, slotNow()); }

    static void record(Context ctx, String pkg, int slot) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(ctx.getPackageName())) return;
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor e = p.edit();
        String prefix = slot + ":";
        for (Map.Entry<String, ?> kv : p.getAll().entrySet()) {
            if (!kv.getKey().startsWith(prefix) || !(kv.getValue() instanceof Integer)) continue;
            int faded = (Integer) kv.getValue() * 9 / 10;
            if (faded <= 0) e.remove(kv.getKey()); else e.putInt(kv.getKey(), faded);
        }
        int now = p.getInt(key(slot, pkg), 0);
        e.putInt(key(slot, pkg), now * 9 / 10 + STEP);
        e.apply();
    }

    static int score(Context ctx, String pkg, int slot) { return pkg == null ? 0 : prefs(ctx).getInt(key(slot, pkg), 0); }

    /** Higher score first; ties keep the given order. */
    static int compare(Context ctx, int slot, String a, String b) {
        return Integer.compare(score(ctx, b, slot), score(ctx, a, slot));
    }

    /** Sorts package names for {@code slot}, stable, most used first. */
    static void sort(Context ctx, List<String> pkgs, int slot) {
        pkgs.sort((a, b) -> compare(ctx, slot, a, b));
    }

    static void clear(Context ctx) { prefs(ctx).edit().clear().apply(); }

    static int labelRes(int slot) {
        switch (slot) {
            case MORNING: return R.string.mi5g_tod_morning;
            case AFTERNOON: return R.string.mi5g_tod_afternoon;
            case EVENING: return R.string.mi5g_tod_evening;
            default: return R.string.mi5g_tod_night;
        }
    }
}
