package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mi5G 2.0.1: what the phone arranges for Home (ArrangeServer), kept in files/mi5g_layout.json.
 * Tile names, app folders, pinned inputs (HDMI and others) and the layout options. The order, the
 * hidden tiles and the custom images keep their own stores (Order, TileMenu). Idea and shape after
 * Aerial's LayoutStore (pengchujin/aerial-tv, MIT); the code is Mi5G's own.
 */
final class HomeLayout {
    static final String FILE_NAME = "mi5g_layout.json";
    static final int MAX_NAME = 40, MAX_FOLDERS = 12, MAX_INPUTS = 12, MAX_APPS_PER_FOLDER = 48;

    static final class Folder {
        final String id, name;
        final List<String> apps;
        Folder(String id, String name, List<String> apps) { this.id = id; this.name = name; this.apps = Collections.unmodifiableList(new ArrayList<>(apps)); }
    }

    static final class PinnedInput {
        final String id, name;
        PinnedInput(String id, String name) { this.id = id; this.name = name; }
    }

    static final class Options {
        /** 4, 5 or 6 tiles per row; 0 keeps the theme's. */
        final int columns;
        /** 1 or 2 rows under the dock; 0 keeps the theme's. */
        final int rows;
        /** "s", "m" or "l". */
        final String clockSize;
        /** "start" or "end" (top corner). */
        final String clockCorner;
        /** "focus" (the name of the focused tile), "always" or "never". */
        final String names;
        /** "scale", "border" or "glow". */
        final String focus;

        Options(int columns, int rows, String clockSize, String clockCorner, String names, String focus) {
            this.columns = columns >= 4 && columns <= 6 ? columns : 0;
            this.rows = rows == 1 || rows == 2 ? rows : 0;
            this.clockSize = oneOf(clockSize, "m", "s", "m", "l");
            this.clockCorner = oneOf(clockCorner, "start", "start", "end");
            this.names = oneOf(names, "focus", "focus", "always", "never");
            this.focus = oneOf(focus, "scale", "scale", "border", "glow");
        }

        static final Options DEFAULT = new Options(0, 0, "m", "start", "focus", "scale");
    }

    final Map<String, String> names;
    final List<Folder> folders;
    final List<PinnedInput> inputs;
    final Options options;

    HomeLayout(Map<String, String> names, List<Folder> folders, List<PinnedInput> inputs, Options options) {
        this.names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
        this.folders = Collections.unmodifiableList(new ArrayList<>(folders));
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.options = options;
    }

    static final HomeLayout EMPTY = new HomeLayout(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Options.DEFAULT);

    private static String oneOf(String v, String fallback, String... allowed) {
        for (String a : allowed) if (a.equals(v)) return v;
        return fallback;
    }

    /** A name as typed on the phone: trimmed, no control or bidi override characters, at most MAX_NAME. */
    static String cleanName(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c) || (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069') || c == '\u2028' || c == '\u2029') continue;
            sb.append(c);
        }
        String s = sb.toString().trim();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    static boolean validPackage(String p) {
        return p != null && p.length() <= 255 && p.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }

    static boolean validId(String id) {
        return id != null && id.length() <= 200 && id.matches("[A-Za-z0-9._:/-]+");
    }

    /** The folder an app is in, or null. */
    Folder folderOf(String pkg) {
        for (Folder f : folders) if (f.apps.contains(pkg)) return f;
        return null;
    }

    // ---- JSON ------------------------------------------------------------------------------------

