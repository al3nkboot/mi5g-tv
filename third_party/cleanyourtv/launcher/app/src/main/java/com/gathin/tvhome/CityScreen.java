package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * Mi5G 2.0.1: the Arabic city screensaver. Clock, Hijri and Gregorian date, the next prayer as the Mi5G prayer
 * module reports it (its own names and times; nothing is computed or written here), and the weather for the
 * module's city from Open-Meteo. Mi5G teal and gold on a dark glow, Cairo font, right to left; it drifts every
 * minute against burn-in. Idea from Aerial TV (MIT, Copyright (c) 2026 pengchujin), with its own look and code.
 */
final class CityScreen {

    static final Uri PRAYER_TODAY = Uri.parse("content://com.midroid.mi5gtv.prayer.times/today");
    private static final String WEATHER_KEY = "mi5g_city_weather", WEATHER_AT = "mi5g_city_weather_at";
    private static final long WEATHER_EVERY_MS = 30 * 60_000L;

    /** What the screen shows besides the clock; empty strings are hidden. */
    static final class Data {
        String city = "", prayer = "", weather = "";
        double lat = Double.NaN, lon = Double.NaN;
    }

    final View root;
    final LinearLayout block;
    final TextView hijri, gregorian, prayer, weather, city;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Context ctx;
    private boolean running;

