package com.gathin.tvhome;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Mi5G 2.0 Dynamic-Island style pill at the top of Home: what is playing and in which app. OK on it
 * plays or pauses. Hidden when nothing plays or media access is not granted.
 */
final class MediaPill {
    private static final long POLL_MS = 4000;
    private final LinearLayout pill;
    private final ImageView icon;
    private final TextView text;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() { @Override public void run() { update(); handler.postDelayed(this, POLL_MS); } };

    MediaPill(FrameLayout root) {
        float dp = root.getResources().getDisplayMetrics().density;
        pill = new LinearLayout(root.getContext());
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2081413);
        bg.setCornerRadius(20 * dp);
        bg.setStroke((int) (1 * dp), 0x66D4AF37);
        pill.setBackground(bg);
        pill.setPadding((int) (14 * dp), (int) (6 * dp), (int) (16 * dp), (int) (6 * dp));
        pill.setFocusable(true);
        pill.setClickable(true);
        icon = new ImageView(root.getContext());
        icon.setColorFilter(0xFF2EC4B6);
        pill.addView(icon, new LinearLayout.LayoutParams((int) (18 * dp), (int) (18 * dp)));
        text = new TextView(root.getContext());
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(13f);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setMaxWidth((int) (360 * dp));
        text.setTypeface(root.getResources().getFont(R.font.cairo), Typeface.BOLD);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.setMarginStart((int) (8 * dp));
        pill.addView(text, tl);
        pill.setOnClickListener(v -> { MediaSessions.toggle(MediaSessions.current(v.getContext())); update(); });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (18 * dp);
        pill.setVisibility(View.GONE);
        root.addView(pill, lp);
    }

    /** Shown for a known title only. Package-visible for the screenshot test. */
    void show(String title, String app, boolean playing) {
        if (title == null || title.isEmpty()) { pill.setVisibility(View.GONE); return; }
        icon.setImageResource(playing ? R.drawable.ic_speaker : R.drawable.ic_play);
        text.setText(app == null || app.isEmpty() ? title : title + "  ·  " + app);
        pill.setVisibility(View.VISIBLE);
    }

    void update() {
        MediaSessions.Now n = MediaSessions.current(pill.getContext());
        if (n == null) show(null, null, false); else show(n.title, n.app, n.playing);
    }

    void start() { handler.removeCallbacks(poll); handler.post(poll); }

    void stop() { handler.removeCallbacks(poll); }
}
