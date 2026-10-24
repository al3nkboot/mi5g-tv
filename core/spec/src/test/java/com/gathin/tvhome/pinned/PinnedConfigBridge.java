package com.gathin.tvhome.pinned;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tv.cleanyour.core.spec.upstream.UpstreamConfig;

// Runs upstream Config.load at the pinned submodule commit (v2.5.0, 9071ec8), the launcher the app
// bundles. Config.java, Festival.java, WallpaperRotation.java and RaceWeekend.java are copied from
// the third_party/cleanyourtv submodule into this package at build time (core/spec/build.gradle.kts). `prefs` plays the launcher's private
// launcher_settings file (the choices made on the TV); null starts from an empty one.
public final class PinnedConfigBridge {
    private PinnedConfigBridge() {}

    public static UpstreamConfig load(File dir, Map<String, Object> values, SharedPreferences prefs) {
        Config c = Config.load(new Context(dir, resources(values), prefs));
        List<Integer> keycodes = new ArrayList<>();
        for (int k : c.settingsKeycodes) keycodes.add(k);
        Festival f = c.festival;
        UpstreamConfig.V24 v24 = new UpstreamConfig.V24(c.fillDock, c.animations, c.advancedEffects, c.greetingName,
                c.reducedMotion, c.dockTitle, c.dockRow, c.reorderButton, c.showStats, c.appLibrary, c.skyIntensity,
                keycodes, c.settingsKeyPanel, f.name, f.until == null ? "" : f.until.toString(), f.greeting, f.accent);
        List<Integer> inputKeycodes = new ArrayList<>();
        for (int k : c.inputKeycodes) inputKeycodes.add(k);
        List<String> races = new ArrayList<>();
        for (RaceWeekend.Race r : c.raceCalendar) {
            StringBuilder b = new StringBuilder().append(r.round).append('|').append(r.name).append('|').append(r.place).append('|');
            for (int i = 0; i < r.sessions.size(); i++) {
                if (i > 0) b.append(';');
                b.append(r.sessions.get(i).name).append('@').append(r.sessions.get(i).start);
            }
            races.add(b.toString());
        }
        UpstreamConfig.V25 v25 = new UpstreamConfig.V25(new HashMap<>(c.launchActions), new HashMap<>(c.appLabels),
                new HashMap<>(c.appBanners), inputKeycodes, new ArrayList<>(c.appKeys), c.cinematicHome,
                c.wallpaperRotationMinutes, c.wallpaperDim, races);
        return new UpstreamConfig(c.dockApps, c.hiddenApps, c.liveTvPackage, c.liveTvAction, c.sourcePanelService,
                c.sourcePanelAction, c.inputAction, c.columns, c.rowsBelowDock, c.shamsiDate, c.moduleCards, c.vpnApp,
                c.showHomeSettingsShortcut, c.backgroundColor, c.wallpaper != null, c.overlayApplied, v24, v25);
    }

    static Resources resources(Map<String, Object> v) {
        Map<Integer, Object> m = new HashMap<>();
        m.put(R.array.dock_apps, v.get("dock_apps"));
        m.put(R.array.hidden_apps, v.get("hidden_apps"));
        m.put(R.array.settings_key_keycodes, v.get("settings_key_keycodes"));
        m.put(R.array.launch_actions, v.get("launch_actions"));
        m.put(R.array.app_labels, v.get("app_labels"));
        m.put(R.array.app_banners, v.get("app_banners"));
        m.put(R.array.input_key_keycodes, v.get("input_key_keycodes"));
        m.put(R.array.app_keys, v.get("app_keys"));
        m.put(R.string.live_tv_package, v.get("live_tv_package"));
        m.put(R.string.live_tv_action, v.get("live_tv_action"));
        m.put(R.string.source_panel_service, v.get("source_panel_service"));
        m.put(R.string.source_panel_action, v.get("source_panel_action"));
        m.put(R.string.input_action, v.get("input_action"));
        m.put(R.string.vpn_app, v.get("vpn_app"));
        m.put(R.string.greeting_name, v.get("greeting_name"));
        m.put(R.integer.columns, v.get("columns"));
        m.put(R.integer.rows_below_dock, v.get("rows_below_dock"));
        m.put(R.integer.sky_intensity, v.get("sky_intensity"));
        m.put(R.bool.shamsi_date, v.get("shamsi_date"));
        m.put(R.bool.module_cards, v.get("module_cards"));
        m.put(R.bool.show_home_settings_shortcut, v.get("show_home_settings_shortcut"));
        m.put(R.bool.fill_dock, v.get("fill_dock"));
        m.put(R.bool.animations, v.get("animations"));
        m.put(R.bool.advanced_effects, v.get("advanced_effects"));
        m.put(R.bool.reduced_motion, v.get("reduced_motion"));
        m.put(R.bool.dock_title, v.get("dock_title"));
        m.put(R.bool.dock_row, v.get("dock_row"));
        m.put(R.bool.reorder_button, v.get("reorder_button"));
        m.put(R.bool.show_stats, v.get("show_stats"));
        m.put(R.bool.app_library, v.get("app_library"));
        m.put(R.bool.settings_key_panel, v.get("settings_key_panel"));
        m.put(R.bool.cinematic_home, v.get("cinematic_home"));
        m.values().removeIf(x -> x == null);
        return new Resources(m);
    }
}
