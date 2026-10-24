package tv.cleanyour.core.spec

import org.junit.Test
import tv.cleanyour.core.model.PackageName
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// The dock and the rows under it: one ordered list cut into rows of `columns`, written as
// dock_apps (the list) and rows_below_dock (the rows under the dock that hold an app).
class DockLayersTest {
    private fun apps(n: Int) = List(n) { PackageName("com.example.app$it") }

    // Launcher 2.5 keeps a dock or tile order set on the TV (Edit dock, Reorder; files/order.txt)
    // only while dock_apps is the same list (Order.ranks), so a new look must write the same
    // dock_apps for the same dock: the Home screen note promises that a new look keeps it.
    @Test fun aNewLookWritesTheSameDockApps() {
        val looks = BundledThemeSource().bundled().map { it.doc } + LauncherConfigDoc()
        for (n in listOf(1, 4, 8, 12)) {
            val written = looks.map { DockLayers.applyTo(it, apps(n)).dockApps }
            assertEquals(listOf(apps(n)), written.distinct(), "$n apps")
        }
    }

    // RF-LAUNCHER-250 F4: up to 12 apps every look writes the same dock_apps. Six columns holds up
    // to 18 (the dock and two rows of six); a four-tile look holds 12, so a longer dock is cut to its
    // first 12 there and the TV's own dock edit for the longer list no longer applies (upstream #340).
    @Test fun aDockOfMoreThanTwelveAppsIsCutByAFourTileLook() {
        val looks = BundledThemeSource().bundled().associateBy { it.id }
        val six = looks.getValue("six-columns").doc
        assertEquals(6, DockLayers.columnsOf(six))
        for (n in listOf(13, 18)) {
            assertEquals(apps(n), DockLayers.applyTo(six, apps(n)).dockApps, "$n apps under Six columns")
            for (id in listOf("default", "aurora")) assertEquals(apps(12), DockLayers.applyTo(looks.getValue(id).doc, apps(n)).dockApps, "$n apps under $id")
            assertEquals(apps(12), DockLayers.applyTo(LauncherConfigDoc(), apps(n)).dockApps)
        }
        assertEquals(apps(18), DockLayers.applyTo(six, apps(20)).dockApps, "never a fourth row")
    }

    @Test fun theListIsCutIntoRowsOfColumns() {
        assertEquals(emptyList(), DockLayers.rows(apps(0), 4))
        assertEquals(listOf(apps(3)), DockLayers.rows(apps(3), 4))
        assertEquals(listOf(apps(4)), DockLayers.rows(apps(4), 4))
        assertEquals(listOf(apps(4), apps(5).drop(4)), DockLayers.rows(apps(5), 4))
        assertEquals(listOf(apps(6)), DockLayers.rows(apps(6), 6))
    }

