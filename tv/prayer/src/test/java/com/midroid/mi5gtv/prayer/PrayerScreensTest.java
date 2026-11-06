package com.midroid.mi5gtv.prayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Arabic RTL TV screens, written to build/prayer-screens for review. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "ar-rSA-ldrtl-w960dp-h540dp-land-television-xhdpi")
public class PrayerScreensTest {
    static void save(View v, String name) throws Exception {
        int w = 1920, h = 1080;
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        File out = new File("build/prayer-screens");
        out.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(out, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test public void home() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.showAt(ZonedDateTime.of(2026, 11, 6, 14, 41, 12, 0, ZoneId.of("Asia/Muscat")));
        save(a.getWindow().getDecorView(), "prayer-home");
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("/workspace/out/mi5g-tv-2.1/screens"));
        java.nio.file.Files.copy(java.nio.file.Paths.get("build/prayer-screens/prayer-home.png"),java.nio.file.Paths.get("/workspace/out/mi5g-tv-2.1/screens/asr_option_ar.png"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Test public void adhan() throws Exception {
        Activity a = Robolectric.buildActivity(AdhanActivity.class, new Intent().putExtra("prayer", Engine.ASR)).setup().get();
        save(a.getWindow().getDecorView(), "prayer-adhan");
    }

    @Test public void radio() throws Exception {
        RadioActivity a = Robolectric.buildActivity(RadioActivity.class).setup().get();
        a.show(Arrays.asList(new RadioActivity.Station("إبراهيم الأخضر", "https://x/1"),
                new RadioActivity.Station("أبو بكر الشاطري", "https://x/2"), new RadioActivity.Station("أحمد العجمي", "https://x/3")));
        save(a.getWindow().getDecorView(), "quran-radio");
    }

    @Test public void bannersAndWidget() throws Exception {
        Activity a = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout stage = new FrameLayout(a);
        stage.setBackgroundColor(0xFF202830);
        View pre = Banner.make(a, a.getString(R.string.pre_adhan, Engine.NAMES_AR[Engine.ASR], 10));
        stage.addView(pre, new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL));
        View kahf = Banner.make(a, a.getString(R.string.kahf_title));
        stage.addView(kahf, new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER));
        android.widget.TextView w = (android.widget.TextView) Banner.make(a, OverlayService.label(EngineTest.city("muscat"),
                ZonedDateTime.of(2026, 11, 6, 14, 41, 12, 0, ZoneId.of("Asia/Muscat"))));
        w.setTextSize(16f);
        w.setAlpha(0.85f);
        stage.addView(w, new FrameLayout.LayoutParams(-2, -2, OverlayService.gravity("bottom_end")));
        a.setContentView(stage);
        save(a.getWindow().getDecorView(), "prayer-banners-widget");
    }
}
