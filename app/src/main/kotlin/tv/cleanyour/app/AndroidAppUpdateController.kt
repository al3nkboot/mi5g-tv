package tv.cleanyour.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.cleanyour.android.platform.SignerDigests
import tv.cleanyour.core.flows.AppUpdateAsset
import tv.cleanyour.core.flows.AppUpdateController
import tv.cleanyour.core.flows.AppUpdateFailure
import tv.cleanyour.core.flows.AppUpdateInstallResult
import tv.cleanyour.core.flows.AppUpdateRelease
import tv.cleanyour.core.flows.AppUpdateReleaseSource
import tv.cleanyour.core.flows.AppUpdateState
import tv.cleanyour.core.flows.SemanticVersion
import tv.cleanyour.core.model.ApkInfo
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.FetchFailure
import tv.cleanyour.core.registry.FetchResult
import tv.cleanyour.core.store.BlobStore
import tv.cleanyour.core.trust.ApkInspector
import tv.cleanyour.core.trust.DownloadHosts
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers

/** Full-flavour update lifecycle. Network access remains behind the store's exact GitHub allowlist. */
class AndroidAppUpdateController(
    context: Context,
    private val store: StorePorts,
    private val source: AppUpdateReleaseSource,
    private val blobs: BlobStore,
    private val fetcher: ArtifactFetcher,
    private val inspector: ApkInspector,
    private val installedVersionName: String,
) : AppUpdateController {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gate = Mutex()
    private val mutableState = MutableStateFlow<AppUpdateState>(cachedState())
    override val state: StateFlow<AppUpdateState> = mutableState

    override suspend fun checkOnLaunch() = gate.withLock {
        if (!store.rules.updateCheck) return@withLock
        val now = System.currentTimeMillis()
        val cached = readCachedRelease()
        if (cached != null) {
            if (SemanticVersion.isNewer(cached.version, installedVersionName)) {
                val show = promptFor(cached.version, now)
                mutableState.value = AppUpdateState.Available(cached, show)
                postNotificationOnce(cached)
            } else {
                mutableState.value = AppUpdateState.UpToDate
            }
        }
        if (now < prefs.getLong(NEXT_AUTO_CHECK, 0L)) return@withLock
        checkLocked(now)
    }

    override suspend fun checkNow() = gate.withLock {
        if (!store.rules.updateCheck) return@withLock
        checkLocked(System.currentTimeMillis())
    }

    private suspend fun checkLocked(now: Long) {
        val cachedPrompt = mutableState.value as? AppUpdateState.Available
        mutableState.value = AppUpdateState.Checking
        val release = try {
            source.latest()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (release == null) {
            prefs.edit {
                putLong(NEXT_AUTO_CHECK, now + RETRY_AFTER_FAILURE_MS)
                putBoolean(LAST_CHECK_LATEST, false)
            }
            mutableState.value = AppUpdateState.Failed(
                AppUpdateFailure.NETWORK,
                readCachedRelease(),
                cachedPrompt?.showPrompt == true,
            )
            return
        }
        if (SemanticVersion.parse(release.version) == null) {
            prefs.edit {
                putLong(NEXT_AUTO_CHECK, now + RETRY_AFTER_FAILURE_MS)
                putBoolean(LAST_CHECK_LATEST, false)
            }
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.INVALID_RELEASE, readCachedRelease(), cachedPrompt?.showPrompt == true)
            return
        }
        prefs.edit {
            putLong(NEXT_AUTO_CHECK, now + CHECK_INTERVAL_MS)
            putBoolean(LAST_CHECK_LATEST, false)
        }
        if (!SemanticVersion.isNewer(release.version, installedVersionName)) {
            prefs.edit {
                remove(RELEASE_VERSION)
                putBoolean(LAST_CHECK_LATEST, true)
            }
            mutableState.value = AppUpdateState.UpToDate
            return
        }
        writeCachedRelease(release)
        val show = cachedPrompt?.takeIf { it.release.version == release.version }?.showPrompt
            ?: promptFor(release.version, now)
        mutableState.value = AppUpdateState.Available(release, show)
        postNotificationOnce(release)
    }

    override suspend fun download() = gate.withLock {
        if (!store.rules.updateCheck) return@withLock
        val release = when (val current = mutableState.value) {
            is AppUpdateState.Available -> current.release
            is AppUpdateState.Failed -> current.release ?: readCachedRelease()
            is AppUpdateState.ReadyToInstall -> current.release
            else -> readCachedRelease()
        } ?: run {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.INVALID_RELEASE)
            return@withLock
        }
        val asset = release.asset
        if (asset == null) {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.APK_NOT_PUBLISHED, release)
            return@withLock
        }
        val digest = asset.sha256
        if (digest == null) {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.CHECKSUM_UNAVAILABLE, release)
            return@withLock
        }
        if (!DownloadHosts.isAllowed(asset.url, store.hosts) || asset.sizeBytes <= 0 || asset.sizeBytes > MAX_APK_BYTES) {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.INVALID_RELEASE, release)
            return@withLock
        }
        val installed = installedInfo()
        if (installed == null) {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.SIGNER_MISMATCH, release)
            return@withLock
        }
        val publishedVersion = SemanticVersion.parse(release.version)
        val currentVersion = SemanticVersion.parse(installed.versionName)
        if (publishedVersion == null || currentVersion == null || publishedVersion <= currentVersion) {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.VERSION_MISMATCH, release)
            return@withLock
        }
        val ref = ArtifactRef(asset.url, asset.sizeBytes, digest, installed.signer)
        mutableState.value = AppUpdateState.Downloading(release, 0L, asset.sizeBytes)
        val result = try {
            fetcher.fetch(ref) { received, total -> mutableState.value = AppUpdateState.Downloading(release, received, total) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.DOWNLOAD_FAILED, release)
            return@withLock
        }
        when (result) {
            is FetchResult.Failed -> {
                val reason = when (result.failure) {
                    FetchFailure.OFFLINE -> AppUpdateFailure.NETWORK
                    FetchFailure.HASH_MISMATCH, FetchFailure.SIZE_MISMATCH -> AppUpdateFailure.CHECKSUM_MISMATCH
                    FetchFailure.SIGNER_MISMATCH -> AppUpdateFailure.SIGNER_MISMATCH
                    else -> AppUpdateFailure.DOWNLOAD_FAILED
                }
                mutableState.value = AppUpdateState.Failed(reason, release)
            }
            is FetchResult.Ok -> {
                val info = withContext(Dispatchers.IO) { inspector.inspect(result.blob) }
                when {
                    info == null || info.sha256 != digest -> {
                        blobs.delete(digest)
                        mutableState.value = AppUpdateState.Failed(AppUpdateFailure.CHECKSUM_MISMATCH, release)
                    }
                    info.pkg.value != app.packageName -> {
                        blobs.delete(digest)
                        mutableState.value = AppUpdateState.Failed(AppUpdateFailure.PACKAGE_MISMATCH, release)
                    }
                    info.signer != installed.signer -> {
                        blobs.delete(digest)
                        mutableState.value = AppUpdateState.Failed(AppUpdateFailure.SIGNER_MISMATCH, release)
                    }
                    info.versionCode <= installed.versionCode || !sameVersion(info.versionName, release.version) -> {
                        blobs.delete(digest)
                        mutableState.value = AppUpdateState.Failed(AppUpdateFailure.VERSION_MISMATCH, release)
                    }
                    else -> mutableState.value = AppUpdateState.ReadyToInstall(release, result.blob)
                }
            }
        }
    }

    override suspend fun install(): AppUpdateInstallResult {
        if (!store.rules.updateCheck) return AppUpdateInstallResult.FAILED
        val ready = mutableState.value as? AppUpdateState.ReadyToInstall ?: return AppUpdateInstallResult.FAILED
        if (!canInstallFromUnknownSources()) return AppUpdateInstallResult.PERMISSION_REQUIRED
        val verified = withContext(Dispatchers.IO) { verifyReadyArtifact(ready.release, ready.apk) }
        if (verified == null) {
            blobs.delete(ready.apk.sha256)
            mutableState.value = AppUpdateState.Failed(AppUpdateFailure.VERSION_MISMATCH, ready.release)
            return AppUpdateInstallResult.FAILED
        }
        val updateDir = File(app.cacheDir, "updates")
        val file = File(updateDir, "mi5g-${ready.release.version}-${ready.apk.sha256.hex}.apk")
        val temp = File(updateDir, file.name + ".tmp")
        return try {
            val uri = withContext(Dispatchers.IO) {
                updateDir.mkdirs()
                updateDir.listFiles()?.filter {
                    it.isFile && (it.name.endsWith(".tmp") || (it != file && it.name.startsWith("mi5g-") && it.name.endsWith(".apk")))
                }?.forEach { it.delete() }
                FileInputStream(ready.apk.path).use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                if (temp.length() != ready.apk.size || fileSha256(temp) != ready.apk.sha256) {
                    mutableState.value = AppUpdateState.Failed(AppUpdateFailure.CHECKSUM_MISMATCH, ready.release)
                    return@withContext null
                }
                if (file.exists()) file.delete()
                if (!temp.renameTo(file)) return@withContext null
                FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
            } ?: return AppUpdateInstallResult.FAILED
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            AppUpdateInstallResult.STARTED
        } catch (_: Exception) {
            AppUpdateInstallResult.FAILED
        } finally {
            temp.delete()
        }
    }

    override fun canInstallFromUnknownSources(): Boolean =
        !store.rules.updateCheck || app.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        if (!store.rules.updateCheck || canInstallFromUnknownSources()) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${app.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    override fun remindLater() {
        val available = mutableState.value as? AppUpdateState.Available ?: return
        prefs.edit {
            putString(REMIND_VERSION, available.release.version)
            putLong(REMIND_UNTIL, System.currentTimeMillis() + REMIND_DELAY_MS)
            remove(NOTIFIED_VERSION)
        }
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        mutableState.value = available.copy(showPrompt = false)
    }

    private fun promptFor(version: String, now: Long): Boolean {
        if (prefs.getString(REMIND_VERSION, null) == version) {
            if (now < prefs.getLong(REMIND_UNTIL, 0L)) return false
            prefs.edit {
                remove(REMIND_VERSION)
                remove(REMIND_UNTIL)
                putString(PROMPT_VERSION, version)
                putLong(PROMPT_AT, now)
            }
            return true
        }
        val previous = prefs.getString(PROMPT_VERSION, null)
        val lastAt = prefs.getLong(PROMPT_AT, 0L)
        val show = previous != version || now - lastAt >= PROMPT_REPEAT_MS
        if (show) prefs.edit {
            putString(PROMPT_VERSION, version)
            putLong(PROMPT_AT, now)
        }
        return show
    }

    private fun postNotificationOnce(release: AppUpdateRelease) {
        if (prefs.getString(NOTIFIED_VERSION, null) == release.version) return
        if (prefs.getString(REMIND_VERSION, null) == release.version && System.currentTimeMillis() < prefs.getLong(REMIND_UNTIL, 0L)) return
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            app.getString(tv.cleanyour.feature.manage.R.string.appsettings_update_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ))
        val open = Intent(app, MainActivity::class.java).putExtra(EXTRA_OPEN_UPDATES, true)
        val pending = PendingIntent.getActivity(app, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(app.getString(tv.cleanyour.feature.manage.R.string.appsettings_update_notification_title))
            .setContentText(app.getString(tv.cleanyour.feature.manage.R.string.appsettings_update_notification_body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            return
        }
        prefs.edit { putString(NOTIFIED_VERSION, release.version) }
    }

    private fun cachedState(): AppUpdateState {
        if (!store.rules.updateCheck) return AppUpdateState.Idle
        val release = readCachedRelease() ?: return if (prefs.getBoolean(LAST_CHECK_LATEST, false)) AppUpdateState.UpToDate else AppUpdateState.Idle
        return if (SemanticVersion.isNewer(release.version, installedVersionName)) AppUpdateState.Available(release, showPrompt = false) else AppUpdateState.UpToDate
    }

    private fun writeCachedRelease(release: AppUpdateRelease) {
        prefs.edit {
            putString(RELEASE_VERSION, release.version)
            putString(RELEASE_TITLE, release.title)
            putString(RELEASE_DATE, release.publishedAt)
            putString(RELEASE_NOTES, release.notes)
            putString(RELEASE_URL, release.releaseUrl)
            val asset = release.asset
            if (asset == null) {
                remove(ASSET_NAME)
                remove(ASSET_URL)
                remove(ASSET_SIZE)
                remove(ASSET_SHA)
            } else {
                putString(ASSET_NAME, asset.name)
                putString(ASSET_URL, asset.url)
                putLong(ASSET_SIZE, asset.sizeBytes)
                val sha256 = asset.sha256
                if (sha256 == null) remove(ASSET_SHA) else putString(ASSET_SHA, sha256.hex)
            }
        }
    }

    private fun readCachedRelease(): AppUpdateRelease? {
        val version = prefs.getString(RELEASE_VERSION, null) ?: return null
        if (SemanticVersion.parse(version) == null) return null
        val assetUrl = prefs.getString(ASSET_URL, null)
        val size = prefs.getLong(ASSET_SIZE, 0L)
        val sha = prefs.getString(ASSET_SHA, null)?.let { runCatching { Sha256(it) }.getOrNull() }
        val asset = if (assetUrl != null && DownloadHosts.isAllowed(assetUrl, store.hosts) && size in 1..MAX_APK_BYTES)
            AppUpdateAsset(prefs.getString(ASSET_NAME, "Mi5G-TV-v$version.apk").orEmpty(), assetUrl, size, sha) else null
        val releaseUrl = prefs.getString(RELEASE_URL, null)
            ?.takeIf { it.startsWith("https://github.com/al3nkboot/mi5g-tv/releases/tag/") && DownloadHosts.isAllowed(it, store.hosts) }
            ?: "https://github.com/al3nkboot/mi5g-tv/releases/tag/v$version"
        return AppUpdateRelease(
            version = version,
            title = prefs.getString(RELEASE_TITLE, "Mi5G TV $version").orEmpty(),
            publishedAt = prefs.getString(RELEASE_DATE, "").orEmpty(),
            notes = prefs.getString(RELEASE_NOTES, "").orEmpty(),
            releaseUrl = releaseUrl,
            asset = asset,
        )
    }

    private data class InstalledInfo(val versionCode: Long, val versionName: String, val signer: Sha256)

    @Suppress("DEPRECATION")
    private fun installedInfo(): InstalledInfo? = try {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = info.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
            ?: info.signatures?.map { it.toByteArray() }.orEmpty()
        if (signatures.size != 1) null
        else InstalledInfo(info.longVersionCode, info.versionName.orEmpty(), SignerDigests.digest(signatures.single()))
    } catch (_: Exception) {
        null
    }

    private suspend fun verifyReadyArtifact(release: AppUpdateRelease, blob: BlobRef): ApkInfo? {
        val info = withContext(Dispatchers.IO) { inspector.inspect(blob) } ?: return null
        val installed = installedInfo() ?: return null
        if (info.pkg.value != app.packageName || info.signer != installed.signer || info.sha256 != blob.sha256 || info.size != blob.size) return null
        if (info.versionCode <= installed.versionCode || !sameVersion(info.versionName, release.version)) return null
        return info
    }

    private fun sameVersion(a: String, b: String): Boolean {
        val left = SemanticVersion.parse(a) ?: return false
        val right = SemanticVersion.parse(b) ?: return false
        return left.compareTo(right) == 0
    }

    private fun fileSha256(file: File): Sha256 {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return Sha256(digest.digest().joinToString("") { "%02x".format(it) })
    }

    companion object {
        const val EXTRA_OPEN_UPDATES = "tv.cleanyour.app.OPEN_UPDATES"
        const val APK_MIME = "application/vnd.android.package-archive"
        private const val PREFERENCES = "mi5gtv_update_state"
        private const val CHANNEL_ID = "mi5gtv_updates"
        private const val NOTIFICATION_ID = 1500
        private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val RETRY_AFTER_FAILURE_MS = 60L * 60 * 1000
        private const val PROMPT_REPEAT_MS = 7L * 24 * 60 * 60 * 1000
        private const val REMIND_DELAY_MS = 24L * 60 * 60 * 1000
        private const val MAX_APK_BYTES = 100L * 1024 * 1024
        private const val NEXT_AUTO_CHECK = "next_auto_check"
        private const val LAST_CHECK_LATEST = "last_check_latest"
        private const val RELEASE_VERSION = "release_version"
        private const val RELEASE_TITLE = "release_title"
        private const val RELEASE_DATE = "release_date"
        private const val RELEASE_NOTES = "release_notes"
        private const val RELEASE_URL = "release_url"
        private const val ASSET_NAME = "asset_name"
        private const val ASSET_URL = "asset_url"
        private const val ASSET_SIZE = "asset_size"
        private const val ASSET_SHA = "asset_sha"
        private const val PROMPT_VERSION = "prompt_version"
        private const val PROMPT_AT = "prompt_at"
        private const val REMIND_VERSION = "remind_version"
        private const val REMIND_UNTIL = "remind_until"
        private const val NOTIFIED_VERSION = "notified_version"
    }
}
