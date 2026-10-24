package tv.cleanyour.core.backup

import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Structure checks for the files no interpreter runs here (restore.ps1, commands.txt): the same
// steps as restore.sh, each TV line quoted so that it reads back as exactly the renderer's line.
class RestoreScriptsTest {
    private val steps = RestoreCommands(ShellRenderer()).steps(Scenario.changes())
    private val header = ScriptHeader(Samples.miTvId(), "Mi TV 4A", "MiTV-AXSO0", "2026-10-19", "0.1.0")

    // PowerShell single-quoted literals: 'text' with '' for an apostrophe.
    private val psCall = Regex("^(Step|AppStep|NotHere) '((?:[^']|'')*)' '((?:[^']|'')*)'$")
    private fun psUnquote(s: String) = s.replace("''", "'")

    @Test fun ps1CarriesEveryLineExactly() {
        val ps = RestoreScripts.ps1(header, steps)
        val calls = ps.lines().mapNotNull { psCall.matchEntire(it) }
        val expected = steps.flatMap { s ->
            when (s.kind) {
                RestoreStepKind.PUT_BACK -> s.lines.map { "Step" to it }
                RestoreStepKind.REMOVE_APP -> s.lines.map { "AppStep" to it }
                RestoreStepKind.NOT_FROM_LAPTOP -> listOf("NotHere" to (s.note ?: ""))
            }
        }
        assertEquals(expected, calls.map { it.groupValues[1] to psUnquote(it.groupValues[3]) })
        assertTrue(ps.lines().first { !it.startsWith("#") } == "param(", "param must be the first statement")
        assertTrue(ps.contains("\$TvId = '${header.tvId.value}'"))
    }

    @Test fun commandsTxtIsTvPyBatchInput() {
        val txt = RestoreScripts.commandsTxt(header, steps)
        val runnable = txt.lines().filter { it.isNotBlank() && !it.trim().startsWith("#") }
        assertEquals(steps.filter { it.kind == RestoreStepKind.PUT_BACK }.flatMap { it.lines }, runnable)
        assertTrue(txt.lines().contains("# remove-app: 'pm' 'uninstall' '--user' '0' 'com.gathin.tvhome'"))
    }

    @Test fun hostileTextNeverLeavesItsLiteral() {
        val h = header.copy(tvName = "a'\n\$(reboot)`x`\r\n# \"b", model = "m''\u2028n")
        for (text in listOf(RestoreScripts.sh(h, steps), RestoreScripts.ps1(h, steps), RestoreScripts.commandsTxt(h, steps))) {
            assertTrue(text.lines().all { Regex("^[ -~]*$").matches(it) }, "printable ASCII only")
            assertTrue(text.lines().none { it.startsWith("\$(reboot)") || it.startsWith("# \"b") })
        }
        val sh = RestoreScripts.sh(h, steps)
        assertTrue(sh.lines().contains("TV_NAME='a'\\''?\$(reboot)`x`??# \"b'"), sh.lines().first { it.startsWith("TV_NAME=") })
        val ps = RestoreScripts.ps1(h, steps)
        assertTrue(ps.lines().contains("\$TvName = 'a''?\$(reboot)`x`??# \"b'"), ps.lines().first { it.startsWith("\$TvName") })
    }

    @Test fun shWordQuoting() {
        assertEquals("\"pm enable --user 0 'a.b'\"", TextSafety.shWord("pm enable --user 0 'a.b'"))
        assertEquals("\"'pm' 'enable' '--user' '0' 'a.b'\"", TextSafety.shWord("'pm' 'enable' '--user' '0' 'a.b'"))
        assertEquals("'x \$y'", TextSafety.shWord("x \$y"))
        assertEquals("'it'\\''s'", TextSafety.shq("it's"))
        assertEquals("'it''s'", TextSafety.psq("it's"))
    }
}
