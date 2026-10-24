plugins { id("cleanyourtv.jvm") }

// Bottom layer: depends on nothing of ours (rule 1). Coroutines for Flow and StateFlow in the
// seams (R0: 1); serialization-json only inside SnapshotCodec (D-W1-5).
dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
