package com.midroid.mi5gtv.matches;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Mi5G 2.4 (M6): kick-off reminders, kept in prefs and armed with AlarmManager (re-armed after a reboot). */
final class Reminders {
    private Reminders() {}

    static final String PREFS = "matches_prefs";
    static final String KEY = "kickoff_reminders";
    static final int MAX = 30;

    static final class Item {
        final String id, slug, home, away, league;
        final long kickoff;
        Item(String id, String slug, String home, String away, String league, long kickoff) {
            this.id = id; this.slug = slug; this.home = home; this.away = away; this.league = league; this.kickoff = kickoff;
        }

        String title() { return home + " × " + away; }
    }

    static String encode(List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item i : items) {
                a.put(new JSONObject().put("id", i.id).put("slug", i.slug).put("home", i.home).put("away", i.away)
                        .put("league", i.league).put("kickoff", i.kickoff));
            }
        } catch (Exception ignored) {
        }
        return a.toString();
    }

    static List<Item> decode(String json) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json == null || json.isEmpty() ? "[]" : json);
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                String id = o.optString("id");
                if (!id.matches("\\d+")) continue;
                out.add(new Item(id, o.optString("slug"), o.optString("home"), o.optString("away"), o.optString("league"), o.optLong("kickoff", -1)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Drops reminders whose kick-off has passed; keeps at most {@link #MAX}. */
    static List<Item> prune(List<Item> items, long now) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) if (i.kickoff > now && out.size() < MAX) out.add(i);
        return out;
    }

    static List<Item> load(Context c) { return decode(prefs(c).getString(KEY, "[]")); }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, 0); }

    private static void save(Context c, List<Item> items) { prefs(c).edit().putString(KEY, encode(items)).apply(); }

    static boolean has(Context c, String id) {
        for (Item i : load(c)) if (i.id.equals(id)) return true;
        return false;
    }

    /** Adds (or replaces) a reminder and arms it; false when the kick-off is already past. */
    static boolean add(Context c, Item item) {
        long now = System.currentTimeMillis();
        if (Football.remindAt(item.kickoff, now) < 0) return false;
        List<Item> items = new ArrayList<>();
        for (Item i : load(c)) if (!i.id.equals(item.id)) items.add(i);
        items.add(item);
        save(c, prune(items, now));
        arm(c, item, now);
        return true;
    }

    static void remove(Context c, String id) {
        List<Item> items = new ArrayList<>();
        for (Item i : load(c)) if (!i.id.equals(id)) items.add(i);
        save(c, items);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(pending(c, id));
    }

    static Item take(Context c, String id) {
        for (Item i : load(c)) if (i.id.equals(id)) { remove(c, id); return i; }
        return null;
    }

    /** After a reboot or an update: prune and arm everything still ahead. */
    static void armAll(Context c) {
        long now = System.currentTimeMillis();
        List<Item> items = prune(load(c), now);
        save(c, items);
        for (Item i : items) arm(c, i, now);
    }

    private static void arm(Context c, Item i, long now) {
        long at = Football.remindAt(i.kickoff, now);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null || at < 0) return;
        PendingIntent pi = pending(c, i.id);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static PendingIntent pending(Context c, String id) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ReminderReceiver.ACTION).putExtra("id", id);
        return PendingIntent.getBroadcast(c, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
