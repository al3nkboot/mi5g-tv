package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mi5G 2.0.1 wallpapers: Bing's photo of the day, three built-in Mi5G backdrops (drawn, no image
 * files), and photos or videos sent from the phone (ArrangeServer). Images live in the theme's
 * wallpapers/ folder and are picked through Config's wallpaper_choice; a video is kept beside them
 * and played by WallpaperPlayer. No Apple aerial videos. Wallpaper sources after Aerial's
 * Wallpapers.kt (pengchujin/aerial-tv, MIT); the code is Mi5G's own.
 */
final class Mi5gWallpapers {
    static final String VIDEO_KEY = "mi5g_video_wallpaper";
    static final String BING_KEY = "mi5g_bing_at";
    static final String BING_FILE = "bing.jpg";
    static final List<String> BUILTINS = Arrays.asList("mi5g-teal.png", "mi5g-gold.png", "mi5g-night.png");
    static final int MAX_UPLOADS = 12;
    private static final long BING_EVERY_MS = 12 * 3_600_000L;

    private Mi5gWallpapers() {}

    static File dir(Context ctx) {
        File d = Config.dir(ctx);
        return d == null ? null : new File(d, "wallpapers");
    }

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE); }

    /** jpg, png, webp or mp4 told from the first bytes; null for anything else. */
    static String kindOf(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8) return "jpg";
        if ((b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        if (b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "mp4";
        return null;
    }

    static boolean safeName(String n) { return n != null && n.matches("[A-Za-z0-9._-]{1,64}") && !n.startsWith("."); }

    /** Saves a photo or video from the phone and picks it. The file name, or null when refused. */
    static String saveUpload(Context ctx, byte[] data) {
        String kind = kindOf(data);
        File d = dir(ctx);
        if (kind == null || d == null) return null;
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        prune(d);
        String name = "phone-" + System.currentTimeMillis() + "." + kind;
        try (FileOutputStream out = new FileOutputStream(new File(d, name))) {
            out.write(data);
        } catch (IOException e) {
            return null;
        }
        return choose(ctx, name) ? name : null;
    }

    /** Keeps at most MAX_UPLOADS - 1 earlier phone uploads, oldest out first. */
    private static void prune(File d) {
        File[] f = d.listFiles((x, n) -> n.startsWith("phone-"));
        if (f == null || f.length < MAX_UPLOADS) return;
        Arrays.sort(f, (a, b) -> a.getName().compareTo(b.getName()));
        for (int i = 0; i <= f.length - MAX_UPLOADS; i++) //noinspection ResultOfMethodCallIgnored
            f[i].delete();
    }

    /** "none", "bing", a built-in or an uploaded file name. */
    static boolean choose(Context ctx, String name) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if ("none".equals(name)) {
            e.putString(Config.WALLPAPER_CHOICE_KEY, "none").remove(VIDEO_KEY).remove(BING_KEY).apply();
            return true;
        }
        File d = dir(ctx);
        if (d == null) return false;
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        if ("bing".equals(name)) {
            e.putString(Config.WALLPAPER_CHOICE_KEY, BING_FILE).remove(VIDEO_KEY).putLong(BING_KEY, 0).apply();
            new Thread(() -> { if (refreshBing(ctx, true)) new android.os.Handler(android.os.Looper.getMainLooper()).post(HomeLayout::changed); }, "Mi5gBing").start();
            return true;
        }
        if (!safeName(name)) return false;
        if (BUILTINS.contains(name)) {
            File f = new File(d, name);
            if (!f.isFile() && !drawBuiltin(name, f, 1920, 1080)) return false;
            e.putString(Config.WALLPAPER_CHOICE_KEY, name).remove(VIDEO_KEY).remove(BING_KEY).apply();
            return true;
        }
        File f = new File(d, name);
        if (!f.isFile()) return false;
        if (name.endsWith(".mp4")) e.putString(VIDEO_KEY, name).putString(Config.WALLPAPER_CHOICE_KEY, "none");
        else e.putString(Config.WALLPAPER_CHOICE_KEY, name).remove(VIDEO_KEY);
        e.remove(BING_KEY).apply();
        return true;
    }

    /** The chosen video wallpaper, or null. */
    static File video(Context ctx) {
        String n = prefs(ctx).getString(VIDEO_KEY, null);
        File d = dir(ctx);
        if (n == null || d == null || !safeName(n)) return null;
        File f = new File(d, n);
        return f.isFile() ? f : null;
    }

    static JSONObject state(Context ctx) throws Exception {
        SharedPreferences p = prefs(ctx);
        String choice = p.getString(Config.WALLPAPER_CHOICE_KEY, "");
        String video = p.getString(VIDEO_KEY, null);
        JSONArray uploads = new JSONArray();
        File d = dir(ctx);
        File[] f = d == null ? null : d.listFiles((x, n) -> n.startsWith("phone-"));
        if (f != null) {
            Arrays.sort(f, (a, b) -> b.getName().compareTo(a.getName()));
            for (File x : f) uploads.put(x.getName());
        }
        JSONArray builtins = new JSONArray();
        for (String b : BUILTINS) builtins.put(b);
        return new JSONObject().put("choice", video != null ? video : BING_FILE.equals(choice) ? "bing" : choice)
                .put("builtins", builtins).put("uploads", uploads);
    }

    /** Three Mi5G backdrops: teal, gold and night, drawn as gradients. */
    static Bitmap builtin(String name, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int a, z, glow;
        switch (name) {
            case "mi5g-gold.png": a = 0xFF2A1F08; z = 0xFF0B1F1E; glow = 0x66D4AF37; break;
            case "mi5g-night.png": a = 0xFF05080C; z = 0xFF0B1F1E; glow = 0x3322B8A8; break;
            default: a = 0xFF0E4D48; z = 0xFF061514; glow = 0x55D4AF37; break;
        }
        p.setShader(new LinearGradient(0, 0, w, h, a, z, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.82f, h * 0.12f, h * 0.9f, glow, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    private static boolean drawBuiltin(String name, File f, int w, int h) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            return builtin(name, w, h).compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (IOException e) {
            return false;
        }
    }

    /** The image URL in Bing's HPImageArchive answer, only a path on www.bing.com; else null. */
    static String bingUrl(String json) {
        try {
            JSONArray images = new JSONObject(json).getJSONArray("images");
            String u = images.getJSONObject(0).getString("url");
            if (!u.startsWith("/th?id=") || u.contains("//") || u.contains("@")) return null;
            return "https://www.bing.com" + u;
        } catch (Exception e) {
            return null;
        }
    }

    /** Refreshes the Bing photo off the main thread when it is chosen and due; runs done on the main thread if it changed. */
    static void refreshBingAsync(Context ctx, Runnable done) {
        SharedPreferences p = prefs(ctx);
        if (!BING_FILE.equals(p.getString(Config.WALLPAPER_CHOICE_KEY, ""))) return;
        if (System.currentTimeMillis() - p.getLong(BING_KEY, 0) < BING_EVERY_MS) return;
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            if (refreshBing(app, false)) new android.os.Handler(android.os.Looper.getMainLooper()).post(done);
        }, "Mi5gBing").start();
    }

    /** Downloads today's Bing photo when Bing is chosen and the copy is older than 12 h (or forced). */
    static boolean refreshBing(Context ctx, boolean force) {
        SharedPreferences p = prefs(ctx);
        if (!BING_FILE.equals(p.getString(Config.WALLPAPER_CHOICE_KEY, ""))) return false;
        long at = p.getLong(BING_KEY, 0);
        if (!force && System.currentTimeMillis() - at < BING_EVERY_MS) return false;
        File d = dir(ctx);
        if (d == null) return false;
        try {
            String json = new String(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US", 64 * 1024), StandardCharsets.UTF_8);
            String url = bingUrl(json);
            if (url == null) return false;
            byte[] img = get(url, 12 * 1024 * 1024);
            if (!"jpg".equals(kindOf(img))) return false;
            //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
            File tmp = new File(d, BING_FILE + ".part");
            try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(img); }
            if (!tmp.renameTo(new File(d, BING_FILE))) return false;
            p.edit().putLong(BING_KEY, System.currentTimeMillis()).apply();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setInstanceFollowRedirects(false);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > max) throw new IOException("too large");
            }
            return b.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
