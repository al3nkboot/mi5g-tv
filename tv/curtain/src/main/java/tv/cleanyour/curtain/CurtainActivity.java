package tv.cleanyour.curtain;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

// The curtain's one screen (RFC-CURTAIN-1). Started by the phone's plan (am start, after the
// engine's restore point), it shows the Lantern while the phone runs Setup and takes its cues from
// CueReceiver through CueBus. It finishes itself: when the launcher (or anything else) covers it,
// when Back is pressed, six seconds after FINALE, or after four minutes without a cue, so a phone
// that went away never leaves the TV behind a curtain. It never stops, pauses or changes the setup.
public final class CurtainActivity extends Activity implements CueBus.Listener, CurtainView.Host, Choreographer.FrameCallback {
    private static final double DEAD_MAN_S = 240.0;

    private final CueQueue queue = new CueQueue();
    private CurtainView view;
    private long startNanos;
    private double lastCueAt;
    private boolean skip;
    private boolean running;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        boolean motion = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        view = new CurtainView(this, motion, this, getString(R.string.hint), getString(R.string.caption), getString(R.string.chip), getString(R.string.all_clean));
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(view);
        startNanos = System.nanoTime();
        lastCueAt = 0;
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
        CueBus.attach(this);
    }

    @Override public void onCue(Cue cue) {
        if (queue.offer(cue)) lastCueAt = seconds(System.nanoTime());
    }

    // 30 fps: every other vsync draws; the queue releases cues as their minimum times pass.
    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        Choreographer.getInstance().postFrameCallback(this);
        skip = !skip;
        if (skip) return;
        double now = seconds(frameTimeNanos);
        for (Cue cue = queue.poll(now); cue != null; cue = queue.poll(now)) view.apply(cue, (float) now, words(cue), done(cue));
        if (now - lastCueAt > DEAD_MAN_S) { finish(); return; }
        view.frame((float) now);
    }

    @Override public void finaleDone() {
        finish();
    }

    private double seconds(long nanos) {
        return (nanos - startNanos) / 1e9;
    }

    private static boolean done(Cue cue) {
        return cue.stage == Cue.Stage.RESTORE_SAVED || cue.stage == Cue.Stage.GROUP_DONE || cue.stage == Cue.Stage.INSTALLED;
    }

    // The line under the picture, in this app's own words.
    private String words(Cue cue) {
        String group = group(cue.group);
        switch (cue.stage) {
            case SCAN: return getString(R.string.stage_scan);
            case RESTORE_SAVED: return getString(R.string.stage_restore_saved);
            case OFF: return group == null ? getString(R.string.stage_off, cue.count, cue.total)
                : getString(R.string.stage_off_group, group, cue.groupCount, cue.groupTotal);
            case GROUP_DONE: return group == null ? getString(R.string.stage_group_done, cue.count)
                : getString(R.string.stage_group_done_group, group, cue.groupCount);
            case INSTALL: return getString(R.string.stage_install);
            case INSTALLED: return getString(R.string.stage_installed);
            default: return getString(R.string.stage_finale);
        }
    }

    private String group(Cue.Group g) {
        switch (g) {
            case ADVERTS: return getString(R.string.group_adverts);
            case TRACKING: return getString(R.string.group_tracking);
            case NOBODY_ASKED: return getString(R.string.group_nobody);
            case BRAND_EXTRAS: {
                String b = Build.BRAND == null ? "" : Build.BRAND.trim();
                if (b.isEmpty() || !b.matches("[A-Za-z0-9 .&-]{1,24}")) return getString(R.string.group_brand_unknown);
                return getString(R.string.group_brand, Character.toUpperCase(b.charAt(0)) + b.substring(1));
            }
            case SHOP_DEMO: return getString(R.string.group_shop);
            case OTHER: return getString(R.string.group_other);
            default: return null;
        }
    }

    // Back ends the curtain (the platform's default for an activity that is not a launcher's root;
    // the setup carries on); the remote's other keys do nothing here, and volume and the like stay
    // with the system.
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MENU:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // Covered by the launcher, the stock home screen or anything else: done for good.
    @Override protected void onStop() {
        super.onStop();
        finish();
    }

    @Override protected void onDestroy() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        CueBus.detach(this);
        super.onDestroy();
    }
}
