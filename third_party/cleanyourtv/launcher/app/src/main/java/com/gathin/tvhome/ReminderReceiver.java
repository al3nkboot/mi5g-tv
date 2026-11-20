package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import java.time.LocalDateTime;
import java.util.List;

/** 2.4 M4: fires reminders (after any adhan) and the weekly sleep/wake schedule, then arms the next one. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String REMIND = "com.gathin.tvhome.REMIND", SCHEDULE = "com.gathin.tvhome.SCHEDULE", LATER = "com.gathin.tvhome.REMIND_LATER";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (REMIND.equals(action)) remind(ctx);
        else if (SCHEDULE.equals(action)) schedule(ctx);
        else if (LATER.equals(action)) NoticeActivity.show(ctx, NoticeActivity.REMINDER, intent.getStringExtra("text"));
    }

    private static void remind(Context ctx) {
        LocalDateTime now = LocalDateTime.now();
        int minute = now.getHour() * 60 + now.getMinute();
        List<Reminders.Item> due = new java.util.ArrayList<>();
        for (Reminders.Item i : Reminders.load(ctx)) {
            if (i.on && (i.days & Reminders.bit(now.getDayOfWeek())) != 0 && Math.abs(i.minute - minute) <= 2) due.add(i);
        }
        Reminders.schedule(ctx);   // the next one, whatever happens to this one
        if (due.isEmpty()) return;
        int at = Reminders.afterAdhan(minute, Reminders.prayerMinutes(ctx));
        if (at > minute) {
            // On an adhan: show it once the adhan window has passed.
            long when = System.currentTimeMillis() + (at - minute) * 60_000L;
            android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            Intent later = new Intent(ctx, ReminderReceiver.class).setAction(LATER).putExtra("text", due.get(0).text);
            if (am != null) Reminders.setAlarm(am, when, android.app.PendingIntent.getBroadcast(ctx, 43, later,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
            return;
        }
        NoticeActivity.show(ctx, NoticeActivity.REMINDER, due.get(0).text);
    }

    private static void schedule(Context ctx) {
        Object[] n = Schedule.load(ctx).next(LocalDateTime.now().minusMinutes(2));
        Schedule.arm(ctx);
        if (n == null || !Schedule.on(ctx)) return;
        boolean sleep = (Boolean) n[1];
        if (sleep) {
            NoticeActivity.show(ctx, NoticeActivity.SLEEP, "");
        } else {
            // Best effort: some TVs wake the panel for a wake-up lock, then Home opens.
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                @SuppressWarnings("deprecation")
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "mi5g:wake");
                wl.acquire(10_000L);
            }
            try { ctx.startActivity(new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (RuntimeException ignored) { }
        }
    }
}
