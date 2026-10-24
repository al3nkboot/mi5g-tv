// Package com.gathin.tvhome on purpose: the takeover section targets the pinned launcher's
// component (com.gathin.tvhome/.MainActivity), so the crash happens exactly where a broken
// launcher release would crash. versionCode 1 and versionName "e2e-crash" are read by
// S05TakeoverWithCrashingLauncher. Debug signed with the build machine's throwaway key.
plugins {
    id("com.android.application") version "9.4.1"
}

android {
    namespace = "com.gathin.tvhome"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gathin.tvhome"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "e2e-crash"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
