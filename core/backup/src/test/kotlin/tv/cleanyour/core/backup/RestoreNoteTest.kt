package tv.cleanyour.core.backup

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.spec.PackageFacts
import tv.cleanyour.testing.fixtures.Fixtures
import tv.cleanyour.testing.fixtures.MapFactsTable
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.testing.fixtures.sha256Of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestoreNoteTest {
    private val note = PlainRestoreNote()
    private val tv = Scenario.tv()
    private val facts = MapFactsTable(mapOf(
        PackageName("com.miui.tv.analytics") to PackageFacts("Mi TV analytics", "Usage reports to Xiaomi", "tracking"),
        PackageName("com.google.android.tvlauncher") to PackageFacts("Android TV Home", "The TV's own home screen", "home"),
    ))

    // Every package of the upstream Mi TV profile (xiaomi-mitv-axso0) switched off in one plan.
    private fun miTvProfileChanges(): List<ChangeRecord> {
        val pkgs = Fixtures.upstreamExamples().single { it.folder == "xiaomi-mitv-axso0" }.debloatTxt.lines()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() && '.' in it }.map(::PackageName)
        return pkgs.mapIndexed { i, p -> ChangeRecord(PlanId("p"), i, Op.Disable(p), Prior(packageState = EnabledState.ENABLED), 10L + i) } +
            ChangeRecord(PlanId("p"), pkgs.size, Op.SetHome(Samples.launcherComponent), Prior(home = Samples.stockLauncher), 1_000)
    }

    @Test fun theMiTvNoteFitsOnAPage() {
        val changes = miTvProfileChanges()
        val text = note.text(tv, changes, facts, "Android TV Home", "19 October 2026")
        val lines = text.lines()
        assertTrue(lines.size < 60, "${lines.size} lines:\n$text")
        assertEquals("Restore note for Mi TV 4A (Living room)", lines.first())
        assertTrue(text.contains("set up on 19 October 2026 with Clean Your TV"))
        assertTrue(text.contains("Settings > Apps > See all apps > Show system apps"))
        assertTrue(text.contains("1. ") && text.contains("2. ") && text.contains("3. Choose Enable."))
        assertTrue(text.contains("enable \"Android TV Home\" first"))
        assertTrue(text.contains("- Mi TV analytics"), "friendly names from the facts table")
        val listedCount = lines.count { it.startsWith("- ") }
        assertEquals(changes.mapNotNull { (it.op as? Op.Disable)?.pkg }.distinct().size, listedCount)
        assertTrue(text.contains("${listedCount} apps were switched off"))
        assertFalse(text.contains("\u2014") || text.contains("--"), "no em dash or double hyphen in copy")
        assertFalse(text.contains("Keep hidden"), "no card line without a module")
    }

    @Test fun moreThanFortyCollapse() {
        val changes = (1..52).map { ChangeRecord(PlanId("p"), it, Op.Disable(PackageName("com.example.app$it")), Prior(packageState = EnabledState.ENABLED), it.toLong()) }
        val text = note.text(tv, changes, MapFactsTable(), "Android TV Home", "19 October 2026")
        val lines = text.lines()
        assertEquals(41, lines.count { it.startsWith("- ") })
        assertTrue(text.contains("- and 12 more, listed on the TV under Files > cleanyourtv"), text)
        assertTrue(lines.size < 60, "${lines.size} lines")
        assertFalse(text.contains("old home screen"), "no home line when HOME was not changed")
    }

    @Test fun theLongestNoteStaysUnderSixtyLines() {
        // every optional part at once: more than 40 names, HOME replaced and a module installed
        val blob = BlobRef(sha256Of("retro"), 1, "mem:retro")
        val changes = (1..52).map { ChangeRecord(PlanId("p"), it, Op.Disable(PackageName("com.example.app$it")), Prior(packageState = EnabledState.ENABLED), it.toLong()) } +
            ChangeRecord(PlanId("p"), 53, Op.SetHome(Samples.launcherComponent), Prior(home = Samples.stockLauncher), 100) +
            ChangeRecord(PlanId("m"), 0, Op.InstallStream(blob, blob.sha256, PackageName("com.gathin.retro"), 1, Samples.zeroHash), Prior(present = false), 200)
        val text = note.text(tv, changes, facts, "Android TV Home", "19 October 2026")
        val lines = text.lines()
        assertTrue(lines.size < 60, "${lines.size} lines:\n$text")
        assertTrue(text.contains("old home screen") && text.contains("Keep hidden") && text.contains("and 12 more"), text)
    }

    @Test fun aModuleAddsTheCardLine() {
        val blob = BlobRef(sha256Of("retro"), 1, "mem:retro")
        val changes = Scenario.changes() + ChangeRecord(PlanId("m"), 0, Op.InstallStream(blob, blob.sha256, PackageName("com.gathin.retro"), 1, Samples.zeroHash), Prior(present = false), Long.MAX_VALUE)
        val text = note.text(tv, changes, facts, "Android TV Home", "19 October 2026")
        assertTrue(text.contains("Keep hidden is always a safe answer"), text)
        assertTrue(text.contains("- Android TV Home"), "the stock launcher is listed by its friendly name")
    }

    @Test fun freeTextStaysOnItsLine() {
        val odd = tv.copy(nickname = "Den\nFAKE LINE\u0007", room = "Up\rstairs")
        val text = note.text(odd, Scenario.changes(), facts, "Android TV Home\nX", "19 Oct\n2026")
        val first = text.lines().first()
        assertEquals("Restore note for Den FAKE LINE (Up stairs)", first)
        assertFalse(text.lines().any { it == "FAKE LINE" || it == "X" || it == "2026" })
    }

    @Test fun nothingSwitchedOff() {
        val text = note.text(tv, emptyList(), facts, "Android TV Home", "19 October 2026")
        assertTrue(text.contains("Nothing on it is switched off."))
        assertFalse(text.contains("Switched off:"))
    }
}
