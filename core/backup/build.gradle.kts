plugins { id("cleanyourtv.jvm") }

dependencies {
    api(project(":core:spec"))
    api(project(":core:ops"))
    // JSON element API for manifest.json, changes.json and restore-point.json (no compiler plugin)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":testing:fixtures"))
    // RestoreCommandsTest compares the laptop steps with the planner's UndoEverything (T5); test scope only
    testImplementation(project(":core:planner"))
}
