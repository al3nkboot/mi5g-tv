package com.midroid.mi5gtv.prayer;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TextView;

final class Ui {
    private Ui() {}

    static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
