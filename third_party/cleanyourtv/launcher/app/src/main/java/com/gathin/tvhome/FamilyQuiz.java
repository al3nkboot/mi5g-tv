package com.gathin.tvhome;

import android.content.res.Resources;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 2.4 M7 (backlog 37, UrTV idea, own code and own questions): a family quiz on the TV answered from phones.
 * Two games: four-choice general knowledge and true or false. Up to eight players join from the page behind the
 * TV's QR; each question runs for {@link #SECONDS} seconds and a right answer scores 100 plus up to 50 for speed.
 * The engine is plain Java (tests); the server threads and the TV screen share it, hence synchronized.
 */
final class FamilyQuiz {
    static final int GAME_CHOICE = 0, GAME_TRUE_FALSE = 1;
    static final int LOBBY = 0, QUESTION = 1, REVEAL = 2, DONE = 3;
    static final int ROUND = 10, MAX_PLAYERS = 8, MAX_NAME = 16, SECONDS = 20;

    static final class Question {
        final String text;
        final String[] options;
        final int answer;
        Question(String text, String[] options, int answer) { this.text = text; this.options = options; this.answer = answer; }
    }

    static final class Player {
        final String id, name;
        int score, choice = -1, gained;
        long answeredAt;
        Player(String id, String name) { this.id = id; this.name = name; }
    }

