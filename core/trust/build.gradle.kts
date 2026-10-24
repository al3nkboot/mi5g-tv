plugins { id("cleanyourtv.jvm") }

dependencies {
    api(project(":core:spec"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle.bcprov)
    testImplementation(libs.apksig)
}

// Build-time assertion (PRODUCT-SPEC W5, 7.3 item 4; RFC-CURTAIN-1): each bundled APK's sha256, size
// and signer digest must equal its pin (PinnedLauncher, PinnedCurtain). Runs with `check` and before
// :android:platform builds.
fun registerPinCheck(taskName: String, pin: String, asset: String) = tasks.register(taskName, JavaExec::class) {
    val apk = rootProject.layout.projectDirectory.file("android/platform/src/main/assets/bundled/$asset")
    group = "verification"
    description = "Fails when the bundled $pin APK does not match its pin."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("tv.cleanyour.core.trust.LauncherPinCheckKt")
    args(pin, apk.asFile.absolutePath)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    inputs.file(apk)
    val stamp = layout.buildDirectory.file("$pin-pin/ok")
    outputs.file(stamp)
    doLast { stamp.get().asFile.writeText("ok\n") }
}
val verifyLauncherPin = registerPinCheck("verifyLauncherPin", "launcher", "cleanyourtv-launcher.apk")
val verifyCurtainPin = registerPinCheck("verifyCurtainPin", "curtain", "cleanyourtv-curtain.apk")
tasks.named("check") { dependsOn(verifyLauncherPin, verifyCurtainPin) }
