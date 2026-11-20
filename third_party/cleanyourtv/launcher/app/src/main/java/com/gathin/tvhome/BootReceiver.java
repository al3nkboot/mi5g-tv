package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

/**
 * Mi5G 2.0.1: after boot (and the Xiaomi/TCL "quick boot" after standby) put back our key services and, when
 * we are the default Home, bring Home to the front so the TV doesn't land on the vendor launcher.
 * Idea from Aerial TV (MIT, Copyright (c) 2026 pengchujin); own code.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(a) && !Intent.ACTION_BOOT_COMPLETED.equals(a) && !"android.intent.action.QUICKBOOT_POWERON".equals(a)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(a)) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                Revival.restore(app);
                KeepAlive.register(app);   // 2.4: hear standby wake from now on
                KeepAlive.onEvent(app);
                Reminders.schedule(app);   // 2.4 M4: alarms do not survive a reboot
                Schedule.arm(app);
                // 2.4 M2: after a system update (Philips) Mi5G comes to the front once even when the update gave
                // Home back to the stock launcher; the Home-key helper put back above keeps the Home button ours.
                boolean updated = KeepAlive.systemUpdated(app) && LauncherSwitches.keepAlive(app);
                if (isDefaultHome(app) || updated) {
                    app.startActivity(new Intent(app, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                }
            } catch (Exception ignored) {
                // background starts can be refused; the system opens Home itself then
            } finally {
                pending.finish();
            }
        }, "Mi5gBoot").start();
    }

    static boolean isDefaultHome(Context ctx) {
        ResolveInfo r = ctx.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        return r != null && r.activityInfo != null && ctx.getPackageName().equals(r.activityInfo.packageName);
    }
}