    CityScreen(Context ctx) {
        this.ctx = ctx;
        float d = ctx.getResources().getDisplayMetrics().density;
        FrameLayout frame = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF021A1A, 0xFF06302F, 0xFF020C0C});
        frame.setBackground(bg);
        frame.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        Typeface cairo = ctx.getResources().getFont(R.font.cairo);
        block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        block.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextClock clock = new TextClock(ctx);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextLocale(new Locale("ar"));
        style(clock, cairo, 112, 0xFFFFFFFF);
        block.addView(clock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        city = add(cairo, 26, 0xFFD4AF37);
        hijri = add(cairo, 28, 0xFFE8D9A8);
        gregorian = add(cairo, 22, 0xCCFFFFFF);
        prayer = add(cairo, 26, 0xFF7FE0D6);
        weather = add(cairo, 22, 0xCCFFFFFF);
        GradientDrawable line = new GradientDrawable();
        line.setColor(0xFFD4AF37);
        View rule = new View(ctx);
        rule.setBackground(line);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams((int) (180 * d), (int) (2 * d));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.bottomMargin = (int) (8 * d);
        block.addView(rule, 1, rp);
        frame.addView(block, new FrameLayout.LayoutParams((int) (620 * d), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root = frame;
        setDates(new Date());
    }

    private TextView add(Typeface font, int sp, int color) {
        TextView t = new TextView(ctx);
        style(t, font, sp, color);
        block.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private static void style(TextView t, Typeface font, int sp, int color) {
        t.setTypeface(font);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
    }

    void setDates(Date now) {
        hijri.setText(hijriDate(now));
        gregorian.setText(gregorianDate(now));
    }

    void bind(Data data) {
        show(city, data.city);
        show(prayer, data.prayer.isEmpty() ? "" : ctx.getString(R.string.mi5g_city_next_prayer, data.prayer));
        show(weather, data.weather);
    }

    private static void show(TextView t, String s) {
        t.setText(s);
        t.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Starts the minute tick: dates, prayer, weather, and a small drift. */
    void start() {
        running = true;
        tick.run();
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            setDates(new Date());
            refreshAsync();
            View p = (View) block.getParent();
            if (p != null && p.getWidth() > 0) {
                float dx = (p.getWidth() - block.getWidth()) * 0.25f, dy = (p.getHeight() - block.getHeight()) * 0.25f;
                block.animate().translationX((random.nextFloat() * 2 - 1) * Math.max(0, dx))
                        .translationY((random.nextFloat() * 2 - 1) * Math.max(0, dy)).setDuration(1500).start();
            }
            handler.postDelayed(this, 60_000L);
        }
    };

    private void refreshAsync() {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Data data = load(app);
            handler.post(() -> { if (running) bind(data); });
        }, "Mi5gCity").start();
    }

    /** Reads the prayer module and the cached (or fresh) weather. Off the main thread. */
    static Data load(Context ctx) {
        Data data = new Data();
        try (Cursor c = ctx.getContentResolver().query(PRAYER_TODAY, null, null, null, null)) {
            if (c != null) readPrayer(c, data);
        } catch (Exception ignored) {
            // no prayer module, or an older one: the line stays hidden
        }
        data.weather = weather(ctx, data.lat, data.lon);
        return data;
    }

    static void readPrayer(Cursor c, Data data) {
        int name = c.getColumnIndex("name"), time = c.getColumnIndex("time"), next = c.getColumnIndex("is_next");
        int city = c.getColumnIndex("city"), lat = c.getColumnIndex("lat"), lon = c.getColumnIndex("lon");
        while (c.moveToNext()) {
            if (city >= 0 && data.city.isEmpty() && !c.isNull(city)) data.city = HomeLayout.cleanName(c.getString(city));
            if (lat >= 0 && lon >= 0 && Double.isNaN(data.lat) && !c.isNull(lat) && !c.isNull(lon)) {
                data.lat = c.getDouble(lat);
                data.lon = c.getDouble(lon);
            }
            if (next >= 0 && c.getInt(next) == 1 && name >= 0 && time >= 0) {
                data.prayer = HomeLayout.cleanName(c.getString(name)) + "  " + HomeLayout.cleanName(c.getString(time));
            }
        }
    }

    private static String weather(Context ctx, double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) return "";
        SharedPreferences p = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String cached = p.getString(WEATHER_KEY, "");
        if (System.currentTimeMillis() - p.getLong(WEATHER_AT, 0) < WEATHER_EVERY_MS) return cached;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code", lat, lon);
            String s = parseWeather(get(url));
            if (s.isEmpty()) return cached;
            p.edit().putString(WEATHER_KEY, s).putLong(WEATHER_AT, System.currentTimeMillis()).apply();
            return s;
        } catch (Exception e) {
            return cached;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0 && out.size() < 64 * 1024) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    /** "٢٨° · صحو" from an Open-Meteo answer, or "" when it isn't one. */
    static String parseWeather(String json) {
        try {
            JSONObject cur = new JSONObject(json).getJSONObject("current");
            double t = cur.getDouble("temperature_2m");
            int code = cur.optInt("weather_code", -1);
            String temp = String.format(new Locale("ar"), "%d°", Math.round(t));
            String sky = sky(code);
            return sky.isEmpty() ? temp : temp + " \u00B7 " + sky;
        } catch (Exception e) {
            return "";
        }
    }

    /** WMO weather code to a short Arabic word. */
    static String sky(int code) {
        if (code == 0) return "صحو";
        if (code >= 1 && code <= 3) return "غائم جزئياً";
        if (code == 45 || code == 48) return "ضباب";
        if (code >= 51 && code <= 67) return "مطر";
        if (code >= 71 && code <= 77) return "ثلج";
        if (code >= 80 && code <= 82) return "زخات مطر";
        if (code >= 95) return "عواصف رعدية";
        return "";
    }

    /** The Umm al-Qura Hijri date in Arabic, e.g. "١٢ جمادى الأولى ١٤٤٨ هـ". */
    static String hijriDate(Date d) {
        try {
            android.icu.util.ULocale ar = new android.icu.util.ULocale("ar@calendar=islamic-umalqura");
            android.icu.util.Calendar cal = android.icu.util.Calendar.getInstance(ar);
            android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat("d MMMM y", ar);
            f.setCalendar(cal);
            return f.format(d) + " هـ";
        } catch (Throwable e) {
            return "";
        }
    }

    /** The Gregorian date in Arabic, e.g. "الثلاثاء، ٣ نوفمبر ٢٠٢٦". */
    static String gregorianDate(Date d) {
        try {
            return new android.icu.text.SimpleDateFormat("EEEE، d MMMM y", new Locale("ar")).format(d);
        } catch (Throwable e) {
            return new java.text.SimpleDateFormat("EEEE، d MMMM y", new Locale("ar")).format(d);
        }
    }
}
