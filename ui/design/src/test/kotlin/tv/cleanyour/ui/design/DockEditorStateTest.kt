package tv.cleanyour.ui.design

import org.junit.Test
import tv.cleanyour.ui.design.component.DockApp
import tv.cleanyour.ui.design.component.DockEditorState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The dock sheet's rows: "Remove row" takes a row under the dock away (never the dock), its apps go
// back to the other apps and the row under it moves up; Undo puts it all back.
class DockEditorStateTest {
    private val apps = List(12) { DockApp("com.example.app$it", "App $it") }

    @Test fun removingRowTwoPullsRowThreeUp() {
        val s = DockEditorState(apps, 4)
        assertEquals(3, s.rowCount)
        val before = s.removeRow(1)!!
        assertEquals(apps.take(4) + apps.drop(8), s.apps)
        assertEquals(2, s.rowCount)
        s.restore(before)
        assertEquals(apps, s.apps)
    }

    @Test fun theDockIsNeverRemoved() {
        val s = DockEditorState(apps.take(6), 4)
        assertNull(s.removeRow(0))
        assertNull(s.removeRow(5))
        assertEquals(apps.take(6), s.apps)
    }

    @Test fun anAddedEmptyRowCanBeRemovedAgain() {
        val s = DockEditorState(apps.take(4), 4)
        s.addRow()
        assertEquals(2, s.rowCount)
        assertTrue(s.hasRoom)
        val before = s.removeRow(1)!!
        assertEquals(1, s.rowCount)
        assertFalse(s.hasRoom)
        assertTrue(s.canAddRow)
        s.restore(before)
        assertEquals(2, s.rowCount)
    }

    @Test fun movesStayOneListWithoutGaps() {
        val s = DockEditorState(apps.take(6), 4)
        s.moveToRow(0, 1)
        assertEquals(listOf(1, 2, 3, 4, 0, 5).map { apps[it] }, s.apps)
        s.moveToRow(5, 0)
        assertEquals(listOf(1, 2, 3, 5, 4, 0).map { apps[it] }, s.apps)
        s.remove(apps[1].key)
        assertEquals(listOf(2, 3, 5, 4, 0).map { apps[it] }, s.apps)
    }
}
