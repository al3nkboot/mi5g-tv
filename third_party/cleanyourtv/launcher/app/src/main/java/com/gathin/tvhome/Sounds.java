package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * 2.4 M2 (backlog 28): optional soft navigation and click sounds, off by default. The two sounds are Mi5G's own
 * (generated tones, res/raw/mi5g_nav.wav and mi5g_click.wav). One small SoundPool per process, loaded on first use.
 */
final class Sounds {
    private static SoundPool pool;
    private static int nav, click;

    private Sounds() {}

    private static synchronized SoundPool pool(Context ctx) {
        if (pool == null) {
            Context app = ctx.getApplicationContext();
            pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            nav = pool.load(app, R.raw.mi5g_nav, 1);
            click = pool.load(app, R.raw.mi5g_click, 1);
        }
        return pool;
    }

    static void nav(Context ctx) { play(ctx, true); }

    static void click(Context ctx) { play(ctx, false); }

    private static void play(Context ctx, boolean isNav) {
        if (ctx == null || !LauncherSwitches.sounds(ctx)) return;
        try {
            SoundPool p = pool(ctx);
            p.play(isNav ? nav : click, 0.35f, 0.35f, 1, 0, 1f);
        } catch (RuntimeException ignored) { }
    }
}
