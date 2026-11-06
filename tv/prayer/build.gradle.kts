// مواقيتي for the TV (backlog 27-29): a separate TV app, bundled through the seed index as a signed
// module, never a Gradle dependency. Engine: adhan-java (MIT, Batoul Apps), same methods as Mi5G Prayer 2.0.
plugins {
    id("cleanyourtv.tv.app")
}

android {
    namespace = "com.midroid.mi5gtv.prayer"
    defaultConfig {
        applicationId = "com.midroid.mi5gtv.prayer"
        versionCode = 6
        versionName = "2.1.0"
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(libs.adhan)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "1280m"
    systemProperty("robolectric.graphicsMode", "NATIVE")
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED")
}
