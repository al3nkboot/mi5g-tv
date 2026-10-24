// The TV curtain (RFC-CURTAIN-1, app-design/CURTAIN-PLAN.md): what the TV shows while the phone runs
// Setup. Built, signed and copied into :android:platform's assets by scripts/build-curtain-on-forge.sh.
plugins {
    id("cleanyourtv.tv.app")
}

android {
    namespace = "tv.cleanyour.curtain"
    defaultConfig {
        applicationId = "tv.cleanyour.curtain"
        versionCode = 1
        versionName = "0.1"
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    // Test only (never in the APK): the key frames are drawn on the JVM with Robolectric's native
    // graphics, so the Lantern can be checked against the approved mockup frames.
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "1280m"
    systemProperty("robolectric.graphicsMode", "NATIVE")
    // The key frames, for review beside the mockup's (not compared): build/curtain-frames, or next
    // to the tests in src/test/frames with -PcurtainFrames (RF-CURTAIN-1 F11).
    val frames = if (providers.gradleProperty("curtainFrames").isPresent) layout.projectDirectory.dir("src/test/frames").asFile
        else layout.buildDirectory.dir("curtain-frames").get().asFile
    systemProperty("curtain.frames", frames.absolutePath)
}
