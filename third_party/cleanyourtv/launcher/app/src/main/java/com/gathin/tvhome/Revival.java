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

    /**
     * Mi5G 2.3: our services are on the list but accessibility_enabled is a confirmed 0. -1 (absent or unreadable)
     * with a complete list is left alone, as upstream does.
     */
    static boolean needsCycle(Set<String> ours, int accessibilityEnabled) {
        return !ours.isEmpty() && accessibilityEnabled == 0;
    }

    /** enabled without our own services, other apps' entries kept in order. */
    static String without(String enabled, String pkg) {
        if (enabled == null) return "";
        StringBuilder b = new StringBuilder();
        Set<String> mine = ours(pkg, enabled);
        for (String s : enabled.split(":")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            ComponentName c = ComponentName.unflattenFromString(t);
            if (c != null && mine.contains(c.flattenToShortString())) continue;
            if (b.length() > 0) b.append(':');
            b.append(t);
        }
        return b.toString();
    }

    static void restore(Context ctx) {
        try {
            String pkg = ctx.getPackageName();
            String enabled = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            SharedPreferences p = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            Set<String> now = ours(pkg, enabled);
            if (!now.isEmpty()) {   // remember what the owner has on
                p.edit().putStringSet(WANTED_KEY, now).apply();
                int flag = Settings.Secure.getInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, -1);
                if (!needsCycle(now, flag)) return;
                if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return;
                // Mi5G 2.3: the list still names our helper but the switch reads 0 after standby (Hisense, some TCL and
                // Xiaomi sets mark the helper crashed). A crashed service is bound again only after it leaves the list
                // and comes back, so take ours out, put the list back, then the switch on. Ported from CleanYourTV
                // ServiceRepair, commit 86760c9 (#3404), MIT, Copyright (c) 2026 Gathin.
                Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without(enabled, pkg));
                Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabled);
                Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
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
