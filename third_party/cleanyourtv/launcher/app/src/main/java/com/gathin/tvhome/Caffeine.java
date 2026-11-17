package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * 2.4: Keep screen awake (caffeine mode): Off, 1 h, 2 h, 4 h or until turned off. CaffeineService holds a screen
 * wake lock for the time left and Home keeps its window on. "Until turned off" survives a launcher restart but not
 * a reboot (the boot count is saved with it). Idea from tvOS-Launcher (Apache-2.0, techgenius1); own code.
 * Needs no WRITE_SECURE_SETTINGS.
 */
final class Caffeine {
    static final String CHOICE_KEY = "mi5g_caffeine_choice";
    static final String UNTIL_KEY = "mi5g_caffeine_until";   // elapsedRealtime, FOREVER, or 0 = off
    static final String BOOT_KEY = "mi5g_caffeine_boot";
    static final long HOUR = 3_600_000L;
    static final long FOREVER = Long.MAX_VALUE;
    /** The choices in the order a press cycles them. */
    static final long[] CHOICES = {0, HOUR, 2 * HOUR, 4 * HOUR, FOREVER};

    private Caffeine() {}

    static long next(long choice) {
        for (int i = 0; i < CHOICES.length; i++) if (CHOICES[i] == choice) return CHOICES[(i + 1) % CHOICES.length];
        return CHOICES[1];
    }

    /** When a choice made at now ends: 0 for off, FOREVER for until turned off. */
    static long until(long now, long choice) {
        if (choice <= 0) return 0;
        if (choice == FOREVER) return FOREVER;
        return now + choice;
    }

    /** Time left, 0 when off, ended, or set before the last reboot. */
    static long left(long until, long now, int savedBoot, int boot) {
        if (until == 0 || savedBoot != boot) return 0;
        if (until == FOREVER) return FOREVER;
        return Math.max(0, until - now);
    }

    static int bootCount(Context ctx) {
        try {
            return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static long choice(Context ctx) {
        return left(ctx) > 0 ? LauncherSwitches.prefs(ctx).getLong(CHOICE_KEY, 0) : 0;
    }

    static long left(Context ctx) {
        SharedPreferences p = LauncherSwitches.prefs(ctx);
        return left(p.getLong(UNTIL_KEY, 0), SystemClock.elapsedRealtime(), p.getInt(BOOT_KEY, -2), bootCount(ctx));
    }

    static boolean active(Context ctx) { return left(ctx) > 0; }

    /** Saves the choice and starts or stops the service. */
    static void set(Context ctx, long choice) {
        LauncherSwitches.prefs(ctx).edit().putLong(CHOICE_KEY, choice)
                .putLong(UNTIL_KEY, until(SystemClock.elapsedRealtime(), choice))
                .putInt(BOOT_KEY, bootCount(ctx)).apply();
        sync(ctx);
    }

    /** Service on while time is left, off otherwise (Home calls this when it starts and resumes). */
    static void sync(Context ctx) {
        Context app = ctx.getApplicationContext();
        Intent i = new Intent(app, CaffeineService.class);
        try {
            if (active(app)) app.startForegroundService(i);
            else app.stopService(i);
        } catch (RuntimeException ignored) {
            // a start refused from the background: the next Home resume tries again
        }
    }

    static int labelRes(long choice) {
        if (choice == HOUR) return R.string.mi5g_caffeine_1h;
        if (choice == 2 * HOUR) return R.string.mi5g_caffeine_2h;
        if (choice == 4 * HOUR) return R.string.mi5g_caffeine_4h;
        if (choice == FOREVER) return R.string.mi5g_caffeine_forever;
        return R.string.mi5g_off;
    }
}
