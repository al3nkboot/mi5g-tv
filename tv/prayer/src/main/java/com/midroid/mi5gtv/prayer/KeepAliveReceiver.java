package com.midroid.mi5gtv.prayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * 2.4 M5: Keep Alive. Mi5G Home sends KEEP_ALIVE (with FLAG_INCLUDE_STOPPED_PACKAGES) whenever it comes back
 * after boot or standby, so a TV that force-stopped this module (Xiaomi, TCL) gets its adhan alarms back.
 * Open to any sender on purpose: all it does is re-arm, at most once a minute.
 */
public class KeepAliveReceiver extends BroadcastReceiver {
    static final String ACTION = "com.midroid.mi5gtv.prayer.KEEP_ALIVE";
    private static long last = -60_000L;

    /** Pure, for tests: re-arm only when a minute has passed since the last time. */
    static boolean due(long nowElapsed, long lastElapsed) { return nowElapsed - lastElapsed >= 60_000L; }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        long now = SystemClock.elapsedRealtime();
        if (!due(now, last)) return;
        last = now;
        if (Settings.load(ctx).keepAlive) Alarms.arm(ctx);
    }

    static void resetForTests() { last = -60_000L; }
}
