package tv.cleanyour.core.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 2.4 M3: the phone companion's shell lines and the live drawing token.
class CompanionLinesTest {
    @Test fun drawingRoundTripsOnTheGrid() {
        val token = LiveBoard.encode(listOf(listOf(0f to 0f, 0.5f to 0.5f, 1f to 1f), listOf(0.25f to 0.75f)))
        assertEquals("000000500500999999-250749", token)
        assertTrue(LiveBoard.valid(token))
        assertEquals(listOf(listOf(0 to 0, 500 to 500, 999 to 999), listOf(250 to 749)), LiveBoard.decode(token))
    }

    @Test fun tinyStepsAreDroppedAndOutsidePointsClamped() {
        assertEquals("000000", LiveBoard.encode(listOf(listOf(0f to 0f, 0.001f to 0.001f, -3f to -3f))))
        assertEquals("", LiveBoard.encode(listOf(emptyList())))
    }

    @Test fun aLongDrawingStopsUnderTheTokenLimit() {
        val zigzag = (0 until 5000).map { i -> (i % 100) / 99f to (if (i % 2 == 0) 0f else 1f) }
        val token = LiveBoard.encode(listOf(zigzag, zigzag))
        assertTrue(token.length <= LiveBoard.MAX_CHARS)
        assertTrue(LiveBoard.valid(token))
        assertTrue(ShellTokens.isSafeToken(token))
    }

    @Test fun badTokensAreRefused() {
        assertFalse(LiveBoard.valid("12345"))
        assertFalse(LiveBoard.valid("000000--000000"))
        assertFalse(LiveBoard.valid("000000;reboot"))
        assertTrue(LiveBoard.decode("abc").isEmpty())
    }

    @Test fun liveBoardLineCarriesArabicAsHex() {
        val argv = ShellTokens.tokenize(ShellRenderer.liveBoardLine("", "سلام").text)
        assertEquals(listOf("am", "start", "-n", "com.gathin.tvhome/.LiveBoardActivity", "--es", "hex", "d8b3d984d8a7d985"), argv)
        assertTrue(runCatching { ShellRenderer.liveBoardLine("", " ") }.isFailure)
        assertTrue(runCatching { ShellRenderer.liveBoardLine("x';reboot", "hi") }.isFailure)
    }

    @Test fun storePageOnlyForAPackageName() {
        assertEquals(listOf("am", "start", "-a", "android.intent.action.VIEW", "-d", "market://details?id=org.xbmc.kodi", "-p", "com.android.vending"),
            ShellTokens.tokenize(ShellRenderer.playStorePageLine("org.xbmc.kodi").text))
        assertTrue(runCatching { ShellRenderer.playStorePageLine("kodi") }.isFailure)
        assertTrue(runCatching { ShellRenderer.playStorePageLine("a.b&c=d") }.isFailure)
    }

    @Test fun allAppsOpensTheLauncherGrid() {
        assertEquals(listOf("am", "start", "-n", "com.gathin.tvhome/.AllAppsActivity"), ShellTokens.tokenize(ShellRenderer.tvAllAppsLine().text))
    }
}
