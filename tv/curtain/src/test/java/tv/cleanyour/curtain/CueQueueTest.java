package tv.cleanyour.curtain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// The queue's timing: every cue in order, each on screen for its minimum time, faster when the
// phone is ahead; one run's token only; FINALE last, and with the INSTALLED it implies.
public class CueQueueTest {
    private static final String T = "0123456789abcdef";

    private static Cue cue(String stage, int count, int total) {
        return Cue.parse(T, stage, "NONE", count, total, count, total);
    }

    @Test public void cuesComeInOrderEachForItsMinimumTime() {
        CueQueue q = new CueQueue();
        q.offer(cue("SCAN", 0, 3));
        assertEquals(Cue.Stage.SCAN, q.poll(0).stage);
        q.offer(cue("RESTORE_SAVED", 0, 0));
        // Four cards (at least four are shown): the scan takes 0.4 + 4 x 1.05 + 0.6 = 5.2 s.
        assertNull(q.poll(5.1));
        assertEquals(Cue.Stage.RESTORE_SAVED, q.poll(5.3).stage);
        q.offer(cue("OFF", 1, 3));
        q.offer(cue("OFF", 2, 3));
        assertNull(q.poll(6.2));
        assertEquals(1, q.poll(6.35).count);
        assertNull(q.poll(7.5));
        assertEquals(2, q.poll(7.6).count);
        none(q.poll(100), "nothing waiting");
    }

    @Test public void aLongScanHoldsItsCardsAndAShortOneItsFourSeconds() {
        CueQueue q = new CueQueue();
        assertEquals(Progress.scanSeconds(10), q.minimum(cue("SCAN", 0, 40), 0), 1e-9);
        assertEquals(11.5, q.minimum(cue("SCAN", 0, 40), 0), 1e-9);
        assertEquals(CueQueue.SCAN_MIN, q.minimum(cue("SCAN", 0, 40), 3), 1e-9);
        assertEquals(CueQueue.OFF, q.minimum(cue("OFF", 1, 3), 2), 1e-9);
        assertEquals(CueQueue.OFF_HURRIED, q.minimum(cue("OFF", 1, 3), 3), 1e-9);
        assertEquals(CueQueue.GROUP_DONE, q.minimum(cue("GROUP_DONE", 1, 3), 0), 1e-9);
        assertEquals(CueQueue.INSTALL, q.minimum(cue("INSTALL", 0, 0), 0), 1e-9);
        assertEquals(CueQueue.INSTALLED, q.minimum(cue("INSTALLED", 0, 0), 0), 1e-9);
        assertEquals(CueQueue.RESTORE_SAVED, q.minimum(cue("RESTORE_SAVED", 0, 0), 0), 1e-9);
    }

    @Test public void moreThanTwoWaitingHurriesTheTvAlong() {
        CueQueue q = new CueQueue();
        q.offer(cue("SCAN", 0, 10));
        q.poll(0);
        for (int k = 1; k <= 4; k++) q.offer(cue("OFF", k, 10));
        // Four wait behind the scan: it holds for 4 s, not 11.5 s.
        assertNull(q.poll(3.9));
        assertEquals(1, q.poll(4.0).count);
        // Three still wait: 0.6 s each until two or fewer remain.
        assertNull(q.poll(4.5));
        assertEquals(2, q.poll(4.65).count);
        none(q.poll(5.8), "two waiting: back to 1.2 s");
        assertEquals(3, q.poll(5.9).count);
    }

    @Test public void anotherRunsTokenIsDropped() {
        CueQueue q = new CueQueue();
        assertTrue(q.offer(cue("SCAN", 0, 1)));
        assertFalse(q.offer(Cue.parse("fedcba9876543210", "FINALE", "NONE", 0, 0, 0, 0)));
        assertFalse(q.offer(null));
        assertEquals(1, q.waiting());
    }

    @Test public void finaleIsLastAndBringsTheInstalledItImplies() {
        CueQueue q = new CueQueue();
        q.offer(cue("INSTALL", 0, 0));
        q.offer(cue("FINALE", 0, 0));
        assertEquals(Cue.Stage.INSTALL, q.poll(0).stage);
        assertNull(q.poll(2.5));
        Cue implied = q.poll(2.65);
        assertEquals(Cue.Stage.INSTALLED, implied.stage);
        assertEquals(T, implied.token);
        assertNull(q.poll(3.95));
        assertEquals(Cue.Stage.FINALE, q.poll(4.1).stage);
        assertFalse("nothing after FINALE", q.offer(cue("OFF", 1, 1)));
        assertNull(q.poll(1000));
        // Without an INSTALL (the launcher was already there) FINALE comes straight after.
        CueQueue r = new CueQueue();
        r.offer(cue("GROUP_DONE", 3, 3));
        r.offer(cue("FINALE", 0, 0));
        r.poll(0);
        assertNull(r.poll(0.25));
        assertEquals(Cue.Stage.FINALE, r.poll(0.35).stage);
    }

    private static void none(Cue c, String why) {
        org.junit.Assert.assertNull(why, c);
    }
}
