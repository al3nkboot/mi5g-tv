// Mi5G Score for the TV (backlog 30): a separate TV app bundled through the seed index as a signed module.
plugins {
    id("cleanyourtv.tv.app")
}

android {
    namespace = "com.midroid.mi5gtv.matches"
    defaultConfig {
        applicationId = "com.midroid.mi5gtv.matches"
        versionCode = 3
        versionName = "2.0.0"
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "1280m"
    systemProperty("robolectric.graphicsMode", "NATIVE")
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED")
}