    /** "question|a|b|c|d|2" (four choices) or "question|1" (true or false: 1 true, 0 false); null when malformed. */
    static Question parse(String line, String yes, String no) {
        if (line == null) return null;
        String[] p = line.split("\\|", -1);
        try {
            if (p.length == 6) {
                int a = Integer.parseInt(p[5].trim());
                if (a < 0 || a > 3 || p[0].trim().isEmpty()) return null;
                return new Question(p[0].trim(), new String[]{p[1].trim(), p[2].trim(), p[3].trim(), p[4].trim()}, a);
            }
            if (p.length == 2) {
                int a = Integer.parseInt(p[1].trim());
                if ((a != 0 && a != 1) || p[0].trim().isEmpty()) return null;
                return new Question(p[0].trim(), new String[]{yes, no}, a == 1 ? 0 : 1);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    static List<Question> bank(Resources r, int game) {
        String[] lines = r.getStringArray(game == GAME_TRUE_FALSE ? R.array.mi5g_quiz_true_false : R.array.mi5g_quiz_choice);
        String yes = r.getString(R.string.mi5g_quiz_true), no = r.getString(R.string.mi5g_quiz_false);
        List<Question> out = new ArrayList<>();
        for (String l : lines) {
            Question q = parse(l, yes, no);
            if (q != null) out.add(q);
        }
        return out;
    }

    /** Points for an answer {@code ms} after the question showed. */
    static int points(boolean right, long ms) {
        if (!right) return 0;
        long total = SECONDS * 1000L;
        long left = Math.max(0, total - Math.max(0, ms));
        return 100 + (int) (50 * left / total);
    }

    /** Trims, drops control and markup characters, and caps the length; empty when nothing is left. */
    static String cleanName(String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        raw.codePoints().forEach(c -> {
            if (Character.isISOControl(c) || c == '<' || c == '>' || c == '&' || c == '"' || c == '\'' || Character.getType(c) == Character.FORMAT) return;
            b.appendCodePoint(c);
        });
        String s = b.toString().trim().replaceAll("\\s+", " ");
        return s.codePointCount(0, s.length()) > MAX_NAME ? s.substring(0, s.offsetByCodePoints(0, MAX_NAME)) : s;
    }

    private final Random random;
    private final SecureRandom ids = new SecureRandom();
    private final Map<String, Player> players = new LinkedHashMap<>();
    private List<Question> round = new ArrayList<>();
    private int game = GAME_CHOICE, index = -1, phase = LOBBY;
    private long shownAt;

    FamilyQuiz(Random random) { this.random = random; }

    synchronized int phase() { return phase; }
    synchronized int game() { return game; }
    synchronized int index() { return index; }
    synchronized int total() { return round.size(); }
    synchronized Question current() { return index >= 0 && index < round.size() ? round.get(index) : null; }
    synchronized long shownAt() { return shownAt; }
    synchronized int playerCount() { return players.size(); }

    synchronized List<Player> players() { return new ArrayList<>(players.values()); }

    /** Players by score, best first (ties keep the join order). */
    synchronized List<Player> standings() {
        List<Player> s = new ArrayList<>(players.values());
        s.sort((a, b) -> Integer.compare(b.score, a.score));
        return s;
    }

    /** A new player's id, or null when the name is empty, taken, the room is full or the game is over. */
    synchronized String join(String rawName) {
        String name = cleanName(rawName);
        if (name.isEmpty() || players.size() >= MAX_PLAYERS || phase == DONE) return null;
        for (Player p : players.values()) if (p.name.equalsIgnoreCase(name)) return null;
        byte[] b = new byte[8];
        ids.nextBytes(b);
        StringBuilder id = new StringBuilder();
        for (byte x : b) id.append(String.format("%02x", x & 0xff));
        players.put(id.toString(), new Player(id.toString(), name));
        return id.toString();
    }

    /** Picks {@link #ROUND} questions of {@code g} from {@code bank} and shows the first; scores start at zero. */
    synchronized boolean start(int g, List<Question> bank, long now) {
        if (players.isEmpty() || bank.isEmpty()) return false;
        game = g;
        List<Question> pool = new ArrayList<>(bank);
        Collections.shuffle(pool, random);
        round = new ArrayList<>(pool.subList(0, Math.min(ROUND, pool.size())));
        for (Player p : players.values()) { p.score = 0; p.choice = -1; p.gained = 0; }
        index = -1;
        return next(now);
    }

    /** The next question, or DONE after the last. */
    synchronized boolean next(long now) {
        if (index + 1 >= round.size()) { phase = DONE; return false; }
        index++;
        phase = QUESTION;
        shownAt = now;
        for (Player p : players.values()) { p.choice = -1; p.gained = 0; p.answeredAt = 0; }
        return true;
    }

    /** One answer per player per question, only while the question is open and in time. */
    synchronized boolean answer(String id, int q, int choice, long now) {
        Player p = id == null ? null : players.get(id);
        Question cur = current();
        if (p == null || cur == null || phase != QUESTION || q != index || p.choice >= 0) return false;
        if (choice < 0 || choice >= cur.options.length || now - shownAt > SECONDS * 1000L) return false;
        p.choice = choice;
        p.answeredAt = now;
        return true;
    }

    synchronized int answered() {
        int n = 0;
        for (Player p : players.values()) if (p.choice >= 0) n++;
        return n;
    }

    synchronized boolean allAnswered() { return !players.isEmpty() && answered() == players.size(); }

    synchronized boolean timeUp(long now) { return phase == QUESTION && now - shownAt >= SECONDS * 1000L; }

    /** Closes the question and adds the points. */
    synchronized void reveal() {
        Question cur = current();
        if (phase != QUESTION || cur == null) return;
        for (Player p : players.values()) {
            p.gained = p.choice < 0 ? 0 : points(p.choice == cur.answer, p.answeredAt - shownAt);
            p.score += p.gained;
        }
        phase = REVEAL;
    }

    /** Back to the lobby with the same players (play again). */
    synchronized void reset() {
        phase = LOBBY;
        index = -1;
        round = new ArrayList<>();
        for (Player p : players.values()) { p.score = 0; p.choice = -1; p.gained = 0; }
    }

    /** What a phone sees. Unknown ids get the phase only, so the page can ask for a name again. */
    synchronized JSONObject state(String id) throws JSONException {
        JSONObject o = new JSONObject().put("phase", phase).put("q", index).put("total", round.size()).put("game", game);
        Player me = id == null ? null : players.get(id);
        o.put("joined", me != null);
        Question cur = current();
        if (cur != null && (phase == QUESTION || phase == REVEAL)) {
            o.put("question", cur.text);
            JSONArray opts = new JSONArray();
            for (String s : cur.options) opts.put(s);
            o.put("options", opts);
            if (phase == QUESTION) o.put("left", Math.max(0, SECONDS - (int) ((System.currentTimeMillis() - shownAt) / 1000)));
            if (phase == REVEAL) o.put("answer", cur.answer);
        }
        if (me != null) {
            o.put("name", me.name).put("score", me.score).put("choice", me.choice).put("gained", me.gained);
            List<Player> s = standings();
            o.put("rank", s.indexOf(me) + 1).put("players", s.size());
        }
        return o;
    }
}
