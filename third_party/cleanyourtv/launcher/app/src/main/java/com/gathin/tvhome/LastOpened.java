package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateUtils;

import java.util.Locale;

/**
 * When an app was last opened from Home, for the cinematic shelf's "Last opened" line: the launcher notes the
 * time whenever it opens an app. No permission and nothing read from other apps, so an app started some other
 * way (a remote's own Netflix button) shows the last time Home opened it.
 */
final class LastOpened {
    private static final String PREFS = "last_opened";

    private LastOpened() {
    }

    /** Notes that Home just opened this app. */
    static void record(Context ctx, String pkg) {
        prefs(ctx).edit().putLong(pkg, System.currentTimeMillis()).apply();
    }

    /** Milliseconds since the epoch, or 0 when Home never opened the app. */
    static long get(Context ctx, String pkg) {
        return pkg == null ? 0L : prefs(ctx).getLong(pkg, 0L);
    }

    /**
     * "5 minutes ago" within the hour, then the day and the time: "today, 11:40 am", "yesterday, 9:40 pm",
     * "3 days ago, 9:40 pm", "Oct 12, 9:40 pm". The parts are Android's own wording in the TV's language.
     */
    static String describe(Context ctx, long when) {
        long now = System.currentTimeMillis();
        // Under a minute Android says "0 minutes ago"; one minute reads better.
        when = Math.min(when, now - DateUtils.MINUTE_IN_MILLIS);
        if (now - when < DateUtils.HOUR_IN_MILLIS) {
            return DateUtils.getRelativeTimeSpanString(when, now, DateUtils.MINUTE_IN_MILLIS).toString();
        }
        String day = DateUtils.getRelativeTimeSpanString(when, now, DateUtils.DAY_IN_MILLIS).toString();
        // It follows "Last opened" mid-sentence: "yesterday", not "Yesterday". Month names keep their capital.
        if (Locale.getDefault().getLanguage().equals("en") && (day.startsWith("Yesterday") || day.startsWith("Today"))) {
            day = Character.toLowerCase(day.charAt(0)) + day.substring(1);
        }
        return day + ", " + DateUtils.formatDateTime(ctx, when, DateUtils.FORMAT_SHOW_TIME);
    }

    /** 2.4 M4: the packages Home opened most recently, newest first, at most n. */
    static java.util.List<String> recent(Context ctx, int n) {
        java.util.List<java.util.Map.Entry<String, ?>> all = new java.util.ArrayList<>(prefs(ctx).getAll().entrySet());
        java.util.List<String> out = new java.util.ArrayList<>();
        all.sort((a, b) -> Long.compare(asLong(b.getValue()), asLong(a.getValue())));
        for (java.util.Map.Entry<String, ?> e : all) {
            if (out.size() >= n) break;
            if (!(e.getValue() instanceof Long) || e.getKey().equals(ctx.getPackageName())) continue;
            out.add(e.getKey());
        }
        return out;
    }

    private static long asLong(Object o) { return o instanceof Long ? (Long) o : 0L; }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
