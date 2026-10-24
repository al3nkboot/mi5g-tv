plugins { id("cleanyourtv.android.lib") }

// Implementations of the core seams (W2, W5, W8 per PRODUCT-SPEC 6).
dependencies {
    implementation(project(":core:model"))
    implementation(project(":adb:wire"))
    implementation(project(":adb:pairing"))
    implementation(project(":core:trust"))
    implementation(project(":core:registry"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(project(":testing:fake-adbd"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// W5: the bundled launcher APK (assets/bundled) must match its pin before anything is packaged;
// RFC-CURTAIN-1: so must the bundled TV curtain.
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(":core:trust:verifyLauncherPin", ":core:trust:verifyCurtainPin") }
