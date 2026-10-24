
plugins { id("cleanyourtv.jvm") }

// Plan, Step and GuardedSection come from :core:planner, so the engine sits one layer above it
// (RFC-W1-1 in DECISIONS-W1.md). serialization-json for the journal and plan files (ARCHITECTURE 5.2).
dependencies {
    api(project(":core:planner"))
    api(project(":core:probe"))
    api(project(":core:ops"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing:fixtures"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// A hung coroutine or a deadlocked run must fail the build, not stall it.
