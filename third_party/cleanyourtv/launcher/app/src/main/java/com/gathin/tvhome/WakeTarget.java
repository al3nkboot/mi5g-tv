package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;

/** Mi5G 2.0: what to open when the TV wakes: "" (Home), "app:&lt;pkg&gt;" or "input:&lt;tv input id&gt;". */
final class WakeTarget {
    static final String KEY = "mi5g_wake_target";

    private WakeTarget() {}

    static String get(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, "");
    }

    static void set(Context ctx, String target) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, target).apply();
    }

    static void onScreenOn(Context ctx) {
        String t = get(ctx);
        if (t.startsWith("app:")) {
            RemoteKeys.launch(ctx, t.substring(4));
        } else if (t.startsWith("input:")) {
            InputLauncher.open(ctx, t.substring(6));
        }
    }
}
