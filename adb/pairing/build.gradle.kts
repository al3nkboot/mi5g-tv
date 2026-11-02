plugins { id("cleanyourtv.jvm") }

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.conscrypt.openjdk)
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.net=ALL-UNNAMED")
}
