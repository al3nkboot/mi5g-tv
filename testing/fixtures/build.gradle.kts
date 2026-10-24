plugins { id("cleanyourtv.jvm") }

// Fakes for every section 5 interface plus the fixture loaders. Test scope only (rule 5).
dependencies {
    api(project(":core:model"))
    api(project(":core:ops"))
    api(project(":core:spec"))
    api(project(":core:probe"))
    api(project(":core:trust"))
    api(project(":core:planner"))
    api(project(":core:registry"))
    api(project(":core:backup"))
    api(project(":core:engine"))
    api(project(":adb:wire"))
    testImplementation(libs.kotlinx.coroutines.test)
}
