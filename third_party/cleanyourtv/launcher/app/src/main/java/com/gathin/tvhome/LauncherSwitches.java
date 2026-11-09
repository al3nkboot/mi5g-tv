package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Mi5G 2.2 opt-in launcher switches (off by default). Stored in launcher_settings so the TV
 * Settings screen can toggle them; config.xml / theme JSON supply the default when unset.
 * Ideas from CleanYourTV (MIT, Gathin): home_ignore_channel_keys, settings_key_clear_task.
 */
final class LauncherSwitches {
    static final String IGNORE_CHANNEL = "mi5g_home_ignore_channel_keys";
    static final String CLEAR_TASK = "mi5g_settings_key_clear_task";

    private LauncherSwitches() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    /** Channel Up/Down on Home do nothing (CleanYourTV home_ignore_channel_keys). */
    static boolean homeIgnoreChannelKeys(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.contains(IGNORE_CHANNEL)) return p.getBoolean(IGNORE_CHANNEL, false);
        return ctx.getResources().getBoolean(R.bool.home_ignore_channel_keys);
    }

    static void setHomeIgnoreChannelKeys(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(IGNORE_CHANNEL, on).apply();
    }

    /** Settings gear clears the Settings task so Back returns Home (CleanYourTV settings_key_clear_task). */
    static boolean settingsKeyClearTask(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.contains(CLEAR_TASK)) return p.getBoolean(CLEAR_TASK, false);
        return ctx.getResources().getBoolean(R.bool.settings_key_clear_task);
    }

    static void setSettingsKeyClearTask(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(CLEAR_TASK, on).apply();
    }
}
