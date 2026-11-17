package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Mi5G 2.0 Control Center: a glass side panel over whatever is on screen, opened by a mapped button
 * (InputKeyService) or from Home. Translucent and in its own task, like QuickSettingsActivity, so the
 * app behind keeps playing.
 */
public final class ControlCenterActivity extends Activity {
    static final int[] SLEEP_STEPS = {0, 15, 30, 60, 90, 120};
    private static final String SLEEP_AT = "mi5g_sleep_at";

    static void open(Context ctx) {
        try {
            ctx.startActivity(new Intent(ctx, ControlCenterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** Next sleep step after the current one, wrapping to off. Pure, for tests. */
    static int nextSleep(int minutes) {
        for (int i = 0; i < SLEEP_STEPS.length; i++) if (SLEEP_STEPS[i] > minutes) return SLEEP_STEPS[i];
        return 0;
    }

    private View sleepRow, mediaRow;
    private int sleepMinutes;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(v -> finish());
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.glass_panel);
        panel.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (340 * dp), -1, Rows.start());
        lp.setMargins((int) (24 * dp), (int) (24 * dp), (int) (24 * dp), (int) (24 * dp));
        TextView title = new TextView(this);
        title.setText(R.string.mi5g_cc_title);
        title.setTextColor(0xFFD4AF37);
        title.setTextSize(20f);
        title.setTypeface(getResources().getFont(R.font.cairo), Typeface.BOLD);
        title.setPadding((int) (12 * dp), (int) (4 * dp), (int) (12 * dp), (int) (8 * dp));
        title.setGravity(Rows.start());
        panel.addView(title);
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        scrim.addView(panel, lp);
        setContentView(scrim);

        mediaRow = Rows.add(this, rows, R.drawable.ic_play, getString(R.string.mi5g_cc_nothing_playing), null, () -> {
            MediaSessions.toggle(MediaSessions.current(this));
            refreshMedia();
        });
        sleepRow = Rows.add(this, rows, R.drawable.ic_timer, getString(R.string.mi5g_cc_sleep), null, this::cycleSleep);
        // 2.4: Keep screen awake tile; OK cycles Off, 1 h, 2 h, 4 h, until turned off.
        View caffeineRow = Rows.add(this, rows, R.drawable.ic_timer, getString(R.string.mi5g_caffeine), getString(Caffeine.labelRes(Caffeine.choice(this))), null);
        caffeineRow.setOnClickListener(v -> {
            Caffeine.set(this, Caffeine.next(Caffeine.choice(this)));
            Rows.setStatus(caffeineRow, getString(Caffeine.labelRes(Caffeine.choice(this))));
        });
        Rows.add(this, rows, R.drawable.ic_power, getString(R.string.mi5g_cc_off), null, () -> {
            if (!global(this, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) needsService();
            finish();
        });
        Rows.add(this, rows, R.drawable.ic_recent, getString(R.string.mi5g_cc_recents), null, () -> {
            if (!global(this, AccessibilityService.GLOBAL_ACTION_RECENTS)) needsService();
            finish();
        });
        // 2.4 M4: the TV's inputs (backlog 27) and the apps Home opened last (backlog 11), over any app.
        Rows.add(this, rows, R.drawable.ic_input, getString(R.string.mi5g_cc_inputs), null, () -> showInputs(rows, title));
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_cc_recent_apps), null, () -> showRecentApps(rows, title));
        // 2.4 M2: All apps with search (backlog 25) and the Play Store (backlog 22).
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_all_apps), null,
                () -> start(new Intent(this, AllAppsActivity.class)));
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_play_store), null, this::openPlayStore);
        Rows.add(this, rows, R.drawable.ic_speed, getString(R.string.mi5g_cc_speed), null,
                () -> start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://fast.com"))));
        Rows.add(this, rows, R.drawable.ic_wifi, getString(R.string.mi5g_cc_wifi), null,
                () -> start(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        Rows.add(this, rows, R.drawable.ic_bluetooth, getString(R.string.mi5g_cc_bluetooth), null,
                () -> start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        Rows.add(this, rows, R.drawable.ic_speaker, getString(R.string.mi5g_cc_sound), null,
                () -> start(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        Rows.add(this, rows, R.drawable.ic_memory, getString(R.string.mi5g_cc_ram), null, this::freeMemory);
        Rows.add(this, rows, R.drawable.ic_home_settings, getString(R.string.mi5g_settings), null,
                () -> start(new Intent(this, Mi5gSettingsActivity.class)));
        long at = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getLong(SLEEP_AT, 0);
        long left = at - System.currentTimeMillis();
        sleepMinutes = left > 0 ? (int) Math.ceil(left / 60000.0) : 0;
        showSleep();
        refreshMedia();
        rows.getChildAt(0).requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();   // the mapped button again closes the panel
    }

    /** 2.4 M4: the panel lists the TV's inputs (names and hiding from Inputs on Home); OK switches. */
    private void showInputs(LinearLayout rows, TextView title) {
        rows.removeAllViews();
        title.setText(R.string.mi5g_cc_inputs);
        HomeLayout layout = HomeLayout.load(this);
        for (ArrangeServer.InputRow in : ArrangeServer.inputs(this)) {
            if (layout.hiddenInputs.contains(in.id)) continue;
            String name = layout.inputName(in.id);
            Rows.add(this, rows, in.hdmi ? R.drawable.ic_input : R.drawable.ic_av, name.isEmpty() ? in.label : name, null, () -> {
                if (!InputLauncher.open(this, in.id)) Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                finish();
            });
        }
        if (rows.getChildCount() == 0) Rows.add(this, rows, R.drawable.ic_info, getString(R.string.mi5g_cc_no_inputs), null, () -> { });
        rows.getChildAt(0).requestFocus();
    }

    /** 2.4 M4: the last apps Home or All apps opened, newest first; OK opens one. */
    private void showRecentApps(LinearLayout rows, TextView title) {
        rows.removeAllViews();
        title.setText(R.string.mi5g_cc_recent_apps);
        android.content.pm.PackageManager pm = getPackageManager();
        for (String pkg : LastOpened.recent(this, 8)) {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            CharSequence label;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)); } catch (Exception e) { continue; }
            final Intent open = launch;
            Rows.add(this, rows, R.drawable.ic_apps, label, LastOpened.describe(this, LastOpened.get(this, pkg)), () -> {
                LastOpened.record(this, pkg);
                start(open);
            });
        }
        if (rows.getChildCount() == 0) Rows.add(this, rows, R.drawable.ic_info, getString(R.string.mi5g_cc_no_recent), null, () -> { });
        rows.getChildAt(0).requestFocus();
    }

    /** 2.4 M2: the store's own category first (Google TV hides its tile), then its TV entry. */
    private void openPlayStore() {
        Intent market = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET).setPackage("com.android.vending");
        Intent tv = getPackageManager().getLeanbackLaunchIntentForPackage("com.android.vending");
        if (getPackageManager().resolveActivity(market, 0) != null) start(market);
        else if (tv != null) start(tv);
        else android.widget.Toast.makeText(this, R.string.not_available, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void refreshMedia() {
        MediaSessions.Now n = MediaSessions.current(this);
        TextView t = mediaRow.findViewById(R.id.title);
        if (n == null) {
            t.setText(R.string.mi5g_cc_nothing_playing);
            Rows.setStatus(mediaRow, getString(R.string.mi5g_cc_media_off));
        } else if (n.title.isEmpty()) {
            t.setText(R.string.mi5g_cc_nothing_playing);
            Rows.setStatus(mediaRow, null);
        } else {
            t.setText(n.title);
            Rows.setStatus(mediaRow, n.app);
        }
    }

    private void cycleSleep() {
        sleepMinutes = nextSleep(sleepMinutes);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(this, SleepReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        long at = 0;
        if (sleepMinutes > 0) {
            at = System.currentTimeMillis() + sleepMinutes * 60_000L;
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + sleepMinutes * 60_000L, pi);
        }
        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit().putLong(SLEEP_AT, at).apply();
        showSleep();
    }

    private void showSleep() {
        Rows.setStatus(sleepRow, sleepMinutes == 0 ? getString(R.string.mi5g_cc_sleep_off)
                : getString(R.string.mi5g_cc_sleep_in, sleepMinutes));
    }

    /** Asks the system to stop background apps; only processes not in use are affected. */
    private void freeMemory() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName())) continue;
            am.killBackgroundProcesses(ai.packageName);
        }
        ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(after);
        long freed = Math.max(0, (after.availMem - before.availMem) / (1024 * 1024));
        Toast.makeText(this, getString(R.string.mi5g_cc_ram_done, (int) freed), Toast.LENGTH_SHORT).show();
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void needsService() {
        Toast.makeText(this, R.string.mi5g_cc_needs_service, Toast.LENGTH_LONG).show();
    }

    static boolean global(Context ctx, int action) {
        InputKeyService s = InputKeyService.instance;
        return s != null && s.performGlobalAction(action);
    }
}
