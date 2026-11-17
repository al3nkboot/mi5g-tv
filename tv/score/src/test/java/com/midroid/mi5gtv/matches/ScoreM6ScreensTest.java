package com.midroid.mi5gtv.matches;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Mi5G 2.4 (M6) review screens: big tournaments with prayer-clash notes, match actions, corner card and banner. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "ar-rSA-ldrtl-w960dp-h540dp-land-television-xhdpi")
public class ScoreM6ScreensTest {
    static MainActivity.MatchItem item(String code, String league, String home, String away, String hs, String as,
                                       String state, String status, String time, String id, String iso) {
        MainActivity.MatchItem m = new MainActivity.MatchItem(code, league, home, away, hs, as, state, status, time, true);
        m.eventId = id;
        m.slug = code;
        m.kickoff = Football.kickoffMs(iso);
        return m;
    }

    @Test public void tournaments() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"));
        RuntimeEnvironment.getApplication().getSharedPreferences(Reminders.PREFS, 0).edit()
                .putInt("active_tab", 4).putBoolean("initialized_v1", true)
                .putString("fav_teams_csv", "الهلال,ريال مدريد").putString("fav_leagues_csv", "uefa.champions")
                .putString(Reminders.KEY, Reminders.encode(Arrays.asList(new Reminders.Item("700002", "afc.champions",
                        "الهلال", "السد", "دوري أبطال آسيا للنخبة", Football.kickoffMs("2026-11-25T17:00Z"))))).commit();
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.setPrayers(FootballM6Test.TIMES);
        List<MainActivity.MatchItem> list = Arrays.asList(
                item("uefa.champions", "دوري أبطال أوروبا", "ريال مدريد", "ليفربول", "2", "1", "in", "مباشر الآن 🔴 67'", "11:00 م", "700001", "2026-11-18T20:00Z"),
                item("uefa.champions", "دوري أبطال أوروبا", "بايرن ميونخ", "أرسنال", "0", "0", "pre", "⏰ 11:00 م", "11:00 م", "700003", "2026-11-25T20:00Z"),
                item("afc.champions", "دوري أبطال آسيا للنخبة", "الهلال", "السد", "0", "0", "pre", "⏰ 8:00 م", "8:00 م", "700002", "2026-11-25T17:00Z"),
                item("afc.champions", "دوري أبطال آسيا للنخبة", "النصر", "العين", "0", "0", "pre", "⏰ 5:15 م", "5:15 م", "700004", "2026-11-25T14:15Z"));
        a.setTournaments(list);
        ScoreScreensTest.save(a.getWindow().getDecorView(), "m6-score-tournaments");
        android.widget.ScrollView sv = find(a.getWindow().getDecorView());
        sv.scrollTo(0, sv.getChildAt(0).getHeight());
        ScoreScreensTest.save(a.getWindow().getDecorView(), "m6-score-tournaments-prayer-clash");
    }

    static android.widget.ScrollView find(android.view.View v) {
        if (v instanceof android.widget.ScrollView) return (android.widget.ScrollView) v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { android.widget.ScrollView s = find(g.getChildAt(i)); if (s != null) return s; }
        }
        return null;
    }

    @Test public void matchActions() throws Exception {
        Intent i = new Intent().putExtra("slug", "afc.champions").putExtra("event", "700002")
                .putExtra("title", "الهلال  0 - 0  السد").putExtra("league", "دوري أبطال آسيا للنخبة")
                .putExtra("home", "الهلال").putExtra("away", "السد").putExtra("state", "pre")
                .putExtra("kickoff", System.currentTimeMillis() + 86_400_000L);
        MatchActivity a = Robolectric.buildActivity(MatchActivity.class, i).setup().get();
        a.show(Arrays.<String>asList(), Arrays.<String[]>asList());
        ScoreScreensTest.save(a.getWindow().getDecorView(), "m6-score-match-actions");
    }

    @Test public void cornerCardAndBannerOverAnApp() throws Exception {
        Activity host = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout tv = new FrameLayout(host);
        tv.setBackgroundColor(Color.rgb(28, 36, 52));
        TextView app = Screens.text(host, "▶", 120, 0x33FFFFFF, true);
        tv.addView(app, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        int m = Screens.dp(host, 32);
        FrameLayout.LayoutParams pin = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        pin.setMargins(m, m, m, Screens.dp(host, 28));
        tv.addView(CornerService.card(host, "⚽ الهلال 2 - 1 النصر  67'", "دوري روشن السعودي"), pin);
        FrameLayout.LayoutParams ban = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        ban.setMargins(m, Screens.dp(host, 28), m, m);
        tv.addView(CornerService.card(host, host.getString(R.string.score_kickoff_soon, "الاتحاد × الأهلي السعودي", "دوري روشن السعودي"),
                host.getString(R.string.app_name)), ban);
        host.setContentView(tv);
        ScoreScreensTest.save(host.getWindow().getDecorView(), "m6-score-corner-card");
    }
}
