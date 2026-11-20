package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 2.4 M4: a card over whatever is on the TV. REMINDER shows a task reminder (backlog 30) for two minutes or until OK;
 * SLEEP (backlog 34) counts a minute down with Cancel focused, then sleeps the TV through the Home-key helper.
 */
public final class NoticeActivity extends Activity {
    static final String REMINDER = "reminder", SLEEP = "sleep";
    static final long REMINDER_MS = 2 * 60_000L;
    static final int SLEEP_SECONDS = 60;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int left = SLEEP_SECONDS;
    private boolean sleepMode;

    static void show(Context ctx, String kind, String text) {
        try {
            ctx.startActivity(new Intent(ctx, NoticeActivity.class).putExtra("kind", kind).putExtra("text", text == null ? "" : text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (RuntimeException ignored) {
            // Started from the background where the TV forbids it: the Home line still shows the reminder.
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        Typeface cairo = getResources().getFont(R.font.cairo);
        sleepMode = SLEEP.equals(getIntent().getStringExtra("kind"));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF20B1F1E);
        bg.setCornerRadius(24 * dp);
        bg.setStroke((int) (2 * dp), 0xFFD4AF37);
        card.setBackground(bg);
        int pad = (int) (32 * dp);
        card.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText(sleepMode ? R.string.mi5g_schedule_sleep_title : R.string.mi5g_reminder_title);
        title.setTextColor(0xFFD4AF37);
        title.setTextSize(22f);
        title.setTypeface(cairo, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView body = new TextView(this);
        body.setId(android.R.id.message);
        body.setTextColor(0xFFFFFFFF);
        body.setTextSize(30f);
        body.setTypeface(cairo, Typeface.BOLD);
        body.setGravity(Gravity.CENTER);
        body.setTextDirection(android.view.View.TEXT_DIRECTION_FIRST_STRONG);
        body.setPadding(0, (int) (12 * dp), 0, (int) (20 * dp));
        card.addView(body);
        Button ok = new Button(this);
        ok.setText(sleepMode ? R.string.mi5g_schedule_cancel : android.R.string.ok);
        ok.setTypeface(cairo, Typeface.BOLD);
        ok.setAllCaps(false);
        ok.setOnClickListener(v -> finish());
        card.addView(ok, new LinearLayout.LayoutParams(-2, -2));
        root.addView(card, new FrameLayout.LayoutParams((int) (560 * dp), -2, Gravity.CENTER));
        setContentView(root);
        ok.requestFocus();
        if (sleepMode) {
            tickSleep.run();
        } else {
            body.setText(getIntent().getStringExtra("text"));
            main.postDelayed(this::finish, REMINDER_MS);
        }
    }

    private final Runnable tickSleep = new Runnable() {
        @Override public void run() {
            TextView body = findViewById(android.R.id.message);
            body.setText(getResources().getQuantityString(R.plurals.mi5g_seconds, left, left));
            if (left-- <= 0) {
                ControlCenterActivity.global(NoticeActivity.this, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                finish();
                return;
            }
            main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
