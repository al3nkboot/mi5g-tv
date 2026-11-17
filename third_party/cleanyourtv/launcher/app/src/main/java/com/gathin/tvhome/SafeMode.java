package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 2.4 M2: crash safe mode and the 5-minute safety switch back to the stock home (v1.7 item 1). Own code.
 *
 * Every crash of the launcher process is written down (time only) before the process dies. Two crashes within
 * five minutes turn safe mode on: Home starts with the extras off (no cards, wallpaper video, sky, race, stats or
 * effects) until the owner turns it off in Mi5G settings. A third crash within the same five minutes means Home
 * still does not come up: while that lasts, Home hands over to the TV's own home screen instead of starting, and
 * after five quiet minutes Mi5G tries again (in safe mode). Opening Mi5G from the app list always opens it.
 */
final class SafeMode {
    static final String CRASHES = "mi5g_crash_times";
    static final String ACTIVE = "mi5g_safe_mode";
    static final long WINDOW_MS = 5 * 60_000L;
    static final int SAFE_AFTER = 2;
    static final int HAND_OVER_AFTER = 3;
    private static final int KEEP = 8;

    private static volatile boolean installed;

    private SafeMode() {}

    static SharedPreferences prefs(Context ctx) { return LauncherSwitches.prefs(ctx); }

    /** The crash times (ms) of raw that are inside the window before now. */
    static List<Long> recent(String raw, long now) {
        List<Long> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(",")) {
            try {
                long t = Long.parseLong(part.trim());
                if (t <= now && now - t < WINDOW_MS) out.add(t);
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /** raw with a crash at now added, older ones outside the window dropped, at most KEEP kept. */
    static String add(String raw, long now) {
        List<Long> times = recent(raw, now);
        times.add(now);
        while (times.size() > KEEP) times.remove(0);
        StringBuilder sb = new StringBuilder();
        for (Long t : times) { if (sb.length() > 0) sb.append(','); sb.append(t); }
        return sb.toString();
    }

    static int crashes(Context ctx, long now) { return recent(prefs(ctx).getString(CRASHES, ""), now).size(); }

    /** Writes the crash down synchronously (the process is about to die) and turns safe mode on after two. */
    static void record(Context ctx, long now) {
        SharedPreferences p = prefs(ctx);
        String next = add(p.getString(CRASHES, ""), now);
        SharedPreferences.Editor e = p.edit().putString(CRASHES, next);
        if (recent(next, now).size() >= SAFE_AFTER) e.putBoolean(ACTIVE, true);
        e.commit();
    }

    static boolean active(Context ctx) { return prefs(ctx).getBoolean(ACTIVE, false); }

    /** Off from Mi5G settings: the crash list is cleared too, so the next start is a normal one. */
    static void setActive(Context ctx, boolean on) {
        SharedPreferences.Editor e = prefs(ctx).edit().putBoolean(ACTIVE, on);
        if (!on) e.remove(CRASHES);
        e.apply();
    }

    /** Home did not come up three times inside five minutes: hand Home to the TV's own home screen for now. */
    static boolean handOver(Context ctx, long now) { return crashes(ctx, now) >= HAND_OVER_AFTER; }

    /** Only a Home press (CATEGORY_HOME) is handed over; opening Mi5G from an app list always opens it. */
    static boolean isHomeLaunch(Intent intent) { return intent != null && intent.hasCategory(Intent.CATEGORY_HOME); }

    /** Records every uncaught crash of this process, then lets the system handle it as before. Once per process. */
    static void install(Context ctx) {
        if (installed) return;
        installed = true;
        final Context app = ctx.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { record(app, System.currentTimeMillis()); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** The TV's own home screen (a system Home activity other than ours), or null when there is none. */
    static Intent stockHome(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> homes = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        ResolveInfo pick = null;
        for (ResolveInfo ri : homes) {
            if (ri.activityInfo == null || ctx.getPackageName().equals(ri.activityInfo.packageName)) continue;
            // Settings' fallback home only shows "no home" screens; prefer a real one.
            if ("com.android.settings".equals(ri.activityInfo.packageName) || "com.android.tv.settings".equals(ri.activityInfo.packageName)) continue;
            boolean system = ri.activityInfo.applicationInfo != null && (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (pick == null || system) pick = ri;
            if (system) break;
        }
        if (pick == null) return null;
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setClassName(pick.activityInfo.packageName, pick.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
}
