package com.midroid.mi5gtv.matches;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Arabic RTL TV screens from recorded ESPN data, written to build/score-screens. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "ar-rSA-ldrtl-w960dp-h540dp-land-television-xhdpi")
public class ScoreScreensTest {
    static void save(View v, String name) throws Exception {
        int w = 1920, h = 1080;
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(b));
        File out = new File("build/score-screens");
        out.mkdirs();
        try (FileOutputStream o = new FileOutputStream(new File(out, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }

    @Test public void standings() throws Exception {
        StandingsActivity a = Robolectric.buildActivity(StandingsActivity.class).setup().get();
        a.show(Espn.standings(EspnTest.res("standings.json")));
        save(a.getWindow().getDecorView(), "score-standings");
    }

    @Test public void match() throws Exception {
        Intent i = new Intent().putExtra("slug", "eng.1").putExtra("event", "401879246")
                .putExtra("title", "ليفربول 0 - 1 آرسنال").putExtra("league", "الدوري الإنجليزي");
        MatchActivity a = Robolectric.buildActivity(MatchActivity.class, i).setup().get();
        String j = EspnTest.res("summary.json");
        a.show(Espn.keyEvents(j), Espn.stats(j));
        save(a.getWindow().getDecorView(), "score-match");
    }
}
