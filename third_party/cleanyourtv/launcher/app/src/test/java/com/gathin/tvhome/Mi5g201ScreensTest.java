package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.util.Arrays;

/** Mi5G 2.0.1 screens in Arabic (RTL) at TV size; written to build/mi5g-screens. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "ar-rSA-ldrtl-w960dp-h540dp-land-television-xhdpi")
public class Mi5g201ScreensTest {

    @Test public void arrangeFromPhone() throws Exception {
        Activity a = Robolectric.buildActivity(ArrangeActivity.class).setup().get();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Mi5gScreensTest.save(a.getWindow().getDecorView(), "201-arrange-qr");
        a.finish();
        ArrangeServer.stopRunning();
    }

    @Test public void cityScreensaver() throws Exception {
        CityDreamActivity a = Robolectric.buildActivity(CityDreamActivity.class).create().get();
        CityScreen.Data d = new CityScreen.Data();
        d.city = "مسقط";
        d.prayer = "الظهر  11:55";
        d.weather = CityScreen.parseWeather("{\"current\":{\"temperature_2m\":29,\"weather_code\":0}}");
        a.screen.bind(d);
        Mi5gScreensTest.save(a.getWindow().getDecorView(), "201-city-screensaver");
    }

    @Test public void folderOpen() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        AppAdapter.Entry one = new AppAdapter.Entry("يوتيوب", new ComponentName("com.google.android.youtube.tv", "A"), null, a.getDrawable(R.drawable.ic_apps));
        AppAdapter.Entry two = new AppAdapter.Entry("شاهد", new ComponentName("net.mbc.shahid", "B"), null, a.getDrawable(R.drawable.ic_apps));
        AppAdapter.Entry three = new AppAdapter.Entry("نتفليكس", new ComponentName("com.netflix.ninja", "C"), null, a.getDrawable(R.drawable.ic_apps));
        a.onFolder("أفلام ومسلسلات", Arrays.asList(one, two, three));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Dialog d = ShadowDialog.getLatestDialog();
        Mi5gScreensTest.saveOver(a, d, "201-folder");
    }
}
