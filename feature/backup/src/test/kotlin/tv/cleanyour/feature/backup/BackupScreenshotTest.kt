package tv.cleanyour.feature.backup

import androidx.compose.ui.test.junit4.createComposeRule
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.RestorePhase
import tv.cleanyour.core.flows.RestorePointState
import tv.cleanyour.core.flows.UndoPreview
import tv.cleanyour.testing.fixtures.Samples

// Every section 4 state of :feature:backup (PRODUCT-SPEC 4: M6 with the Undo everything preview,
// its confirmation and the factory reset sheet), each in dark, light, 200% font and right to
// left, with ATF on.

private const val NOW = Samples.NOW_MS

private fun restore(phase: RestorePhase = RestorePhase.Idle, baseline: Long? = NOW, googleTv: Boolean = false, exporting: Boolean = false) =
    RestorePointState(tvName = "Mi TV 4A", baselineAtMs = baseline, latestAtMs = baseline, googleTv = googleTv, phase = phase, exporting = exporting)

private val undoLines = listOf("Restore point read", "Turning things back on: 14 of 23...")
private val preview = UndoPreview(Samples.plan(emptyList()), thingsBack = 23, stockName = "PatchWall", wizardBack = true, removeLauncher = false)
private const val BASELINE = "9 Oct, 08:53"

object BackupShots {
    val all: List<Shot> = listOf(
        Shot("restore_idle") { RestoreScreen(restore(), {}, {}) },
        Shot("restore_idle_google_tv") { RestoreScreen(restore(googleTv = true), {}, {}) },
        Shot("restore_no_restore_point") { RestoreScreen(restore(baseline = null), {}, {}) },
        Shot("restore_exporting") { RestoreScreen(restore(exporting = true), {}, {}) },
        Shot("restore_working_undo") { RestoreScreen(restore(RestorePhase.Working("undo", 58, undoLines)), {}, {}) },
        Shot("restore_working_factory") { RestoreScreen(restore(RestorePhase.Working("factory", 40, listOf("Switching the setup wizard back on..."))), {}, {}) },
        Shot("restore_finished_undo") { RestoreScreen(restore(RestorePhase.Finished("undo", NOW)), {}, {}) },
        Shot("restore_finished_factory") { RestoreScreen(restore(RestorePhase.Finished("factory", NOW), googleTv = true), {}, {}) },
        Shot("restore_failed") { RestoreScreen(restore(RestorePhase.Failed), {}, {}) },

        // The sheets: preview loading, the preview (keep or remove the new home screen), the one
        // danger button in its confirmation, and Before a factory reset.
        Shot("restore_previewing") { SheetOver({ RestoreScreen(restore(), {}, {}) }) { PreviewLoadingContent() } },
        Shot("restore_preview_keep") { SheetOver({ RestoreScreen(restore(), {}, {}) }) { PreviewContent(preview, BASELINE) {} } },
        Shot("restore_preview_remove") { SheetOver({ RestoreScreen(restore(), {}, {}) }) { PreviewContent(preview.copy(removeLauncher = true, stockName = null, wizardBack = false), BASELINE) {} } },
        Shot("restore_confirm") { SheetOver({ RestoreScreen(restore(), {}, {}) }) { ConfirmContent {} } },
        Shot("restore_factory_sheet") { SheetOver({ RestoreScreen(restore(googleTv = true), {}, {}) }) { FactoryContent({}, {}) } },
    )
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class BackupScreenshotTest(private val shot: Shot, private val variant: Variant) {
    @get:Rule val compose = createComposeRule()

    @Before fun fixedTimeZone() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }

    @Test fun shot() = compose.shoot(shot, variant)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun params(): List<Array<Any>> = BackupShots.all.flatMap { s -> Variant.entries.map { v -> arrayOf<Any>(s, v) } }
    }
}
