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
    private LinearLayout actions;
    TextView remindBtn, pinBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout page = Screens.page(this, getIntent().getStringExtra("title"), getIntent().getStringExtra("league"));
        status = Screens.text(this, "جاري التحميل…", 15, 0xCCFFFFFF, false);
        actions = actionsRow();
        if (actions.getChildCount() > 0) page.addView(actions);
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
        if (actions.getChildCount() > 0) actions.getChildAt(0).requestFocus();
        else if (body.getChildCount() > 1) body.getChildAt(1).requestFocus();
    }

    /** M6: remind me before kick-off (upcoming matches) and pin the score in a corner over any app (not finished). */
    private LinearLayout actionsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Screens.dp(this, 10), 0, Screens.dp(this, 6));
        final android.content.Intent in = getIntent();
        final String event = in.getStringExtra("event"), slug = in.getStringExtra("slug");
        final String state = in.getStringExtra("state") == null ? "" : in.getStringExtra("state");
        final long kickoff = in.getLongExtra("kickoff", -1);
        if (event == null || slug == null) return row;
        final String home = in.getStringExtra("home") == null ? "" : in.getStringExtra("home");
        final String away = in.getStringExtra("away") == null ? "" : in.getStringExtra("away");
        final String league = in.getStringExtra("league") == null ? "" : in.getStringExtra("league");
        if ("pre".equals(state) && Football.remindAt(kickoff, System.currentTimeMillis()) > 0) {
            remindBtn = button(row, getString(Reminders.has(this, event) ? R.string.score_remind_off : R.string.score_remind_on));
            remindBtn.setOnClickListener(v -> {
                if (Reminders.has(this, event)) {
                    Reminders.remove(this, event);
                    remindBtn.setText(R.string.score_remind_on);
                    toast(getString(R.string.score_remind_cleared));
                } else if (Reminders.add(this, new Reminders.Item(event, slug, home, away, league, kickoff))) {
                    remindBtn.setText(R.string.score_remind_off);
                    toast(getString(R.string.score_remind_set, Football.REMIND_BEFORE_MIN));
                }
            });
        }
        if (!"post".equals(state)) {
            pinBtn = button(row, getString(event.equals(CornerService.pinned(this)) ? R.string.score_unpin : R.string.score_pin));
            pinBtn.setOnClickListener(v -> {
                if (event.equals(CornerService.pinned(this))) {
                    CornerService.unpin(this);
                    pinBtn.setText(R.string.score_pin);
                } else if (CornerService.pin(this, slug, event, league, home + " × " + away)) {
                    pinBtn.setText(R.string.score_unpin);
                    toast(getString(R.string.score_pinned));
                } else {
                    toast(getString(R.string.score_overlay_needed));
                }
            });
        }
        return row;
    }

    private TextView button(LinearLayout row, String label) {
        TextView t = Screens.text(this, label, 16, 0xFFFFFFFF, true);
        t.setPadding(Screens.dp(this, 18), Screens.dp(this, 8), Screens.dp(this, 18), Screens.dp(this, 8));
        Screens.focusable(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(Screens.dp(this, 10));
        row.addView(t, lp);
        return t;
    }

    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show(); }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Screens.dp(this, 5);
        return lp;
    }
}
