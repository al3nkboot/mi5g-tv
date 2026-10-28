package tv.cleanyour.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import tv.cleanyour.core.flows.InstallPart
import tv.cleanyour.core.flows.TvToolsIntent
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.ops.TellyFeatures

internal suspend fun readPickedApk(context: Context, graph: AppGraph, uri: Uri): TvToolsIntent.ApkPicked? = runCatching {
    val name = displayName(context, uri)
    val kind = TellyFeatures.bundleKind(name)
    if (kind == TellyFeatures.BundleKind.OTHER && !name.endsWith(".apk", true)) return null
    val tmp = copyToTemp(context, graph, uri) ?: return null
    try {
        if (kind == TellyFeatures.BundleKind.APK || kind == TellyFeatures.BundleKind.OTHER) {
            val meta = apkMeta(tmp)
            val blob = store(graph, tmp)
            TvToolsIntent.ApkPicked(name, blob.size, blob, meta?.first, meta?.second)
        } else {
            val parts = extractBundle(tmp, context, graph) ?: return null
            val meta = parts.firstNotNullOfOrNull { apkMeta(it.second) }
            val first = store(graph, parts.first().second)
            val installParts = parts.map { (partName, file) ->
                val blob = if (file == parts.first().second) first else store(graph, file)
                InstallPart(partName, blob.size, blob)
            }
            TvToolsIntent.ApkPicked(name, installParts.sumOf { it.sizeBytes }, first, meta?.first, meta?.second, installParts)
        }
    } finally {
        tmp.delete()
    }
}.getOrNull()

internal suspend fun readPickedSendFile(context: Context, graph: AppGraph, uri: Uri, folder: String): TvToolsIntent.SendFile? = runCatching {
    if (TellyFeatures.folderPath(folder) == null) return null
    val name = displayName(context, uri)
    val tmp = copyToTemp(context, graph, uri) ?: return null
    try {
        val blob = store(graph, tmp)
        TvToolsIntent.SendFile(folder, name, blob.size, blob)
    } finally {
        tmp.delete()
    }
}.getOrNull()

internal fun saveSharedFile(context: Context, bytes: ByteArray, fileName: String, mime: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, TellyFeatures.safeFileName(fileName))
    file.writeBytes(bytes)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = android.content.ClipData.newRawUri("", uri)
    }, null))
}

private fun displayName(context: Context, uri: Uri): String {
    var name = "file.bin"
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) name = c.getString(0) ?: name
    }
    return TellyFeatures.safeFileName(name)
}

private fun copyToTemp(context: Context, graph: AppGraph, uri: Uri): File? {
    val tmp = File.createTempFile("picked-", ".bin", context.cacheDir)
    var total = 0L
    context.contentResolver.openInputStream(uri)?.use { input ->
        tmp.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > 200L * 1024 * 1024) {
                    tmp.delete()
                    return null
                }
                out.write(buf, 0, n)
            }
        }
    } ?: return null
    if (total <= 0L) {
        tmp.delete()
        return null
    }
    return tmp
}

private suspend fun store(graph: AppGraph, file: File): tv.cleanyour.core.model.BlobRef {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    val sha = Sha256(md.digest().joinToString("") { "%02x".format(it) })
    return file.inputStream().use { graph.blobs.put(sha, it, file.length()) }
}

private fun apkMeta(file: File): Pair<String, Long>? = runCatching {
    ZipFile(file).use { zip ->
        val entry = zip.getEntry("AndroidManifest.xml") ?: return null
        val bytes = zip.getInputStream(entry).readBytes()
        val text = bytes.toString(Charsets.ISO_8859_1)
        val pkg = Regex("([A-Za-z][A-Za-z0-9_]{0,63}(?:\\.[A-Za-z][A-Za-z0-9_]{0,63}){1,8})").findAll(text)
            .map { it.groupValues[1] }
            .firstOrNull { it.count { c -> c == '.' } >= 1 && !it.startsWith("android.") }
        val version = bytes.let { raw ->
            // Binary manifest stores versionCode as a little-endian int near the attribute named versionCode.
            var found: Long? = null
            val needle = "versionCode".toByteArray(Charsets.US_ASCII)
            var i = 0
            while (i < raw.size - needle.size - 8) {
                if (raw.copyOfRange(i, i + needle.size).contentEquals(needle)) {
                    val start = (i + needle.size + 4).coerceAtMost(raw.size - 4)
                    val value = (raw[start].toInt() and 0xff) or
                        ((raw[start + 1].toInt() and 0xff) shl 8) or
                        ((raw[start + 2].toInt() and 0xff) shl 16) or
                        ((raw[start + 3].toInt() and 0xff) shl 24)
                    if (value in 1..2_000_000_000) found = value.toLong()
                }
                i++
            }
            found
        }
        if (pkg == null) null else pkg to (version ?: 0L)
    }
}.getOrNull()

private fun extractBundle(zipFile: File, context: Context, graph: AppGraph): List<Pair<String, File>>? = runCatching {
    ZipFile(zipFile).use { zip ->
        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".apk", true) && !it.name.contains("..") && it.size in 1..200L * 1024 * 1024 }
            .toList()
        if (entries.isEmpty() || entries.sumOf { it.size } > 200L * 1024 * 1024) return null
        val base = entries.firstOrNull { it.name.substringAfterLast('/').equals("base.apk", true) } ?: entries.maxBy { it.size }
        val ordered = listOf(base) + entries.filter { it != base }
        ordered.mapIndexed { index, entry ->
            val name = entry.name.substringAfterLast('/').let { TellyFeatures.safeFileName(it) }
                .let { if (it.endsWith(".apk")) it.removeSuffix(".apk").take(24) else "part$index" }
                .let { if (it.firstOrNull()?.isLetterOrDigit() == true) it else "part$index" } + ".apk"
            val out = File.createTempFile("split-", ".apk", context.cacheDir)
            zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            name.replace(".apk.apk", ".apk") to out
        }
    }
}.getOrNull()
