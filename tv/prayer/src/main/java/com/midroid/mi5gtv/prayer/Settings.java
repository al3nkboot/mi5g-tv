package com.midroid.mi5gtv.prayer;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What the phone app sends over ADB as files/settings.json (pushed, then a RELOAD broadcast).
 * Every field is optional; anything missing or invalid keeps the default.
 */
final class Settings {
    static final String FILE = "settings.json";
    static final int MODE_OFF = 0, MODE_BANNER = 1, MODE_ADHAN = 2;

    String placeId = "muscat", cityAr = "مسقط", zone = "Asia/Muscat", method = "OMAN", highLat = "NONE";
    double lat = 23.58413, lon = 58.40778;
    boolean hanafi;
    int[] adjust = new int[Engine.COUNT];
    int[] modes = {MODE_ADHAN, MODE_OFF, MODE_ADHAN, MODE_ADHAN, MODE_ADHAN, MODE_ADHAN};
    /** Forced adhan: unmutes, raises the volume to forcedVolume % and takes audio focus over any app. */
    boolean forced = true;
    int forcedVolume = 70;
    int preMinutes = 10;
    int hijriAdjust;
    boolean overlay;
    String overlayCorner = "top_end";
    int overlaySize = 100, overlayOpacity = 85;
    boolean kahf = true;
    int kahfHour = 10;

    static Settings parse(String json) {
        Settings s = new Settings();
        if (json == null || json.trim().isEmpty()) return s;
        try {
            JSONObject o = new JSONObject(json);
            JSONObject c = o.optJSONObject("city");
            if (c != null) {
                double la = c.optDouble("lat", Double.NaN), lo = c.optDouble("lon", Double.NaN);
                String z = c.optString("zone", "");
                if (!Double.isNaN(la) && !Double.isNaN(lo) && Math.abs(la) <= 90 && Math.abs(lo) <= 180 && validZone(z)) {
                    s.lat = la; s.lon = lo; s.zone = z;
                    s.placeId = c.optString("id", "");
                    s.cityAr = c.optString("ar", s.placeId);
                }
            }
            String m = o.optString("method", s.method);
            if (m.matches("OMAN|UMM_AL_QURA|MWL|DUBAI|QATAR|KUWAIT|EGYPTIAN|KARACHI|ISNA")) s.method = m;
            String h = o.optString("high_lat", s.highLat);
            if (h.matches("NONE|MIDDLE_OF_NIGHT|SEVENTH_OF_NIGHT|TWILIGHT_ANGLE")) s.highLat = h;
            s.hanafi = o.optBoolean("hanafi", false);
            JSONArray a = o.optJSONArray("adjust");
            if (a != null && a.length() == Engine.COUNT) for (int i = 0; i < Engine.COUNT; i++) s.adjust[i] = clamp(a.optInt(i), -30, 30);
            JSONArray md = o.optJSONArray("modes");
            if (md != null && md.length() == Engine.COUNT) for (int i = 0; i < Engine.COUNT; i++) s.modes[i] = clamp(md.optInt(i), 0, 2);
            s.forced = o.optBoolean("forced_adhan", s.forced);
            s.forcedVolume = clamp(o.optInt("forced_volume", s.forcedVolume), 10, 100);
            s.preMinutes = clamp(o.optInt("pre_minutes", s.preMinutes), 0, 60);
            s.hijriAdjust = clamp(o.optInt("hijri_adjust", 0), -2, 2);
            JSONObject ov = o.optJSONObject("overlay");
            if (ov != null) {
                s.overlay = ov.optBoolean("on", false);
                String corner = ov.optString("corner", s.overlayCorner);
                if (corner.matches("top_start|top_end|bottom_start|bottom_end")) s.overlayCorner = corner;
                s.overlaySize = clamp(ov.optInt("size", 100), 60, 160);
                s.overlayOpacity = clamp(ov.optInt("opacity", 85), 30, 100);
            }
            JSONObject k = o.optJSONObject("kahf");
            if (k != null) {
                s.kahf = k.optBoolean("on", true);
                s.kahfHour = clamp(k.optInt("hour", 10), 0, 23);
            }
        } catch (Exception ignored) {
            return new Settings();
        }
        return s;
    }

    private static boolean validZone(String z) {
        try { java.time.ZoneId.of(z); return true; } catch (Exception e) { return false; }
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static Settings load(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, FILE);
        if (f == null || !f.isFile() || f.length() > 64 * 1024) return new Settings();
        try {
            return parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new Settings();
        }
    }
}
