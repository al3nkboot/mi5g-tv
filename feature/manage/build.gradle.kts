plugins {
    id("cleanyourtv.android.feature")
    id("cleanyourtv.android.roborazzi")
}

// The Roborazzi convention plugin adds Robolectric, Roborazzi, Compose UI test (the test manifest
// as testImplementation only, so the debug APK keeps one exported component) and the forge test
// settings; the build-logic test timeout covers hangs.
dependencies {
    testImplementation(project(":testing:fixtures"))
}
