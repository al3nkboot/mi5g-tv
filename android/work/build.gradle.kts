plugins { id("cleanyourtv.android.lib") }

dependencies {
    implementation(project(":core:engine"))
    implementation(project(":core:flows"))
    implementation(libs.kotlinx.coroutines.android)
}
