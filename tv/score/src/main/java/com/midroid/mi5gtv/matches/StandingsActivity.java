package com.midroid.mi5gtv.matches;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** League tables for the leagues ESPN covers, picked with the chips at the top. */
public class StandingsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout table;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout page = Screens.page(this, "📊 جدول الترتيب", null);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout chips = new LinearLayout(this);
        hs.addView(chips);
        page.addView(hs);
        for (String[] l : MainActivity.LEAGUES) {
            if (TextUtils.isEmpty(l[2]) || l[2].startsWith("fifa") || l[2].contains("champions")) continue;
            TextView chip = Screens.text(this, l[1], 16, 0xFFFFFFFF, true);
            chip.setPadding(Screens.dp(this, 16), Screens.dp(this, 8), Screens.dp(this, 16), Screens.dp(this, 8));
            Screens.focusable(chip);
            chip.setOnClickListener(v -> load(l[2]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Screens.dp(this, 8));
            chips.addView(chip, lp);
        }
        status = Screens.text(this, "", 15, 0xCCFFFFFF, false);
        page.addView(status);
        page.addView(Screens.row(this, new String[]{"#", "الفريق", "لعب", "الفارق", "النقاط"}, W, 0xFFD4AF37, true));
        ScrollView sv = new ScrollView(this);
        table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        sv.addView(table);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        if (chips.getChildCount() > 0) chips.getChildAt(0).requestFocus();
        load(MainActivity.LEAGUES[0][2]);
    }

    static final float[] W = {0.6f, 3f, 1f, 1f, 1f};

    private void load(String slug) {
        status.setText("جاري التحميل…");
        new Thread(() -> {
            String json = Espn.get(Espn.STANDINGS + slug + "/standings");
            List<Espn.Row> rows = json == null ? null : Espn.standings(json);
            main.post(() -> show(rows));
        }).start();
    }

    void show(List<Espn.Row> rows) {
        table.removeAllViews();
        if (rows == null) { status.setText("لا يوجد اتصال بالإنترنت"); return; }
        status.setText(rows.isEmpty() ? "لا يتوفر جدول لهذه البطولة" : "");
        for (Espn.Row r : rows) {
            LinearLayout row = Screens.row(this, new String[]{r.rank, r.team, r.played, "\u200E" + r.diff, r.points}, W, 0xFFFFFFFF, false);
            Screens.focusable(row);
            table.addView(row, gap());
        }
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Screens.dp(this, 5);
        return lp;
    }
}
