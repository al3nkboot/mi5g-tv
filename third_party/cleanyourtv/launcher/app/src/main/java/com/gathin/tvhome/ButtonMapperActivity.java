package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Map;

/**
 * Mi5G 2.0 button mapper: any remote button, with an action per press, double press, triple press
 * and hold. Runs through InputKeyService, so it works over every app once that service is on.
 */
public final class ButtonMapperActivity extends Activity {
    static final String SMARTTUBE = "com.teamsmart.videomanager.tv";
    static final int[] PATTERNS = {PressPattern.SINGLE, PressPattern.DOUBLE, PressPattern.TRIPLE, PressPattern.HOLD};
    private LinearLayout rows;
    private boolean capturing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B1F1E);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (48 * dp);
        col.setPadding(pad, (int) (32 * dp), pad, pad);
        TextView title = new TextView(this);
        title.setText(R.string.mi5g_mapper);
        title.setTextColor(0xFFD4AF37);
        title.setTextSize(26f);
        title.setTypeface(getResources().getFont(R.font.cairo), Typeface.BOLD);
        title.setGravity(Rows.start());
        col.addView(title, new LinearLayout.LayoutParams((int) (560 * dp), -2));
        col.setGravity(Rows.start());
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        panel.setPadding((int) (6 * dp), (int) (6 * dp), (int) (6 * dp), (int) (6 * dp));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(rows);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (560 * dp), -2);
        lp.topMargin = (int) (16 * dp);
        col.addView(panel, lp);
        scroll.addView(col);
        setContentView(scroll);
        build();
    }

    static String keyName(int code) {
        switch (code) {
            case 176: return "Settings (176)";
            case 190: return "YouTube (190)";
            case 4065: return "YouTube · Xiaomi (4065)";
            case 191: return "Netflix (191)";
            case 4062: return "Netflix · Xiaomi (4062)";
            case 284: return "Apps (284)";
            case 82: return "Menu (82)";
            default: break;
        }
        String n = KeyEvent.keyCodeToString(code);
        return n.startsWith("KEYCODE_") ? n.substring(8).replace('_', ' ') + " (" + code + ")" : String.valueOf(code);
    }

    String summary(Map<String, String> byPattern) {
        StringBuilder b = new StringBuilder();
        for (int p : PATTERNS) {
            String a = byPattern.get(PressPattern.suffix(p));
            if (a == null) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(patternLabel(p)).append(": ").append(actionLabel(a));
        }
        return b.toString();
    }

    String patternLabel(int p) {
        switch (p) {
            case PressPattern.DOUBLE: return getString(R.string.mi5g_press_2);
            case PressPattern.TRIPLE: return getString(R.string.mi5g_press_3);
            case PressPattern.HOLD: return getString(R.string.mi5g_press_H);
            default: return getString(R.string.mi5g_press_1);
        }
    }

    String actionLabel(String a) {
        if (a.startsWith("input:")) return a.substring(6);
        if (a.startsWith("app:")) {
            try {
                return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(a.substring(4), 0)));
            } catch (Exception e) {
                return a.substring(4);
            }
        }
        switch (a) {
            case "control_center": return getString(R.string.mi5g_action_control_center);
            case "settings": return getString(R.string.mi5g_action_settings);
            case "home": return getString(R.string.mi5g_action_home);
            case "back": return getString(R.string.mi5g_action_back);
            case "recents": return getString(R.string.mi5g_action_recents);
            case "ignore": return getString(R.string.mi5g_action_ignore);
            case "inputs": return getString(R.string.mi5g_action_inputs);
            default: return a;
        }
    }

    private void build() {
        rows.removeAllViews();
        Map<Integer, Map<String, String>> map = ButtonMap.load(this);
        Rows.add(this, rows, R.drawable.ic_keys, getString(R.string.mi5g_mapper_add), null, () -> {
            capturing = true;
            Toast.makeText(this, R.string.mi5g_mapper_press, Toast.LENGTH_LONG).show();
        });
        Rows.add(this, rows, R.drawable.ic_play, getString(R.string.mi5g_mapper_youtube), null, () -> {
            ButtonMap.put(this, 190, PressPattern.SINGLE, "app:" + SMARTTUBE);
            ButtonMap.put(this, 4065, PressPattern.SINGLE, "app:" + SMARTTUBE);
            saved();
        });
        Rows.add(this, rows, R.drawable.ic_settings, getString(R.string.mi5g_mapper_settings_key), null, () -> {
            ButtonMap.put(this, KeyEvent.KEYCODE_SETTINGS, PressPattern.SINGLE, "control_center");
            saved();
        });
        if (map.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.mi5g_mapper_empty);
            empty.setTextColor(0x99FFFFFF);
            empty.setPadding(24, 16, 24, 16);
            rows.addView(empty);
        }
        for (Map.Entry<Integer, Map<String, String>> e : map.entrySet()) {
            int code = e.getKey();
            Rows.add(this, rows, R.drawable.ic_keys, keyName(code), summary(e.getValue()), () -> pickPattern(code));
        }
        rows.getChildAt(0).requestFocus();
    }

    private void saved() {
        InputKeyService.reloadIfRunning();
        build();
    }

    private void pickPattern(int code) {
        LinearLayout[] r = new LinearLayout[1];
        Dialog d = TileMenu.panel(this, keyName(code), r);
        Map<String, String> cur = ButtonMap.load(this).get(code);
        for (int p : PATTERNS) {
            String a = cur == null ? null : cur.get(PressPattern.suffix(p));
            Rows.add(this, r[0], 0, patternLabel(p), a == null ? getString(R.string.mi5g_action_none) : actionLabel(a),
                    () -> { d.dismiss(); pickAction(code, p); });
        }
        d.show();
        r[0].getChildAt(1).requestFocus();
    }

    private void pickAction(int code, int pattern) {
        LinearLayout[] r = new LinearLayout[1];
        Dialog d = TileMenu.panel(this, keyName(code) + " · " + patternLabel(pattern), r);
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        r[0].addView(sv, new LinearLayout.LayoutParams(-1, (int) (320 * getResources().getDisplayMetrics().density)));
        Rows.add(this, list, R.drawable.ic_remove, getString(R.string.mi5g_action_none), null,
                () -> { d.dismiss(); ButtonMap.put(this, code, pattern, null); saved(); });
        for (String a : ButtonMap.FIXED_ACTIONS) {
            Rows.add(this, list, 0, actionLabel(a), null, () -> { d.dismiss(); ButtonMap.put(this, code, pattern, a); saved(); });
        }
        for (ArrangeServer.InputRow in : ArrangeServer.inputs(this)) {
            Rows.add(this,list,R.drawable.ic_input,in.label,null,()->{d.dismiss();ButtonMap.put(this,code,pattern,"input:"+in.id);saved();});
        }
        for (String[] app : Mi5gSettingsActivity.apps(this)) {
            Rows.add(this, list, 0, app[1], null, () -> { d.dismiss(); ButtonMap.put(this, code, pattern, "app:" + app[0]); saved(); });
        }
        d.show();
        list.getChildAt(0).requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (capturing && !isNavigation(keyCode)) {
            capturing = false;
            pickPattern(keyCode);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** D-pad, OK and Back drive this screen, so they cannot be mapped here. */
    static boolean isNavigation(int k) {
        return k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_DPAD_LEFT
                || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_BACK;
    }
}
