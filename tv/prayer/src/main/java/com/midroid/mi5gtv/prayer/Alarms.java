package com.midroid.mi5gtv.prayer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.time.ZonedDateTime;
import java.util.List;

/** Arms the plan with exact alarms; re-armed after every alarm, boot, time change and settings push. */
final class Alarms {
    private Alarms() {}

    static void arm(Context ctx) {
        Settings s = Settings.load(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (int code = 0; code < 30; code++) am.cancel(pending(ctx, code, null));
        List<Plan.Alarm> plan = Plan.upcoming(s, ZonedDateTime.now());
        for (Plan.Alarm a : plan) {
            PendingIntent pi = pending(ctx, a.code(), a);
            long t = a.at.toInstant().toEpochMilli();
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            }
        }
        if (s.overlay) OverlayService.start(ctx); else OverlayService.stop(ctx);
    }

    private static PendingIntent pending(Context ctx, int code, Plan.Alarm a) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction("com.midroid.mi5gtv.prayer.ALARM_" + code);
        if (a != null) i.putExtra("kind", a.kind).putExtra("prayer", a.prayer);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
