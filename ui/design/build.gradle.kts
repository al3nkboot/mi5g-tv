plugins {
    id("cleanyourtv.android.compose")
    id("cleanyourtv.android.roborazzi")
}

// Compose compiler reports for this module (W7 definition of done: the state classes are stable,
// so screens skip recomposition; ARCHITECTURE 2.1 "verified with the compiler metrics report").
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}

// The reports are outputs of the release compile, so a build-cache hit restores them too.
tasks.matching { it.name == "compileReleaseKotlin" }.configureEach {
    outputs.dir(layout.buildDirectory.dir("compose-reports")).withPropertyName("composeReports")
}

// Fails when a class of :ui:design is reported unstable by the Compose compiler.
val checkComposeStability by tasks.registering {
    group = "verification"
    description = "Fails when the Compose compiler reports an unstable :ui:design class."
    dependsOn("compileReleaseKotlin")
    val reports = layout.buildDirectory.dir("compose-reports")
    doLast {
        val dir = reports.get().asFile
        val files = dir.listFiles { f -> f.name.endsWith("-classes.txt") }.orEmpty()
        require(files.isNotEmpty()) { "no Compose compiler class report in $dir" }
        val unstable = files.flatMap { f ->
            f.readLines().filter { it.startsWith("unstable class ") }.map { "${f.name}: $it" }
        }
        if (unstable.isNotEmpty()) {
            throw GradleException("Unstable :ui:design classes:\n" + unstable.joinToString("\n"))
        }
        logger.lifecycle("checkComposeStability: ${files.size} report(s), no unstable classes")
    }
}
tasks.named("check") { dependsOn(checkComposeStability) }
