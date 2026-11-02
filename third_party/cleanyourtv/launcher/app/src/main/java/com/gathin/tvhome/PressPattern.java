package com.gathin.tvhome;

/**
 * Mi5G 2.0: tells a single, double or triple press from a hold for one remote button. Pure state
 * machine fed with key down/up times; the caller schedules {@link #deadline()} and calls
 * {@link #timeout(long)} when it passes. Kept free of Android types so it runs in plain JVM tests.
 */
final class PressPattern {
    static final int NONE = 0, SINGLE = 1, DOUBLE = 2, TRIPLE = 3, HOLD = 4;
    static final long GAP_MS = 350;   // max pause between presses of a double/triple
    static final long HOLD_MS = 600;  // a press this long is a hold

    private int presses;
    private long downAt = -1, lastUpAt = -1;
    private boolean holdFired;

    /** Key down. Returns HOLD once if this press already counts as a hold (auto-repeat), else NONE. */
    int down(long now, int repeatCount) {
        if (downAt < 0) { downAt = now; holdFired = false; }
        if (!holdFired && repeatCount > 0 && now - downAt >= HOLD_MS) { holdFired = true; reset(); holdFired = true; return HOLD; }
        return NONE;
    }

    /** Key up. Returns HOLD for a long press without repeats, TRIPLE at the third press, else NONE (wait). */
    int up(long now) {
        if (holdFired) { holdFired = false; downAt = -1; return NONE; }
        long held = downAt < 0 ? 0 : now - downAt;
        downAt = -1;
        if (held >= HOLD_MS) { reset(); return HOLD; }
        presses++;
        lastUpAt = now;
        if (presses >= 3) { reset(); return TRIPLE; }
        return NONE;
    }

    /** When the pending presses resolve if nothing else comes; -1 when idle. */
    long deadline() { return presses > 0 && downAt < 0 ? lastUpAt + GAP_MS : -1; }

    /** Called at or after {@link #deadline()}: resolves the presses counted so far. */
    int timeout(long now) {
        if (presses == 0 || downAt >= 0 || now < lastUpAt + GAP_MS) return NONE;
        int r = presses == 1 ? SINGLE : DOUBLE;
        reset();
        return r;
    }

    private void reset() { presses = 0; lastUpAt = -1; }

    static String suffix(int pattern) {
        switch (pattern) { case SINGLE: return "1"; case DOUBLE: return "2"; case TRIPLE: return "3"; case HOLD: return "H"; default: return ""; }
    }
}
