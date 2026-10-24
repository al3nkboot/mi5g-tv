pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// No toolchain resolver plugin (foojay): the JVM modules' JDK 21 toolchain is the JDK that runs
// Gradle, so a build never downloads a JDK. F-Droid builds with Debian's openjdk-21 the same way.

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mi5g-tv"

// Every module of the module graph (ARCHITECTURE 1.2 and PRODUCT-SPEC 6). Layers and allowed
// edges live in build-logic ModuleRules.kt and are enforced by checkModuleGraph.

// Pure Kotlin JVM modules
include(":core:model")
include(":core:spec")
include(":core:ops")
include(":core:probe")
include(":core:planner")
include(":core:engine")
include(":core:trust")
include(":core:registry")
include(":core:backup")
include(":core:flows")
include(":adb:wire")
include(":adb:pairing")
include(":adb:session")

// Test-only modules
include(":testing:fixtures")
include(":testing:fake-adbd")
include(":e2e")

// Android edge modules
include(":android:platform")
include(":android:work")
include(":ui:design")
include(":feature:connect")
include(":feature:setup")
include(":feature:manage")
include(":feature:launcher")
include(":feature:modules")
include(":feature:backup")
include(":app")
include(":benchmark")

// The TV curtain (RFC-CURTAIN-1): a TV app of its own, bundled into :android:platform as a signed asset
include(":tv:curtain")
