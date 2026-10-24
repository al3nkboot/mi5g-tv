package tv.cleanyour.android.platform

import android.content.Context
import tv.cleanyour.core.registry.DiskBlobStore
import tv.cleanyour.core.registry.IndexBundle
import tv.cleanyour.core.registry.PartialStore
import tv.cleanyour.core.store.BlobStore
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.File

// The BlobStore (section 5.13) on the phone: verified downloads in cache/artifacts (LRU, 300 MB,
// the system may clear it), bundled assets copied once into files/bundled so every BlobRef.path is
// a plain file the session can stream from. All logic is DiskBlobStore's (JVM-tested in
// :core:registry); this class only supplies the Android directories and the asset source.
class FileBlobStore private constructor(private val disk: DiskBlobStore) : BlobStore by disk, PartialStore by disk {

    constructor(context: Context, capBytes: Long = DiskBlobStore.DEFAULT_CAP_BYTES) : this(
        DiskBlobStore(
            dir = File(context.cacheDir, "artifacts"),
            bundledDir = File(context.filesDir, "bundled"),
            bundledSource = { name -> BundledAssets.open(context, name) },
            capBytes = capBytes,
        ),
    )

    fun cachedBytes(): Long = disk.cachedBytes()
    suspend fun clear() = disk.clear()
}

// Files shipped in this module's assets/bundled/: the pinned launcher APK and the signed seed index.
object BundledAssets {
    private const val DIR = "bundled"

    fun open(context: Context, name: String): InputStream? = try {
        context.assets.open("$DIR/$name")
    } catch (e: FileNotFoundException) {
        null
    }

    // The seed copy of the module index (PRODUCT-SPEC D29), verified by IndexRegistry like any other.
    fun seedIndex(context: Context): IndexBundle {
        fun read(name: String): ByteArray = open(context, name)?.use { it.readBytes() } ?: error("missing bundled $name")
        return IndexBundle(read("index.json"), read("index.json.minisig"), read("keys.json"), read("keys.json.minisig"))
    }
}
