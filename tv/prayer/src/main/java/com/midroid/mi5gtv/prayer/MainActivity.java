package com.midroid.mi5gtv.prayer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** TV home of مواقيتي: today's times, the next prayer with a countdown, Quran radio. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Settings s;
    private TextView nextName, countdown, hijri, date;
    private final TextView[] times = new TextView[Engine.COUNT];
    private final LinearLayout[] cards = new LinearLayout[Engine.COUNT];
    private ZonedDateTime clock;   // fixed time for screenshots; null = now

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float dp = getResources().getDisplayMetrics().density;
        Typeface cairo = getResources().getFont(R.font.cairo);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundResource(R.drawable.bg_teal);
        root.setPadding((int) (48 * dp), (int) (28 * dp), (int) (48 * dp), (int) (24 * dp));
        s = Settings.load(this);
        root.addView(Ui.text(this, s.cityAr, 30, 0xFFD4AF37, cairo, true));
        hijri = Ui.text(this, "", 18, 0xFFE6F4F1, cairo, false);
        date = Ui.text(this, "", 15, 0xB3E6F4F1, cairo, false);
        root.addView(hijri);
        root.addView(date);
        nextName = Ui.text(this, "", 22, 0xFFFFFFFF, cairo, false);
        nextName.setPadding(0, (int) (18 * dp), 0, 0);
        countdown = Ui.text(this, "", 64, 0xFFFFFFFF, cairo, true);
        root.addView(nextName);
        root.addView(countdown);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < Engine.COUNT; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding((int) (10 * dp), (int) (10 * dp), (int) (10 * dp), (int) (10 * dp));
            card.addView(Ui.text(this, Engine.NAMES_AR[i], 17, 0xFFE6F4F1, cairo, false));
            times[i] = Ui.text(this, "", 24, 0xFFFFFFFF, cairo, true);
            card.addView(times[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
            row.addView(card, lp);
            cards[i] = card;
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = (int) (22 * dp);
        root.addView(row, rl);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        TextView asr=button(getString(s.hanafi?R.string.prayer_asr_hanafi:R.string.prayer_asr_shafi),cairo,v->{
            if(Settings.saveHanafi(this,!s.hanafi)){s=Settings.load(this);((TextView)v).setText(s.hanafi?R.string.prayer_asr_hanafi:R.string.prayer_asr_shafi);refresh();Alarms.arm(this);}
        });
        buttons.addView(asr);
        buttons.addView(button(getString(R.string.radio), cairo, v -> startActivity(new Intent(this, RadioActivity.class))));
        buttons.addView(button(getString(R.string.test_adhan), cairo, v -> AdhanService.start(this, Engine.next(s, now()).prayer)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = (int) (22 * dp);
        root.addView(buttons, bl);
        setContentView(root);
        buttons.getChildAt(0).requestFocus();
        Alarms.arm(this);
    }

    /** Freezes the screen at one moment (screenshot tests). */
    void showAt(ZonedDateTime at) { clock = at; refresh(); }

    private ZonedDateTime now() { return clock != null ? clock : ZonedDateTime.now(ZoneId.of(s.zone)); }

    private TextView button(String label, Typeface tf, View.OnClickListener l) {
        float dp = getResources().getDisplayMetrics().density;
        TextView t = Ui.text(this, label, 18, 0xFFFFFFFF, tf, true);
        t.setPadding((int) (26 * dp), (int) (10 * dp), (int) (26 * dp), (int) (10 * dp));
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(l);
        GradientDrawable idle = round(0x33FFFFFF, 0), focus = round(0xFFD4AF37, 0);
        t.setBackground(idle);
        t.setOnFocusChangeListener((v, f) -> {
            v.setBackground(f ? focus : idle);
            ((TextView) v).setTextColor(f ? 0xFF0B2B29 : 0xFFFFFFFF);
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(150).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins((int) (10 * dp), 0, (int) (10 * dp), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private GradientDrawable round(int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(22 * getResources().getDisplayMetrics().density);
        if (stroke != 0) g.setStroke((int) (2 * getResources().getDisplayMetrics().density), stroke);
        return g;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    private void refresh() {
        ZonedDateTime n = now();
        ZonedDateTime[] d = Engine.day(s, n.toLocalDate());
        Engine.Next next = Engine.next(s, n);
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("h:mm", new Locale("ar"));
        for (int i = 0; i < Engine.COUNT; i++) {
            times[i].setText(d[i].format(hm));
            boolean isNext = next.at.equals(d[i]);
            cards[i].setBackground(round(isNext ? 0x40D4AF37 : 0x1FFFFFFF, isNext ? 0xFFD4AF37 : 0));
        }
        nextName.setText(getString(R.string.next_prayer, Engine.NAMES_AR[next.prayer]));
        countdown.setText(Engine.countdown(Duration.between(n, next.at).getSeconds()));
        hijri.setText(Engine.hijri(s, n));
        date.setText(n.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", new Locale("ar"))));
    }

    @Override protected void onResume() { super.onResume(); s = Settings.load(this); if (clock == null) handler.post(tick); else refresh(); }

    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
}
