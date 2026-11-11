package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Mi5G 2.3: a device switched on over HDMI-CEC (One Touch Play: a console put in its dock, a box woken) gets the
 * TV's picture, as the stock launcher does it. The firmware notices the device; who switches is up to the
 * launcher. Amlogic's (droidlogic) input service says so with a broadcast that the stock launcher answers and
 * nobody did with ours as Home. Other firmwares have not been seen to send anything an app can hear. Only while the
 * owner turned it on (LauncherSwitches.cecSwitch, off by default): not everyone wants a console waking to take
 * the TV.
 *
 * Ported from Aerial TV's CecSwitch.kt (v1.89, commit 3a09e7d, issue #1020), MIT, Copyright (c) 2026 pengchujin.
 * Not tested on a TV with this launcher.
 */
final class CecSwitch {
    /** droidlogic's DroidLogicTvUtils.ACTION_OTP_INPUT_SOURCE_CHANGED. */
    static final String OTP_ACTION = "droidlogic.tv.action.OTP_INPUT_SOURCE_CHANGED";

    private static volatile boolean registered;

    private CecSwitch() {}

    private static final BroadcastReceiver RECEIVER = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!LauncherSwitches.cecSwitch(context)) return;
            final PendingResult pending = goAsync();
            final Bundle extras = intent.getExtras();
            new Thread(() -> {
                try { onOneTouchPlay(context.getApplicationContext(), extras); } finally { pending.finish(); }
            }).start();
        }
    };

    /** Once per process, with the application context, from Home and from the bound key helper. */
    static void register(Context context) {
        if (registered) return;
        registered = true;
        try {
            Context app = context.getApplicationContext();
            IntentFilter filter = new IntentFilter(OTP_ACTION);
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(RECEIVER, filter, Context.RECEIVER_EXPORTED);
            else app.registerReceiver(RECEIVER, filter);
        } catch (RuntimeException e) {
            registered = false;
        }
    }

    private static void onOneTouchPlay(Context context, Bundle extras) {
        TvInputManager tv = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        if (tv == null) return;
        List<String> ids = new ArrayList<>();
        try {
            for (TvInputInfo info : tv.getTvInputList()) if (info.getParentId() == null) ids.add(info.getId());
        } catch (RuntimeException e) {
            return;
        }
        String id = pick(ids, texts(extras), child -> {
            try {
                TvInputInfo info = tv.getTvInputInfo(child);
                return info == null ? null : info.getParentId();
            } catch (RuntimeException e) {
                return null;
            }
        });
        if (id != null) InputLauncher.open(context, id);
    }

    /**
     * Which of ids the broadcast names: an input's id among its text extras (Android's TvInputInfo.EXTRA_INPUT_ID
     * first), or a CEC device's input under one (parentOf). Null when none matches.
     */
    static String pick(List<String> ids, List<String> texts, Function<String, String> parentOf) {
        for (String t : texts) {
            if (t == null) continue;
            if (ids.contains(t)) return t;
            String parent = parentOf == null ? null : parentOf.apply(t);
            if (parent != null && ids.contains(parent)) return parent;
        }
        return null;
    }

    /** The broadcast's text extras, Android's input id first. */
    @SuppressWarnings("deprecation")
    static List<String> texts(Bundle extras) {
        List<String> out = new ArrayList<>();
        if (extras == null) return out;
        String id = extras.getString(TvInputInfo.EXTRA_INPUT_ID);
        if (id != null) out.add(id);
        for (String key : extras.keySet()) {
            Object v;
            try { v = extras.get(key); } catch (RuntimeException e) { continue; }
            if (v instanceof String && !out.contains(v)) out.add((String) v);
        }
        return out;
    }
}
