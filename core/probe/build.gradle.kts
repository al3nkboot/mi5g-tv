plugins { id("cleanyourtv.jvm") }

dependencies {
    api(project(":core:model"))
    api(project(":core:ops"))
    testImplementation(project(":testing:fixtures"))
}
