// S10 fixture for the TV e2e harness (W9): a launcher that crashes when it is HOME. Standalone
// build, never part of the app build; nightly.yml and scripts/build-e2e-fixtures.sh build it.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "crashing-launcher"
