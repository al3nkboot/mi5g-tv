package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;

/**
 * 2.4: Keep Alive for Xiaomi sets (PatchWall, Mi Box S) that take Home back after standby. On boot, a package
 * update, the screen coming on and Home resuming, the launcher checks once (short debounce, never a loop) that it
 * is still the default Home and that its Home-key helper is on, and puts the helper back through Revival. Setting
 * the default Home itself needs adb: when it was lost, the Mi5G settings row says so and the phone's
 * "Re-apply home screen" fixes it. Idea from tvOS-Launcher (Apache-2.0, techgenius1); own code.
 */
final class KeepAlive {
    static final long DEBOUNCE_MS = 5_000L;
    static final String LOST_KEY = "mi5g_keep_alive_home_lost";

    private static long lastRun;
    private static volatile boolean registered;

    private KeepAlive() {}

    /** A check is due when none ran yet, the last one is DEBOUNCE_MS old, or the clock went back (reboot). */
    static boolean due(long now, long last) {
        return last == 0 || now < last || now - last >= DEBOUNCE_MS;
    }

    /** One check for one event; does nothing while the switch is off or a check just ran. */
    static void onEvent(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (!LauncherSwitches.keepAlive(app)) return;
        synchronized (KeepAlive.class) {
            long now = SystemClock.elapsedRealtime();
            if (!due(now, lastRun)) return;
            lastRun = now;
        }
        Revival.restore(app);
        boolean home;
        try {
            home = BootReceiver.isDefaultHome(app);
        } catch (RuntimeException e) {
            return;
        }
        LauncherSwitches.prefs(app).edit().putBoolean(LOST_KEY, !home).apply();
    }

    /** Home was taken by another launcher at the last check (Keep Alive on). */
    static boolean homeLost(Context ctx) {
        return LauncherSwitches.keepAlive(ctx) && LauncherSwitches.prefs(ctx).getBoolean(LOST_KEY, false);
    }

    private static final BroadcastReceiver SCREEN_ON = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { onEvent(context); }
    };

    /** Screen-on (standby wake) can only be heard by a registered receiver: once per process, application context. */
    static void register(Context ctx) {
        if (registered) return;
        registered = true;
        try {
            Context app = ctx.getApplicationContext();
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_DREAMING_STOPPED);
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(SCREEN_ON, f, Context.RECEIVER_EXPORTED);
            else app.registerReceiver(SCREEN_ON, f);
        } catch (RuntimeException e) {
            registered = false;
        }
    }
}
