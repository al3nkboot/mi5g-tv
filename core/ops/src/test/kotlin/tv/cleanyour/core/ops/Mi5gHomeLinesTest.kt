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
        // 2.4: verifies WRITE_SECURE_SETTINGS is revoked from com.gathin.tvhome (M1 item 7)
        assertTrue(lines.any { "revoke" in it && "com.gathin.tvhome" in it && "WRITE_SECURE_SETTINGS" in it })
        assertFailsWith<IllegalArgumentException> { ShellRenderer.returnToStockLines(listOf("com.evil")) }
    }

    // 2.4 (Keep Alive): Re-apply home screen sets Home, gives the launcher back the grant its helper revival needs,
    // and never switches the stock home off or uninstalls anything.
    @Test fun reapplyHomeSetsHomeAndGrantsTheRevival() {
        val lines = ShellRenderer.reapplyHomeLines().map { ShellTokens.tokenize(it.text) }
        assertEquals(listOf("cmd", "package", "set-home-activity", "com.gathin.tvhome/.MainActivity"), lines[0])
        assertEquals(listOf("pm", "grant", "com.gathin.tvhome", "android.permission.WRITE_SECURE_SETTINGS"), lines[1])
        assertTrue(lines.last().containsAll(listOf("am", "start", "android.intent.category.HOME", "com.gathin.tvhome/.MainActivity")))
        assertTrue(lines.none { "disable-user" in it || "uninstall" in it || "revoke" in it })
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

    // 2.0.2: after installing from Mi5G, Home comes back on TVs that reopen the last app (TCL).
    @Test fun backToHomeStartsOnlyTheHomeIntent() {
        assertEquals(listOf("am", "start", "-n", "com.gathin.tvhome/.MainActivity"),
            ShellTokens.tokenize(ShellRenderer.backToHomeLine().text))
    }

    // 2.4: the remote's Play Store button opens the store, nothing else.
    @Test fun openPlayStoreOnlyStartsTheStore() {
        val lines = ShellRenderer.openPlayStoreLines().map { ShellTokens.tokenize(it.text) }
        assertEquals(listOf("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.APP_MARKET"), lines[0])
        assertEquals(listOf("monkey", "-p", "com.android.vending", "-c", "android.intent.category.LEANBACK_LAUNCHER", "1"), lines[1])
    }

    // 2.4 (v1.7 item 5): the Play Store stops running in the background without being switched off, and comes back.
    @Test fun playStoreBackgroundIsRestrictedNotDisabled() {
        val stop = ShellRenderer.playStoreBackgroundLines(true).map { ShellTokens.tokenize(it.text) }
        assertEquals(listOf(
            listOf("cmd", "appops", "set", "com.android.vending", "RUN_IN_BACKGROUND", "ignore"),
            listOf("cmd", "appops", "set", "com.android.vending", "RUN_ANY_IN_BACKGROUND", "ignore"),
            listOf("am", "force-stop", "com.android.vending"),
        ), stop)
        val allow = ShellRenderer.playStoreBackgroundLines(false).map { ShellTokens.tokenize(it.text) }
        assertEquals(listOf(
            listOf("cmd", "appops", "set", "com.android.vending", "RUN_IN_BACKGROUND", "default"),
            listOf("cmd", "appops", "set", "com.android.vending", "RUN_ANY_IN_BACKGROUND", "default"),
        ), allow)
        assertTrue((stop + allow).none { "disable-user" in it || "uninstall" in it })
    }
}
