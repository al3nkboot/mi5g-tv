
plugins { id("cleanyourtv.jvm") }

dependencies {
    api(project(":core:spec"))
    api(project(":core:ops"))
    testImplementation(project(":testing:fixtures"))
}

// A hung coroutine or a deadlocked run must fail the build, not stall it.
