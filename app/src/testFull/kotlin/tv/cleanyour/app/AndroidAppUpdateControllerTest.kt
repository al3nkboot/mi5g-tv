package tv.cleanyour.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import tv.cleanyour.android.platform.FileBlobStore
import tv.cleanyour.android.platform.SignerDigests
import tv.cleanyour.core.flows.*
import tv.cleanyour.core.model.*
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.FetchResult
import tv.cleanyour.core.trust.ApkInspector
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidAppUpdateControllerTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val cert = byteArrayOf(1, 2, 3, 4)
    private val signer = SignerDigests.digest(cert)
    private val bytes = "verified APK test bytes".encodeToByteArray()
    private val digest = Sha256(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    private val release = AppUpdateRelease("v1.6.0", "Mi5G TV 1.6.0", "2026-10-27T00:00:00Z", "Notes",
        "https://github.com/al3nkboot/mi5g-tv/releases/tag/v1.6.0",
        AppUpdateAsset("Mi5G-TV-v1.6.0.apk", "https://github.com/al3nkboot/mi5g-tv/releases/download/v1.6.0/Mi5G-TV-v1.6.0.apk", bytes.size.toLong(), digest))
    private val blobs = FileBlobStore(app)
    private var candidate = ApkInfo(PackageName(app.packageName), 16000, "1.6.0", signer, digest, bytes.size.toLong(), "test")
    private var calls = 0
    private var offline = false
    private fun controller() = AndroidAppUpdateController(app, Store, AppUpdateReleaseSource {
        calls++
        if (offline) null else release
    }, blobs, object : ArtifactFetcher {
        override suspend fun fetch(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult {
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            return FetchResult.Ok(blobs.put(digest, ByteArrayInputStream(bytes), bytes.size.toLong()))
        }
    }, object : ApkInspector {
        override fun inspect(blob: BlobRef): ApkInfo = candidate
    }, "1.5.0")

    @Before fun installCurrentPackage() {
        app.getSharedPreferences("mi5gtv_update_state", Context.MODE_PRIVATE).edit().clear().commit()
        val info = app.packageManager.getPackageInfo(app.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA
                or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS)
        info.versionName = "1.5.0"
        info.longVersionCode = 15000
        info.signingInfo = ReflectionHelpers.callConstructor(SigningInfo::class.java).also {
            shadowOf(it).setSignatures(arrayOf(Signature(cert)))
        }
        shadowOf(app.packageManager).installPackage(info)
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
    }

    @Test fun matchingSemanticVersionReachesOfficialInstaller() = runBlocking {
        val updater = controller()
        updater.checkNow()
        updater.download()
        assertIs<AppUpdateState.ReadyToInstall>(updater.state.value)
        assertNotNull(app.packageManager.resolveContentProvider("${app.packageName}.updates", PackageManager.GET_META_DATA))
        assertEquals(AppUpdateInstallResult.STARTED, updater.install())
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(AndroidAppUpdateController.APK_MIME, intent.type)
        assertEquals("content", intent.data?.scheme)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun installPermissionIsRequiredBeforeStartingInstaller() = runBlocking {
        val updater = controller()
        updater.checkNow(); updater.download()
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        assertEquals(AppUpdateInstallResult.PERMISSION_REQUIRED, updater.install())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun packageSignerAndVersionMismatchesAreRefusedAndDeleted() = runBlocking {
        val valid = candidate
        for ((bad, expected) in listOf(
            valid.copy(pkg = PackageName("com.example.other")) to AppUpdateFailure.PACKAGE_MISMATCH,
            valid.copy(signer = Sha256("b".repeat(64))) to AppUpdateFailure.SIGNER_MISMATCH,
            valid.copy(versionName = "1.7.0") to AppUpdateFailure.VERSION_MISMATCH,
            valid.copy(versionCode = 15000) to AppUpdateFailure.VERSION_MISMATCH,
            valid.copy(sha256 = Sha256("c".repeat(64))) to AppUpdateFailure.CHECKSUM_MISMATCH,
        )) {
            candidate = bad
            val updater = controller()
            updater.checkNow(); updater.download()
            assertEquals(expected, assertIs<AppUpdateState.Failed>(updater.state.value).reason)
            assertNull(blobs.get(digest))
        }
    }

    @Test fun installRechecksCandidateInsteadOfTrustingDownloadState() = runBlocking {
        val updater = controller()
        updater.checkNow(); updater.download()
        candidate = candidate.copy(versionName = "1.7.0")
        assertEquals(AppUpdateInstallResult.FAILED, updater.install())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun startupUsesCacheWithoutRepeatingPromptAndSnoozeCancelsNotification() = runBlocking {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(NotificationManager::class.java)
        val updater = controller()
        updater.checkNow()
        assertTrue(assertIs<AppUpdateState.Available>(updater.state.value).showPrompt)
        assertEquals(1, manager.activeNotifications.size)
        manager.cancelAll()
        updater.checkOnLaunch()
        assertEquals(1, calls)
        assertFalse(assertIs<AppUpdateState.Available>(updater.state.value).showPrompt)
        assertEquals(0, manager.activeNotifications.size)
        updater.remindLater()
        updater.checkOnLaunch()
        assertFalse(assertIs<AppUpdateState.Available>(updater.state.value).showPrompt)
        assertEquals(0, manager.activeNotifications.size)
    }

    @Test fun offlineCheckKeepsCachedReleaseForRetry() = runBlocking {
        val updater = controller()
        updater.checkNow()
        offline = true
        updater.checkNow()
        val failed = assertIs<AppUpdateState.Failed>(updater.state.value)
        assertEquals(AppUpdateFailure.NETWORK, failed.reason)
        assertEquals(release, failed.release)
    }
}
