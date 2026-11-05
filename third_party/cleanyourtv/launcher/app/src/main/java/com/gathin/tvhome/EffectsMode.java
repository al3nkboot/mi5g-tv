package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Choreographer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mi5G 2.0.2: Auto Lite effects. On the first few D-pad moves on Home the launcher times the frames it
 * draws; a TV that is too slow for the full effects switches to Lite (no springs, glow or tile
 * animations) by itself. The setting is Auto (default), Full or Lite. Mi5G's own code.
 */
final class EffectsMode {
    static final String KEY = "mi5g_effects", AUTO_LITE_KEY = "mi5g_effects_auto_lite", MEASURED_KEY = "mi5g_effects_measured";
    static final String AUTO = "auto", FULL = "full", LITE = "lite";
    /** D-pad moves timed before deciding. */
    static final int MOVES = 6;
    /** A frame slower than this missed 60 Hz badly (two or more vsyncs). */
    static final long SLOW_FRAME_NS = 34_000_000L;

    private EffectsMode() {}

    /**
     * Slow when the median frame is over 28 ms, or more than a third of the frames took over 34 ms.
     * Needs at least 12 frames to say anything.
     */
    static boolean slow(List<Long> frameNs) {
        if (frameNs == null || frameNs.size() < 12) return false;
        List<Long> s = new ArrayList<>(frameNs);
        Collections.sort(s);
        long median = s.get(s.size() / 2);
        int janky = 0;
        for (long f : s) if (f > SLOW_FRAME_NS) janky++;
        return median > 28_000_000L || janky * 3 > s.size();
    }

    static String get(Context ctx) {
        String v = prefs(ctx).getString(KEY, AUTO);
        return FULL.equals(v) || LITE.equals(v) ? v : AUTO;
    }

    static void set(Context ctx, String v) { prefs(ctx).edit().putString(KEY, v).apply(); }

    static String next(String v) { return AUTO.equals(v) ? FULL : FULL.equals(v) ? LITE : AUTO; }

    /** Whether Home should run in Lite mode now. */
    static boolean lite(Context ctx) {
        String v = get(ctx);
        return LITE.equals(v) || (AUTO.equals(v) && prefs(ctx).getBoolean(AUTO_LITE_KEY, false));
    }

    static boolean measured(Context ctx) { return prefs(ctx).getBoolean(MEASURED_KEY, false); }

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE); }

    /** Times frames for a short while after each D-pad move, then decides once. */
    static final class Probe implements Choreographer.FrameCallback {
        interface Result { void slow(); }

        private final Context ctx;
        private final Result result;
        private final List<Long> frames = new ArrayList<>();
        private int moves;
        private long last, until;
        private boolean done;

        Probe(Context ctx, Result result) {
            this.ctx = ctx.getApplicationContext();
            this.result = result;
            done = !AUTO.equals(get(ctx)) || measured(ctx);
        }

        /** Called on each D-pad key down on Home. */
        void onMove() {
            if (done) return;
            moves++;
            boolean idle = until == 0;
            until = System.nanoTime() + 400_000_000L;
            last = 0;
            if (idle) Choreographer.getInstance().postFrameCallback(this);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (done) return;
            if (last != 0) frames.add(frameTimeNanos - last);
            last = frameTimeNanos;
            if (frameTimeNanos < until) {
                Choreographer.getInstance().postFrameCallback(this);
                return;
            }
            until = 0;
            if (moves >= MOVES) finish();
        }

        private void finish() {
            done = true;
            boolean slow = slow(frames);
            prefs(ctx).edit().putBoolean(MEASURED_KEY, true).putBoolean(AUTO_LITE_KEY, slow).apply();
            if (slow && result != null) result.slow();
        }
    }
}
