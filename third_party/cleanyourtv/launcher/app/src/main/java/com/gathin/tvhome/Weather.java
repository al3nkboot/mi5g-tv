package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Mi5G 2.0.2: weather for the city screensaver, with a backup provider and a location that needs no setup.
 *
 * Location, first that is there: the city the user set (TV settings or the phone page), the prayer
 * module's city, then an approximate location from the TV's public IP address (ipwho.is, then ipapi.co,
 * then get.geojs.io), looked up at most once a week and shown as "قرب مسقط". The user can remove it.
 *
 * Weather: Open-Meteo first; when it fails, MET Norway's locationforecast (api.met.no), which asks
 * every client to send a User-Agent that names the app and a contact (see their terms of service).
 * Mi5G's own code.
 */
final class Weather {
    static final String USER_AGENT = "Mi5G-TV-launcher/2.7-mi5g (+https://github.com/al3nkboot/mi5g-tv)";
    static final long IP_EVERY_MS = 7L * 24 * 60 * 60_000L;

    static final String K_USER_NAME = "mi5g_wx_user_name", K_USER_LAT = "mi5g_wx_user_lat", K_USER_LON = "mi5g_wx_user_lon";
    static final String K_IP_NAME = "mi5g_wx_ip_name", K_IP_LAT = "mi5g_wx_ip_lat", K_IP_LON = "mi5g_wx_ip_lon", K_IP_AT = "mi5g_wx_ip_at";
    /** The user removed the approximate location: no IP lookups until they set "automatic" again. */
    static final String K_IP_OFF = "mi5g_wx_ip_off";

    /** One HTTP GET; the tests answer from memory. */
    interface Fetcher { String get(String url, String userAgent) throws Exception; }

    static final Fetcher HTTP = Weather::httpGet;

    /** Where the weather is for. */
    static final class Place {
        static final int USER = 1, PRAYER = 2, IP = 3;
        final String name;
        final double lat, lon;
        final int source;
        Place(String name, double lat, double lon, int source) { this.name = name == null ? "" : name; this.lat = lat; this.lon = lon; this.source = source; }
        /** What the screensaver shows as the city: "قرب مسقط" for an approximate place. */
        String shown() { return source == IP && !name.isEmpty() ? "قرب " + name : name; }
    }

    private Weather() {}

    static boolean valid(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 && !(lat == 0 && lon == 0);
    }

    /** The user's own place wins, then the prayer module's city, then the IP place. Null when none. */
    static Place choose(Place user, Place prayer, Place ip) {
        if (user != null && valid(user.lat, user.lon)) return user;
        if (prayer != null && valid(prayer.lat, prayer.lon)) return prayer;
        if (ip != null && valid(ip.lat, ip.lon)) return ip;
        return null;
    }

    // ---- weather providers -----------------------------------------------------------------------

    static String openMeteoUrl(double lat, double lon) {
        return String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code", lat, lon);
    }

    static String metNoUrl(double lat, double lon) {
        // MET Norway asks for at most four decimals.
        return String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.3f&lon=%.3f", lat, lon);
    }

    /** "٢٩° · صحو" from Open-Meteo, else from MET Norway, else "". */
    static String fetch(Fetcher f, double lat, double lon) {
        try {
            String s = CityScreen.parseWeather(f.get(openMeteoUrl(lat, lon), USER_AGENT));
            if (!s.isEmpty()) return s;
        } catch (Exception ignored) { }
        try {
            return parseMetNo(f.get(metNoUrl(lat, lon), USER_AGENT));
        } catch (Exception ignored) {
            return "";
        }
    }

