package tv.cleanyour.core.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.store.BlobStore
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

// RFC-CURTAIN-1: the bundled TV curtain, pinned like the launcher and signed with the same key.
class CurtainPinTest {
    private val apk = File(Repo.bundled, PinnedCurtain.ASSET_NAME)

    @Test fun bundledApkMatchesThePin() {
        assertEquals(emptyList<String>(), PinCheck.problems(PinCheck.curtain, apk))
    }

    @Test fun aModifiedApkFailsTheCheck() {
        val tmp = File.createTempFile("curtain", ".apk").apply { deleteOnExit() }
        val bytes = apk.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        tmp.writeBytes(bytes)
        val problems = PinCheck.problems(PinCheck.curtain, tmp)
        assertTrue(problems.toString(), problems.any { it.startsWith("sha256") })
        // The launcher's bytes are not the curtain, even with the same signer.
        val launcher = PinCheck.problems(PinCheck.curtain, File(Repo.bundled, PinnedLauncher.ASSET_NAME))
        assertTrue(launcher.toString(), launcher.any { it.startsWith("sha256") } && launcher.none { it.startsWith("signer") })
    }

    @Test fun oneSignerForBothTvApps() {
        assertEquals(PinnedLauncher.SIGNER_SHA256, PinnedCurtain.SIGNER_SHA256)
        assertEquals("tv.cleanyour.curtain", PinnedCurtain.PACKAGE)
        assertTrue(PinnedCurtain.SIZE < 200L * 1024)
    }

    @Test fun bundledCurtainPinServesThePinnedBlob() {
        val artifact = BundledCurtainPin(OneBlob(PinnedCurtain.ASSET_NAME, apk.readBytes())).bundled()
        assertEquals(PackageName("tv.cleanyour.curtain"), artifact.pkg)
        assertEquals(PinnedCurtain.VERSION_CODE, artifact.versionCode)
        assertEquals(Sha256(PinnedCurtain.SHA256), artifact.sha256)
        assertEquals(Sha256(PinnedLauncher.SIGNER_SHA256), artifact.signer)
        assertEquals(artifact.sha256, artifact.blob.sha256)
    }

    @Test fun bundledCurtainPinRefusesASwappedAsset() {
        assertThrows(IllegalStateException::class.java) { BundledCurtainPin(OneBlob(PinnedCurtain.ASSET_NAME, "not the curtain".toByteArray())).bundled() }
        val launcherBytes = File(Repo.bundled, PinnedLauncher.ASSET_NAME).readBytes()
        assertThrows(IllegalStateException::class.java) { BundledCurtainPin(OneBlob(PinnedCurtain.ASSET_NAME, launcherBytes)).bundled() }
    }

    private class OneBlob(private val name: String, private val bytes: ByteArray) : BlobStore {
        private val ref = BlobRef(Sha256(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }), bytes.size.toLong(), "mem:$name")
        override suspend fun put(sha256: Sha256, source: InputStream, expectedSize: Long): BlobRef = error("read only")
        override fun get(sha256: Sha256): BlobRef? = ref.takeIf { it.sha256 == sha256 }
        override suspend fun delete(sha256: Sha256) = Unit
        override fun bundled(name: String): BlobRef = if (name == this.name) ref else error("no $name")
    }
}
