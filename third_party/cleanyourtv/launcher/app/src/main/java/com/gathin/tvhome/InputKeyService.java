package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.HashMap;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.Map;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it, others (KTC and Kalley Realtek Google TV remotes) KEYCODE_TV_INPUT; on Google TV
 * the stock launcher handles both, so with the stock launcher disabled the button does nothing. Buttons listed in
 * input_key_keycodes (config.xml or a theme) open the input switcher the same way, and app_keys start an app (a
 * YouTube button that only the stock launcher answered). Every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private int[] inputKeycodes = new int[0];
    private Map<Integer, String> appKeys = Collections.emptyMap();
    // Mi5G 2.0 button mapper: per-key press patterns from ButtonMap; reloaded on every connect and save.
    static InputKeyService instance;
    private Map<Integer, Map<String, String>> mapped = Collections.emptyMap();
    private final Map<Integer, PressPattern> patterns = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // From config.xml or a theme's config.json; theme.sh rebinds this service after a push.
        Config config = Config.load(this);
        inputKeycodes = config.inputKeycodes;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        instance = this;
        reloadMap();
        // Item 17: this service stays bound across app switches, so it is where the wake is heard.
        if (screenOn == null) {
            screenOn = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context c, Intent i) { WakeTarget.onScreenOn(c); }
            };
            registerReceiver(screenOn, new android.content.IntentFilter(Intent.ACTION_SCREEN_ON));
        }
    }

    private android.content.BroadcastReceiver screenOn;

    void reloadMap() { mapped = ButtonMap.load(this); patterns.clear(); }

    static void reloadIfRunning() { if (instance != null) instance.reloadMap(); }

    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) instance = null;
        if (screenOn != null) { unregisterReceiver(screenOn); screenOn = null; }
        return super.onUnbind(intent);
    }

    /** Runs a mapper action; global actions need this service, the rest are plain intents. */
    void run(String action) {
        if (action == null) return;
        switch (action) {
            case "home": performGlobalAction(GLOBAL_ACTION_HOME); break;
            case "back": performGlobalAction(GLOBAL_ACTION_BACK); break;
            case "recents": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
            case "control_center": ControlCenterActivity.open(this); break;
            case "settings":
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ignored) {
                }
                break;
            case "inputs":
                try {
                    startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ignored) {
                }
                break;
            default:
                if (action.startsWith("app:")) RemoteKeys.launch(this, action.substring(4));
        }
    }

    private boolean onMappedKey(KeyEvent event, Map<String, String> byPattern) {
        int code = event.getKeyCode();
        long now = SystemClock.uptimeMillis();
        // Only single press mapped: fire on release without waiting for a possible second press.
        boolean onlySingle = byPattern.size() == 1 && byPattern.containsKey("1");
        PressPattern p = patterns.computeIfAbsent(code, c -> new PressPattern());
        int r;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            r = onlySingle ? PressPattern.NONE : p.down(now, event.getRepeatCount());
        } else {
            if (event.isCanceled()) return true;
            r = onlySingle ? PressPattern.SINGLE : p.up(now);
            long at = p.deadline();
            if (r == PressPattern.NONE && at > 0) {
                handler.postAtTime(() -> fire(byPattern, p.timeout(SystemClock.uptimeMillis())), code, at);
            }
        }
        fire(byPattern, r);
        return true;
    }

    private void fire(Map<String, String> byPattern, int pattern) {
        if (pattern != PressPattern.NONE) run(byPattern.get(PressPattern.suffix(pattern)));
    }

    private boolean isInputKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT) return true;
        for (int code : inputKeycodes) if (keyCode == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Map<String, String> byPattern = mapped.get(event.getKeyCode());
        if (byPattern != null && !byPattern.isEmpty()) return onMappedKey(event, byPattern);
        String app = appKeys.get(event.getKeyCode());
        if (app == null && !isInputKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            if (app != null) {
                RemoteKeys.launch(this, app);
            } else {
                try {
                    startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ignored) {
                }
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
