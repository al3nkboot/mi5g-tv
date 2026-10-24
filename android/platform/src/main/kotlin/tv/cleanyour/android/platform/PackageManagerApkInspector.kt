package tv.cleanyour.android.platform

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import tv.cleanyour.core.model.ApkInfo
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.trust.ApkInspector
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

// ApkInspector on the phone (SECURITY 2.4 S1, SPIKE S8): sha256 and size of the file, then
// package, version and signer digest from PackageManager.getPackageArchiveInfo.
//
// The signer is asked for with GET_SIGNING_CERTIFICATES. Android 9 can return a PackageInfo whose
// signingInfo is null for an archive (seen in the spike), so the GET_SIGNATURES answer is used
// then, and `method` says which one produced the digest. An APK with more than one signer, or
// none, is refused (null): every pin and index entry names exactly one signer.
class PackageManagerApkInspector(private val context: Context) : ApkInspector {

    override fun inspect(blob: BlobRef): ApkInfo? {
        val file = File(blob.path)
        if (!file.isFile) return null
        val (size, sha) = hash(file)
        val pm = context.packageManager
        val modern = archiveInfo(pm, file, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = modern?.signingInfo
        val (info, choice) = if (modern != null && signing != null) {
            modern to SignerDigests.fromSigningInfo(signing.hasMultipleSigners(), signing.apkContentsSigners.map { it.toByteArray() })
        } else {
            @Suppress("DEPRECATION")
            val legacy = archiveInfo(pm, file, PackageManager.GET_SIGNATURES) ?: return null
            @Suppress("DEPRECATION")
            legacy to SignerDigests.fromLegacy(legacy.signatures?.map { it.toByteArray() }.orEmpty())
        }
        val signer = choice ?: return null
        val pkg = info.packageName?.let { runCatching { PackageName(it) }.getOrNull() } ?: return null
        return ApkInfo(
            pkg = pkg,
            versionCode = info.longVersionCode,
            versionName = info.versionName ?: "",
            signer = signer,
            sha256 = sha,
            size = size,
            method = if (signing != null) SignerDigests.METHOD_SIGNING_CERTIFICATES else SignerDigests.METHOD_SIGNATURES_FALLBACK,
        )
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, file: File, flags: Int): PackageInfo? =
        try { pm.getPackageArchiveInfo(file.absolutePath, flags) } catch (e: RuntimeException) { null }

    private fun hash(file: File): Pair<Long, Sha256> {
        val md = MessageDigest.getInstance("SHA-256")
        var n = 0L
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                n += r
                md.update(buf, 0, r)
            }
        }
        return n to Sha256(SignerDigests.hex(md.digest()))
    }
}

// The digest rules, apart from PackageManager so they are unit tested on the JVM.
object SignerDigests {
    const val METHOD_SIGNING_CERTIFICATES = "GET_SIGNING_CERTIFICATES"
    const val METHOD_SIGNATURES_FALLBACK = "GET_SIGNATURES (signingInfo was null)"

    // signingInfo: the current signer set (apkContentsSigners); a v3 rotation lineage is history and
    // does not change the current signer.
    fun fromSigningInfo(hasMultipleSigners: Boolean, currentSigners: List<ByteArray>): Sha256? =
        if (hasMultipleSigners || currentSigners.size != 1) null else digest(currentSigners.single())

    fun fromLegacy(signatures: List<ByteArray>): Sha256? = if (signatures.size != 1) null else digest(signatures.single())

    fun digest(certDer: ByteArray): Sha256 = Sha256(hex(MessageDigest.getInstance("SHA-256").digest(certDer)))

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
