package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Mi5G 2.0.2: the Mi5G settings look (title in gold, glass panel of rows) for the 2.0.2 screens. */
final class Mi5gPanel {
    private Mi5gPanel() {}

    /** Sets the activity's content; returns the rows container. note may be null. */
    static LinearLayout build(Activity a, CharSequence title, CharSequence note) {
        float dp = a.getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(0xFF0B1F1E);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (48 * dp);
        col.setPadding(pad, (int) (32 * dp), pad, pad);
        col.setGravity(Rows.start());
        Typeface cairo = a.getResources().getFont(R.font.cairo);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextColor(0xFFD4AF37);
        t.setTextSize(26f);
        t.setTypeface(cairo, Typeface.BOLD);
        t.setGravity(Rows.start());
        col.addView(t, new LinearLayout.LayoutParams((int) (560 * dp), -2));
        if (note != null) {
            TextView n = new TextView(a);
            n.setText(note);
            n.setTextColor(0xCCE8D9A8);
            n.setTextSize(15f);
            n.setTypeface(cairo);
            n.setGravity(Rows.start());
            col.addView(n, new LinearLayout.LayoutParams((int) (560 * dp), -2));
        }
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        int p6 = (int) (6 * dp);
        panel.setPadding(p6, p6, p6, p6);
        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(rows);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (560 * dp), -2);
        lp.topMargin = (int) (16 * dp);
        col.addView(panel, lp);
        scroll.addView(col);
        a.setContentView(scroll);
        return rows;
    }
}
