package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** Mi5G 2.0.2: two short lines typed on the phone (ArrangeServer) and shown on the Mi5G screensavers. */
final class ScreensaverMessage {
    static final String KEY1 = "mi5g_saver_line1", KEY2 = "mi5g_saver_line2";
    static final int MAX = 40;

    private ScreensaverMessage() {}

    /** Trimmed, without control or bidi override characters, at most MAX characters. */
    static String clean(String raw) {
        String s = HomeLayout.cleanName(raw);
        return s.length() > MAX ? s.substring(0, MAX) : s;
    }

    static String[] get(Context ctx) {
        SharedPreferences p = prefs(ctx);
        return new String[]{p.getString(KEY1, ""), p.getString(KEY2, "")};
    }

    static void set(Context ctx, String line1, String line2) {
        prefs(ctx).edit().putString(KEY1, clean(line1)).putString(KEY2, clean(line2)).apply();
    }

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE); }
}
