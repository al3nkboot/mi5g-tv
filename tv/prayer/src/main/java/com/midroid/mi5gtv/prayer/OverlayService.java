package com.midroid.mi5gtv.prayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import java.time.Duration;
import java.time.ZonedDateTime;

/** Optional floating widget over every app: next prayer and a countdown; corner, size and opacity from settings. */
public class OverlayService extends Service {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView view;
    private Settings settings;

    static void start(Context ctx) {
        if (!android.provider.Settings.canDrawOverlays(ctx)) return;
        Intent i = new Intent(ctx, OverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        } catch (Exception ignored) {
        }
    }

    static void stop(Context ctx) { ctx.stopService(new Intent(ctx, OverlayService.class)); }

    /** Text the widget shows. Pure, for tests. */
    static String label(Settings s, ZonedDateTime now) {
        Engine.Next n = Engine.next(s, now);
        return Engine.NAMES_AR[n.prayer] + "  " + Engine.countdown(Duration.between(now, n.at).getSeconds());
    }

    static int gravity(String corner) {
        switch (corner) {
            case "top_start": return Gravity.TOP | Gravity.START;
            case "bottom_start": return Gravity.BOTTOM | Gravity.START;
            case "bottom_end": return Gravity.BOTTOM | Gravity.END;
            default: return Gravity.TOP | Gravity.END;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("overlay", getString(R.string.overlay_name), NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "overlay").setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.overlay_name)).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(8, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(8, n);
        settings = Settings.load(this);
        if (view == null) {
            view = (TextView) Banner.make(this, "");
            float scale = settings.overlaySize / 100f;
            view.setTextSize(16f * scale);
            view.setAlpha(settings.overlayOpacity / 100f);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = gravity(settings.overlayCorner);
            int m = (int) (24 * getResources().getDisplayMetrics().density);
            lp.x = m;
            lp.y = m;
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(view, lp);
            } catch (Exception e) {
                stopSelf();
                return START_NOT_STICKY;
            }
            tick.run();
        }
        return START_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (view == null) return;
            view.setText(label(settings, ZonedDateTime.now()));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (view != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(view); } catch (Exception ignored) { }
            view = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