    @Test fun rowsBelowDockCountsTheRowsUnderTheDockThatHoldAnApp() {
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2), (0..13).map { DockLayers.rowsBelowDock(it, 4) })
        assertEquals(0, DockLayers.rowsBelowDock(6, 6))
        assertEquals(1, DockLayers.rowsBelowDock(7, 6))
    }

    // Dock plus two rows is the most: a fourth row is never written.
    @Test fun atMostThreeRows() {
        assertEquals(12, DockLayers.capacity(4))
        assertEquals(apps(12), DockLayers.trim(apps(20), 4))
        assertEquals(3, DockLayers.rows(apps(20), 4).size)
        assertEquals(2, DockLayers.rowsBelowDock(20, 4))
        val doc = DockLayers.applyTo(LauncherConfigDoc(), apps(20))
        assertEquals(apps(12), doc.dockApps)
        assertEquals(2, doc.rowsBelowDock)
    }

    // A row only holds apps once the rows above it are full: every row but the last of the cut is
    // full, whatever was added, removed or moved.
    @Test fun aLowerRowNeverHoldsAppsWhileARowAboveHasRoom() {
        for (n in 0..12) {
            val rows = DockLayers.rows(apps(n), 4)
            rows.dropLast(1).forEach { assertEquals(4, it.size, "n=$n") }
            assertEquals(n, rows.sumOf { it.size })
            assertEquals(maxOf(0, rows.size - 1), DockLayers.rowsBelowDock(n, 4))
        }
    }

    @Test fun applyWritesExactlyTheListAndItsRows() {
        val six = apps(6)
        val doc = DockLayers.applyTo(LauncherConfigDoc(backgroundColor = "#000000"), six + six.take(2))
        assertEquals(six, doc.dockApps, "repeats are dropped")
        assertEquals(1, doc.rowsBelowDock)
        assertEquals("#000000", doc.backgroundColor, "the theme's other keys stay")
        // The six columns theme: six fit in the dock, so no row under it.
        val wide = DockLayers.applyTo(LauncherConfigDoc(columns = 6, rowsBelowDock = 1), six)
        assertEquals(6, DockLayers.columnsOf(wide))
        assertEquals(0, wide.rowsBelowDock)
    }

    // Owner decision D1: three rows (the dock and two under it) write dock_title false, so launcher
    // 2.4 fits all three on the screen; one or two rows leave the theme's dock_title as it is.
    @Test fun threeRowsTurnTheDockTitleOff() {
        assertEquals(false, DockLayers.applyTo(LauncherConfigDoc(), apps(9)).dockTitle)
        assertEquals(false, DockLayers.applyTo(LauncherConfigDoc(dockTitle = true), apps(12)).dockTitle)
        assertNull(DockLayers.applyTo(LauncherConfigDoc(), apps(8)).dockTitle)
        assertNull(DockLayers.applyTo(LauncherConfigDoc(), apps(1)).dockTitle)
        assertEquals(true, DockLayers.applyTo(LauncherConfigDoc(dockTitle = true), apps(5)).dockTitle)
        assertEquals(false, DockLayers.applyTo(LauncherConfigDoc(dockTitle = false), apps(4)).dockTitle)
        assertNull(DockLayers.applyTo(LauncherConfigDoc(), emptyList()).dockTitle)
        assertEquals(false, DockLayers.applyTo(LauncherConfigDoc(columns = 6), apps(13)).dockTitle)
        assertNull(DockLayers.applyTo(LauncherConfigDoc(columns = 6), apps(12)).dockTitle)
        val text = LauncherConfigCodec.encode(DockLayers.applyTo(LauncherConfigDoc(), apps(9))).decodeToString()
        assertEquals(true, "\"rows_below_dock\": 2," in text && "\"dock_title\": false" in text, text)
    }

    // An empty dock writes no dock_apps (the launcher keeps its own) and leaves the theme's rows.
    @Test fun anEmptyDockLeavesTheLauncherItsOwn() {
        val doc = DockLayers.applyTo(LauncherConfigDoc(dockApps = apps(2), rowsBelowDock = 1), emptyList())
        assertNull(doc.dockApps)
        assertEquals(1, doc.rowsBelowDock)
    }

    @Test fun columnsFollowTheLauncherRange() {
        assertEquals(4, DockLayers.columnsOf(null))
        assertEquals(4, DockLayers.columnsOf(LauncherConfigDoc()))
        assertEquals(6, DockLayers.columnsOf(LauncherConfigDoc(columns = 6)))
        assertEquals(4, DockLayers.columnsOf(LauncherConfigDoc(columns = 9)))
        assertEquals(4, DockLayers.columnsOf(LauncherConfigDoc(columns = 0)))
    }

    // Decoding skips what the TV cannot show, so later apps move up a slot, as on the TV.
    @Test fun decodeSkipsAppsTheTvDoesNotShow() {
        val all = apps(7)
        val gone = setOf(all[1], all[5])
        val shown = DockLayers.decode(all, { it !in gone }, 4)
        assertEquals(listOf(all[0], all[2], all[3], all[4], all[6]), shown)
        assertEquals(listOf(listOf(all[0], all[2], all[3], all[4]), listOf(all[6])), DockLayers.rows(shown, 4))
        assertEquals(apps(12), DockLayers.decode(apps(15), { true }, 4))
    }

    // Encode, then read the bytes back the launcher's way: the same list and the same rows.
    @Test fun roundTripThroughTheConfigFile() {
        for (n in 0..13) for (columns in listOf(4, 6)) {
            val theme = LauncherConfigDoc(columns = columns.takeIf { it != 4 })
            val doc = DockLayers.applyTo(theme, apps(n))
            val back = assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(LauncherConfigCodec.encode(doc))).doc
            assertEquals(doc, back)
            val decoded = DockLayers.decode(back.dockApps.orEmpty(), { true }, DockLayers.columnsOf(back))
            assertEquals(DockLayers.trim(apps(n), columns), decoded)
            if (n > 0) assertEquals(DockLayers.rows(decoded, columns).size - 1, back.rowsBelowDock)
        }
    }
}
