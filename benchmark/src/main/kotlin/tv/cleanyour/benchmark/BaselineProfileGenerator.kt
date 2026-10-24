package tv.cleanyour.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Generates the Baseline Profile and Startup Profile for :app (ARCHITECTURE 4). nightly.yml runs
// it on an emulator image that allows profile capture, copies the result to
// app/src/main/baseline-prof.txt and opens a pull request; release builds only ever use the
// committed file, so they stay reproducible. The journey is the cold start to the first screen;
// W8 extends it with the wizard's first screens once they exist.
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
    }
}
