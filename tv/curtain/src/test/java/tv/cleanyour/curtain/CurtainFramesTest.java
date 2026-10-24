package tv.cleanyour.curtain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

// The Lantern's key frames, drawn on the JVM (Robolectric native graphics) at the moments of the
// approved mockup's frames (app-design/mockups/tv-curtain/frames l1 to l5), for a side by side look.
// The cues are applied straight to the view at the mockup's script times (SCRIPT_L), as the queue
// would release them. Each frame is written at half size (build/curtain-frames, or src/test/frames
// with -PcurtainFrames); the test checks
// that the frames are not empty and that the stages differ where they must.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CurtainFramesTest {
    private static final String T = "0123456789abcdef";
    private boolean finished;

    private CurtainView view(boolean motion) {
        CurtainView v = new CurtainView(RuntimeEnvironment.getApplication(), motion, () -> finished = true,
            "Keep the TV on. Your phone shows the details.", "Clean Your TV", "Restore point", "All clean.");
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, 1920, 1080);
        return v;
    }

    private static Cue cue(String stage, int count, int total, String group, int gc, int gt) {
        return Cue.parse(T, stage, group, count, total, gc, gt);
    }

    // The mockup's run: ten switch-offs in three groups (4, 3, 3), then the install and the finale.
    private void play(CurtainView v, float until) {
        Object[][] script = {
            {0.0f, cue("SCAN", 0, 10, "NONE", 0, 0), "Saving a restore point...", false},
            {10.9f, cue("RESTORE_SAVED", 0, 0, "NONE", 0, 0), "Restore point saved", true},
            {12.0f, cue("OFF", 1, 10, "ADVERTS", 1, 4), "Adverts: 1 of 4...", false},
            {13.2f, cue("OFF", 2, 10, "ADVERTS", 2, 4), "Adverts: 2 of 4...", false},
            {14.4f, cue("OFF", 3, 10, "ADVERTS", 3, 4), "Adverts: 3 of 4...", false},
            {15.6f, cue("OFF", 4, 10, "ADVERTS", 4, 4), "Adverts: 4 of 4...", false},
            {16.5f, cue("GROUP_DONE", 4, 10, "ADVERTS", 4, 4), "Adverts switched off (4)", true},
            {17.3f, cue("OFF", 5, 10, "TRACKING", 1, 3), "Tracking: 1 of 3...", false},
            {18.5f, cue("OFF", 6, 10, "TRACKING", 2, 3), "Tracking: 2 of 3...", false},
            {19.7f, cue("OFF", 7, 10, "TRACKING", 3, 3), "Tracking: 3 of 3...", false},
            {20.6f, cue("GROUP_DONE", 7, 10, "TRACKING", 3, 3), "Tracking switched off (3)", true},
            {21.4f, cue("OFF", 8, 10, "NOBODY_ASKED", 1, 3), "Apps nobody asked for: 1 of 3...", false},
            {22.6f, cue("OFF", 9, 10, "NOBODY_ASKED", 2, 3), "Apps nobody asked for: 2 of 3...", false},
            {23.8f, cue("OFF", 10, 10, "NOBODY_ASKED", 3, 3), "Apps nobody asked for: 3 of 3...", false},
            {24.3f, cue("GROUP_DONE", 10, 10, "NOBODY_ASKED", 3, 3), "Apps nobody asked for switched off (3)", true},
            {24.8f, cue("INSTALL", 0, 0, "NONE", 0, 0), "Installing the new home screen", false},
            {27.6f, cue("INSTALLED", 0, 0, "NONE", 0, 0), "New home screen installed", true},
            {28.4f, cue("FINALE", 0, 0, "NONE", 0, 0), "Switching Home over", false},
        };
        int next = 0;
        // 30 fps, as on the TV.
        for (float t = 0f; t <= until + 1e-4f; t += 1f / 30f) {
            while (next < script.length && (float) script[next][0] <= t) {
                Object[] s = script[next++];
                v.apply((Cue) s[1], t, (String) s[2], (Boolean) s[3]);
            }
            v.frame(t);
        }
    }

    private Bitmap shot(CurtainView v, String name) throws Exception {
        Bitmap full = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(full));
        Bitmap half = Bitmap.createScaledBitmap(full, 960, 540, true);
        String dir = System.getProperty("curtain.frames");
        if (dir != null) {
            File d = new File(dir);
            assertTrue(d.isDirectory() || d.mkdirs());
            try (FileOutputStream out = new FileOutputStream(new File(d, name + ".png"))) {
                half.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
        return full;
    }

    private static int lit(Bitmap b, int x0, int y0, int x1, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y += 4) for (int x = x0; x < x1; x += 4) {
            int c = b.getPixel(x, y);
            if (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF) > 60) n++;
        }
        return n;
    }

    @Test public void keyFramesOfTheLantern() throws Exception {
        String[] names = {"c1-scan", "c2-restore", "c3-cleaning", "c4-installing", "c5-finale"};
        float[] at = {3.3f, 11.6f, 13.9f, 27.3f, 29.6f};
        Bitmap[] frames = new Bitmap[names.length];
        for (int i = 0; i < names.length; i++) {
            CurtainView v = view(true);
            play(v, at[i]);
            frames[i] = shot(v, names[i]);
        }
        // The caption and the line are drawn in every stage before the finale.
        for (int i = 0; i < 4; i++) assertTrue(names[i], lit(frames[i], 100, 50, 520, 100) > 0);
        // The scan's cards fill the upper screen; the install leaves it to the band.
        assertTrue(lit(frames[0], 100, 100, 1800, 800) > lit(frames[3], 100, 100, 1800, 560));
        // The finale has no progress line but "All clean.".
        assertTrue(lit(frames[4], 150, 500, 600, 600) > 0);
        assertEquals(0, lit(frames[4], 1600, 900, 1812, 940));
    }

    @Test public void reducedMotionShowsTheSameStagesAtOnce() throws Exception {
        CurtainView v = view(false);
        play(v, 13.9f);
        Bitmap b = shot(v, "c3-cleaning-reduced");
        assertTrue(lit(b, 100, 880, 900, 940) > 0);
    }

    @Test public void theFinaleEndsTheCurtainAfterSixSeconds() {
        CurtainView v = view(true);
        play(v, 28.4f + 5.9f);
        assertTrue(!finished);
        v.frame(28.4f + 6.05f);
        assertTrue(finished);
    }
}
