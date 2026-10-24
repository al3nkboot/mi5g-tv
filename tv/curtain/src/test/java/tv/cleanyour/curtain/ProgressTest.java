package tv.cleanyour.curtain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// The bar's percent per stage, the cards and when they go, the scan's length.
public class ProgressTest {
    @Test public void percentMapFollowsTheStages() {
        assertEquals(0f, Progress.percent(Cue.Stage.SCAN, 0, 12, 0f), 1e-6);
        assertEquals(3f, Progress.percent(Cue.Stage.SCAN, 0, 12, .5f), 1e-6);
        assertEquals(6f, Progress.percent(Cue.Stage.SCAN, 0, 12, 2f), 1e-6);
        assertEquals(6f, Progress.percent(Cue.Stage.RESTORE_SAVED, 0, 0, 0f), 1e-6);
        assertEquals(8f + 62f / 12f, Progress.percent(Cue.Stage.OFF, 1, 12, 0f), 1e-4);
        assertEquals(70f, Progress.percent(Cue.Stage.OFF, 12, 12, 0f), 1e-6);
        assertEquals(70f, Progress.percent(Cue.Stage.GROUP_DONE, 12, 12, 0f), 1e-6);
        assertEquals(70f, Progress.percent(Cue.Stage.OFF, 13, 12, 0f), 1e-6);
        assertEquals(8f, Progress.percent(Cue.Stage.OFF, 0, 0, 0f), 1e-6);
        assertEquals(72f, Progress.percent(Cue.Stage.INSTALL, 0, 0, 0f), 1e-6);
        assertEquals(92f, Progress.percent(Cue.Stage.INSTALLED, 0, 0, 0f), 1e-6);
        assertEquals(96f, Progress.percent(Cue.Stage.FINALE, 0, 0, 0f), 1e-6);
        float last = -1f;
        for (int k = 1; k <= 30; k++) {
            float p = Progress.percent(Cue.Stage.OFF, k, 30, 0f);
            assertEquals(true, p > last);
            last = p;
        }
    }

    @Test public void cardsAreFourToTen() {
        assertEquals(4, Progress.cards(0));
        assertEquals(4, Progress.cards(3));
        assertEquals(7, Progress.cards(7));
        assertEquals(10, Progress.cards(10));
        assertEquals(10, Progress.cards(41));
    }

    @Test public void cardsGoOnePerSwitchOffOrSpreadEvenly() {
        assertEquals(0, Progress.pulled(0, 3, 4));
        assertEquals(1, Progress.pulled(1, 3, 4));
        assertEquals(3, Progress.pulled(3, 3, 4));
        assertEquals(7, Progress.pulled(7, 7, 7));
        // 30 switch-offs, 10 cards: one card every third, the last with the last.
        assertEquals(0, Progress.pulled(2, 30, 10));
        assertEquals(1, Progress.pulled(3, 30, 10));
        assertEquals(9, Progress.pulled(29, 30, 10));
        assertEquals(10, Progress.pulled(30, 30, 10));
        assertEquals(10, Progress.pulled(31, 30, 10));
    }

    @Test public void theScanTakesAboutASecondACard() {
        assertEquals(11.5, Progress.scanSeconds(10), 1e-9);
        assertEquals(5.2, Progress.scanSeconds(4), 1e-9);
    }
}
