import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import tv.cleanyour.buildlogic.ApkSizeCheckTask
import tv.cleanyour.buildlogic.ReleaseSigning
import tv.cleanyour.buildlogic.Toolchain
import tv.cleanyour.buildlogic.cytNamespace
import tv.cleanyour.buildlogic.libs
import tv.cleanyour.buildlogic.repoRootProperty

// Android library. AGP 9 built-in Kotlin: no kotlin-android plugin (ARCHITECTURE 1.1).
class AndroidLibConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            namespace = cytNamespace()
            compileSdk = Toolchain.COMPILE_SDK
            defaultConfig {
                minSdk = Toolchain.MIN_SDK
            }
            compileOptions {
                sourceCompatibility = Toolchain.JAVA
                targetCompatibility = Toolchain.JAVA
            }
            lint {
                abortOnError = true
                warningsAsErrors = false
                checkReleaseBuilds = false
            }
        }
        dependencies {
            add("testImplementation", libs.findLibrary("junit4").get())
            add("testImplementation", libs.findLibrary("kotlin-test-junit").get())
        }
        configureTests()
    }
}

// Android library with Compose (used by :ui:design; features build on it).
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("cleanyourtv.android.lib")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> {
            buildFeatures { compose = true }
        }
        addCompose()
    }
}

// Feature module: Compose plus the only three things a feature may see (rule 3).
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("cleanyourtv.android.compose")
        dependencies {
            add("implementation", project(":ui:design"))
            add("implementation", project(":core:flows"))
            add("implementation", project(":core:model"))
        }
    }
}

// The application, with the APK size gate (8 MB hard, PRODUCT-SPEC 7.2, ARCHITECTURE D14).
class AndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<ApplicationExtension> {
            namespace = "tv.cleanyour.app"
            compileSdk = Toolchain.COMPILE_SDK
            defaultConfig {
                applicationId = "com.midroid.mi5gtv"
                minSdk = Toolchain.MIN_SDK
                targetSdk = Toolchain.TARGET_SDK
                // versionCode and versionName live in app/build.gradle.kts (STORES-PLAN 3), the one
                // place both store flavours and F-Droid's checkupdates read them from.
            }
            // Optional local signing (ReleaseSigning): env or a gitignored keystore.properties only.
            // Unset, the release APK is unsigned, as CI and scripts/release-on-forge.sh build it.
            val releaseSigning = ReleaseSigning.resolve(
                { name -> providers.environmentVariable(name).orNull },
                rootProject.file(ReleaseSigning.PROPERTIES_FILE),
                rootProject.projectDir,
            )
            val releaseConfig = releaseSigning?.let { k ->
                signingConfigs.create("release").apply {
                    storeFile = k.storeFile
                    storePassword = k.storePassword
                    keyAlias = k.keyAlias
                    keyPassword = k.keyPassword
                    enableV1Signing = false
                    enableV2Signing = true
                    enableV3Signing = true
                }
            }
            buildTypes {
                getByName("release") {
                    if (releaseConfig != null) signingConfig = releaseConfig
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    // W9, reproducibility (ARCHITECTURE 6.8): no VCS info file in the APK, so a
                    // build from a git archive and one from a clone are byte identical.
                    vcsInfo { include = false }
                }
                // W9: what :benchmark measures. Release code (R8, same rules), signed with the
                // debug key so the test can install it, and profileable from the shell.
                create("benchmark") {
                    initWith(getByName("release"))
                    signingConfig = signingConfigs.getByName("debug")
                    matchingFallbacks += listOf("release")
                    isDebuggable = false
                }
            }
            sourceSets.getByName("benchmark").manifest.srcFile(rootProject.file("benchmark/app-benchmark/AndroidManifest.xml"))
            compileOptions {
                sourceCompatibility = Toolchain.JAVA
                targetCompatibility = Toolchain.JAVA
            }
            buildFeatures {
                compose = true
                buildConfig = false
            }
            // Reproducibility (ARCHITECTURE 6.8): F-Droid rejects the signed dependency block.
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
            lint {
                abortOnError = true
                warningsAsErrors = false
                checkReleaseBuilds = false
            }
        }
        addCompose()
        configureTests()

        val limit = providers.gradleProperty("cleanyourtv.apkSizeLimitBytes").map { it.toLong() }
            .orElse(ApkSizeCheckTask.DEFAULT_LIMIT_BYTES)
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                val name = variant.name.replaceFirstChar { it.uppercase() }
                tasks.register<ApkSizeCheckTask>("checkApkSize$name") {
                    group = "verification"
                    description = "Fails when the $name APK is larger than the size gate."
                    apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
                    builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                    limitBytes.set(limit)
                    variantName.set(variant.name)
                }
            }
        }
    }
}

// RFC-CURTAIN-1: a small Java app that runs on the TV (the curtain). No Kotlin, no AndroidX, no R8:
// nothing to shrink, and the release build stays byte for byte reproducible. The module sets its
// own namespace and applicationId. The release APK is unsigned here; scripts/build-curtain-on-forge.sh
// signs it with the launcher key. The size gate is TV_APP_LIMIT_BYTES.
class TvAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            compileSdk = Toolchain.COMPILE_SDK
            defaultConfig {
                minSdk = Toolchain.TV_MIN_SDK
                targetSdk = Toolchain.TARGET_SDK
            }
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = false
                    vcsInfo { include = false }
                }
            }
            compileOptions {
                sourceCompatibility = Toolchain.JAVA
                targetCompatibility = Toolchain.JAVA
            }
            buildFeatures { buildConfig = false }
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
            lint {
                abortOnError = true
                warningsAsErrors = false
                checkReleaseBuilds = false
            }
        }
        // AGP's built-in Kotlin adds the Kotlin standard library to every module (as an api
        // dependency, during evaluation); this app has no Kotlin source and must not carry it (rule 7
        // of ModuleRules, and the 200 KB budget). The declaration is taken out again once the module
        // is evaluated, and the group is excluded from every main configuration as a second net.
        // Only the app's own classpaths: lint and the other tools keep theirs.
        fun appClasspath(n: String) = !n.contains("test", ignoreCase = true) && (n.endsWith("RuntimeClasspath") || n.endsWith("CompileClasspath"))
        configurations.configureEach {
            if (appClasspath(name)) exclude(mapOf("group" to "org.jetbrains.kotlin"))
        }
        afterEvaluate {
            configurations.filter { tv.cleanyour.buildlogic.ModuleRules.isDeclarable(it.name) && !it.name.contains("test", ignoreCase = true) }
                .forEach { c -> c.dependencies.removeIf { it.group == "org.jetbrains.kotlin" } }
        }
        dependencies {
            add("testImplementation", libs.findLibrary("junit4").get())
        }
        configureTests()

        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                val name = variant.name.replaceFirstChar { it.uppercase() }
                tasks.register<ApkSizeCheckTask>("checkApkSize$name") {
                    group = "verification"
                    description = "Fails when the $name APK is larger than the TV app size gate."
                    apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
                    builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                    limitBytes.set(ApkSizeCheckTask.TV_APP_LIMIT_BYTES)
                    variantName.set(variant.name)
                }
            }
        }
    }
}

private fun Project.addCompose() {
    dependencies {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        add("implementation", platform(bom))
        add("implementation", libs.findLibrary("androidx-compose-runtime").get())
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-foundation").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
    }
}

private fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        useJUnit()
        systemProperty(repoRootProperty, rootDir.absolutePath)
        // A hung test fails after 5 minutes instead of holding the shared forge build lock.
        timeout.set(java.time.Duration.ofMinutes(5))
    }
}
