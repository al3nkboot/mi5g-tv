package com.midroid.mi5gtv.matches;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Mi5G 2.4 (M6): pure helpers for kick-off times, prayer clashes, big tournaments and the pinned score. */
final class Football {
    private Football() {}

    /** Minutes before kick-off the reminder fires, and how long a match is assumed to last. */
    static final int REMIND_BEFORE_MIN = 10;
    static final int MATCH_MINUTES = 115;
    static final int CLASH_LEAD_MIN = 10;

    /** Big tournaments on ESPN's public scoreboard: {code, Arabic name, ESPN slug}. Each returns its current or next round. */
    static final String[][] TOURNAMENTS = {
        {"fifa.world", "كأس العالم", "fifa.world"},
        {"fifa.worldq.afc", "تصفيات كأس العالم - آسيا", "fifa.worldq.afc"},
        {"fifa.cwc", "كأس العالم للأندية", "fifa.cwc"},
        {"afc.asian.cup", "كأس آسيا", "afc.asian.cup"},
        {"caf.nations", "كأس الأمم الأفريقية", "caf.nations"},
        {"uefa.euro", "كأس أمم أوروبا", "uefa.euro"},
        {"conmebol.america", "كوبا أمريكا", "conmebol.america"},
        {"uefa.nations", "دوري الأمم الأوروبية", "uefa.nations"},
        {"uefa.champions", "دوري أبطال أوروبا", "uefa.champions"},
        {"afc.champions", "دوري أبطال آسيا للنخبة", "afc.champions"},
    };

    /** The tournaments tab keeps matches from a day and a half ago up to this many days ahead. */
    static final int TOURNAMENT_DAYS = 30;

    static boolean inTournamentWindow(long kickoff, long now) {
        return kickoff > 0 && kickoff >= now - 36L * 3_600_000L && kickoff <= now + TOURNAMENT_DAYS * 86_400_000L;
    }

    /** ESPN ISO kick-off ("2026-11-18T17:00Z" or with seconds) to epoch ms; -1 when unreadable. */
    static long kickoffMs(String iso) {
        if (iso == null || iso.isEmpty()) return -1;
        try {
            String clean = iso.replace("Z", "+0000");
            String pattern = clean.length() >= 20 && clean.charAt(16) == ':' ? "yyyy-MM-dd'T'HH:mm:ssZ" : "yyyy-MM-dd'T'HH:mmZ";
            SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
            Date d = f.parse(clean);
            return d == null ? -1 : d.getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    /** When a reminder for this kick-off should fire, or -1 when it is already too late. */
    static long remindAt(long kickoff, long now) {
        if (kickoff <= 0) return -1;
        long at = kickoff - REMIND_BEFORE_MIN * 60_000L;
        if (at > now) return at;
        return kickoff > now ? now + 1_000L : -1;
    }

    /**
     * Prayers that fall between {@link #CLASH_LEAD_MIN} minutes before kick-off and the assumed end of the match.
     * {@code prayers} rows are {name, "HH:mm"} for the kick-off day in {@code zone}; sunrise is not a prayer and is skipped.
     * Returns {name, "HH:mm"} rows in order.
     */
    static List<String[]> clashes(long kickoff, List<String[]> prayers, TimeZone zone) {
        List<String[]> out = new ArrayList<>();
        if (kickoff <= 0 || prayers == null) return out;
        Calendar k = Calendar.getInstance(zone);
        k.setTimeInMillis(kickoff);
        for (String[] p : prayers) {
            if (p == null || p.length < 2 || isSunrise(p[0])) continue;
            int hm = minutes(p[1]);
            if (hm < 0) continue;
            Calendar c = (Calendar) k.clone();
            c.set(Calendar.HOUR_OF_DAY, hm / 60);
            c.set(Calendar.MINUTE, hm % 60);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long t = c.getTimeInMillis();
            if (t >= kickoff - CLASH_LEAD_MIN * 60_000L && t <= kickoff + MATCH_MINUTES * 60_000L) out.add(new String[]{p[0], p[1]});
        }
        return out;
    }

    static boolean isSunrise(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase(Locale.ROOT);
        return n.equals("الشروق") || n.equals("sunrise") || n.equals("shuruq");
    }

    static int minutes(String hhmm) {
        try {
            String[] p = hhmm.trim().split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            return h >= 0 && h < 24 && m >= 0 && m < 60 ? h * 60 + m : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** One match on a scoreboard, for the pinned corner card. */
    static final class Live {
        final String home, away, homeScore, awayScore, state, clock;
        Live(String home, String away, String homeScore, String awayScore, String state, String clock) {
            this.home = home; this.away = away; this.homeScore = homeScore; this.awayScore = awayScore; this.state = state; this.clock = clock;
        }

        /** "الهلال 1 - 0 النصر  67'": the corner card's line. */
        String line() {
            String mid = "pre".equals(state) ? " × " : " " + homeScore + " - " + awayScore + " ";
            String tail = "in".equals(state) && !clock.isEmpty() ? "  " + clock : ("post".equals(state) ? "  ✔" : "");
            return home + mid + away + tail;
        }
    }

    /** The match line from ESPN's summary JSON (header.competitions[0]); null when missing. */
    static Live fromSummary(String json) {
        try {
            JSONObject comp = new JSONObject(json).getJSONObject("header").getJSONArray("competitions").getJSONObject(0);
            JSONObject status = comp.optJSONObject("status");
            JSONObject type = status == null ? null : status.optJSONObject("type");
            String state = type == null ? "pre" : type.optString("state", "pre");
            String clock = status == null ? "" : status.optString("displayClock", "");
            if (clock.isEmpty() && "in".equals(state) && type != null) clock = type.optString("shortDetail", "");
            String home = "", away = "", hs = "0", as = "0";
            JSONArray cs = comp.getJSONArray("competitors");
            for (int c = 0; c < cs.length(); c++) {
                JSONObject t = cs.getJSONObject(c);
                JSONObject team = t.optJSONObject("team");
                String name = MainActivity.translateTeamName(team == null ? "" : team.optString("displayName", team.optString("name")));
                if ("home".equals(t.optString("homeAway", c == 0 ? "home" : "away"))) { home = name; hs = t.optString("score", "0"); }
                else { away = name; as = t.optString("score", "0"); }
            }
            return new Live(home, away, hs, as, state, clock);
        } catch (Exception e) {
            return null;
        }
    }
}
