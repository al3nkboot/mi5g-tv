package tv.cleanyour.curtain;

import android.os.SystemClock;

import java.util.ArrayList;

// Hands cues from the receiver to the live curtain, in the one process, on the main thread. While no
// curtain is attached, a few cues are kept for at most ten seconds (the start and the first cues are
// sent back to back and may beat the activity's creation, also for a second curtain in a process
// that outlived the first, RF-CURTAIN-1 F7). A curtain that attaches takes only the kept cues of the
// newest run (the token of the last one kept), so a gone run's late cues can never bind it. Nothing
// here ever starts an activity.
final class CueBus {
    interface Listener {
        void onCue(Cue cue);
    }

    private static final int KEEP = 64;
    private static final long KEEP_MS = 10_000;
    private static final ArrayList<Cue> early = new ArrayList<>();
    private static final ArrayList<Long> earlyAt = new ArrayList<>();
    private static Listener listener;

    private CueBus() {}

    static void post(Cue cue) {
        if (listener != null) {
            listener.onCue(cue);
        } else if (early.size() < KEEP) {
            early.add(cue);
            earlyAt.add(SystemClock.uptimeMillis());
        }
    }

    static void attach(Listener l) {
        listener = l;
        long now = SystemClock.uptimeMillis();
        String newest = early.isEmpty() ? null : early.get(early.size() - 1).token;
        for (int i = 0; i < early.size(); i++) {
            Cue c = early.get(i);
            if (now - earlyAt.get(i) <= KEEP_MS && c.token.equals(newest)) l.onCue(c);
        }
        early.clear();
        earlyAt.clear();
    }

    static void detach(Listener l) {
        if (listener != l) return;
        listener = null;
        early.clear();
        earlyAt.clear();
    }
}
