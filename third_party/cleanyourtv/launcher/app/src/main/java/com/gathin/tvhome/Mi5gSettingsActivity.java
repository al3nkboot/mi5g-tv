package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Mi5G 2.0 settings on the TV: button mapper, wake target, Back clock, 12/24h, removed tiles. */
public final class Mi5gSettingsActivity extends Activity {
    private LinearLayout rows;

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
        title.setText(R.string.mi5g_settings);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (520 * dp), -2);
        lp.topMargin = (int) (16 * dp);
        col.addView(panel, lp);
        scroll.addView(col);
        setContentView(scroll);
        build();
    }

    private void build() {
        rows.removeAllViews();
        SharedPreferences p = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        // Mi5G 2.0.1: credits for the projects this launcher builds on.
        Rows.add(this, rows, R.drawable.ic_home_settings, getString(R.string.mi5g_credits), "Aerial TV · Clean Your TV · tv-vibehack · talkies · tvOS Launcher",
                () -> new android.app.AlertDialog.Builder(this).setTitle(R.string.mi5g_credits)
                        .setMessage(R.string.mi5g_credits_body).setPositiveButton(android.R.string.ok, null).show());
        // Mi5G 2.0.1: arrange Home from a phone on the same Wi-Fi (QR code, off when this screen closes).
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_arrange_row), getString(R.string.mi5g_arrange_row_hint),
                () -> startActivity(new Intent(this, ArrangeActivity.class)));
        // Mi5G 2.0.1: the city screensaver is picked in the TV's screensaver settings.
        Rows.add(this, rows, R.drawable.ic_clock, getString(R.string.mi5g_city_dream_row), getString(R.string.mi5g_city_dream_hint), () -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_DREAM_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(this, CityDreamActivity.class));
            }
        });
        Rows.add(this,rows,R.drawable.ic_home_settings,getString(R.string.mi5g_fast_home),onOff(FastHomeService.enabled(this)),()->{
            if(FastHomeService.enabled(this)){getSharedPreferences(Config.SETTINGS_NAME,MODE_PRIVATE).edit().putBoolean(FastHomeService.KEY,false).apply();build();}
            else new android.app.AlertDialog.Builder(this).setTitle(R.string.mi5g_fast_home).setMessage(R.string.mi5g_fast_home_note)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{
                    getSharedPreferences(Config.SETTINGS_NAME,MODE_PRIVATE).edit().putBoolean(FastHomeService.KEY,true).apply();
                    try{startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(RuntimeException ignored){} build();
                }).show();
        });
        Rows.add(this,rows,R.drawable.ic_play,getString(R.string.mi5g_continue_watching),onOff(ContinueWatching.enabled(this)),()->{
            if(!ContinueWatching.enabled(this) && checkSelfPermission("android.permission.READ_TV_LISTINGS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},61);}
            else {getSharedPreferences(Config.SETTINGS_NAME,MODE_PRIVATE).edit().putBoolean(ContinueWatching.KEY,!ContinueWatching.enabled(this)).apply();build();}
        });
        Rows.add(this,rows,R.drawable.ic_apps,getString(R.string.mi5g_grid),null,()->startActivity(new Intent(this,GridSettingsActivity.class)));
        // Mi5G 2.0.2
        Rows.add(this, rows, R.drawable.ic_input, getString(R.string.mi5g_device_on_row),
                onOff(DeviceOnCard.enabled(this)), () -> { DeviceOnCard.setEnabled(this, !DeviceOnCard.enabled(this)); build(); });
        Rows.add(this, rows, R.drawable.ic_input, getString(R.string.mi5g_cec_only),
                onOff(DeviceOnCard.cecOnly(this)), () -> { getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit().putBoolean(DeviceOnCard.CEC_ONLY, !DeviceOnCard.cecOnly(this)).apply(); build(); });
        // Mi5G 2.2 / CleanYourTV
        Rows.add(this, rows, R.drawable.ic_keys, getString(R.string.mi5g_ignore_channel_keys),
                onOff(LauncherSwitches.homeIgnoreChannelKeys(this)),
                () -> { LauncherSwitches.setHomeIgnoreChannelKeys(this, !LauncherSwitches.homeIgnoreChannelKeys(this)); build(); });
        Rows.add(this, rows, R.drawable.ic_home_settings, getString(R.string.mi5g_settings_clear_task),
                onOff(LauncherSwitches.settingsKeyClearTask(this)),
                () -> { LauncherSwitches.setSettingsKeyClearTask(this, !LauncherSwitches.settingsKeyClearTask(this)); build(); });
        Rows.add(this, rows, R.drawable.ic_lock, getString(R.string.mi5g_home_lock),
                onOff(LauncherSwitches.homeLocked(this)),
                () -> { LauncherSwitches.setHomeLocked(this, !LauncherSwitches.homeLocked(this)); build(); });
        // 2.4 M2: home screen polish (own code). Clean mode, Arabic names, sounds, All apps, safe mode.
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_minimal), onOff(LauncherSwitches.minimal(this)), () -> {
            LauncherSwitches.setMinimal(this, !LauncherSwitches.minimal(this));
            setResult(RESULT_OK);
            HomeLayout.changed();
            build();
        });
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_arabic_names), onOff(LauncherSwitches.arabicNames(this)), () -> {
            LauncherSwitches.setArabicNames(this, !LauncherSwitches.arabicNames(this));
            setResult(RESULT_OK);
            HomeLayout.changed();
            build();
        });
        Rows.add(this, rows, R.drawable.ic_speaker, getString(R.string.mi5g_sounds), onOff(LauncherSwitches.sounds(this)), () -> {
            LauncherSwitches.setSounds(this, !LauncherSwitches.sounds(this));
            Sounds.click(this);
            build();
        });
        // 2.4 M3 (backlog 15): a QR that connects the Mi5G TV phone app to this TV.
        Rows.add(this, rows, R.drawable.ic_home_settings, getString(R.string.mi5g_connect_phone), null,
                () -> startActivity(new Intent(this, PhoneConnectActivity.class)));
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_all_apps), null,
                () -> startActivity(new Intent(this, AllAppsActivity.class)));
        Rows.add(this, rows, R.drawable.ic_home_settings, getString(R.string.mi5g_safe_mode),
                SafeMode.active(this) ? getString(R.string.mi5g_safe_mode_status_on) : onOff(false), () -> {
                    SafeMode.setActive(this, !SafeMode.active(this));
                    setResult(RESULT_OK);
                    HomeLayout.changed();
                    build();
                });
        // 2.4 M4: Home widgets (backlog 26), the screensaver's calendar line (backlog 5), reminders (backlog 30)
        // and the weekly sleep and wake schedule (backlog 34). Reminders and the week are set on the phone page.
        Rows.add(this, rows, R.drawable.ic_swap_vert, getString(R.string.mi5g_widgets), null,
                () -> startActivity(new Intent(this, HomeWidgetsActivity.class)));
        Rows.add(this, rows, R.drawable.ic_clock, getString(R.string.mi5g_saver_info), onOff(Occasions.saverInfoOn(this)), () -> {
            Occasions.setSaverInfo(this, !Occasions.saverInfoOn(this));
            build();
        });
        int reminders = Reminders.load(this).size();
        Rows.add(this, rows, R.drawable.ic_timer, getString(R.string.mi5g_reminders),
                reminders == 0 ? getString(R.string.mi5g_reminders_none) : getResources().getQuantityString(R.plurals.mi5g_reminders_count, reminders, reminders),
                this::showReminders);
        Rows.add(this, rows, R.drawable.ic_wake, getString(R.string.mi5g_schedule),
                Schedule.load(this).empty() ? getString(R.string.mi5g_schedule_empty) : onOff(Schedule.on(this)), () -> {
                    if (Schedule.load(this).empty()) {
                        android.widget.Toast.makeText(this, R.string.mi5g_set_on_phone, android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    Schedule.setOn(this, !Schedule.on(this));
                    build();
                });
        // 2.4: Keep Alive (on by default on Xiaomi) and Keep screen awake, ideas from tvOS-Launcher (Apache-2.0).
        Rows.add(this, rows, R.drawable.ic_home_settings, getString(R.string.mi5g_keep_alive),
                KeepAlive.homeLost(this) ? getString(R.string.mi5g_keep_alive_lost) : onOff(LauncherSwitches.keepAlive(this)),
                () -> { LauncherSwitches.setKeepAlive(this, !LauncherSwitches.keepAlive(this)); KeepAlive.onEvent(this); build(); });
        Rows.add(this, rows, R.drawable.ic_timer, getString(R.string.mi5g_caffeine), getString(Caffeine.labelRes(Caffeine.choice(this))),
                () -> { Caffeine.set(this, Caffeine.next(Caffeine.choice(this))); build(); });
        Rows.add(this, rows, R.drawable.ic_input, getString(R.string.mi5g_cec_switch),
                onOff(LauncherSwitches.cecSwitch(this)),
                () -> { LauncherSwitches.setCecSwitch(this, !LauncherSwitches.cecSwitch(this)); build(); });
        Rows.add(this, rows, R.drawable.ic_input, getString(R.string.mi5g_inputs_edit), null,
                () -> startActivity(new Intent(this, InputsEditActivity.class)));
        Rows.add(this, rows, R.drawable.ic_timer, getString(R.string.mi5g_quiet_hours), quietLabel(), () -> {
            QuietHours.set(this, QuietHours.next(QuietHours.get(this)));
            build();
        });
        Rows.add(this, rows, R.drawable.ic_wifi, getString(R.string.mi5g_weather_place), weatherLabel(),
                () -> startActivity(new Intent(this, WeatherPlaceActivity.class)));
        Rows.add(this, rows, R.drawable.ic_speed, getString(R.string.mi5g_effects), effectsLabel(), () -> {
            EffectsMode.set(this, EffectsMode.next(EffectsMode.get(this)));
            setResult(RESULT_OK);
            build();
        });
        Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_hidden_apps), null,
                () -> startActivity(new Intent(this, HiddenAppsActivity.class)));
        Rows.add(this, rows, R.drawable.ic_keys, getString(R.string.mi5g_mapper), null,
                () -> startActivity(new Intent(this, ButtonMapperActivity.class)));
        Rows.add(this, rows, R.drawable.ic_wake, getString(R.string.mi5g_wake), wakeLabel(), this::pickWake);
        Rows.add(this, rows, R.drawable.ic_clock, getString(R.string.mi5g_back_clock),
                onOff(ClockActivity.backOpensClock(this)), () -> {
                    p.edit().putBoolean(ClockActivity.BACK_CLOCK, !ClockActivity.backOpensClock(this)).apply();
                    build();
                });
        Rows.add(this, rows, R.drawable.ic_clock, getString(R.string.mi5g_clock_24h),
                onOff(ClockActivity.use24h(this)), () -> {
                    p.edit().putBoolean(ClockActivity.CLOCK_24H, !ClockActivity.use24h(this)).apply();
                    build();
                });
        if (TileMenu.anyRemoved(this)) {
            Rows.add(this, rows, R.drawable.ic_apps, getString(R.string.mi5g_restore_tiles), null, () -> {
                TileMenu.restoreAll(this);
                setResult(RESULT_OK);
                build();
            });
        }
        rows.getChildAt(0).requestFocus();
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==61){boolean granted=grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
            getSharedPreferences(Config.SETTINGS_NAME,MODE_PRIVATE).edit().putBoolean(ContinueWatching.KEY,granted).apply();
            if(!granted)android.widget.Toast.makeText(this,R.string.mi5g_continue_permission,android.widget.Toast.LENGTH_LONG).show();build();}
    }

    private String quietLabel() {
        String q = QuietHours.get(this);
        return QuietHours.OFF.equals(q) ? getString(R.string.mi5g_off) : q.replace("-", " – ");
    }

    private String weatherLabel() {
        Weather.Place u = Weather.userPlace(this), ip = Weather.ipPlace(this);
        if (u != null) return u.name;
        if (ip != null) return ip.shown();
        return getString(R.string.mi5g_weather_auto_short);
    }

    private String effectsLabel() {
        String m = EffectsMode.get(this);
        if (EffectsMode.FULL.equals(m)) return getString(R.string.mi5g_effects_full);
        if (EffectsMode.LITE.equals(m)) return getString(R.string.mi5g_effects_lite);
        return getString(EffectsMode.lite(this) ? R.string.mi5g_effects_auto_lite : R.string.mi5g_effects_auto);
    }

    /** 2.4 M4: the reminders, OK turns one on or off; new ones come from the phone's arrange page. */
    private void showReminders() {
        List<Reminders.Item> items = Reminders.load(this);
        if (items.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.mi5g_set_on_phone, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout[] r = new LinearLayout[1];
        android.app.Dialog d = TileMenu.panel(this, getString(R.string.mi5g_reminders), r);
        for (int i = 0; i < items.size(); i++) {
            final int k = i;
            Reminders.Item it = items.get(i);
            Rows.add(this, r[0], R.drawable.ic_timer, Schedule.hhmm(it.minute) + "  " + it.text, onOff(it.on), () -> {
                List<Reminders.Item> now = Reminders.load(this);
                if (k < now.size()) {
                    Reminders.Item x = now.get(k);
                    now.set(k, new Reminders.Item(x.text, x.minute, x.days, !x.on));
                    Reminders.save(this, now);
                }
                d.dismiss();
                build();
            });
        }
        d.show();
    }

    private String onOff(boolean on) { return getString(on ? R.string.mi5g_on : R.string.mi5g_off); }

    private String wakeLabel() {
        String t = WakeTarget.get(this);
        if (t.startsWith("app:")) return label(t.substring(4));
        if (t.startsWith("input:")) return t.substring(t.lastIndexOf('/') + 1);
        return getString(R.string.mi5g_wake_home);
    }

    private String label(String pkg) {
        try {
            return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    private void pickWake() {
        AppPicker.show(this, getString(R.string.mi5g_wake), getString(R.string.mi5g_wake_home), pkg -> {
            WakeTarget.set(this, pkg == null ? "" : "app:" + pkg);
            build();
        });
    }

    /** Launchable TV apps, sorted by name. */
    static List<String[]> apps(Activity a) {
        List<String[]> out = new ArrayList<>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : a.getPackageManager().queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(a.getPackageName())) continue;
            out.add(new String[]{pkg, String.valueOf(ri.loadLabel(a.getPackageManager()))});
        }
        out.sort((x, y) -> x[1].compareToIgnoreCase(y[1]));
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rows.getChildCount() > 0) build();
    }

    interface Picked { void on(String pkg); }

    static final class AppPicker {
        static void show(Activity a, CharSequence title, CharSequence none, Picked cb) {
            LinearLayout[] r = new LinearLayout[1];
            android.app.Dialog d = TileMenu.panel(a, title, r);
            ScrollView sv = new ScrollView(a);
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            sv.addView(list);
            r[0].addView(sv, new LinearLayout.LayoutParams(-1, (int) (320 * a.getResources().getDisplayMetrics().density)));
            Rows.add(a, list, R.drawable.ic_home_settings, none, null, () -> { d.dismiss(); cb.on(null); });
            for (String[] app : apps(a)) {
                Rows.add(a, list, 0, app[1], null, () -> { d.dismiss(); cb.on(app[0]); });
            }
            d.show();
            View first = list.getChildAt(0);
            if (first != null) first.requestFocus();
        }
    }
}
