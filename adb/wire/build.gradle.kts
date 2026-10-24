import java.time.Duration

plugins { id("cleanyourtv.jvm") }

// Endpoint, Sha256 and ShellLine appear in the 5.3 signatures. Vendored from dadb 2.0.0 (NOTICE);
// no dadb artifact (D11).
dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(project(":testing:fake-adbd"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.property)
}

// The hostile-frame property test (HostileFrameFuzzTest) runs for cleanyourtv.fuzzSeconds
// (default 15); the 10-minute CI run passes -Pcleanyourtv.fuzzSeconds=600.
val fuzzSeconds = providers.gradleProperty("cleanyourtv.fuzzSeconds").orElse("15")
val fuzzSeed = providers.gradleProperty("cleanyourtv.fuzzSeed").orElse("")
tasks.withType<Test>().configureEach {
    systemProperty("cleanyourtv.fuzzSeconds", fuzzSeconds.get())
    if (fuzzSeed.get().isNotEmpty()) systemProperty("cleanyourtv.fuzzSeed", fuzzSeed.get())
    // build-logic gives every test task 5 minutes; a longer fuzz run gets its budget on top.
    val budget = fuzzSeconds.get().toLongOrNull() ?: 15L
    if (budget > 15L) timeout.set(Duration.ofMinutes(5).plusSeconds(2 * budget))
}
