package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mi5G 2.0.1: the small web server the phone talks to after scanning the QR code on the TV
 * (ArrangeActivity). Local network only: it answers private, link-local and loopback addresses and
 * closes anything else. Every path starts with a 128-bit token made fresh each time it starts and
 * shown only on the TV. It runs only while the QR screen is open and stops by itself after
 * {@link #IDLE_MS} without a request. The idea follows Aerial's PhoneServer (pengchujin/aerial-tv,
 * MIT); the code is Mi5G's own, with no third-party HTTP library.
 */
final class ArrangeServer {
    private static final String TAG = "Mi5G.Arrange";
    static final long IDLE_MS = 10 * 60_000L;
    static final int MAX_JSON = 256 * 1024, MAX_ICON = 4 * 1024 * 1024, MAX_WALLPAPER = 80 * 1024 * 1024;

    private static ArrangeServer running;

    private final Context ctx;
    private final String token;
    private final ServerSocket socket;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile long lastUse = System.currentTimeMillis();
    private volatile boolean closed;
    private Runnable onStopped;

    private ArrangeServer(Context ctx, String token, ServerSocket socket) {
        this.ctx = ctx.getApplicationContext();
        this.token = token;
        this.socket = socket;
    }

    static String newToken(SecureRandom r) {
        byte[] b = new byte[16];
        r.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    /** Starts the server (or returns the one running). Null when no port could be opened. */
    static synchronized ArrangeServer start(Context ctx, Runnable onStopped) {
        if (running != null && !running.closed) { running.onStopped = onStopped; return running; }
        try {
            ServerSocket s = new ServerSocket(0);
            ArrangeServer server = new ArrangeServer(ctx, newToken(new SecureRandom()), s);
            server.onStopped = onStopped;
            running = server;
            Thread t = new Thread(server::acceptLoop, "Mi5gArrange");
            t.setDaemon(true);
            t.start();
            server.main.postDelayed(server::idleCheck, 30_000);
            return server;
        } catch (IOException e) {
            Log.w(TAG, "cannot open a port: " + e);
            return null;
        }
    }

    static synchronized void stopRunning() {
        if (running != null) running.stop();
        running = null;
    }

    static synchronized boolean isRunning() { return running != null && !running.closed; }

    void stop() {
        if (closed) return;
        closed = true;
        try { socket.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
        main.removeCallbacksAndMessages(null);
        Runnable r = onStopped;
        if (r != null) main.post(r);
    }

    private void idleCheck() {
        if (closed) return;
        if (System.currentTimeMillis() - lastUse > IDLE_MS) { stop(); return; }
        main.postDelayed(this::idleCheck, 30_000);
    }

    /** http://<TV address>:<port>/<token>/ or null without a local IPv4 address. */
    String url() {
        String ip = localAddress();
        return ip == null ? null : "http://" + ip + ":" + socket.getLocalPort() + "/" + token + "/";
    }

    static String localAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Private (10/8, 172.16/12, 192.168/16), link-local or loopback: the only peers answered. */
    static boolean localPeer(InetAddress a) {
        return a != null && (a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isLoopbackAddress());
    }

    static boolean tokenMatches(String expected, String got) {
        if (got == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), got.getBytes(StandardCharsets.US_ASCII));
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket c = socket.accept();
                if (!localPeer(c.getInetAddress())) { c.close(); continue; }
                pool.execute(() -> handle(c));
            } catch (IOException e) {
                if (!closed) Log.w(TAG, "accept: " + e);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                return;
            }
        }
    }

    // ---- HTTP ------------------------------------------------------------------------------------

    static final class Request {
        final String method, path;
        final Map<String, String> query;
        final Map<String, String> headers;
        Request(String method, String path, Map<String, String> query, Map<String, String> headers) {
            this.method = method; this.path = path; this.query = query; this.headers = headers;
        }
    }

    /** "GET /tok/api/state?x=1 HTTP/1.1" into its parts; null when it is not a request line. */
    static Request parseLine(String line, Map<String, String> headers) {
        if (line == null) return null;
        String[] p = line.split(" ");
        if (p.length != 3 || !p[2].startsWith("HTTP/1.")) return null;
        String target = p[1];
        int q = target.indexOf('?');
        String path = q < 0 ? target : target.substring(0, q);
        Map<String, String> query = new HashMap<>();
        if (q >= 0) {
            for (String kv : target.substring(q + 1).split("&")) {
                int e = kv.indexOf('=');
                try {
                    if (e > 0) query.put(URLDecoder.decode(kv.substring(0, e), "UTF-8"), URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
                } catch (Exception ignored) { }
            }
        }
        return new Request(p[0], path, query, headers);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("line too long");
        }
        return c == -1 && b.size() == 0 ? null : b.toString("UTF-8");
    }

    private void handle(Socket c) {
        try (Socket s = c) {
            s.setSoTimeout(30_000);
            InputStream in = s.getInputStream();
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String first = readLine(in);
            Map<String, String> headers = new HashMap<>();
            String h;
            int count = 0;
            while ((h = readLine(in)) != null && !h.isEmpty() && count++ < 64) {
                int i = h.indexOf(':');
                if (i > 0) headers.put(h.substring(0, i).trim().toLowerCase(java.util.Locale.ROOT), h.substring(i + 1).trim());
            }
            Request r = parseLine(first, headers);
            if (r == null) { respond(out, 400, "text/plain", "bad request".getBytes()); return; }
            String prefix = "/" + token + "/";
            String[] seg = r.path.split("/", 3);   // "", token, rest
            if (seg.length < 2 || !tokenMatches(token, seg[1])) { respond(out, 404, "text/plain", "not found".getBytes()); return; }
            lastUse = System.currentTimeMillis();
            String rest = seg.length > 2 ? seg[2] : "";
            if (r.path.equals("/" + token)) { redirect(out, prefix); return; }
            long length = 0;
            try { length = Long.parseLong(headers.getOrDefault("content-length", "0")); } catch (NumberFormatException ignored) { }
            route(r, rest, in, length, out);
        } catch (Exception e) {
            Log.w(TAG, "request: " + e);
        }
    }

    private static byte[] readBody(InputStream in, long length, int max) throws IOException {
        if (length < 0 || length > max) throw new IOException("body too large");
        byte[] b = new byte[(int) length];
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) throw new IOException("short body");
            off += n;
        }
        return b;
    }

    private void route(Request r, String rest, InputStream in, long length, OutputStream out) throws Exception {
        boolean post = "POST".equals(r.method);
        switch (rest) {
            case "":
                respond(out, 200, "text/html; charset=utf-8", asset("mi5g_arrange.html"));
                return;
            case "cairo.ttf":
                respond(out, 200, "font/ttf", readAll(ctx.getResources().openRawResource(R.font.cairo)));
                return;
            case "api/state":
                respond(out, 200, "application/json", state().toString().getBytes(StandardCharsets.UTF_8));
                return;
            case "api/tile.png": {
                byte[] png = tilePng(r.query.get("key"));
                if (png == null) respond(out, 404, "text/plain", new byte[0]); else respond(out, 200, "image/png", png);
                return;
            }
            default:
                break;
        }
        if (!post) { respond(out, 405, "text/plain", "method".getBytes()); return; }
        switch (rest) {
            case "api/layout": {
                JSONObject body = new JSONObject(new String(readBody(in, length, MAX_JSON), StandardCharsets.UTF_8));
                boolean ok = applyLayout(ctx, body, Config.load(ctx).dockApps);
                ok(out, ok);
                return;
            }
            case "api/icon": {
                String pkg = r.query.get("pkg");
                byte[] img = readBody(in, length, MAX_ICON);
                ok(out, HomeLayout.validPackage(pkg) && saveIcon(ctx, pkg, img));
                return;
            }
            case "api/icon/reset": {
                String pkg = r.query.get("pkg");
                File f = HomeLayout.validPackage(pkg) ? TileMenu.imageFile(ctx, pkg) : null;
                //noinspection ResultOfMethodCallIgnored
                if (f != null) f.delete();
                changed();
                ok(out, f != null);
                return;
            }
            case "api/wallpaper": {
                byte[] data = readBody(in, length, MAX_WALLPAPER);
                ok(out, Mi5gWallpapers.saveUpload(ctx, data) != null && changedTrue());
                return;
            }
            case "api/wallpaper/choose":
                ok(out, Mi5gWallpapers.choose(ctx, r.query.get("name")) && changedTrue());
                return;
            case "api/stop":
                ok(out, true);
                main.post(this::stop);
                return;
            default:
                respond(out, 404, "text/plain", "not found".getBytes());
        }
    }

    private boolean changedTrue() { changed(); return true; }

    private void changed() { main.post(HomeLayout::changed); }

    private void ok(OutputStream out, boolean ok) throws IOException {
        respond(out, ok ? 200 : 400, "application/json", ("{\"saved\":" + ok + "}").getBytes(StandardCharsets.UTF_8));
    }

    private static void redirect(OutputStream out, String to) throws IOException {
        out.write(("HTTP/1.1 302 Found\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static void respond(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 400 ? "Bad Request" : code == 404 ? "Not Found" : code == 405 ? "Method Not Allowed" : "Error";
        out.write(("HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private byte[] asset(String name) throws IOException { return readAll(ctx.getAssets().open(name)); }

    static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }

    // ---- state -----------------------------------------------------------------------------------

    /** The launchable TV apps, as Home lists them (leanback first, then the library's added apps). */
    static List<ResolveInfo> tvApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        Set<String> seen = new HashSet<>();
        List<ResolveInfo> out = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null || ri.activityInfo.packageName.equals(ctx.getPackageName())) continue;
            if (seen.add(ri.activityInfo.packageName)) out.add(ri);
        }
        Set<String> added = LibraryApps.load(ctx);
        if (!added.isEmpty()) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
                if (ri.activityInfo != null && added.contains(ri.activityInfo.packageName) && seen.add(ri.activityInfo.packageName)) out.add(ri);
            }
        }
        return out;
    }

    static final class InputRow {
        final String id, label;
        final boolean hdmi;
        InputRow(String id, String label, boolean hdmi) { this.id = id; this.label = label; this.hdmi = hdmi; }
    }

    /** The TV's inputs with the name the TV gives them (a CEC device's own name when it has one). */
    static List<InputRow> inputs(Context ctx) {
        List<InputRow> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        try {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == TvInputInfo.TYPE_TUNER) continue;
                CharSequence custom = info.loadCustomLabel(ctx);
                CharSequence label = custom != null && custom.length() > 0 ? custom : info.loadLabel(ctx);
                out.add(new InputRow(info.getId(), label == null ? info.getId() : label.toString(), info.getType() == TvInputInfo.TYPE_HDMI));
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    private JSONObject state() throws Exception {
        PackageManager pm = ctx.getPackageManager();
        Config cfg = Config.load(ctx);
        HomeLayout layout = HomeLayout.load(ctx);
        Map<String, Integer> ranks = Order.ranks(Order.load(Order.file(ctx)), cfg.dockApps);
        Set<String> hidden = new HashSet<>(TileMenu.removed(ctx));
        List<JSONObject> tiles = new ArrayList<>();
        for (ResolveInfo ri : tvApps(ctx)) {
            String pkg = ri.activityInfo.packageName;
            String key = new ComponentName(pkg, ri.activityInfo.name).flattenToShortString();
            HomeLayout.Folder f = layout.folderOf(pkg);
            File icon = TileMenu.imageFile(ctx, pkg);
            tiles.add(new JSONObject().put("key", key).put("pkg", pkg).put("label", String.valueOf(ri.loadLabel(pm)))
                    .put("name", layout.names.containsKey(pkg) ? layout.names.get(pkg) : "")
                    .put("hidden", hidden.contains(pkg)).put("folder", f == null ? "" : f.id)
                    .put("customIcon", icon != null && icon.isFile())
                    .put("rank", ranks.containsKey(key) ? ranks.get(key) : 10_000 + tiles.size()));
        }
        Collections.sort(tiles, (a, b) -> Integer.compare(a.optInt("rank"), b.optInt("rank")));
        JSONArray t = new JSONArray();
        for (JSONObject o : tiles) t.put(o);
        JSONArray ins = new JSONArray();
        Map<String, String> pinned = new LinkedHashMap<>();
        for (HomeLayout.PinnedInput p : layout.inputs) pinned.put(p.id, p.name);
        for (InputRow in : inputs(ctx)) {
            ins.put(new JSONObject().put("id", in.id).put("label", in.label).put("hdmi", in.hdmi)
                    .put("pinned", pinned.containsKey(in.id)).put("name", pinned.containsKey(in.id) ? pinned.get(in.id) : ""));
        }
        JSONObject o = HomeLayout.encode(layout);
        o.put("tiles", t).put("tvInputs", ins).put("wallpapers", Mi5gWallpapers.state(ctx));
        o.put("theme", new JSONObject().put("columns", cfg.columns).put("rows", cfg.rowsBelowDock));
        return o;
    }

    private byte[] tilePng(String key) {
        ComponentName cn = key == null ? null : ComponentName.unflattenFromString(key);
        if (cn == null || !HomeLayout.validPackage(cn.getPackageName())) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            Drawable d = TileMenu.customImage(ctx, cn.getPackageName());
            if (d == null) d = pm.getActivityBanner(cn);
            if (d == null) d = pm.getActivityIcon(cn);
            Bitmap b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(0xFF12302E);
            int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
            if (w > 0 && h > 0 && w < h * 1.4f) d.setBounds(110, 40, 210, 140); else d.setBounds(0, 0, 320, 180);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 90, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    // ---- writes ----------------------------------------------------------------------------------

    /**
     * {"order":[keys], "hidden":[pkgs], "names":{pkg:name}, "folders":[...], "inputs":[...], "options":{...}}.
     * Each part is optional; what is there replaces what was saved.
     */
    static boolean applyLayout(Context ctx, JSONObject body, List<String> dockApps) {
        HomeLayout old = HomeLayout.load(ctx);
        JSONObject merged = HomeLayout.encode(old);
        try {
            for (String k : new String[]{"names", "folders", "inputs", "options"}) if (body.has(k)) merged.put(k, body.get(k));
        } catch (Exception e) {
            return false;
        }
        HomeLayout next = HomeLayout.decode(merged);
        boolean ok = HomeLayout.save(HomeLayout.file(ctx), next);
        JSONArray order = body.optJSONArray("order");
        if (order != null) {
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < order.length() && keys.size() < 1000; i++) {
                String k = order.optString(i);
                if (k.length() <= 300 && (ComponentName.unflattenFromString(k) != null || k.startsWith("mi5g-")) && !keys.contains(k)) keys.add(k);
            }
            ok &= Order.save(Order.file(ctx), new Order(Order.dockKey(dockApps), keys));
        }
        JSONArray hidden = body.optJSONArray("hidden");
        if (hidden != null) {
            Set<String> s = new HashSet<>();
            for (int i = 0; i < hidden.length() && s.size() < 1000; i++) {
                String p = hidden.optString(i);
                if (HomeLayout.validPackage(p)) s.add(p);
            }
            TileMenu.prefs(ctx).edit().putStringSet(TileMenu.REMOVED_KEY, s).apply();
        }
        android.content.SharedPreferences.Editor e = TileMenu.prefs(ctx).edit();
        if (next.options.columns > 0) e.putInt("columns", next.options.columns); else e.remove("columns");
        if (next.options.rows > 0) e.putInt(Config.ROWS_BELOW_DOCK_KEY, next.options.rows); else e.remove(Config.ROWS_BELOW_DOCK_KEY);
        e.apply();
        new Handler(Looper.getMainLooper()).post(HomeLayout::changed);
        return ok;
    }

    /** A JPEG/PNG/WebP from the phone, scaled to at most 640x360 and kept as the tile's image. */
    static boolean saveIcon(Context ctx, String pkg, byte[] img) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(img, 0, img.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 8000 || o.outHeight > 8000) return false;
        o.inJustDecodeBounds = false;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= 640) o.inSampleSize *= 2;
        Bitmap b = BitmapFactory.decodeByteArray(img, 0, img.length, o);
        if (b == null) return false;
        float scale = Math.min(1f, Math.min(640f / b.getWidth(), 360f / b.getHeight()));
        if (scale < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * scale), Math.round(b.getHeight() * scale), true);
        File f = TileMenu.imageFile(ctx, pkg);
        if (f == null) return false;
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 95, out);
        } catch (IOException e) {
            return false;
        }
        new Handler(Looper.getMainLooper()).post(HomeLayout::changed);
        return true;
    }
}
