plugins { id("cleanyourtv.jvm") }

dependencies {
    api(project(":core:trust"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing:fixtures"))
    testImplementation(libs.bouncycastle.bcprov)   // test-side minisign signer for fixture indexes
}
