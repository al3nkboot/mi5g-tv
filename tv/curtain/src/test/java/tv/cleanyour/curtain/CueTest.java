package tv.cleanyour.curtain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

// The cue parser takes exactly what the phone's renderer sends and drops everything else.
public class CueTest {
    private static final String T = "0123456789abcdef";

    @Test public void aGoodCueParses() {
        Cue c = Cue.parse(T, "OFF", "ADVERTS", 3, 12, 2, 4);
        assertNotNull(c);
        assertEquals(Cue.Stage.OFF, c.stage);
        assertEquals(Cue.Group.ADVERTS, c.group);
        assertEquals(3, c.count);
        assertEquals(12, c.total);
        assertEquals(2, c.groupCount);
        assertEquals(4, c.groupTotal);
        for (Cue.Stage s : Cue.Stage.values()) assertNotNull(s.name(), Cue.parse(T, s.name(), "NONE", 0, 0, 0, 0));
        for (Cue.Group g : Cue.Group.values()) assertNotNull(g.name(), Cue.parse(T, "OFF", g.name(), 1, 1, 1, 1));
        assertNotNull(Cue.parse(T, "OFF", "NONE", 999, 999, 999, 999));
    }

    @Test public void tokensOutsideTheGrammarAreDropped() {
        for (String t : new String[]{null, "", "0123", "0123456789ABCDEF", "0123456789abcdeg", "0123456789abcdef0", " 123456789abcdef", "0123456789abcde'"}) {
            assertNull(String.valueOf(t), Cue.parse(t, "SCAN", "NONE", 0, 0, 0, 0));
        }
    }

    @Test public void stagesAndGroupsOutsideTheVocabularyAreDropped() {
        for (String s : new String[]{null, "", "scan", "SCAN ", "REBOOT", "FINALE;", "A_VERY_LONG_STAGE_NAME", "OFF\n"}) {
            assertNull(String.valueOf(s), Cue.parse(T, s, "NONE", 0, 0, 0, 0));
        }
        for (String g : new String[]{null, "", "adverts", "ADVERTS!", "EVERYTHING", "Xiaomi extras"}) {
            assertNull(String.valueOf(g), Cue.parse(T, "OFF", g, 1, 1, 1, 1));
        }
    }

    @Test public void numbersOutOfRangeOrMissingAreDropped() {
        // A missing int extra reads as -1.
        assertNull(Cue.parse(T, "OFF", "NONE", -1, 3, 0, 0));
        assertNull(Cue.parse(T, "OFF", "NONE", 1, -1, 0, 0));
        assertNull(Cue.parse(T, "OFF", "NONE", 1, 3, -1, 0));
        assertNull(Cue.parse(T, "OFF", "NONE", 1, 3, 0, -1));
        assertNull(Cue.parse(T, "OFF", "NONE", 1000, 3, 0, 0));
        assertNull(Cue.parse(T, "OFF", "NONE", 1, Integer.MAX_VALUE, 0, 0));
    }
}
