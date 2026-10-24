package tv.cleanyour.buildlogic

import java.io.File
import java.util.Properties

// Optional Gradle signing of the release build, for people who build and sign their own APK.
// The values come only from environment variables or from a gitignored `keystore.properties` at
// the repository root; nothing about a key is ever committed. Without them the release build is
// unsigned, which is what CI and scripts/release-on-forge.sh want: the published APK is the
// reproducible unsigned build signed afterwards with apksigner, outside Gradle.
//
// Environment (wins over the file):
//   CYT_RELEASE_KEYSTORE               keystore path (PKCS#12 or JKS)
//   CYT_RELEASE_KEY_ALIAS              key alias (default "cleanyourtv")
//   CYT_RELEASE_KEYSTORE_PASSWORD      store password, or
//   CYT_RELEASE_KEYSTORE_PASSWORD_FILE a file whose first line is the store password
//   CYT_RELEASE_KEY_PASSWORD           key password (default: the store password)
// keystore.properties: storeFile, keyAlias, storePassword or storePasswordFile, keyPassword.
data class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String) {
    override fun toString(): String = "ReleaseSigning(storeFile=$storeFile, keyAlias=$keyAlias)"   // never the passwords

    companion object {
        const val PROPERTIES_FILE = "keystore.properties"
        const val DEFAULT_ALIAS = "cleanyourtv"

        // Null when no keystore is configured. Fails loudly when one is named but incomplete, so a
        // half configured key never silently produces an unsigned APK the builder thinks is signed.
        fun resolve(env: (String) -> String?, propertiesFile: File?, baseDir: File): ReleaseSigning? {
            val props = Properties()
            if (propertiesFile != null && propertiesFile.isFile) propertiesFile.inputStream().use { props.load(it) }
            fun prop(name: String): String? = props.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
            fun envv(name: String): String? = env(name)?.trim()?.takeIf { it.isNotEmpty() }

            val store = envv("CYT_RELEASE_KEYSTORE") ?: prop("storeFile") ?: return null
            val storeFile = File(store).let { if (it.isAbsolute) it else File(baseDir, store) }
            require(storeFile.isFile) { "release keystore $storeFile not found" }
            val passwordFile = envv("CYT_RELEASE_KEYSTORE_PASSWORD_FILE") ?: prop("storePasswordFile")
            val storePassword = envv("CYT_RELEASE_KEYSTORE_PASSWORD")
                ?: passwordFile?.let { f -> File(f).let { if (it.isAbsolute) it else File(baseDir, f) }.readLines().firstOrNull()?.trim() }
                ?: prop("storePassword")
            require(!storePassword.isNullOrEmpty()) { "release keystore $storeFile is set but its password is not" }
            val alias = envv("CYT_RELEASE_KEY_ALIAS") ?: prop("keyAlias") ?: DEFAULT_ALIAS
            val keyPassword = envv("CYT_RELEASE_KEY_PASSWORD") ?: prop("keyPassword") ?: storePassword
            return ReleaseSigning(storeFile, storePassword, alias, keyPassword)
        }
    }
}
