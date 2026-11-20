package com.midroid.mi5gtv.matches;

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
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Mi5G 2.4 (M6): a small corner card over any app. Either a pinned live score that refreshes from ESPN's public
 * match summary, or a kick-off reminder banner that goes away by itself. Needs the "display over other apps" grant,
 * which the Mi5G phone app gives over ADB when it opens Mi5G Score. Idea from talkies (MIT); own code.
 */
public class CornerService extends Service {
    static final String PIN = "pin", UNPIN = "unpin", BANNER = "banner";
    static final long REFRESH_MS = 45_000L, BANNER_MS = 25_000L, AFTER_FULL_TIME_MS = 180_000L;
    static final String PINNED_KEY = "pinned_event";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View pinView, bannerView;
    private String slug, event, league;
    private long finishedAt;

    static boolean canShow(Context c) { return android.provider.Settings.canDrawOverlays(c); }

    static boolean pin(Context c, String slug, String event, String league, String line) {
        if (!canShow(c) || slug == null || event == null || !slug.matches("[a-z0-9.]+") || !event.matches("\\d+")) return false;
        c.getSharedPreferences(Reminders.PREFS, 0).edit().putString(PINNED_KEY, event).apply();
        return start(c, new Intent(c, CornerService.class).setAction(PIN).putExtra("slug", slug).putExtra("event", event)
                .putExtra("league", league).putExtra("line", line));
    }

    static void unpin(Context c) {
        c.getSharedPreferences(Reminders.PREFS, 0).edit().remove(PINNED_KEY).apply();
        start(c, new Intent(c, CornerService.class).setAction(UNPIN));
    }

    static String pinned(Context c) { return c.getSharedPreferences(Reminders.PREFS, 0).getString(PINNED_KEY, ""); }

    static boolean banner(Context c, String text) {
        return canShow(c) && start(c, new Intent(c, CornerService.class).setAction(BANNER).putExtra("text", text));
    }

    private static boolean start(Context c, Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The card itself: a gold-edged dark teal pill with the main line and a small line under it. */
    static LinearLayout card(Context c, String line, String sub) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Screens.dp(c, 16), Screens.dp(c, 10), Screens.dp(c, 16), Screens.dp(c, 10));
        box.setBackground(Screens.round(c, 0xE607201F, 0xFFD4AF37));
        TextView main = Screens.text(c, line, 18, 0xFFFFFFFF, true);
        main.setTag("line");
        box.addView(main);
        if (sub != null && !sub.isEmpty()) {
            TextView s = Screens.text(c, sub, 12, 0xCCD4AF37, false);
            s.setTag("sub");
            box.addView(s);
        }
        return box;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        foreground();
        wm = getSystemService(WindowManager.class);
        String action = intent == null ? null : intent.getAction();
        if (PIN.equals(action)) {
            slug = intent.getStringExtra("slug");
            event = intent.getStringExtra("event");
            league = intent.getStringExtra("league");
            finishedAt = 0;
            showPin("⚽ " + nz(intent.getStringExtra("line")));
            handler.removeCallbacks(refresh);
            handler.post(refresh);
        } else if (UNPIN.equals(action)) {
            dropPin();
        } else if (BANNER.equals(action)) {
            showBanner(nz(intent.getStringExtra("text")));
        }
        stopIfIdle();
        return START_NOT_STICKY;
    }

    private void foreground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("corner", getString(R.string.score_corner_channel), NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "corner").setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.score_corner_channel)).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(9, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(9, n);
    }

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            final String s = slug, e = event;
            if (s == null || e == null) return;
            new Thread(() -> {
                String json = Espn.get(Espn.SITE + s + "/summary?event=" + e);
                final Football.Live live = json == null ? null : Football.fromSummary(json);
                handler.post(() -> {
                    if (pinView == null || !e.equals(event)) return;
                    if (live != null) {
                        ((TextView) pinView.findViewWithTag("line")).setText("⚽ " + live.line());
                        if ("post".equals(live.state) && finishedAt == 0) finishedAt = System.currentTimeMillis();
                    }
                    if (finishedAt > 0 && System.currentTimeMillis() - finishedAt >= AFTER_FULL_TIME_MS) {
                        getSharedPreferences(Reminders.PREFS, 0).edit().remove(PINNED_KEY).apply();
                        dropPin();
                        stopIfIdle();
                        return;
                    }
                    handler.postDelayed(refresh, finishedAt > 0 ? AFTER_FULL_TIME_MS : REFRESH_MS);
                });
            }).start();
        }
    };

    private WindowManager.LayoutParams params(int gravity, int yDp) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.x = Screens.dp(this, 32);
        lp.y = Screens.dp(this, yDp);
        return lp;
    }

    private void showPin(String line) {
        if (pinView != null) {
            ((TextView) pinView.findViewWithTag("line")).setText(line);
            return;
        }
        pinView = card(this, line, league);
        try { wm.addView(pinView, params(Gravity.BOTTOM | Gravity.LEFT, 28)); } catch (Exception e) { pinView = null; }
    }

    private void dropPin() {
        handler.removeCallbacks(refresh);
        slug = event = null;
        if (pinView != null) { try { wm.removeView(pinView); } catch (Exception ignored) { } pinView = null; }
    }

    private final Runnable hideBanner = () -> {
        if (bannerView != null) { try { wm.removeView(bannerView); } catch (Exception ignored) { } bannerView = null; }
        stopIfIdle();
    };

    private void showBanner(String text) {
        handler.removeCallbacks(hideBanner);
        if (bannerView != null) { try { wm.removeView(bannerView); } catch (Exception ignored) { } }
        bannerView = card(this, text, getString(R.string.app_name));
        try { wm.addView(bannerView, params(Gravity.TOP | Gravity.LEFT, 28)); } catch (Exception e) { bannerView = null; }
        handler.postDelayed(hideBanner, BANNER_MS);
    }

    private void stopIfIdle() {
        if (pinView == null && bannerView == null) stopSelf();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        dropPin();
        if (bannerView != null) { try { wm.removeView(bannerView); } catch (Exception ignored) { } bannerView = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
