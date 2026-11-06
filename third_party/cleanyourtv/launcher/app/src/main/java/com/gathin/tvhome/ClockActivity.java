package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import java.util.Random;

/**
 * Mi5G 2.0: the clock Back opens on Home. Burn-in safe: dark, dim text, and the whole clock drifts to
 * a new spot every minute. Any key returns to Home.
 */
public final class ClockActivity extends Activity {
    static final String BACK_CLOCK = "mi5g_back_clock", CLOCK_24H = "mi5g_clock_24h";
    private static final long SHIFT_MS = 60_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private LinearLayout clock;
    private FrameLayout root;
    private final Runnable shift = new Runnable() {
        @Override public void run() { ClockDream.applyQuiet(root,QuietHours.quietNow(ClockActivity.this));place(); handler.postDelayed(this, SHIFT_MS); }
    };

    static boolean backOpensClock(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getBoolean(BACK_CLOCK, true);
    }

    static boolean use24h(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getBoolean(CLOCK_24H, android.text.format.DateFormat.is24HourFormat(ctx));
    }

    /** Max drift so a clock of this size stays fully on screen. Pure, for tests. */
    static int[] offset(int parentW, int parentH, int w, int h, float fx, float fy) {
        int maxX = Math.max(0, parentW - w), maxY = Math.max(0, parentH - h);
        return new int[]{(int) (maxX * (0.1f + 0.8f * fx)), (int) (maxY * (0.1f + 0.8f * fy))};
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        MinuteClock.watch(root);
        root.setBackgroundColor(0xFF000000);
        clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean h24 = use24h(this);
        Typeface cairo = getResources().getFont(R.font.cairo);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        if (h24) time.setFormat12Hour("HH:mm"); else time.setFormat24Hour("h:mm");
        time.setTextSize(120f);
        time.setTextColor(0xFFB8C7C4);
        time.setTypeface(cairo, Typeface.BOLD);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        date.setTextSize(28f);
        date.setTextColor(0xFFC9A646);
        date.setTypeface(cairo);
        clock.addView(time);
        clock.addView(date);
        ClockDream.addMessage(this,clock);
        clock.setAlpha(0.85f);
        ClockDream.applyQuiet(root,QuietHours.quietNow(this));
        root.addView(clock, new FrameLayout.LayoutParams(-2, -2));
        setContentView(root);
        root.post(this::place);
    }

    private void place() {
        View p = (View) clock.getParent();
        int[] o = offset(p.getWidth(), p.getHeight(), clock.getWidth(), clock.getHeight(), random.nextFloat(), random.nextFloat());
        clock.animate().x(o[0]).y(o[1]).setDuration(1500).start();
    }

    @Override protected void onResume() { super.onResume(); handler.postDelayed(shift, SHIFT_MS); }

    @Override protected void onPause() { handler.removeCallbacks(shift); super.onPause(); finish(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }
}
