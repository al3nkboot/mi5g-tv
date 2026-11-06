package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mi5G 2.0.2: Hidden apps. The TV-maker extras that All apps leaves out (MakerExtras); OK shows one
 * again or hides it again. The first row turns the whole filter on or off. Nothing is disabled.
 */
public final class HiddenAppsActivity extends Activity {
    /** Screenshot tests: {package, label} pairs instead of the installed apps. */
    static List<String[]> testApps;
    private android.widget.LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        rows = Mi5gPanel.build(this, getString(R.string.mi5g_hidden_apps), getString(R.string.mi5g_hidden_apps_note));
        build();
    }

    /** Installed launchable apps that look like maker extras, as {package, label}. */
    private List<String[]> extras() {
        List<String[]> out = new ArrayList<>();
        if (testApps != null) {
            for (String[] a : testApps) if (MakerExtras.isExtra(a[0], a[1])) out.add(a);
            return out;
        }
        PackageManager pm = getPackageManager();
        Map<String, String> seen = new LinkedHashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (seen.containsKey(pkg)) continue;
                String label;
                try { label = String.valueOf(ri.loadLabel(pm)); } catch (RuntimeException e) { label = pkg; }
                seen.put(pkg, label);
            }
        }
        for (Map.Entry<String, String> e : seen.entrySet()) if (MakerExtras.isExtra(e.getKey(), e.getValue())) out.add(new String[]{e.getKey(), e.getValue()});
        return out;
    }

    private void build() {
        rows.removeAllViews();
        boolean on = MakerExtras.filterOn(this);
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_hidden_apps_filter), getString(on ? R.string.mi5g_on : R.string.mi5g_off), () -> {
            MakerExtras.prefs(this).edit().putBoolean(MakerExtras.FILTER_KEY, !MakerExtras.filterOn(this)).apply();
            build();
        });
        Set<String> shown = MakerExtras.shownAgain(this);
        List<String[]> extras = extras();
        if (extras.isEmpty()) Rows.add(this, rows, 0, getString(R.string.mi5g_hidden_apps_none), null, () -> { });
        for (String[] a : extras) {
            boolean visible = !on || shown.contains(a[0]);
            Rows.add(this, rows, 0, a[1], getString(visible ? R.string.mi5g_hidden_app_shown : R.string.mi5g_hidden_app_hidden), () -> {
                MakerExtras.setShown(this, a[0], !MakerExtras.shownAgain(this).contains(a[0]));
                build();
            });
        }
        rows.getChildAt(0).requestFocus();
    }
}
