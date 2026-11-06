package com.midroid.mi5gtv.matches;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared look for the Mi5G Score detail screens: dark teal, gold focus, Cairo. */
final class Screens {
    private Screens() {}

    static int dp(Context c, int v) { return (int) (v * c.getResources().getDisplayMetrics().density); }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(c.getResources().getFont(R.font.cairo), bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    static LinearLayout page(Context c, String title, String sub) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(0xFF07201F);
        root.setPadding(dp(c, 40), dp(c, 24), dp(c, 40), dp(c, 20));
        root.addView(text(c, title, 26, 0xFFD4AF37, true));
        if (sub != null) root.addView(text(c, sub, 15, 0xB3E6F4F1, false));
        return root;
    }

    static GradientDrawable round(Context c, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, 12));
        if (stroke != 0) g.setStroke(dp(c, 2), stroke);
        return g;
    }

    static void focusable(View v) {
        Context c = v.getContext();
        v.setFocusable(true);
        v.setClickable(true);
        GradientDrawable idle = round(c, 0x1FFFFFFF, 0), focus = round(c, 0x40D4AF37, 0xFFD4AF37);
        v.setBackground(idle);
        v.setOnFocusChangeListener((x, f) -> x.setBackground(f ? focus : idle));
    }

    /** A row of columns with weights; first column at the start. */
    static LinearLayout row(Context c, String[] cells, float[] weights, int color, boolean bold) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        for (int i = 0; i < cells.length; i++) {
            TextView t = text(c, cells[i], 17, color, bold);
            t.setGravity(i == 1 && cells.length > 3 ? Gravity.START : Gravity.CENTER);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, weights[i]));
        }
        return r;
    }
}
