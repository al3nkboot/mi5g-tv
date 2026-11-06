package com.gathin.tvhome;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Mi5G 2.0: one glass row (the Inputs menu's item_input) for the Mi5G panels. */
final class Rows {
    private Rows() {}

    static View add(Context ctx, LinearLayout rows, int icon, CharSequence title, CharSequence status, Runnable action) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.item_input, rows, false);
        ImageView i = row.findViewById(R.id.icon);
        if (icon != 0) i.setImageResource(icon); else i.setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        setStatus(row, status);
        if (rtl()) {
            // The launcher keeps LTR layouts (no supportsRtl), so Arabic rows are mirrored by hand.
            android.widget.LinearLayout lr = (android.widget.LinearLayout) row;
            java.util.List<View> kids = new java.util.ArrayList<>();
            for (int k = 0; k < lr.getChildCount(); k++) kids.add(lr.getChildAt(k));
            lr.removeAllViews();
            for (int k = kids.size() - 1; k >= 0; k--) lr.addView(kids.get(k));
            TextView t = row.findViewById(R.id.title);
            t.setGravity(android.view.Gravity.RIGHT | android.view.Gravity.CENTER_VERTICAL);
            android.view.ViewGroup.MarginLayoutParams m = (android.view.ViewGroup.MarginLayoutParams) i.getLayoutParams();
            int gap = (int) (10 * ctx.getResources().getDisplayMetrics().density);
            ((android.view.ViewGroup.MarginLayoutParams) t.getLayoutParams()).setMargins(gap, 0, gap, 0);
            m.setMargins(0, 0, 0, 0);
        }
        row.setOnClickListener(v -> action.run());
        rows.addView(row);
        return row;
    }

    static boolean rtl() {
        return android.text.TextUtils.getLayoutDirectionFromLocale(java.util.Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
    }

    /** START/END for panels, mirrored by hand for Arabic (see add()). */
    static int start() { return rtl() ? android.view.Gravity.RIGHT : android.view.Gravity.LEFT; }

    static void setStatus(View row, CharSequence status) {
        TextView s = row.findViewById(R.id.status);
        s.setText(status);
        s.setVisibility(status == null || status.length() == 0 ? View.GONE : View.VISIBLE);
    }
}
