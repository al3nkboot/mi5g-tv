package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mi5G 2.0 button mapper storage: "map.<keycode>.<1|2|3|H>" = action, in the launcher's settings.
 * Actions: "app:<package>", "settings", "control_center", "home", "back", "recents", "inputs", "ignore".
 */
final class ButtonMap {
    static final String PREFS = "button_map";
    static final String[] FIXED_ACTIONS = {"control_center", "settings", "home", "back", "recents", "inputs", "ignore"};

    static String key(int keyCode, int pattern) { return "map." + keyCode + "." + PressPattern.suffix(pattern); }

    /** Parses a stored action; null when it is not one this version knows (ignored, never run). */
    static String validAction(String a) {
        if (a == null) return null;
        if (a.startsWith("input:")) return HomeLayout.validId(a.substring(6)) ? a : null;
        if (a.startsWith("app:")) return a.substring(4).matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") ? a : null;
        for (String f : FIXED_ACTIONS) if (f.equals(a)) return a;
        return null;
    }

    /** keycode -> pattern suffix -> action, for the mapper screen and the service. */
    static Map<Integer, Map<String, String>> parse(Map<String, ?> all) {
        Map<Integer, Map<String, String>> out = new TreeMap<>();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            String[] p = e.getKey().split("\\.");
            if (p.length != 3 || !"map".equals(p[0]) || !(e.getValue() instanceof String)) continue;
            int code;
            try { code = Integer.parseInt(p[1]); } catch (NumberFormatException x) { continue; }
            String action = validAction((String) e.getValue());
            if (code <= 0 || action == null || !p[2].matches("[123H]")) continue;
            out.computeIfAbsent(code, c -> new TreeMap<>()).put(p[2], action);
        }
        return out;
    }

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static Map<Integer, Map<String, String>> load(Context ctx) { return parse(prefs(ctx).getAll()); }

    static void put(Context ctx, int keyCode, int pattern, String action) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (action == null) e.remove(key(keyCode, pattern)); else e.putString(key(keyCode, pattern), action);
        e.apply();
    }

    static List<Integer> keys(Map<Integer, Map<String, String>> map) { return new ArrayList<>(map.keySet()); }
}