    static String parseMetNo(String json) {
        try {
            JSONObject first = new JSONObject(json).getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0);
            JSONObject data = first.getJSONObject("data");
            double t = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            String symbol = "";
            for (String k : new String[]{"next_1_hours", "next_6_hours", "next_12_hours"}) {
                JSONObject n = data.optJSONObject(k);
                if (n != null && n.optJSONObject("summary") != null) { symbol = n.getJSONObject("summary").optString("symbol_code"); break; }
            }
            String temp = String.format(new Locale("ar"), "%d°", Math.round(t));
            String sky = metSky(symbol);
            return sky.isEmpty() ? temp : temp + " \u00B7 " + sky;
        } catch (Exception e) {
            return "";
        }
    }

    /** MET Norway symbol codes ("clearsky_day", "lightrain"...) to the same Arabic words as Open-Meteo's. */
    static String metSky(String symbol) {
        String s = symbol == null ? "" : symbol.replaceAll("_(day|night|polartwilight)$", "");
        if (s.isEmpty()) return "";
        if (s.contains("thunder")) return "عواصف رعدية";
        if (s.contains("snow") || s.contains("sleet")) return "ثلج";
        if (s.contains("showers")) return "زخات مطر";
        if (s.contains("rain")) return "مطر";
        if (s.equals("fog")) return "ضباب";
        if (s.equals("clearsky")) return "صحو";
        if (s.equals("fair") || s.equals("partlycloudy") || s.equals("cloudy")) return "غائم جزئياً";
        return "";
    }

    // ---- approximate location from the IP address --------------------------------------------------

    static final String[] IP_URLS = {"https://ipwho.is/", "https://ipapi.co/json/", "https://get.geojs.io/v1/ip/geo.json"};

    /** The first provider that answers with a place; null when none does. */
    static Place ipLookup(Fetcher f) {
        for (String url : IP_URLS) {
            try {
                Place p = parseIp(f.get(url, USER_AGENT));
                if (p != null) return p;
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** ipwho.is, ipapi.co and geojs answers all carry city, latitude and longitude (geojs as strings). */
    static Place parseIp(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (o.has("success") && !o.optBoolean("success")) return null;
            if (o.optBoolean("error")) return null;
            double lat = Double.parseDouble(o.optString("latitude", "NaN"));
            double lon = Double.parseDouble(o.optString("longitude", "NaN"));
            if (!valid(lat, lon)) return null;
            return new Place(HomeLayout.cleanName(o.optString("city", "")), lat, lon, Place.IP);
        } catch (Exception e) {
            return null;
        }
    }

    /** The city's Arabic name from Open-Meteo's geocoding (e.g. "Muscat" to "مسقط"), or the name as it was. */
    static String arabicName(Fetcher f, String name) {
        if (name == null || name.isEmpty()) return "";
        try {
            JSONArray r = new JSONObject(f.get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=ar&name="
                    + URLEncoder.encode(name, "UTF-8"), USER_AGENT)).optJSONArray("results");
            String ar = r == null || r.length() == 0 ? "" : HomeLayout.cleanName(r.getJSONObject(0).optString("name"));
            return ar.isEmpty() ? name : ar;
        } catch (Exception e) {
            return name;
        }
    }

    /** A city typed by the user to a place (Open-Meteo geocoding, Arabic names); null when not found. */
    static Place geocode(Fetcher f, String typed) {
        String q = HomeLayout.cleanName(typed);
        if (q.isEmpty()) return null;
        try {
            JSONArray r = new JSONObject(f.get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=ar&name="
                    + URLEncoder.encode(q, "UTF-8"), USER_AGENT)).optJSONArray("results");
            if (r == null || r.length() == 0) return null;
            JSONObject o = r.getJSONObject(0);
            double lat = o.optDouble("latitude", Double.NaN), lon = o.optDouble("longitude", Double.NaN);
            if (!valid(lat, lon)) return null;
            String name = HomeLayout.cleanName(o.optString("name"));
            return new Place(name.isEmpty() ? q : name, lat, lon, Place.USER);
        } catch (Exception e) {
            return null;
        }
    }

    /** Whether the weekly IP lookup is due. */
    static boolean ipDue(long now, long lastAt) { return lastAt <= 0 || now - lastAt >= IP_EVERY_MS || now < lastAt; }

    // ---- stored places ----------------------------------------------------------------------------

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE); }

    static Place userPlace(Context ctx) { return stored(prefs(ctx), K_USER_NAME, K_USER_LAT, K_USER_LON, Place.USER); }

    static Place ipPlace(Context ctx) {
        SharedPreferences p = prefs(ctx);
        return p.getBoolean(K_IP_OFF, false) ? null : stored(p, K_IP_NAME, K_IP_LAT, K_IP_LON, Place.IP);
    }

    private static Place stored(SharedPreferences p, String n, String la, String lo, int source) {
        if (!p.contains(la) || !p.contains(lo)) return null;
        return new Place(p.getString(n, ""), Double.longBitsToDouble(p.getLong(la, 0)), Double.longBitsToDouble(p.getLong(lo, 0)), source);
    }

    static void setUser(Context ctx, Place pl) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (pl == null) e.remove(K_USER_NAME).remove(K_USER_LAT).remove(K_USER_LON);
        else e.putString(K_USER_NAME, pl.name).putLong(K_USER_LAT, Double.doubleToLongBits(pl.lat)).putLong(K_USER_LON, Double.doubleToLongBits(pl.lon));
        e.remove(CityScreen.WEATHER_AT).apply();
    }

    static void saveIp(Context ctx, Place pl, long now) {
        SharedPreferences.Editor e = prefs(ctx).edit().putLong(K_IP_AT, now);
        if (pl != null) e.putString(K_IP_NAME, pl.name).putLong(K_IP_LAT, Double.doubleToLongBits(pl.lat)).putLong(K_IP_LON, Double.doubleToLongBits(pl.lon));
        e.apply();
    }

    /** Removes the approximate place and stops looking it up; automatic=true allows it again. */
    static void setIpAllowed(Context ctx, boolean allowed) {
        SharedPreferences.Editor e = prefs(ctx).edit().putBoolean(K_IP_OFF, !allowed).remove(CityScreen.WEATHER_AT);
        if (!allowed) e.remove(K_IP_NAME).remove(K_IP_LAT).remove(K_IP_LON).remove(K_IP_AT);
        e.apply();
    }

    static boolean ipAllowed(Context ctx) { return !prefs(ctx).getBoolean(K_IP_OFF, false); }

    /**
     * The place to use now, looking the IP place up when nothing else is set and the weekly lookup is due.
     * Off the main thread.
     */
    static Place resolve(Context ctx, Fetcher f, Place prayer, long now) {
        Place user = userPlace(ctx);
        Place ip = ipPlace(ctx);
        Place chosen = choose(user, prayer, ip);
        if (chosen != null && chosen.source != Place.IP) return chosen;
        SharedPreferences p = prefs(ctx);
        if (ipAllowed(ctx) && ipDue(now, p.getLong(K_IP_AT, 0))) {
            Place found = ipLookup(f);
            if (found != null) found = new Place(arabicName(f, found.name), found.lat, found.lon, Place.IP);
            saveIp(ctx, found, now);
            if (found != null) return found;
        }
        return chosen;
    }

    static String httpGet(String url, String userAgent) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", userAgent);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0 && out.size() < 256 * 1024) out.write(b, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
