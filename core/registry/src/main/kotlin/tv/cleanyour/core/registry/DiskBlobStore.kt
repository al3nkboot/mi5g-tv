package tv.cleanyour.core.registry

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.FILE_NAME_RE
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.store.BlobStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// Content-addressed blob cache on a plain directory, pure java.io so it is JVM-tested; FileBlobStore
// in :android:platform is this over the app's cache and files directories and its assets.
//
//   dir/<sha256>.blob     a verified blob; its mtime is the last use (LRU)
//   dir/<sha256>.part     a resumable partial download, dir/<sha256>.etag its validator
//   bundledDir/<sha256>   a bundled asset copied out of the APK once, so every BlobRef.path is a file
//
// put and commitPartial verify size and sha256 before the rename that makes a blob visible, so a
// blob on disk always has the digest its name says. After each new blob the cache is trimmed to
// capBytes, least recently used first, never the blob just written. Partials not touched for
// partialMaxAgeMs are dropped at the same time. Bundled copies are not part of the cache.
class DiskBlobStore(
    private val dir: File,
    private val bundledDir: File,
    private val bundledSource: (String) -> InputStream?,
    private val capBytes: Long = DEFAULT_CAP_BYTES,
    private val partialMaxAgeMs: Long = 7L * 24 * 3600 * 1000,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BlobStore, PartialStore {

    private val lock = Any()
    private val bundledRefs = ConcurrentHashMap<String, BlobRef>()

    private fun blobFile(sha: Sha256) = File(dir, "${sha.hex}.blob")
    private fun partFile(sha: Sha256) = File(dir, "${sha.hex}.part")
    private fun etagFile(sha: Sha256) = File(dir, "${sha.hex}.etag")

    private fun ensureDir(d: File) { if (!d.isDirectory && !d.mkdirs() && !d.isDirectory) throw IOException("cannot create $d") }

    override suspend fun put(sha256: Sha256, source: InputStream, expectedSize: Long): BlobRef = withContext(io) {
        require(expectedSize >= 0)
        ensureDir(dir)
        val tmp = File(dir, ".tmp-${UUID.randomUUID()}")
        try {
            val (size, digest) = source.use { input -> FileOutputStream(tmp).use { out -> copyHashing(input, out, expectedSize).also { out.fd.sync() } } }
            if (size != expectedSize) throw IOException("size $size is not $expectedSize")
            if (digest != sha256.hex) throw IOException("sha256 mismatch")
            synchronized(lock) {
                val target = blobFile(sha256)
                if (!tmp.renameTo(target)) throw IOException("cannot move blob into place")
                target.setLastModified(nowMs())
                trim(keep = sha256)
            }
            BlobRef(sha256, size, blobFile(sha256).absolutePath)
        } finally {
            tmp.delete()
        }
    }

    override fun get(sha256: Sha256): BlobRef? = synchronized(lock) {
        val f = blobFile(sha256)
        if (!f.isFile) return null
        f.setLastModified(nowMs())
        BlobRef(sha256, f.length(), f.absolutePath)
    }

    override suspend fun delete(sha256: Sha256) = withContext(io) {
        synchronized(lock) {
            blobFile(sha256).delete()
            partFile(sha256).delete()
            etagFile(sha256).delete()
        }
        Unit
    }

    // Copies the named asset out once per process into bundledDir/<sha256> and returns that file.
    override fun bundled(name: String): BlobRef {
        require(FILE_NAME_RE.matches(name)) { "bad bundled name" }
        bundledRefs[name]?.let { ref -> if (File(ref.path).length() == ref.size) return ref }
        synchronized(lock) {
            ensureDir(bundledDir)
            val tmp = File(bundledDir, ".tmp-${UUID.randomUUID()}")
            try {
                val input = bundledSource(name) ?: throw IllegalStateException("no bundled asset $name")
                val (size, digest) = input.use { i -> FileOutputStream(tmp).use { out -> copyHashing(i, out, Long.MAX_VALUE).also { out.fd.sync() } } }
                val sha = Sha256(digest)
                val target = File(bundledDir, sha.hex)
                if (!(target.isFile && target.length() == size && hashOf(target) == digest)) {
                    target.delete()
                    if (!tmp.renameTo(target)) throw IOException("cannot move bundled $name into place")
                }
                return BlobRef(sha, size, target.absolutePath).also { bundledRefs[name] = it }
            } finally {
                tmp.delete()
            }
        }
    }

    override fun partialLength(sha256: Sha256): Long = partFile(sha256).let { if (it.isFile) it.length() else 0L }

    override fun partialEtag(sha256: Sha256): String? = etagFile(sha256).takeIf { it.isFile }?.readText()?.takeIf { it.isNotEmpty() }

    override fun startPartial(sha256: Sha256, etag: String?) {
        ensureDir(dir)
        FileOutputStream(partFile(sha256)).close()
        if (etag != null) etagFile(sha256).writeText(etag) else etagFile(sha256).delete()
    }

    override fun appendPartial(sha256: Sha256): OutputStream {
        ensureDir(dir)
        val out = FileOutputStream(partFile(sha256), true)
        return object : OutputStream() {
            private var closed = false
            override fun write(b: Int) = out.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun flush() = out.flush()
            override fun close() { if (closed) return; closed = true; try { out.fd.sync() } finally { out.close() } }
        }
    }

    override fun readPartial(sha256: Sha256): InputStream = FileInputStream(partFile(sha256))

    override suspend fun commitPartial(sha256: Sha256, size: Long): BlobRef = withContext(io) {
        synchronized(lock) {
            val part = partFile(sha256)
            if (!part.isFile || part.length() != size) throw IOException("partial is ${part.length()} bytes, expected $size")
            if (hashOf(part) != sha256.hex) throw IOException("partial sha256 mismatch")
            val target = blobFile(sha256)
            if (!part.renameTo(target)) throw IOException("cannot move partial into place")
            etagFile(sha256).delete()
            target.setLastModified(nowMs())
            trim(keep = sha256)
            BlobRef(sha256, size, target.absolutePath)
        }
    }

    override fun dropPartial(sha256: Sha256) {
        partFile(sha256).delete()
        etagFile(sha256).delete()
    }

    // Total bytes of verified blobs (partials and bundled copies excluded).
    fun cachedBytes(): Long = synchronized(lock) { blobs().sumOf { it.length() } }

    // Settings > "Clear downloads": every blob and partial goes; bundled copies stay.
    suspend fun clear() = withContext(io) { synchronized(lock) { dir.listFiles().orEmpty().forEach { it.delete() } }; Unit }

    private fun blobs(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".blob") }.orEmpty().toList()

    private fun trim(keep: Sha256) {
        val now = nowMs()
        dir.listFiles { f -> f.isFile && (f.name.endsWith(".part") || f.name.startsWith(".tmp-")) }.orEmpty()
            .filter { now - it.lastModified() > partialMaxAgeMs }
            .forEach { f -> f.delete(); File(dir, f.name.removeSuffix(".part") + ".etag").delete() }
        val all = blobs().sortedBy { it.lastModified() }
        var total = all.sumOf { it.length() }
        for (f in all) {
            if (total <= capBytes) break
            if (f.name == "${keep.hex}.blob") continue
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    companion object {
        const val DEFAULT_CAP_BYTES = 300L * 1024 * 1024
        private const val BUFFER = 64 * 1024

        // Copies at most limit bytes (one more is read to detect overflow) and returns (bytes, sha256 hex).
        internal fun copyHashing(input: InputStream, out: OutputStream?, limit: Long): Pair<Long, String> {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(BUFFER)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > limit) throw IOException("more than $limit bytes")
                md.update(buf, 0, n)
                out?.write(buf, 0, n)
            }
            return total to hex(md.digest())
        }

        internal fun hashOf(file: File): String = FileInputStream(file).use { copyHashing(it, null, Long.MAX_VALUE).second }

        internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
