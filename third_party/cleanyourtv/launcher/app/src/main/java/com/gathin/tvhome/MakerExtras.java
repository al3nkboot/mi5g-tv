package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Mi5G 2.0.2: TV-maker extras (manuals, firmware updaters, store demos, factory tools) are left out
 * of All apps by default; Mi5G settings has a Hidden apps list that shows any of them again. Nothing
 * is disabled or uninstalled. The list and the rules are Mi5G's own: package name parts and label
 * words that mark such apps. Protected and core packages (settings, the store, Google services,
 * the package installer, Live TV and input apps, Mi5G's own) are never hidden.
 */
final class MakerExtras {
    static final String SHOWN_KEY = "mi5g_extras_shown";
    static final String FILTER_KEY = "mi5g_extras_filter";

    // Mi5G-maintained package list, not imported from another launcher.
    static final Set<String> PACKAGES = new HashSet<>(Arrays.asList(
        "com.tcl.factory", "com.tcl.factorymenu", "com.tcl.tvmanual", "com.tcl.manual", "com.tcl.storedemo", "com.tcl.fota", "com.tcl.ota",
        "com.mitv.ota", "com.xiaomi.mitv.upgrade", "com.mediatek.factorymode", "com.mediatek.systemupdate",
        "com.sony.dtv.manual", "com.sony.dtv.retailmode", "com.hisense.factorytest", "com.hisense.tv.manual"));
    static boolean makerPackage(String p) {
        for(String prefix:new String[]{"com.tcl.","com.mitv.","com.xiaomi.mitv.","com.mediatek.","com.sony.dtv.","com.hisense.","com.skyworth.","com.changhong."})if(p.startsWith(prefix))return true;
        return false;
    }
    /** Whole parts of a package name ("com.example.emanual" has the part "emanual"). */
    static final Set<String> PACKAGE_PARTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "manual", "emanual", "usermanual", "tvmanual", "userguide", "helpguide", "ehelp",
            "ota", "fota", "otaupdate", "systemupdate", "sysupdate", "swupdate", "softwareupdate", "firmwareupdate", "firmware", "updatecenter",
            "demo", "demomode", "storedemo", "storemode", "shopmode", "retaildemo", "retailmode", "instoredemo", "showroom",
            "factory", "factorymode", "factorytest", "factorymenu", "facmenu", "aging", "burnin", "pqtool", "mtktest")));

    /** Words in an app's label (English and Arabic, lower case). */
    static final String[] LABEL_WORDS = {
            "user manual", "e-manual", "emanual", "user guide", "help guide", "tv guide book",
            "firmware", "system update", "software update", "ota update", "update center",
            "store demo", "store mode", "retail mode", "demo mode", "shop mode", "showroom",
            "factory", "aging test", "burn-in",
            "دليل المستخدم", "الدليل الإلكتروني", "تحديث النظام", "تحديث البرنامج", "وضع العرض", "وضع المتجر", "المصنع"};

    /** Never hidden, whatever their name says. */
    static final Set<String> PROTECTED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "com.tcl.exhibit", "android", "com.android.settings", "com.android.tv.settings", "com.google.android.tv.settings",
            "com.android.vending", "com.google.android.gms", "com.google.android.gsf", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.android.systemui", "com.android.providers.downloads",
            "com.android.tv", "com.google.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher", "com.android.documentsui", "com.google.android.youtube.tv")));

    private MakerExtras() {}

    static boolean isProtected(String pkg) {
        if (pkg == null) return true;
        if (PROTECTED.contains(pkg)) return true;
        String p = pkg.toLowerCase(Locale.ROOT);
        // Mi5G's own apps and anything that is a settings, input or Live TV app stays.
        return p.startsWith("com.midroid.") || p.startsWith("com.gathin.") || p.contains("settings")
                || p.contains("livetv") || p.contains("tvinput") || p.contains("packageinstaller") || p.endsWith(".launcher");
    }

    /** Whether the app looks like a TV-maker extra. */
    static boolean isExtra(String pkg, String label) {
        if (isProtected(pkg)) return false;
        if (PACKAGES.contains(pkg)) return true;
        if (!makerPackage(pkg)) return false;
        for (String part : pkg.toLowerCase(Locale.ROOT).split("[._]")) if (PACKAGE_PARTS.contains(part)) return true;
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        for (String w : LABEL_WORDS) if (l.contains(w)) return true;
        return false;
    }

    /** Hidden from All apps: an extra the user has not shown again, while the filter is on. */
    static boolean hidden(String pkg, String label, boolean filterOn, Set<String> shownAgain) {
        return filterOn && !shownAgain.contains(pkg) && isExtra(pkg, label);
    }

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE); }

    static boolean filterOn(Context ctx) { return prefs(ctx).getBoolean(FILTER_KEY, true); }

    static Set<String> shownAgain(Context ctx) { return new HashSet<>(prefs(ctx).getStringSet(SHOWN_KEY, Collections.emptySet())); }

    static void setShown(Context ctx, String pkg, boolean shown) {
        Set<String> s = shownAgain(ctx);
        if (shown) s.add(pkg); else s.remove(pkg);
        prefs(ctx).edit().putStringSet(SHOWN_KEY, s).apply();
    }
}
