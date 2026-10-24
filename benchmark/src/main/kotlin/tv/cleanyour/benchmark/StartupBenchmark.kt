package tv.cleanyour.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

const val TARGET_PACKAGE = "com.midroid.mi5gtv"

// ARCHITECTURE 4 startup rows: cold TTID P50 400 ms target (500 ms ceiling, P90 700 ms), cold
// TTFD from reportFullyDrawn, warm 150 ms (250 ms ceiling). 15 iterations each. In 0.1 these
// are trend numbers (PRODUCT-SPEC 7.2): nightly.yml uploads the JSON, nothing fails on a budget.
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldWithBaselineProfile() = startup(StartupMode.COLD, CompilationMode.Partial(BaselineProfileMode.UseIfAvailable))

    @Test fun coldWithoutCompilation() = startup(StartupMode.COLD, CompilationMode.None())

    @Test fun warm() = startup(StartupMode.WARM, CompilationMode.Partial(BaselineProfileMode.UseIfAvailable))

    private fun startup(mode: StartupMode, compilation: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = mode,
        iterations = 15,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
