package com.midroid.mi5gtv.matches;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Mi5G Score on the TV: ESPN's public scoreboard, summary and standings JSON. Parsers are pure (tests). */
final class Espn {
    private Espn() {}

    static final String SITE = "https://site.api.espn.com/apis/site/v2/sports/soccer/";
    static final String STANDINGS = "https://site.api.espn.com/apis/v2/sports/soccer/";

    static String get(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Mi5G TV)");
            c.setRequestProperty("Accept-Encoding", "gzip");
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            if ("gzip".equalsIgnoreCase(c.getContentEncoding())) in = new GZIPInputStream(in);
            try (InputStream s = in) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int r;
                while ((r = s.read(b)) > 0 && o.size() < 4_000_000) o.write(b, 0, r);
                return o.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static final class Row {
        final String rank, team, played, diff, points;
        Row(String rank, String team, String played, String diff, String points) {
            this.rank = rank; this.team = team; this.played = played; this.diff = diff; this.points = points;
        }
    }

    static List<Row> standings(String json) {
        List<Row> out = new ArrayList<>();
        try {
            JSONArray entries = new JSONObject(json).getJSONArray("children").getJSONObject(0)
                    .getJSONObject("standings").getJSONArray("entries");
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.getJSONObject(i);
                Map<String, String> st = new LinkedHashMap<>();
                JSONArray stats = e.optJSONArray("stats");
                for (int k = 0; stats != null && k < stats.length(); k++) {
                    JSONObject s = stats.getJSONObject(k);
                    st.put(s.optString("name"), s.optString("displayValue"));
                }
                String team = MainActivity.translateTeamName(e.getJSONObject("team").optString("displayName"));
                out.add(new Row(st.getOrDefault("rank", String.valueOf(i + 1)), team, st.getOrDefault("gamesPlayed", "-"),
                        st.getOrDefault("pointDifferential", "-"), st.getOrDefault("points", "-")));
            }
            out.sort((a, b) -> Integer.compare(num(a.rank), num(b.rank)));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static int num(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 999; } }

    /** Goals, cards and substitutions: "67'  هدف  ريال مدريد". */
    static List<String> keyEvents(String json) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(json).optJSONArray("keyEvents");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject k = a.getJSONObject(i);
                String type = k.optJSONObject("type") == null ? "" : k.getJSONObject("type").optString("text");
                String ar = eventAr(type);
                if (ar == null) continue;
                String clock = k.optJSONObject("clock") == null ? "" : k.getJSONObject("clock").optString("displayValue");
                String team = k.optJSONObject("team") == null ? "" : MainActivity.translateTeamName(k.getJSONObject("team").optString("displayName"));
                out.add((clock + "  " + ar + "  " + team).trim());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static String eventAr(String type) {
        String t = type.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("own goal")) return "⚽ هدف عكسي";
        if (t.contains("penalty") && (t.contains("scored") || t.contains("goal"))) return "⚽ ركلة جزاء";
        if (t.contains("goal")) return "⚽ هدف";
        if (t.contains("yellow")) return "🟨 بطاقة صفراء";
        if (t.contains("red")) return "🟥 بطاقة حمراء";
        if (t.contains("substitution")) return "🔁 تبديل";
        return null;
    }

    static final String[][] STATS = {
            {"possessionPct", "الاستحواذ %"}, {"totalShots", "التسديدات"}, {"shotsOnTarget", "على المرمى"},
            {"wonCorners", "الركنيات"}, {"foulsCommitted", "الأخطاء"}, {"offsides", "التسلل"},
            {"yellowCards", "بطاقات صفراء"}, {"redCards", "بطاقات حمراء"}, {"saves", "التصديات"},
    };

    /** {label, home, away} for the stats both teams report. */
    static List<String[]> stats(String json) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray teams = new JSONObject(json).getJSONObject("boxscore").getJSONArray("teams");
            if (teams.length() < 2) return out;
            Map<String, String>[] v = new Map[2];
            int home = 0;
            for (int t = 0; t < 2; t++) {
                v[t] = new LinkedHashMap<>();
                JSONObject team = teams.getJSONObject(t);
                if ("home".equals(team.optString("homeAway"))) home = t;
                JSONArray s = team.optJSONArray("statistics");
                for (int k = 0; s != null && k < s.length(); k++) v[t].put(s.getJSONObject(k).optString("name"), s.getJSONObject(k).optString("displayValue"));
            }
            int away = 1 - home;
            for (String[] st : STATS) {
                if (v[home].containsKey(st[0]) && v[away].containsKey(st[0])) out.add(new String[]{st[1], v[home].get(st[0]), v[away].get(st[0])});
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
