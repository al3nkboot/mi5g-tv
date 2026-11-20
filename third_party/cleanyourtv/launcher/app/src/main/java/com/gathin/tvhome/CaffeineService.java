package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * 2.4: holds the caffeine wake lock (Caffeine). A foreground service, because target SDK 34 stops a background one
 * within a minute once another app is in front. Stops itself when the time is up.
 */
public final class CaffeineService extends Service {
    private static final String CHANNEL = "mi5g_caffeine";
    private static final int NOTE_ID = 2404;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeUp = this::end;
    private PowerManager.WakeLock lock;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long left = Caffeine.left(this);
        try {
            startInForeground();
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (left <= 0) { end(); return START_NOT_STICKY; }
        release();
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null) {
            //noinspection deprecation: SCREEN_BRIGHT_WAKE_LOCK is the one a TV still honours
            lock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "mi5g:caffeine");
            lock.setReferenceCounted(false);
            if (left == Caffeine.FOREVER) lock.acquire(); else lock.acquire(left);
        }
        handler.removeCallbacks(timeUp);
        if (left != Caffeine.FOREVER) handler.postDelayed(timeUp, left);
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.mi5g_caffeine), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(getString(R.string.mi5g_caffeine_note))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTE_ID, n);
    }

    private void end() {
        Caffeine.set(this, 0);
        stopSelf();
    }

    private void release() {
        if (lock != null && lock.isHeld()) lock.release();
        lock = null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        release();
        super.onDestroy();
    }
}
