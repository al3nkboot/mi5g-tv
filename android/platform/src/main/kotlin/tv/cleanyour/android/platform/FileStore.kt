package tv.cleanyour.android.platform

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.cleanyour.core.store.DocumentStore

// DocumentStore over app-internal files (ARCHITECTURE 5.1 and 5.2): files/v1/<path>. A write goes
// to <name>.tmp, is fsynced, renamed over the target, and the directory is fsynced, so a reader
// sees the old document or the new one, never half of either. Paths are relative, with no empty,
// "." or ".." segment, so nothing can be read or written outside the root.
class FileStore(
    private val root: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DocumentStore {

    private fun resolve(path: String): File {
        val p = path.trim('/')
        require(p.isNotEmpty() && p.split('/').none { it.isEmpty() || it == "." || it == ".." || it.endsWith(TMP) }) { "bad document path" }
        val f = File(root, p)
        require(f.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "bad document path" }
        return f
    }

    override suspend fun <T> read(path: String, decode: (ByteArray) -> T): T? = withContext(io) {
        val f = resolve(path)
        if (!f.isFile) null else decode(f.readBytes())
    }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(io) {
        val f = resolve(path)
        val dir = f.parentFile ?: throw IOException("no parent")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create folder")
        val tmp = File(dir, f.name + TMP)
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(f)) {
            tmp.delete()
            throw IOException("rename failed")
        }
        syncDir(dir)
    }

    override suspend fun delete(path: String) = withContext(io) {
        val f = resolve(path)
        if (f.exists() && !f.delete()) throw IOException("delete failed")
        f.parentFile?.let { syncDir(it) }
        Unit
    }

    override suspend fun list(dir: String): List<String> = withContext(io) {
        val d = if (dir.trim('/').isEmpty()) root else resolve(dir)
        d.listFiles().orEmpty().map { it.name }.filterNot { it.endsWith(TMP) }.sorted()
    }

    // fsync of a directory: opening it read-only and forcing works on Linux (Android); elsewhere it
    // may throw, and the rename is still atomic.
    private fun syncDir(dir: File) {
        runCatching { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }

    companion object { private const val TMP = ".tmp" }
}
