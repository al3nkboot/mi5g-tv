plugins { id("cleanyourtv.jvm") }

// Implements the core.session interfaces of :core:model over :adb:wire (W2).
dependencies {
    api(project(":core:model"))
    implementation(project(":adb:wire"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(project(":testing:fake-adbd"))
    testImplementation(project(":testing:fixtures"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// docs/W2-device-check.md: RealTvCheck runs only when a TV address is passed on the command line.
tasks.withType<Test>().configureEach {
    for (p in listOf("cleanyourtv.realTv", "cleanyourtv.realTvKey", "cleanyourtv.realTvExpectNoPrompt")) {
        providers.gradleProperty(p).orNull?.let { systemProperty(p, it) }
    }
    testLogging { showStandardStreams = providers.gradleProperty("cleanyourtv.realTv").isPresent }
}
