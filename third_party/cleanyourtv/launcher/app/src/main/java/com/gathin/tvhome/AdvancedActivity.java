package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * 2.4 M7 (backlog 29): Advanced settings, ideas from tvOS-Launcher 2.2.0's Advanced page (Apache-2.0, techgenius1;
 * own code): a status list of what Mi5G Home has been allowed to do, each opening the TV screen that changes it,
 * and a "before uninstalling" step that turns Mi5G's helpers off and opens the app's page in TV Settings.
 */
public final class AdvancedActivity extends Activity {
    private LinearLayout rows;

    static final class Check {
        final int title;
        final boolean ok;
        final Intent fix;
        Check(int title, boolean ok, Intent fix) { this.title = title; this.ok = ok; this.fix = fix; }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        rows = KidsActivity.page(this, getString(R.string.mi5g_advanced), getString(R.string.mi5g_advanced_note));
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rows.getChildCount() > 0) build();
    }

    static boolean isHome(Context c) {
        ResolveInfo ri = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
    }

    /** Whether a colon-separated "pkg/cls" list from Settings.Secure names one of this app's components. */
    static boolean listed(String list, String pkg) {
        if (list == null || list.isEmpty()) return false;
        for (String s : list.split(":")) if (s.startsWith(pkg + "/")) return true;
        return false;
    }

    /** Removes this app's components from a colon-separated Settings.Secure list. */
    static String without(String list, String pkg) {
        if (list == null) return "";
        StringBuilder b = new StringBuilder();
        for (String s : list.split(":")) {
            if (s.isEmpty() || s.startsWith(pkg + "/")) continue;
            if (b.length() > 0) b.append(':');
            b.append(s);
        }
        return b.toString();
    }

    static boolean secureSettings(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    static List<Check> checks(Context c) {
        String pkg = c.getPackageName();
        List<Check> out = new ArrayList<>();
        out.add(new Check(R.string.mi5g_adv_home, isHome(c), new Intent(Settings.ACTION_HOME_SETTINGS)));
        out.add(new Check(R.string.mi5g_adv_keys, listed(Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), pkg),
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        out.add(new Check(R.string.mi5g_adv_secure, secureSettings(c), null));
        out.add(new Check(R.string.mi5g_adv_media, listed(Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners"), pkg),
                new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        out.add(new Check(R.string.mi5g_adv_overlay, Settings.canDrawOverlays(c),
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg))));
        AlarmManager am = c.getSystemService(AlarmManager.class);
        out.add(new Check(R.string.mi5g_adv_alarms, Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms()),
                Build.VERSION.SDK_INT >= 31 ? new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pkg)) : null));
        return out;
    }

    /**
     * Turns off what keeps Mi5G Home coming back (Keep Alive, Keep screen awake, the sleep schedule, the kids
     * profile, the fast Home key) and, with the secure-settings grant, takes its key helpers off the accessibility
     * list. Returns how many steps it did. The phone's "back to stock home" still revokes the grant.
     */
    static int prepareUninstall(Context c) {
        int steps = 0;
        if (LauncherSwitches.keepAlive(c)) { LauncherSwitches.setKeepAlive(c, false); steps++; }
        if (Caffeine.active(c)) { Caffeine.set(c, 0); steps++; }
        if (Schedule.on(c)) { Schedule.setOn(c, false); steps++; }
        if (KidsProfile.on(c)) { KidsProfile.setOn(c, false); steps++; }
        if (FastHomeService.enabled(c)) { LauncherSwitches.prefs(c).edit().putBoolean(FastHomeService.KEY, false).apply(); steps++; }
        if (secureSettings(c)) {
            try {
                String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (listed(list, c.getPackageName())) {
                    Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without(list, c.getPackageName()));
                    steps++;
                }
            } catch (SecurityException ignored) {
            }
        }
        return steps;
    }

    private void build() {
        rows.removeAllViews();
        for (Check ch : checks(this)) {
            Rows.add(this, rows, ch.ok ? R.drawable.ic_home_settings : R.drawable.ic_lock, getString(ch.title),
                    getString(ch.ok ? R.string.mi5g_adv_ok : R.string.mi5g_adv_missing), () -> open(ch));
        }
        Rows.add(this, rows, R.drawable.ic_timer, getString(R.string.mi5g_adv_clear_usage), null, () -> {
            TimeOfDay.clear(this);
            Toast.makeText(this, R.string.mi5g_adv_cleared, Toast.LENGTH_SHORT).show();
        });
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_adv_uninstall), getString(R.string.mi5g_adv_uninstall_hint), () ->
                new AlertDialog.Builder(this).setTitle(R.string.mi5g_adv_uninstall).setMessage(R.string.mi5g_adv_uninstall_body)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            int n = prepareUninstall(this);
                            Toast.makeText(this, getResources().getQuantityString(R.plurals.mi5g_adv_steps, n, n), Toast.LENGTH_LONG).show();
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                            } catch (RuntimeException ignored) {
                            }
                            build();
                        }).show());
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { version = "?"; }
        Rows.add(this, rows, R.drawable.ic_home_settings, getString(R.string.mi5g_adv_about), version + "  •  Android " + Build.VERSION.RELEASE + "  •  " + Build.MODEL, () -> { });
        rows.getChildAt(0).requestFocus();
    }

    private void open(Check ch) {
        if (ch.fix == null) {
            Toast.makeText(this, R.string.mi5g_adv_from_phone, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(ch.fix);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.mi5g_adv_no_screen, Toast.LENGTH_LONG).show();
        }
    }
}
