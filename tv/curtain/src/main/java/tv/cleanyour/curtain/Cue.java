package tv.cleanyour.curtain;

import java.util.regex.Pattern;

// One cue from the phone's plan (RFC-CURTAIN-1): a stage, the switch-offs so far over the whole plan
// and within one group of a fixed vocabulary. No free text: the words live in this app. Built only
// by parse, which drops anything outside the grammar the phone's renderer uses.
final class Cue {
    enum Stage { SCAN, RESTORE_SAVED, OFF, GROUP_DONE, INSTALL, INSTALLED, FINALE }
    enum Group { NONE, ADVERTS, TRACKING, NOBODY_ASKED, BRAND_EXTRAS, SHOP_DEMO, OTHER }

    static final int MAX = 999;
    private static final Pattern TOKEN = Pattern.compile("^[0-9a-f]{16}$");
    private static final Pattern WORD = Pattern.compile("^[A-Z_]{1,16}$");

    final String token;
    final Stage stage;
    final Group group;
    final int count;
    final int total;
    final int groupCount;
    final int groupTotal;

    private Cue(String token, Stage stage, Group group, int count, int total, int groupCount, int groupTotal) {
        this.token = token;
        this.stage = stage;
        this.group = group;
        this.count = count;
        this.total = total;
        this.groupCount = groupCount;
        this.groupTotal = groupTotal;
    }

    // A cue the queue adds itself (FINALE implies INSTALLED), with the token it already holds.
    static Cue implied(Stage stage, String token) {
        return new Cue(token, stage, Group.NONE, 0, 0, 0, 0);
    }

    // Null for anything malformed: a missing or mistyped extra arrives as null or -1.
    static Cue parse(String token, String stage, String group, int count, int total, int groupCount, int groupTotal) {
        if (token == null || !TOKEN.matcher(token).matches()) return null;
        Stage s = stageOf(stage);
        Group g = groupOf(group);
        if (s == null || g == null) return null;
        if (!inRange(count) || !inRange(total) || !inRange(groupCount) || !inRange(groupTotal)) return null;
        return new Cue(token, s, g, count, total, groupCount, groupTotal);
    }

    private static boolean inRange(int n) {
        return n >= 0 && n <= MAX;
    }

    private static Stage stageOf(String s) {
        if (s == null || !WORD.matcher(s).matches()) return null;
        for (Stage v : Stage.values()) if (v.name().equals(s)) return v;
        return null;
    }

    private static Group groupOf(String s) {
        if (s == null || !WORD.matcher(s).matches()) return null;
        for (Group v : Group.values()) if (v.name().equals(s)) return v;
        return null;
    }

    @Override public String toString() {
        return stage + " " + count + "/" + total + " " + group + " " + groupCount + "/" + groupTotal;
    }
}
