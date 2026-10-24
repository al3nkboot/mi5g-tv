package tv.cleanyour.curtain;

import java.util.ArrayDeque;

// The cues in the order they came, each held on screen for a minimum time so every stage can be
// seen (RFC-CURTAIN-1): SCAN as long as the scan of its cards takes (at least 4 s), RESTORE_SAVED
// 1 s, OFF 1.2 s, GROUP_DONE 0.3 s, INSTALL 2.6 s, INSTALLED 1.4 s. When more than two are waiting,
// SCAN drops to 4 s and OFF to 0.6 s so the TV catches up with the phone. FINALE is the last word.
// The first cue binds the queue to its run token; a cue of another run is dropped. FINALE after an
// INSTALL that never got its INSTALLED brings the INSTALLED first. Times are seconds on any clock.
final class CueQueue {
    static final double SCAN_MIN = 4.0;
    static final double RESTORE_SAVED = 1.0;
    static final double OFF = 1.2;
    static final double OFF_HURRIED = 0.6;
    static final double GROUP_DONE = 0.3;
    static final double INSTALL = 2.6;
    static final double INSTALLED = 1.4;
    static final int BACKLOG = 2;

    private final ArrayDeque<Cue> waiting = new ArrayDeque<>();
    private String token;
    private Cue current;
    private double appliedAt;
    private boolean sawInstall;
    private boolean sawInstalled;
    private boolean finished;

    // False when the cue is dropped (another run's token, or after FINALE).
    boolean offer(Cue cue) {
        if (cue == null || finished) return false;
        if (token == null) token = cue.token;
        else if (!token.equals(cue.token)) return false;
        waiting.addLast(cue);
        return true;
    }

    int waiting() {
        return waiting.size();
    }

    Cue current() {
        return current;
    }

    // The next cue to show now, or null while the one on screen still needs its time.
    Cue poll(double now) {
        if (waiting.isEmpty() || finished) return null;
        if (current != null && now - appliedAt < minimum(current, waiting.size())) return null;
        Cue next = waiting.peekFirst();
        if (next.stage == Cue.Stage.FINALE && sawInstall && !sawInstalled) {
            next = Cue.implied(Cue.Stage.INSTALLED, token);
        } else {
            waiting.pollFirst();
        }
        if (next.stage == Cue.Stage.INSTALL) sawInstall = true;
        if (next.stage == Cue.Stage.INSTALLED) sawInstalled = true;
        if (next.stage == Cue.Stage.FINALE) finished = true;
        current = next;
        appliedAt = now;
        return next;
    }

    // How long a cue stays before the next may follow, given how many wait behind it.
    double minimum(Cue cue, int backlog) {
        boolean hurry = backlog > BACKLOG;
        switch (cue.stage) {
            case SCAN: return hurry ? SCAN_MIN : Math.max(SCAN_MIN, Progress.scanSeconds(Progress.cards(cue.total)));
            case RESTORE_SAVED: return RESTORE_SAVED;
            case OFF: return hurry ? OFF_HURRIED : OFF;
            case GROUP_DONE: return GROUP_DONE;
            case INSTALL: return INSTALL;
            case INSTALLED: return INSTALLED;
            default: return Double.MAX_VALUE;
        }
    }
}
