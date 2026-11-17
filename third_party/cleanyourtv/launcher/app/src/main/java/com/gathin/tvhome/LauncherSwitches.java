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

    // Mi5G 2.3, after Aerial TV 1.89 (MIT, Copyright (c) 2026 pengchujin): both off by default.
    static final String HOME_LOCK = "mi5g_home_lock";
    static final String CEC_SWITCH = "mi5g_cec_switch";

    /** Home lock: holding OK on a tile or in the library does nothing until unlocked in Mi5G settings. */
    static boolean homeLocked(Context ctx) {
        return prefs(ctx).getBoolean(HOME_LOCK, false);
    }

    static void setHomeLocked(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(HOME_LOCK, on).apply();
    }

    /** Switch to the input an HDMI-CEC device was turned on from (Amlogic's broadcast only). */
    static boolean cecSwitch(Context ctx) {
        return prefs(ctx).getBoolean(CEC_SWITCH, false);
    }

    static void setCecSwitch(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(CEC_SWITCH, on).apply();
    }

    /**
     * 2.4: while Home is locked, an edit (dock, move, remove, hide, arrange from the phone) is refused with a short
     * note. True when it was refused. Opening apps is never refused. Idea from Aerial TV (MIT, pengchujin).
     */
    static boolean refuseIfLocked(Context ctx) {
        if (!homeLocked(ctx)) return false;
        android.widget.Toast.makeText(ctx, R.string.mi5g_home_locked_hint, android.widget.Toast.LENGTH_SHORT).show();
        return true;
    }

    // 2.4: Keep Alive, idea from tvOS-Launcher (Apache-2.0, techgenius1); own code.
    static final String KEEP_ALIVE = "mi5g_keep_alive";

    /**
     * Xiaomi sets (PatchWall, Mi Box S) take Home back after standby, and Philips sets (TP Vision) after a system
     * update (2.4 M2, Teevy idea 23): on by default there only.
     */
    static boolean keepAliveDefault(String manufacturer) {
        if (manufacturer == null) return false;
        String m = manufacturer.trim().toLowerCase(java.util.Locale.ROOT);
        return m.equals("xiaomi") || m.equals("philips") || m.equals("tpv") || m.startsWith("tp vision");
    }

    static boolean keepAlive(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (p.contains(KEEP_ALIVE)) return p.getBoolean(KEEP_ALIVE, false);
        return keepAliveDefault(android.os.Build.MANUFACTURER);
    }

    static void setKeepAlive(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEEP_ALIVE, on).apply();
    }

    // 2.4 M2: home screen polish. All own code; ideas noted per switch.
    static final String MINIMAL = "mi5g_minimal_home";
    static final String ARABIC_NAMES = "mi5g_arabic_names";
    static final String SOUNDS = "mi5g_nav_sounds";
    static final String DOCK_ROWS = "mi5g_dock_rows";

    /** Clean mode (backlog 6): only the clock, the dock and the apps; greeting, race, stats, cards and sky stay off. */
    static boolean minimal(Context ctx) { return prefs(ctx).getBoolean(MINIMAL, false); }

    static void setMinimal(Context ctx, boolean on) { prefs(ctx).edit().putBoolean(MINIMAL, on).apply(); }

    /** Arabic app names on the tiles (v1.7 #3): on by default, used only while the TV speaks Arabic. */
    static boolean arabicNames(Context ctx) { return prefs(ctx).getBoolean(ARABIC_NAMES, true); }

    static void setArabicNames(Context ctx, boolean on) { prefs(ctx).edit().putBoolean(ARABIC_NAMES, on).apply(); }

    /** Our own soft navigation and click sounds (backlog 28): off by default. */
    static boolean sounds(Context ctx) { return prefs(ctx).getBoolean(SOUNDS, false); }

    static void setSounds(Context ctx, boolean on) { prefs(ctx).edit().putBoolean(SOUNDS, on).apply(); }

    /** Dock rows (backlog 24): 1 (as before), 2 or 3. */
    static int dockRows(Context ctx) { return clampDockRows(prefs(ctx).getInt(DOCK_ROWS, 1)); }

    static int clampDockRows(int rows) { return Math.max(1, Math.min(3, rows)); }

    static int nextDockRows(int rows) { return rows >= 3 ? 1 : clampDockRows(rows) + 1; }

    static void setDockRows(Context ctx, int rows) { prefs(ctx).edit().putInt(DOCK_ROWS, clampDockRows(rows)).apply(); }

    /**
     * Apps that always stay on Home and in All apps (backlog 21 and 22): the TV's Settings and the Play Store. Hiding
     * them is refused, so there is always a way back to the settings and to new apps.
     */
    static final java.util.Set<String> ALWAYS_VISIBLE = java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList(
            "com.android.vending", "com.android.tv.settings", "com.google.android.tv.settings", "com.android.settings")));

    static boolean alwaysVisible(String pkg) { return pkg != null && ALWAYS_VISIBLE.contains(pkg); }
}
