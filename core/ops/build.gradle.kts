
plugins { id("cleanyourtv.jvm") }

dependencies {
    api(project(":core:model"))
    testImplementation(project(":testing:fixtures"))
}

// A hung coroutine or a deadlocked run must fail the build, not stall it.
