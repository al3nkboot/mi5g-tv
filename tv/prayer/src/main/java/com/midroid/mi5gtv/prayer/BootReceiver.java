package com.midroid.mi5gtv.prayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Boot, time or zone change, an update, or the phone's RELOAD after a settings push: re-arm. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) { Alarms.arm(ctx); }
}
