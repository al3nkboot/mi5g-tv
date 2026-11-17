package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Set;

/** 2.4 M7 (backlog 3): kids profile settings: on/off, the 4-digit PIN and the allowed apps. */
public final class KidsActivity extends Activity {
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        rows = page(this, getString(R.string.mi5g_kids), getString(R.string.mi5g_kids_note));
        build();
    }

    /** The Mi5G settings look: a title, an optional note and a glass panel of rows. Returns the rows. */
    static LinearLayout page(Activity a, CharSequence title, CharSequence note) {
        float dp = a.getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(0xFF0B1F1E);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Rows.start());
        int pad = (int) (48 * dp);
        col.setPadding(pad, (int) (32 * dp), pad, pad);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextColor(0xFFD4AF37);
        t.setTextSize(26f);
        t.setTypeface(a.getResources().getFont(R.font.cairo), Typeface.BOLD);
        t.setGravity(Rows.start());
        col.addView(t, new LinearLayout.LayoutParams((int) (620 * dp), -2));
        if (note != null) {
            TextView n = new TextView(a);
            n.setText(note);
            n.setTextColor(0xB3FFFFFF);
            n.setTextSize(14f);
            n.setTypeface(a.getResources().getFont(R.font.cairo));
            n.setGravity(Rows.start());
            col.addView(n, new LinearLayout.LayoutParams((int) (620 * dp), -2));
        }
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        panel.setPadding((int) (6 * dp), (int) (6 * dp), (int) (6 * dp), (int) (6 * dp));
        LinearLayout rows = new LinearLayout(a);
        rows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(rows);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (620 * dp), -2);
        lp.topMargin = (int) (16 * dp);
        col.addView(panel, lp);
        scroll.addView(col);
        a.setContentView(scroll);
        return rows;
    }

    void build() {
        rows.removeAllViews();
        boolean on = KidsProfile.on(this);
        int count = KidsProfile.allowed(this).size();
        Rows.add(this, rows, R.drawable.ic_lock, getString(R.string.mi5g_kids), getString(on ? R.string.mi5g_on : R.string.mi5g_off), () -> {
            if (on) { KidsProfile.setOn(this, false); setResult(RESULT_OK); HomeLayout.changed(); build(); return; }
            if (!KidsProfile.hasPin(this)) { Toast.makeText(this, R.string.mi5g_kids_pin_first, Toast.LENGTH_LONG).show(); askNewPin(); return; }
            if (count == 0) Toast.makeText(this, R.string.mi5g_kids_none_allowed, Toast.LENGTH_LONG).show();
            KidsProfile.setOn(this, true);
            KidsProfile.forgetUnlock();
            setResult(RESULT_OK);
            HomeLayout.changed();
            build();
        });
        Rows.add(this, rows, R.drawable.ic_keys, getString(KidsProfile.hasPin(this) ? R.string.mi5g_kids_pin_change : R.string.mi5g_kids_pin_set), null, this::askNewPin);
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_kids_apps),
                count == 0 ? getString(R.string.mi5g_kids_apps_none) : getResources().getQuantityString(R.plurals.mi5g_kids_apps_count, count, count),
                this::pickApps);
        rows.getChildAt(0).requestFocus();
    }

    private void askNewPin() {
        EditText first = KidsProfile.pinField(this);
        new AlertDialog.Builder(this).setTitle(R.string.mi5g_kids_pin_new).setView(first)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String pin = first.getText().toString();
                    if (!KidsProfile.validPin(pin)) { Toast.makeText(this, R.string.mi5g_kids_pin_digits, Toast.LENGTH_LONG).show(); return; }
                    EditText again = KidsProfile.pinField(this);
                    new AlertDialog.Builder(this).setTitle(R.string.mi5g_kids_pin_again).setView(again)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(android.R.string.ok, (d2, w2) -> {
                                if (pin.equals(again.getText().toString()) && KidsProfile.setPin(this, pin)) {
                                    Toast.makeText(this, R.string.mi5g_kids_pin_saved, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, R.string.mi5g_kids_pin_mismatch, Toast.LENGTH_LONG).show();
                                }
                                build();
                            }).show();
                }).show();
    }

    /** Every TV app with ✓ when allowed; OK switches it. */
    void pickApps() {
        LinearLayout[] r = new LinearLayout[1];
        android.app.Dialog d = TileMenu.panel(this, getString(R.string.mi5g_kids_apps), r);
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        r[0].addView(sv, new LinearLayout.LayoutParams(-1, (int) (320 * getResources().getDisplayMetrics().density)));
        List<String[]> apps = Mi5gSettingsActivity.apps(this);
        for (String[] app : apps) {
            final android.view.View[] row = new android.view.View[1];
            row[0] = Rows.add(this, list, 0, app[1], mark(app[0]), () -> {
                Set<String> now = KidsProfile.allowed(this);
                if (!now.remove(app[0])) now.add(app[0]);
                KidsProfile.setAllowed(this, now);
                Rows.setStatus(row[0], mark(app[0]));
            });
        }
        d.setOnDismissListener(x -> { setResult(RESULT_OK); HomeLayout.changed(); build(); });
        d.show();
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private String mark(String pkg) { return KidsProfile.allowed(this).contains(pkg) ? "✓" : "—"; }
}
