package tv.cleanyour.core.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Mi5gHomeLinesTest {
    private val ours = ShellRenderer.MI5G_ACCESSIBILITY

    @Test fun mergeKeepsOtherServicesAndAddsOursOnce() {
        val v = ShellRenderer.mergeAccessibility("a.b/.X:com.gathin.tvhome/.InputKeyService", ours)
        assertEquals("a.b/.X:com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService", v)
        assertEquals(ours.joinToString(":"), ShellRenderer.mergeAccessibility("null\n", ours))
    }

    @Test fun mergeRemovesOursOnReturn() {
        assertEquals("a.b/.X", ShellRenderer.mergeAccessibility("a.b/.X:" + ours.joinToString(":"), emptyList(), ours))
        assertEquals("", ShellRenderer.mergeAccessibility(ours.joinToString(":"), emptyList(), ours))
    }

    @Test fun returnNeverUninstallsAndOnlyTouchesStockHomes() {
        val lines = ShellRenderer.returnToStockLines(listOf("com.google.android.tvlauncher")).map { it.text }
        assertTrue(lines.none { "uninstall" in it || "disable-user" in it })
        assertTrue(lines.any { "enable" in it && "tvlauncher" in it })
        assertFailsWith<IllegalArgumentException> { ShellRenderer.returnToStockLines(listOf("com.evil")) }
    }

    @Test fun lockDisablesXiaomiHomeOnlyWhenAsked() {
        assertTrue(ShellRenderer.keepMi5gHomeLines(false).none { "disable-user" in it.text })
        assertEquals(2, ShellRenderer.keepMi5gHomeLines(true).count { "disable-user" in it.text })
        assertTrue(ShellRenderer.keepMi5gHomeLines(true).none { "uninstall" in it.text })
    }

    @Test fun emptyServiceListDeletesTheSetting() {
        assertEquals(1, ShellRenderer.putAccessibilityLines("").size)
        assertFailsWith<IllegalArgumentException> { ShellRenderer.putAccessibilityLines("x;rm") }
    }

    // Mi5G 2.0.1: the arrange-from-phone button grants one permission to our launcher only, then opens its QR screen.
    @Test fun arrangeHomeOpensOnlyOurLauncherScreen() {
        val lines = ShellRenderer.arrangeHomeLines().map { it.text }
        assertEquals(2, lines.size)
        assertEquals(listOf("pm", "grant", "com.gathin.tvhome", "android.permission.WRITE_SECURE_SETTINGS"), ShellTokens.tokenize(lines[0]))
        assertEquals(listOf("am", "start", "-n", "com.gathin.tvhome/.ArrangeActivity"), ShellTokens.tokenize(lines[1]))
        assertTrue(lines.none { "uninstall" in it || "disable" in it })
    }
}
