package com.midroid.mi5gtv.prayer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/** A teal and gold banner over whatever app is on screen (needs the overlay permission the phone grants). */
final class Banner {
    private Banner() {}

    static View make(Context ctx, CharSequence text) {
        float dp = ctx.getResources().getDisplayMetrics().density;
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(20f);
        t.setTypeface(ctx.getResources().getFont(R.font.cairo), android.graphics.Typeface.BOLD);
        t.setPadding((int) (28 * dp), (int) (12 * dp), (int) (28 * dp), (int) (12 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF20B3D3A);
        bg.setCornerRadius(28 * dp);
        bg.setStroke((int) (2 * dp), 0xFFD4AF37);
        t.setBackground(bg);
        return t;
    }

    static void show(Context ctx, CharSequence text, int seconds) {
        Context app = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!Settings.canDrawOverlays(app)) {
                Toast.makeText(app, text, Toast.LENGTH_LONG).show();
                return;
            }
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            View v = make(app, text);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = (int) (32 * app.getResources().getDisplayMetrics().density);
            try {
                wm.addView(v, lp);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { wm.removeView(v); } catch (Exception ignored) { }
                }, seconds * 1000L);
            } catch (Exception e) {
                Toast.makeText(app, text, Toast.LENGTH_LONG).show();
            }
        });
    }
}
