package tv.cleanyour.core.flows

import kotlinx.coroutines.flow.StateFlow
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Sha256

/** A release entry read from the official store-specific release source. */
data class AppUpdateRelease(
    val version: String,
    val title: String,
    val publishedAt: String,
    val notes: String,
    val releaseUrl: String,
    val asset: AppUpdateAsset?,
)

data class AppUpdateAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: Sha256?,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val release: AppUpdateRelease, val showPrompt: Boolean) : AppUpdateState
    data class Downloading(val release: AppUpdateRelease, val receivedBytes: Long, val totalBytes: Long) : AppUpdateState
    data class ReadyToInstall(val release: AppUpdateRelease, val apk: BlobRef) : AppUpdateState
    data class Failed(
        val reason: AppUpdateFailure,
        val release: AppUpdateRelease? = null,
        val showPrompt: Boolean = false,
    ) : AppUpdateState
}

enum class AppUpdateFailure {
    NETWORK,
    INVALID_RELEASE,
    APK_NOT_PUBLISHED,
    CHECKSUM_UNAVAILABLE,
    DOWNLOAD_FAILED,
    CHECKSUM_MISMATCH,
    SIGNER_MISMATCH,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
}

enum class AppUpdateInstallResult { STARTED, PERMISSION_REQUIRED, FAILED }

fun interface AppUpdateReleaseSource {
    /** Returns the latest stable release, or null when GitHub is unavailable or the response is invalid. */
    suspend fun latest(): AppUpdateRelease?
}

interface AppUpdateController {
    val state: StateFlow<AppUpdateState>
    suspend fun checkOnLaunch()
    suspend fun checkNow()
    suspend fun download()
    suspend fun install(): AppUpdateInstallResult
    fun canInstallFromUnknownSources(): Boolean
    fun openInstallPermissionSettings()
    fun remindLater()
}

/** SemVer 2.0 comparison for tags such as v1.5.0 and 1.6.0-rc.1. Invalid versions never compare. */
object SemanticVersion {
    private val pattern = Regex("^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$")

    class Parsed internal constructor(
        val major: Long,
        val minor: Long,
        val patch: Long,
        val prerelease: List<String>?,
    ) : Comparable<Parsed> {
        override fun compareTo(other: Parsed): Int {
            compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
            compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
            compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
            if (prerelease == null) return if (other.prerelease == null) 0 else 1
            if (other.prerelease == null) return -1
            for (i in 0 until minOf(prerelease.size, other.prerelease.size)) {
                val a = prerelease[i]
                val b = other.prerelease[i]
                val aNumeric = a.all(Char::isDigit)
                val bNumeric = b.all(Char::isDigit)
                val c = when {
                    aNumeric && bNumeric -> compareNumericIdentifiers(a, b)
                    aNumeric -> -1
                    bNumeric -> 1
                    else -> a.compareTo(b)
                }
                if (c != 0) return c
            }
            return compareValues(prerelease.size, other.prerelease.size)
        }
    }

    fun parse(value: String): Parsed? {
        val match = pattern.matchEntire(value.trim()) ?: return null
        val major = match.groupValues[1].toLongOrNull() ?: return null
        val minor = match.groupValues[2].toLongOrNull() ?: return null
        val patch = match.groupValues[3].toLongOrNull() ?: return null
        val pre = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.')
        if (pre?.any { it.length > 1 && it[0] == '0' && it.all(Char::isDigit) } == true) return null
        return Parsed(major, minor, patch, pre)
    }

    private fun compareNumericIdentifiers(a: String, b: String): Int =
        if (a.length != b.length) compareValues(a.length, b.length) else a.compareTo(b)

    fun isNewer(candidate: String, installed: String): Boolean {
        val newVersion = parse(candidate) ?: return false
        val currentVersion = parse(installed) ?: return false
        return newVersion > currentVersion
    }
}
