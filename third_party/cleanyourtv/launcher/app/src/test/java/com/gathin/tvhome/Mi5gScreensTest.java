package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

/** Mi5G 2.0 screens rendered in Arabic (RTL) at TV size; written to build/mi5g-screens. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "ar-rSA-ldrtl-w960dp-h540dp-land-television-xhdpi")
public class Mi5gScreensTest {
    static final File OUT = new File("build/mi5g-screens");

    static void save(View v, String name) throws Exception {
        int w = v.getWidth() > 0 ? v.getWidth() : 1920, h = v.getHeight() > 0 ? v.getHeight() : 1080;
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF0B1F1E);
        v.draw(new Canvas(b));
        OUT.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(OUT, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    /** A dialog drawn over its activity, centred like on the TV. */
    static void saveOver(Activity a, Dialog d, String name) throws Exception {
        View content = a.getWindow().getDecorView();
        View panel = d.getWindow().getDecorView();
        int pw = (int) (300 * a.getResources().getDisplayMetrics().density);
        save(content, name + "-under");
        Bitmap under = android.graphics.BitmapFactory.decodeFile(new File(OUT, name + "-under.png").getPath());
        new File(OUT, name + "-under.png").delete();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        System.out.println("panel before " + panel.getWidth() + "x" + panel.getHeight());
        if (panel.getWidth() == 0) {
            panel.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            panel.layout(0, 0, pw, panel.getMeasuredHeight());
        }
        pw = panel.getWidth();
        Bitmap b = under.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(b);
        c.drawColor(0x88000000);
        c.translate((b.getWidth() - pw) / 2f, (b.getHeight() - panel.getHeight()) / 2f);
        panel.draw(c);
        dump(panel, "");
        try (FileOutputStream o = new FileOutputStream(new File(OUT, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    static void dump(View v, String ind) {
        System.out.println(ind + v.getClass().getSimpleName() + " " + v.getWidth() + "x" + v.getHeight() + (v instanceof android.widget.TextView ? " '" + ((android.widget.TextView) v).getText() + "' c=" + Integer.toHexString(((android.widget.TextView) v).getCurrentTextColor()) : ""));
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) dump(((ViewGroup) v).getChildAt(i), ind + " ");
    }

    @Test public void controlCenter() throws Exception {
        Activity a = Robolectric.buildActivity(ControlCenterActivity.class).setup().get();
        save(a.getWindow().getDecorView(), "control-center");
    }

    @Test public void clock() throws Exception {
        Activity a = Robolectric.buildActivity(ClockActivity.class).setup().get();
        save(a.getWindow().getDecorView(), "screensaver-clock");
    }

    @Test public void buttonMapper() throws Exception {
        Activity a = Robolectric.buildActivity(ButtonMapperActivity.class).create().get();
        ButtonMap.put(a, 190, PressPattern.SINGLE, "app:com.teamsmart.videomanager.tv");
        ButtonMap.put(a, 176, PressPattern.SINGLE, "control_center");
        ButtonMap.put(a, 176, PressPattern.HOLD, "settings");
        ButtonMap.put(a, 4065, PressPattern.DOUBLE, "recents");
        a = Robolectric.buildActivity(ButtonMapperActivity.class).setup().get();
        save(a.getWindow().getDecorView(), "button-mapper");
    }

    @Test public void settings() throws Exception {
        Activity a = Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().get();
        save(a.getWindow().getDecorView(), "mi5g-settings");
    }

    @Test public void home() throws Exception {
        Activity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        save(a.getWindow().getDecorView(), "home");
        TileMenu.show(a, "com.example.tv", "Netflix", () -> {}, () -> {});
        saveOver(a, ShadowDialog.getLatestDialog(), "home-tile-menu");
    }

    @Test public void tileMenu() throws Exception {
        Activity a = Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().get();
        TileMenu.show(a, "com.example.tv", "Netflix", () -> {}, () -> {});
        Dialog d = ShadowDialog.getLatestDialog();
        saveOver(a, d, "tile-menu");
    }

    @Test public void mediaPill() throws Exception {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(a);
        a.setContentView(root);
        MediaPill p = new MediaPill(root);
        p.show("نشرة الأخبار المسائية", "YouTube", true);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        save(a.getWindow().getDecorView(), "media-pill");
    }
}
