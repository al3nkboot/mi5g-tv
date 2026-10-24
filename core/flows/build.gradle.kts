plugins { id("cleanyourtv.jvm") }

dependencies {
    api(project(":core:engine"))
    api(project(":core:registry"))
    api(project(":core:backup"))

    testImplementation(project(":testing:fixtures"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