    static JSONObject encode(HomeLayout l) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            JSONObject n = new JSONObject();
            for (Map.Entry<String, String> e : l.names.entrySet()) n.put(e.getKey(), e.getValue());
            o.put("names", n);
            JSONArray fs = new JSONArray();
            for (Folder f : l.folders) {
                JSONArray apps = new JSONArray();
                for (String a : f.apps) apps.put(a);
                fs.put(new JSONObject().put("id", f.id).put("name", f.name).put("apps", apps));
            }
            o.put("folders", fs);
            JSONArray is = new JSONArray();
            for (PinnedInput i : l.inputs) is.put(new JSONObject().put("id", i.id).put("name", i.name));
            o.put("inputs", is);
            Options p = l.options;
            o.put("options", new JSONObject().put("columns", p.columns).put("rows", p.rows).put("clockSize", p.clockSize)
                    .put("clockCorner", p.clockCorner).put("names", p.names).put("focus", p.focus));
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lenient: anything that does not validate is left out on its own; never throws. */
    static HomeLayout decode(JSONObject o) {
        if (o == null) return EMPTY;
        Map<String, String> names = new LinkedHashMap<>();
        JSONObject n = o.optJSONObject("names");
        if (n != null) {
            JSONArray keys = n.names();
            for (int i = 0; keys != null && i < keys.length() && names.size() < 500; i++) {
                String k = keys.optString(i);
                String v = cleanName(n.optString(k));
                if ((validPackage(k) || (k.startsWith("folder:") && validId(k)) ) && !v.isEmpty()) names.put(k, v);
            }
        }
        List<Folder> folders = new ArrayList<>();
        java.util.Set<String> taken = new java.util.HashSet<>();
        JSONArray fs = o.optJSONArray("folders");
        for (int i = 0; fs != null && i < fs.length() && folders.size() < MAX_FOLDERS; i++) {
            JSONObject f = fs.optJSONObject(i);
            if (f == null) continue;
            String id = f.optString("id");
            String name = cleanName(f.optString("name"));
            if (!id.matches("[a-z0-9]{1,16}") || name.isEmpty()) continue;
            List<String> apps = new ArrayList<>();
            JSONArray a = f.optJSONArray("apps");
            for (int j = 0; a != null && j < a.length() && apps.size() < MAX_APPS_PER_FOLDER; j++) {
                String p = a.optString(j);
                if (validPackage(p) && taken.add(p)) apps.add(p);   // an app is in one folder at most
            }
            boolean dup = false;
            for (Folder g : folders) dup |= g.id.equals(id);
            if (!dup) folders.add(new Folder(id, name, apps));
        }
        List<PinnedInput> inputs = new ArrayList<>();
        JSONArray is = o.optJSONArray("inputs");
        for (int i = 0; is != null && i < is.length() && inputs.size() < MAX_INPUTS; i++) {
            JSONObject in = is.optJSONObject(i);
            if (in == null) continue;
            String id = in.optString("id");
            if (!validId(id)) continue;
            boolean dup = false;
            for (PinnedInput p : inputs) dup |= p.id.equals(id);
            if (!dup) inputs.add(new PinnedInput(id, cleanName(in.optString("name"))));
        }
        JSONObject p = o.optJSONObject("options");
        Options opts = p == null ? Options.DEFAULT : new Options(p.optInt("columns"), p.optInt("rows"), p.optString("clockSize"),
                p.optString("clockCorner"), p.optString("names"), p.optString("focus"));
        return new HomeLayout(names, folders, inputs, opts);
    }

    // ---- file ------------------------------------------------------------------------------------

    static File file(Context ctx) { return new File(ctx.getFilesDir(), FILE_NAME); }

    static HomeLayout load(Context ctx) { return load(file(ctx)); }

    static HomeLayout load(File f) {
        if (!f.isFile()) return EMPTY;
        try {
            return decode(new JSONObject(new String(new AtomicFile(f).readFully(), StandardCharsets.UTF_8)));
        } catch (IOException | JSONException e) {
            return EMPTY;
        }
    }

    static boolean save(File f, HomeLayout l) {
        AtomicFile af = new AtomicFile(f);
        FileOutputStream out = null;
        try {
            out = af.startWrite();
            out.write(encode(l).toString().getBytes(StandardCharsets.UTF_8));
            af.finishWrite(out);
            return true;
        } catch (IOException e) {
            if (out != null) af.failWrite(out);
            return false;
        }
    }

    // ---- change listener (the Home screen reloads when the phone saves) -----------------------------

    private static volatile Runnable listener;
    static void setListener(Runnable r) { listener = r; }
    static void changed() { Runnable r = listener; if (r != null) r.run(); }
}
