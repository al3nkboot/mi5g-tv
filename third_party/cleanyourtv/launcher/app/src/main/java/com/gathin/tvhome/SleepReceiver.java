package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Mi5G 2.0 sleep timer end: puts the TV to sleep through the remote service's lock-screen action. */
public class SleepReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit().remove("mi5g_sleep_at").apply();
        ControlCenterActivity.global(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    }
}
