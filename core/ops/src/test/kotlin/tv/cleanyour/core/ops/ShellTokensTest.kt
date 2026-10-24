package tv.cleanyour.core.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// SECURITY 6.1: the metacharacter corpus against the quoter and the tokenizer, every byte alone
// and in pairs, plus the known payloads.
class ShellTokensTest {
    private val payloads = listOf(
        "\$(id)", "`id`", ";id", "|id", "&&id", "\nid", "%0a", "\$IFS", "'", "\"", "../", "-x", "a'b", "a\\b",
        "\u0000", "​", "‮", "é", "com.x;rm -rf /", "com.x' 'y", "a b", " a", "a ", "",
    )

    @Test fun quoteAcceptsOnlyPrintableAsciiWithoutApostrophe() {
        for (b in 0..255) {
            val s = b.toChar().toString()
            val ok = b in 0x20..0x7e && b != 0x27
            assertEquals(ok, ShellTokens.isSafeToken(s), "byte $b")
            if (ok) assertEquals("'$s'", ShellTokens.quote(s)) else assertFailsWith<IllegalArgumentException> { ShellTokens.quote(s) }
        }
        for (a in 0..255 step 7) for (b in 0..255 step 5) {
            val s = a.toChar().toString() + b.toChar()
            val ok = a in 0x20..0x7e && a != 0x27 && b in 0x20..0x7e && b != 0x27
            assertEquals(ok, ShellTokens.isSafeToken(s), "bytes $a $b")
        }
    }

    @Test fun payloadsAreNeutralisedOrRejected() {
        for (p in payloads) {
            if (ShellTokens.isSafeToken(p)) {
                // A safe token (no apostrophe, printable ASCII) is literal inside single quotes.
                val line = ShellTokens.line(listOf("echo", p))
                assertEquals(listOf("echo", p), ShellTokens.tokenize(line), p)
                assertTrue(line.count { it == '\'' } == 4, p)
            } else {
                assertFailsWith<IllegalArgumentException>(p) { ShellTokens.line(listOf("echo", p)) }
            }
        }
    }

    @Test fun tokenizerRoundTripsAndRejectsEverythingElse() {
        assertEquals(listOf("pm", "disable-user", "--user", "0", "com.a.b"), ShellTokens.tokenize("'pm' 'disable-user' '--user' '0' 'com.a.b'"))
        assertEquals(listOf("settings", "put", "global", "device_name", "Mi TV 4A"), ShellTokens.tokenize("'settings' 'put' 'global' 'device_name' 'Mi TV 4A'"))
        for (bad in listOf("pm list", "'pm'  'list'", " 'pm'", "'pm' ", "'pm''list'", "'pm' x", "'pm' \"list\"", "'pm' 'list' ;", "''", "'a'\n", "'a' | 'b'", "\$HOME")) {
            assertFailsWith<IllegalArgumentException>(bad) { ShellTokens.tokenize(bad) }
        }
    }

    @Test fun assertLineRejectsAnythingOutsideQuotesButSingleSpaces() {
        ShellTokens.assertLine("'a' 'b c' 'd'")
        for (bad in listOf("", "a", "'a'  'b'", " 'a'", "'a' ", "'a'x", "'a';'b'", "'a' b", "'a\n'", "'a' 'b' ;")) {
            assertFailsWith<IllegalStateException>(bad) { ShellTokens.assertLine(bad) }
        }
        assertFalse(ShellTokens.LINE_RE.matches("'a'\n"))
        assertTrue(ShellTokens.LINE_RE.matches("'" + "x".repeat(130_998) + "'"))
        assertFalse(ShellTokens.LINE_RE.matches("'" + "x".repeat(131_000) + "'"))
    }

    @Test fun lineAssertsItsOwnRoundTrip() {
        val argv = listOf("cmd", "package", "set-home-activity", "--user", "0", "com.gathin.tvhome/.MainActivity")
        val line = ShellTokens.line(argv)
        assertEquals("'cmd' 'package' 'set-home-activity' '--user' '0' 'com.gathin.tvhome/.MainActivity'", line)
        assertEquals(argv, ShellTokens.tokenize(line))
        assertFailsWith<IllegalArgumentException> { ShellTokens.line(emptyList()) }
    }
}
