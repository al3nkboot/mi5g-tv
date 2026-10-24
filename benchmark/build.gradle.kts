plugins { id("cleanyourtv.android.benchmark") }

// W9: Macrobenchmark (startup, ARCHITECTURE 4; trend only in 0.1) and the Baseline Profile
// generator for :app. Runs on a device or emulator only (nightly.yml):
//   ./gradlew :benchmark:connectedBenchmarkAndroidTest
//   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
//     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
