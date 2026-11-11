package tv.cleanyour.feature.setup

import org.junit.Test
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.planner.GroupSummary
import tv.cleanyour.core.planner.PlanSummary
import tv.cleanyour.core.planner.RowSummary
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 2.3 (cleanyourtv-app 797279c, #1222): What we found says when rows are left on to choose.
class FoundChoices23Test {
    private fun row(off: Boolean, protected: Boolean) = RowSummary(PackageName("a.b"), "A", "", off, protected, null)
    private fun summary(vararg rows: RowSummary) = PlanSummary(listOf(GroupSummary("ads", "Ads", rows.count { it.off }, rows.size, rows.toList())),
        emptyList(), emptyList(), homeScreenPossible = false, replacesLauncher = false)

    @Test fun aRowNeitherOffNorProtectedIsAChoice() {
        assertTrue(summary(row(off = false, protected = false)).hasChoices)
        assertFalse(summary(row(off = false, protected = true), row(off = true, protected = false)).hasChoices)
    }
}
