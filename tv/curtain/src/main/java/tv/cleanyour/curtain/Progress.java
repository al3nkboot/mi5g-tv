package tv.cleanyour.curtain;

// The numbers behind the picture (RFC-CURTAIN-1), kept apart from Android so they are tested on
// the JVM: the bar's percent per stage, how many cards stand for the old home screen, when each is
// pulled in, and how long the scan of them takes.
final class Progress {
    private Progress() {}

    static final int MAX_CARDS = 10;
    static final int MIN_CARDS = 4;
    // The scan's pace (mockup revision 3): first visit after 0.4 s, one card every 1.05 s, then the
    // lantern goes home (0.1 s later, a 0.5 s move).
    static final double SCAN_LEAD = 0.4;
    static final double SCAN_PER_CARD = 1.05;
    static final double SCAN_TAIL = 0.6;

    // The bar: SCAN 0 to 6 as the scan runs, RESTORE_SAVED 6, OFF 8 to 70 over the plan's
    // switch-offs, GROUP_DONE where its last OFF was, INSTALL 72, INSTALLED 92, FINALE 96.
    static float percent(Cue.Stage stage, int count, int total, float scanDone) {
        switch (stage) {
            case SCAN: return 6f * clamp01(scanDone);
            case RESTORE_SAVED: return 6f;
            case OFF:
            case GROUP_DONE: return total <= 0 ? 8f : 8f + 62f * Math.min(count, total) / total;
            case INSTALL: return 72f;
            case INSTALLED: return 92f;
            default: return 96f;
        }
    }

    // Cards on screen: the plan's switch-offs, at least four (the old home screen is never empty)
    // and at most ten.
    static int cards(int total) {
        return Math.max(MIN_CARDS, Math.min(MAX_CARDS, total));
    }

    // Cards pulled in after `count` of `total` switch-offs: one per switch-off while they last,
    // spread evenly when there are more switch-offs than cards, so the last goes with the last.
    static int pulled(int count, int total, int cards) {
        if (count <= 0 || total <= 0) return 0;
        if (total <= cards) return Math.min(count, cards);
        return (int) Math.min(cards, ((long) Math.min(count, total) * cards) / total);
    }

    static double scanSeconds(int cards) {
        return SCAN_LEAD + cards * SCAN_PER_CARD + SCAN_TAIL;
    }

    private static float clamp01(float f) {
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }
}
