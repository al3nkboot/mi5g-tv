package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2.4 M4 (backlog 26, own code): the Home widgets, shown or hidden and ordered from Mi5G settings → Home widgets
 * (OK shows or hides one, holding OK moves it up). The info widgets are short lines under the date: occasion
 * countdowns, the next reminder, a football score (ESPN's public scoreboard, cached ten minutes) and a news
 * headline (the BBC Arabic RSS feed, credited, cached thirty minutes). Stats and the race panel can be hidden here.
 * Off by default except the reminder line (which only shows with a reminder today) and the two existing panels.
 */
final class HomeWidgets {
    static final String COUNTDOWN = "countdown", REMINDER = "reminder", SPORTS = "sports", NEWS = "news", STATS = "stats", RACE = "race";
    static final List<String> ALL = Arrays.asList(COUNTDOWN, REMINDER, SPORTS, NEWS, STATS, RACE);
    static final String ORDER = "mi5g_widgets_order", LEAGUE = "mi5g_widget_league";
    static final int MAX_LINES = 3;
    static final List<String> LEAGUES = Arrays.asList("ksa.1", "uefa.champions", "eng.1", "esp.1", "uae.1");
    static final String NEWS_URL = "https://feeds.bbci.co.uk/arabic/rss.xml";
    static final long SPORTS_EVERY_MS = 10 * 60_000L, NEWS_EVERY_MS = 30 * 60_000L;

    private HomeWidgets() {}

    static String key(String id) { return COUNTDOWN.equals(id) ? Occasions.COUNTDOWNS : "mi5g_widget_" + id; }
    static boolean defaultShown(String id) { return REMINDER.equals(id) || STATS.equals(id) || RACE.equals(id); }

    static boolean shown(Context ctx, String id) { return LauncherSwitches.prefs(ctx).getBoolean(key(id), defaultShown(id)); }

    static void setShown(Context ctx, String id, boolean on) {
        LauncherSwitches.prefs(ctx).edit().putBoolean(key(id), on).apply();
        HomeLayout.changed();
    }

    /** The saved order, with unknown ids dropped and new ones appended. */
    static List<String> order(Context ctx) { return parseOrder(LauncherSwitches.prefs(ctx).getString(ORDER, "")); }

    static List<String> parseOrder(String csv) {
        List<String> out = new ArrayList<>();
        for (String s : csv.split(",")) if (ALL.contains(s) && !out.contains(s)) out.add(s);
        for (String s : ALL) if (!out.contains(s)) out.add(s);
        return out;
    }

    /** Moves a widget one place up (the first one goes to the end); returns the new order. */
    static List<String> moveUp(List<String> order, String id) {
        List<String> out = new ArrayList<>(order);
        int i = out.indexOf(id);
        if (i < 0) return out;
        out.remove(i);
        out.add(i == 0 ? out.size() : i - 1, id);
        return out;
    }

    static void saveOrder(Context ctx, List<String> order) {
        LauncherSwitches.prefs(ctx).edit().putString(ORDER, String.join(",", order)).apply();
        HomeLayout.changed();
    }

    static int titleRes(String id) {
        switch (id) {
            case COUNTDOWN: return R.string.mi5g_widget_countdown;
            case REMINDER: return R.string.mi5g_widget_reminder;
            case SPORTS: return R.string.mi5g_widget_sports;
            case NEWS: return R.string.mi5g_widget_news;
            case STATS: return R.string.mi5g_widget_stats;
            default: return R.string.mi5g_widget_race;
        }
    }

    static String league(Context ctx) {
        String l = LauncherSwitches.prefs(ctx).getString(LEAGUE, LEAGUES.get(0));
        return LEAGUES.contains(l) ? l : LEAGUES.get(0);
    }

    static void setLeague(Context ctx, String l) {
        LauncherSwitches.prefs(ctx).edit().putString(LEAGUE, LEAGUES.contains(l) ? l : LEAGUES.get(0)).remove("mi5g_w_sports_at").apply();
    }

    static int leagueRes(String l) {
        switch (l) {
            case "uefa.champions": return R.string.mi5g_league_ucl;
            case "eng.1": return R.string.mi5g_league_eng;
            case "esp.1": return R.string.mi5g_league_esp;
            case "uae.1": return R.string.mi5g_league_uae;
            default: return R.string.mi5g_league_ksa;
        }
    }

    /** The info lines in order, at most MAX_LINES non-empty ones. Network is used; call off the main thread. */
    static List<String> lines(Context ctx, Weather.Fetcher f, long nowMs) {
        List<String> out = new ArrayList<>();
        for (String id : order(ctx)) {
            if (out.size() >= MAX_LINES) break;
            if (!shown(ctx, id)) continue;
            String s = "";
            try {
                switch (id) {
                    case COUNTDOWN: s = Occasions.homeLine(ctx, LocalDate.now()); break;
                    case REMINDER: s = Reminders.homeLine(ctx, LocalDateTime.now()); break;
                    case SPORTS: s = cached(ctx, "mi5g_w_sports", SPORTS_EVERY_MS, nowMs,
                            () -> parseScore(f.get(scoreUrl(league(ctx)), Weather.USER_AGENT))); break;
                    case NEWS: s = cached(ctx, "mi5g_w_news", NEWS_EVERY_MS, nowMs, () -> {
                        String h = parseHeadline(f.get(NEWS_URL, Weather.USER_AGENT));
                        return h.isEmpty() ? "" : ctx.getString(R.string.mi5g_widget_news_line, h);
                    }); break;
                    default: break;   // stats and race are panels of their own
                }
            } catch (Exception ignored) { }
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }

    interface Source { String get() throws Exception; }

    private static String cached(Context ctx, String key, long every, long now, Source src) {
        SharedPreferences p = LauncherSwitches.prefs(ctx);
        long at = p.getLong(key + "_at", 0);
        if (at > 0 && now - at >= 0 && now - at < every) return p.getString(key, "");
        try {
            String s = src.get();
            p.edit().putString(key, s == null ? "" : s).putLong(key + "_at", now).apply();
            return s;
        } catch (Exception e) {
            return p.getString(key, "");   // offline: the last one
        }
    }

    static String scoreUrl(String league) { return "https://site.api.espn.com/apis/site/v2/sports/soccer/" + league + "/scoreboard"; }

    /** One line from an ESPN scoreboard: a live match first, then the next one, then the last result. */
    static String parseScore(String json) throws Exception {
        JSONArray events = new JSONObject(json).optJSONArray("events");
        if (events == null) return "";
        String live = null, pre = null, post = null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            JSONObject comp = e.optJSONArray("competitions") == null ? null : e.getJSONArray("competitions").optJSONObject(0);
            if (comp == null) continue;
            JSONArray teams = comp.optJSONArray("competitors");
            if (teams == null || teams.length() < 2) continue;
            JSONObject home = teams.getJSONObject(0), away = teams.getJSONObject(1);
            if ("away".equals(home.optString("homeAway"))) { JSONObject t = home; home = away; away = t; }
            JSONObject type = e.optJSONObject("status") == null ? null : e.getJSONObject("status").optJSONObject("type");
            String state = type == null ? "" : type.optString("state");
            String detail = type == null ? "" : type.optString("shortDetail");
            String h = name(home), a = name(away);
            if ("pre".equals(state)) {
                if (pre == null) pre = h + " – " + a + "  ·  " + detail;
            } else {
                String line = h + " " + home.optString("score", "0") + " – " + away.optString("score", "0") + " " + a + "  ·  " + detail;
                if ("in".equals(state) && live == null) live = "● " + line;
                else if ("post".equals(state)) post = line;
            }
        }
        return plain(live != null ? live : pre != null ? pre : post != null ? post : "");
    }

    private static String name(JSONObject competitor) {
        JSONObject t = competitor.optJSONObject("team");
        if (t == null) return "";
        String s = t.optString("shortDisplayName");
        return s.isEmpty() ? t.optString("displayName") : s;
    }

    private static final Pattern ITEM_TITLE = Pattern.compile("<item>.*?<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</title>", Pattern.DOTALL);

    /** The first item's title of an RSS feed, plain text, at most 90 characters. */
    static String parseHeadline(String rss) {
        Matcher m = ITEM_TITLE.matcher(rss == null ? "" : rss);
        if (!m.find()) return "";
        String s = m.group(1).replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
        s = plain(s.replaceAll("<[^>]*>", ""));
        return s.length() > 90 ? s.substring(0, 89) + "…" : s;
    }

    /** Text from the network, without control or direction characters. */
    static String plain(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (Character.isISOControl(c) || (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069') || c == '\u2028' || c == '\u2029') continue;
            sb.append(c);
        }
        return sb.toString().trim();
    }
}
