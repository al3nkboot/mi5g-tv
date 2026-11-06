package com.midroid.mi5gtv.matches;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** One match: goals, cards and substitutions, then the team statistics. */
public class MatchActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout body;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout page = Screens.page(this, getIntent().getStringExtra("title"), getIntent().getStringExtra("league"));
        status = Screens.text(this, "جاري التحميل…", 15, 0xCCFFFFFF, false);
        page.addView(status);
        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        sv.addView(body);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        String slug = getIntent().getStringExtra("slug"), event = getIntent().getStringExtra("event");
        if (slug == null || event == null || !slug.matches("[a-z0-9.]+") || !event.matches("\\d+")) { finish(); return; }
        new Thread(() -> {
            String json = Espn.get(Espn.SITE + slug + "/summary?event=" + event);
            main.post(() -> show(json == null ? null : Espn.keyEvents(json), json == null ? null : Espn.stats(json)));
        }).start();
    }

    void show(List<String> events, List<String[]> stats) {
        body.removeAllViews();
        if (events == null) { status.setText("لا يوجد اتصال بالإنترنت"); return; }
        status.setText(events.isEmpty() && stats.isEmpty() ? "لا توجد تفاصيل لهذه المباراة بعد" : "");
        if (!events.isEmpty()) body.addView(Screens.text(this, "أحداث المباراة", 19, 0xFFD4AF37, true));
        for (String e : events) {
            TextView t = Screens.text(this, e, 17, 0xFFFFFFFF, false);
            t.setPadding(Screens.dp(this, 14), Screens.dp(this, 6), Screens.dp(this, 14), Screens.dp(this, 6));
            Screens.focusable(t);
            body.addView(t, gap());
        }
        if (!stats.isEmpty()) {
            TextView h = Screens.text(this, "الإحصائيات", 19, 0xFFD4AF37, true);
            h.setPadding(0, Screens.dp(this, 14), 0, 0);
            body.addView(h);
        }
        for (String[] s : stats) {
            LinearLayout r = Screens.row(this, new String[]{s[1], s[0], s[2]}, new float[]{1, 2, 1}, 0xFFFFFFFF, false);
            Screens.focusable(r);
            body.addView(r, gap());
        }
        if (body.getChildCount() > 1) body.getChildAt(1).requestFocus();
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Screens.dp(this, 5);
        return lp;
    }
}
