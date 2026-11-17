package com.midroid.mi5gtv.matches;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Kick-off reminder alarm (and boot/update re-arm): a corner banner over any app, or a notification without the overlay grant. */
public class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION = "com.midroid.mi5gtv.matches.KICKOFF";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (!ACTION.equals(action)) { Reminders.armAll(c); return; }
        String id = intent.getStringExtra("id");
        Reminders.Item item = id == null ? null : Reminders.take(c, id);
        if (item == null) return;
        String text = c.getString(R.string.score_kickoff_soon, item.title(), item.league);
        if (CornerService.banner(c, text)) return;
        notify(c, text);
    }

    static void notify(Context c, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("kickoff", c.getString(R.string.score_reminders_channel), NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, "kickoff").setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(c.getString(R.string.app_name)).setContentText(text).setContentIntent(open).setAutoCancel(true).build();
        try { nm.notify(text.hashCode(), n); } catch (SecurityException ignored) { }
    }
}
