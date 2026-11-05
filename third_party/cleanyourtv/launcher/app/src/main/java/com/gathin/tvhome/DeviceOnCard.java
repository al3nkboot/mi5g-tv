package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mi5G 2.0.2: "a device turns on". While Home is on screen and an HDMI source becomes connected (the
 * TV input framework's state callback), or an HDMI-CEC device appears as an input of its own (its name,
 * e.g. "PlayStation 5", comes from the TV), a card offers to switch to it: «تشغّل PlayStation 5 · انتقل
 * إلى HDMI 2». OK switches, Back closes it, it goes by itself after a while. Off in Mi5G settings.
 * TVs without TvInputManager or without state callbacks simply never show it. Mi5G's own code.
 */
final class DeviceOnCard {
    static final String KEY = "mi5g_device_on_card", CEC_ONLY = "mi5g_cec_only";
    static boolean cecOnly(Context ctx) { return prefs(ctx).getBoolean(CEC_ONLY, false); }
    static final long SHOW_MS = 15_000L, AGAIN_MS = 90_000L;
    /** TvInputManager.INPUT_STATE_CONNECTED. */
    static final int CONNECTED = 0;

    /** Which input changes deserve a card. Plain logic, tested on the JVM. */
    static final class Tracker {
        private final Map<String, Integer> states = new HashMap<>();
        private final Map<String, Long> shownAt = new HashMap<>();

        /** Records a state seen without offering anything (the inputs as they are when Home starts). */
        void seed(String id, int state) { states.put(id, state); }

        /**
         * An input's state changed. True when it just became connected after being seen not connected,
         * is not hidden, and was not offered in the last AGAIN_MS.
         */
        boolean onState(String id, int state, long now, Set<String> hidden) {
            Integer before = states.put(id, state);
            if (state != CONNECTED || before == null || before == CONNECTED) return false;
            return offer(id, now, hidden);
        }

        /** A CEC device appeared as an input of its own (it was switched on). */
        boolean onDeviceAdded(String id, String parentId, long now, Set<String> hidden) {
            states.put(id, CONNECTED);
            if (hidden.contains(id) || (parentId != null && hidden.contains(parentId))) return false;
            return offer(id, now, hidden);
        }

        private boolean offer(String id, long now, Set<String> hidden) {
            if (hidden.contains(id)) return false;
            Long last = shownAt.get(id);
            if (last != null && now - last < AGAIN_MS && now >= last) return false;
            shownAt.put(id, now);
            return true;
        }
    }

    static boolean enabled(Context ctx) { return prefs(ctx).getBoolean(KEY, true); }

    static void setEnabled(Context ctx, boolean on) { prefs(ctx).edit().putBoolean(KEY, on).apply(); }

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE); }

    /** «تشغّل PlayStation 5 · انتقل إلى HDMI 2»; an unnamed device is «جهاز». */
    static String text(Context ctx, String device, String port) {
        String d = device == null || device.trim().isEmpty() ? ctx.getString(R.string.mi5g_device_on_unnamed) : HomeLayout.cleanName(device);
        return ctx.getString(R.string.mi5g_device_on_card, d, HomeLayout.cleanName(port));
    }

    final Tracker tracker = new Tracker();
    private final FrameLayout parent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View card;
    private View focusBefore;
    private final Runnable hide = this::dismiss;

    DeviceOnCard(FrameLayout parent) { this.parent = parent; }

    boolean isShowing() { return card != null; }

    /** Shows the card; onSwitch runs on OK. */
    View show(String device, String port, Runnable onSwitch) {
        dismiss();
        Context ctx = parent.getContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        Typeface cairo = ctx.getResources().getFont(R.font.cairo);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (28 * d), (int) (14 * d), (int) (28 * d), (int) (14 * d));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable normal = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF2124A45, 0xF20B1F1E});
        normal.setCornerRadius(22 * d);
        normal.setStroke((int) (1 * d), 0x55D4AF37);
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF14998E, 0xFF0B3B37});
        focused.setCornerRadius(22 * d);
        focused.setStroke((int) (2 * d), 0xFFD4AF37);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, normal);
        box.setBackground(bg);
        TextView title = new TextView(ctx);
        title.setText(text(ctx, device, port));
        title.setTypeface(cairo, Typeface.BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setTextDirection(View.TEXT_DIRECTION_RTL);
        box.addView(title);
        TextView hint = new TextView(ctx);
        hint.setText(R.string.mi5g_device_on_hint);
        hint.setTypeface(cairo);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hint.setTextColor(0xFFE8D9A8);
        hint.setGravity(Gravity.CENTER);
        hint.setTextDirection(View.TEXT_DIRECTION_RTL);
        box.addView(hint);
        box.setFocusable(true);
        box.setOnClickListener(v -> { dismiss(); onSwitch.run(); });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = (int) (40 * d);
        parent.addView(box, lp);
        card = box;
        focusBefore = parent.getRootView().findFocus();
        box.requestFocus();
        box.setAlpha(0f);
        box.animate().alpha(1f).setDuration(220).start();
        handler.postDelayed(hide, SHOW_MS);
        return box;
    }

    /** Back closes the card; arrows stay on it so OK cannot open a tile by mistake. */
    boolean dispatch(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) dismiss();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) dismiss();
            return true;
        }
        return false;
    }

    void dismiss() {
        handler.removeCallbacks(hide);
        if (card == null) return;
        parent.removeView(card);
        card = null;
        if (focusBefore != null && focusBefore.isAttachedToWindow()) focusBefore.requestFocus();
        focusBefore = null;
    }

    /** Ids of inputs hidden in Edit inputs, for the tracker. */
    static Set<String> hidden(Context ctx) { return new HashSet<>(HomeLayout.load(ctx).hiddenInputs); }
}
