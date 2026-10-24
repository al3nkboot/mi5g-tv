package tv.cleanyour.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReleaseSigningTest {
    private val dir: File = Files.createTempDirectory("signing").toFile()
    private val ks = File(dir, "release.p12").apply { writeText("not a real keystore") }
    private val noEnv: (String) -> String? = { null }

    @Test fun nothingConfiguredMeansUnsigned() {
        assertNull(ReleaseSigning.resolve(noEnv, File(dir, "keystore.properties"), dir))
    }

    @Test fun environmentWithPasswordFile() {
        val pw = File(dir, "pw.txt").apply { writeText("s3cret-value\n") }
        val env = mapOf("CYT_RELEASE_KEYSTORE" to ks.path, "CYT_RELEASE_KEYSTORE_PASSWORD_FILE" to pw.path)
        val s = ReleaseSigning.resolve(env::get, null, dir)!!
        assertEquals(ks, s.storeFile)
        assertEquals("s3cret-value", s.storePassword)
        assertEquals("s3cret-value", s.keyPassword)
        assertEquals(ReleaseSigning.DEFAULT_ALIAS, s.keyAlias)
        assertFalse("passwords never reach a log line", s.toString().contains("s3cret"))
    }

    @Test fun propertiesFileWithRelativeStore() {
        val props = File(dir, "keystore.properties").apply {
            writeText("storeFile=release.p12\nstorePassword=from-file\nkeyAlias=other\n")
        }
        val s = ReleaseSigning.resolve(noEnv, props, dir)!!
        assertEquals(ks.canonicalFile, s.storeFile.canonicalFile)
        assertEquals("other", s.keyAlias)
        assertEquals("from-file", s.keyPassword)
    }

    @Test fun environmentWinsOverTheFile() {
        val props = File(dir, "keystore.properties").apply { writeText("storeFile=missing.p12\nstorePassword=a\n") }
        val env = mapOf("CYT_RELEASE_KEYSTORE" to ks.path, "CYT_RELEASE_KEYSTORE_PASSWORD" to "b")
        assertEquals("b", ReleaseSigning.resolve(env::get, props, dir)!!.storePassword)
    }

    @Test fun aKeystoreWithoutPasswordFailsLoudly() {
        val env = mapOf("CYT_RELEASE_KEYSTORE" to ks.path)
        assertThrows(IllegalArgumentException::class.java) { ReleaseSigning.resolve(env::get, null, dir) }
    }

    @Test fun aMissingKeystoreFailsLoudly() {
        val env = mapOf("CYT_RELEASE_KEYSTORE" to File(dir, "nope.p12").path, "CYT_RELEASE_KEYSTORE_PASSWORD" to "x")
        assertThrows(IllegalArgumentException::class.java) { ReleaseSigning.resolve(env::get, null, dir) }
    }
}
