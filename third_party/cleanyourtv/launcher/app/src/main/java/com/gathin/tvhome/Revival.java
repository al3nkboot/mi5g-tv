package com.gathin.tvhome;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mi5G 2.0.1: staying alive on Xiaomi and TCL TVs. Their standby "clean up" force-stops apps, and a force stop
 * drops our accessibility services (Home and input keys) from the enabled list. When the system starts us
 * again (boot, or the notification listener rebinding) we put back only the services the owner had on.
 * That needs WRITE_SECURE_SETTINGS, which the Mi5G phone app grants over adb; without it nothing happens.
 * Idea from Aerial TV's boot and revival handling (MIT, Copyright (c) 2026 pengchujin); own code.
 */
final class Revival {
    static final String WANTED_KEY = "mi5g_a11y_wanted";
    private static final String[] OURS = {".HomeKeyService", ".InputKeyService"};

    private Revival() {}

    /** Our services in an enabled_accessibility_services value. */
    static Set<String> ours(String pkg, String enabled) {
        Set<String> out = new LinkedHashSet<>();
        if (enabled == null) return out;
        for (String s : enabled.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s.trim());
            if (c == null || !c.getPackageName().equals(pkg)) continue;
            for (String o : OURS) if (c.getClassName().equals(pkg + o)) out.add(c.flattenToShortString());
        }
        return out;
    }

    /** enabled with the wanted services added back (none added twice); unchanged when nothing is missing. */
    static String merge(String enabled, Set<String> wanted, String pkg) {
        Set<String> have = ours(pkg, enabled);
        StringBuilder b = new StringBuilder(enabled == null ? "" : enabled.trim());
        for (String w : wanted) {
            if (have.contains(w)) continue;
            if (b.length() > 0) b.append(':');
            b.append(w);
        }
        return b.toString();
    }

    static void restore(Context ctx) {
        try {
            String pkg = ctx.getPackageName();
            String enabled = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            SharedPreferences p = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            Set<String> now = ours(pkg, enabled);
            if (!now.isEmpty()) {   // remember what the owner has on; nothing to put back
                p.edit().putStringSet(WANTED_KEY, now).apply();
                return;
            }
            Set<String> wanted = p.getStringSet(WANTED_KEY, java.util.Collections.emptySet());
            if (wanted.isEmpty()) return;
            if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return;
            String merged = merge(enabled, wanted, pkg);
            if (merged.equals(enabled)) return;
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, merged);
            Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception ignored) {
            // a TV that refuses: the owner turns the keys back on by hand, as before
        }
    }
}
