package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Mi5G 2.0.2: quiet hours. Between start and end (minutes of the day, end may be after midnight)
 * the Mi5G screensavers dim. Default 23:00 to 07:00; "off" turns it off. Mi5G's own code.
 */
final class QuietHours {
    static final String KEY = "mi5g_quiet_hours";
    static final String DEFAULT = "23:00-07:00";
    static final String OFF = "off";
    /** The choices the TV setting cycles through. */
    static final String[] CHOICES = {DEFAULT, "22:00-06:00", "00:00-06:00", "21:00-07:00", OFF};
    /** How bright the screensaver stays while quiet (1 = normal). */
    static final float DIM_ALPHA = 0.35f;

    private QuietHours() {}

    /** [start, end] in minutes of the day, or null for "off" or anything that does not parse. */
    static int[] parse(String v) {
        if (v == null || OFF.equals(v)) return null;
        String[] p = v.trim().split("-");
        if (p.length != 2) return null;
        int s = minutes(p[0]), e = minutes(p[1]);
        return s < 0 || e < 0 || s == e ? null : new int[]{s, e};
    }

    private static int minutes(String hhmm) {
        String[] p = hhmm.trim().split(":");
        if (p.length != 2) return -1;
        try {
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            return h < 0 || h > 23 || m < 0 || m > 59 ? -1 : h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Whether minuteOfDay falls in [start, end), wrapping past midnight when end < start. */
    static boolean isQuiet(String setting, int minuteOfDay) {
        if(minuteOfDay<0 || minuteOfDay>=1440)return false;
        int[] r = parse(setting);
        if (r == null) return false;
        int s = r[0], e = r[1];
        return s < e ? minuteOfDay >= s && minuteOfDay < e : minuteOfDay >= s || minuteOfDay < e;
    }

    static String get(Context ctx) {
        String v = prefs(ctx).getString(KEY, DEFAULT);
        return OFF.equals(v) || parse(v) != null ? v : DEFAULT;
    }

    static void set(Context ctx, String v) { prefs(ctx).edit().putString(KEY, v).apply(); }

    /** The next choice after the current one (the TV row cycles). */
    static String next(String current) {
        for (int i = 0; i < CHOICES.length; i++) if (CHOICES[i].equals(current)) return CHOICES[(i + 1) % CHOICES.length];
        return CHOICES[0];
    }

    static boolean quietNow(Context ctx) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return isQuiet(get(ctx), c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE));
    }

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE); }
}
