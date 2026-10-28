package tv.cleanyour.core.ops

import java.security.MessageDigest
import java.util.Base64

// Extra tools adapted from Tellyscope, kept pure so the machines only decide when to call them.
// A saved TV is never opened here. Nothing in this file raises an Allow dialog, and no undo
// command is `pm uninstall --user 0`. Factory apps are disabled, not removed.
object TellyFeatures {
    const val MAX_DOCK = 4
    const val MAX_SCREEN_OFF_SDK = 33
    const val DOWNLOAD = "/sdcard/Download"
    const val MOVIES = "/sdcard/Movies"
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]{0,63}(\\.[A-Za-z][A-Za-z0-9_]{0,63}){1,15}$")
    private val KEY = Regex("(QAAAA[A-Za-z0-9+/]+={0,2})(?:[ \\t]+([^\\r\\n\"]*))?")

    enum class BundleKind { APK, APKS, XAPK, APKM, OTHER }
    enum class InstallKind { NEW, UPDATE, SAME, OLDER, UNKNOWN }
    enum class ChangeAction { DISABLE, ENABLE }

    data class ChangeEntry(
        val id: Long,
        val at: Long,
        val action: ChangeAction,
        val pkg: String,
        val label: String,
        val undoneAt: Long? = null,
    )

    data class AllowedComputer(val key: String, val name: String?, val fingerprint: String, val ours: Boolean)

    fun bundleKind(name: String): BundleKind = when (name.substringAfterLast('.').lowercase()) {
        "apk" -> BundleKind.APK
        "apks" -> BundleKind.APKS
        "xapk" -> BundleKind.XAPK
        "apkm" -> BundleKind.APKM
        else -> BundleKind.OTHER
    }

    fun isBundle(name: String): Boolean = bundleKind(name) != BundleKind.APK && bundleKind(name) != BundleKind.OTHER

    fun installKind(incoming: Long?, installed: Long?): InstallKind = when {
        incoming == null -> InstallKind.UNKNOWN
        installed == null -> InstallKind.NEW
        incoming > installed -> InstallKind.UPDATE
        incoming == installed -> InstallKind.SAME
        else -> InstallKind.OLDER
    }

    fun installMessage(kind: InstallKind, pkg: String?, arabic: Boolean): String {
        val name = pkg?.takeIf { it.isNotBlank() } ?: if (arabic) "التطبيق" else "the app"
        return if (arabic) when (kind) {
            InstallKind.NEW -> "$name غير مثبت. هذا تثبيت جديد."
            InstallKind.UPDATE -> "$name أقدم على التلفاز. هذا تحديث."
            InstallKind.SAME -> "$name بنفس رقم الإصدار موجود. هذا إعادة تثبيت."
            InstallKind.OLDER -> "$name على التلفاز أحدث. التثبيت يستبدل الأحدث بنسخة أقدم، فقط إذا أكدت."
            InstallKind.UNKNOWN -> "لم نقرأ رقم الإصدار. التثبيت يستبدل الموجود إن وُجد."
        } else when (kind) {
            InstallKind.NEW -> "$name is not installed. This is a new install."
            InstallKind.UPDATE -> "$name on the TV is older. This is an update."
            InstallKind.SAME -> "$name is already this version. This reinstalls it."
            InstallKind.OLDER -> "$name on the TV is newer. Installing replaces it with an older copy, only if you confirm."
            InstallKind.UNKNOWN -> "The version could not be read. Install replaces what is there, if anything."
        }
    }

    fun parseInstalledVersion(dumpsys: String, pkg: String): Long? {
        if (!PKG.matches(pkg)) return null
        val active = dumpsys.substringBefore("Hidden system packages:")
        val start = active.indexOf("Package [$pkg]")
        if (start < 0) return null
        val block = active.substring(start).lineSequence().take(40).joinToString("\n")
        return Regex("versionCode=(\\d+)").find(block)?.groupValues?.get(1)?.toLongOrNull()
    }

    fun screenOffAllowed(sdk: Int?): Boolean = sdk != null && sdk in 1..MAX_SCREEN_OFF_SDK

    fun screenOffRefusal(sdk: Int?, arabic: Boolean): String? = when {
        screenOffAllowed(sdk) -> null
        arabic -> "إطفاء الصورة مع بقاء الصوت يعمل على أندرويد 13 أو أقدم فقط. لم يُرسل أمر."
        else -> "Picture off with audio still playing works on Android 13 or older only. No command was sent."
    }

    fun addDock(current: List<String>, pkg: String): List<String>? {
        val clean = pkg.trim()
        if (!PKG.matches(clean) || clean in current || current.size >= MAX_DOCK) return null
        return current + clean
    }

    fun safeFileName(name: String): String {
        val cleaned = name.map { c ->
            if (c == '/' || c == '\\' || c == '\'' || c.code < 0x20) '_' else c
        }.joinToString("").trim().take(120)
        return cleaned.takeUnless { it.isEmpty() || it.all { c -> c == '.' } } ?: "file"
    }

    fun uniqueName(name: String, taken: Set<String>): String {
        val safe = safeFileName(name)
        if (safe !in taken) return safe
        val dot = safe.lastIndexOf('.').takeIf { it > 0 } ?: safe.length
        val stem = safe.substring(0, dot)
        val ext = safe.substring(dot)
        return generateSequence(1) { it + 1 }.map { "$stem ($it)$ext" }.first { it !in taken }
    }

    fun folderPath(id: String): String? = when (id) {
        "download" -> DOWNLOAD
        "movies" -> MOVIES
        else -> null
    }

    fun parseNames(ls: String): List<String> = ls.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "." && it != ".." && !it.startsWith(".") }
        .filter { ShellTokens.isSafeToken(it) && ".." !in it }
        .distinct()
        .toList()

    fun parseThermal(text: String): String? {
        val labeled = Regex("mValue=([0-9.]+).*mName=([A-Za-z0-9_-]+)").find(text)
        if (labeled != null) return "${labeled.groupValues[2]} ${labeled.groupValues[1]}°"
        val cpu = Regex("(?i)cpu[^0-9]{0,12}([0-9]{2,3}(?:\\.[0-9])?)").find(text)?.groupValues?.get(1)
        if (cpu != null) return "CPU $cpu°"
        val milli = Regex("=([0-9]{4,6})\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
        if (milli != null && milli in 10_000..120_000) return "CPU ${milli / 1000}°"
        return null
    }

    fun parseNetBytes(procNetDev: String): Long? {
        var total = 0L
        var any = false
        for (line in procNetDev.lineSequence()) {
            val trimmed = line.trim()
            if (':' !in trimmed) continue
            val iface = trimmed.substringBefore(':').trim()
            if (iface == "lo") continue
            val cols = trimmed.substringAfter(':').trim().split(Regex("\\s+"))
            val rx = cols.getOrNull(0)?.toLongOrNull() ?: continue
            total += rx
            any = true
        }
        return total.takeIf { any }
    }

    fun netSpeed(before: Long, after: Long, elapsedMs: Long): String {
        val ms = elapsedMs.coerceAtLeast(1L)
        val bytes = (after - before).coerceAtLeast(0L)
        val perSec = bytes * 1000L / ms
        val kb = perSec / 1024.0
        return if (kb >= 1024) String.format(java.util.Locale.US, "%.1f MB/s", kb / 1024.0)
        else String.format(java.util.Locale.US, "%.0f KB/s", kb)
    }

    fun parseCpuApps(cpuinfo: String): List<String> = cpuinfo.lineSequence()
        .mapNotNull { line ->
            val m = Regex("([0-9.]+%)\\s+[0-9]+/([^:]+):").find(line.trim()) ?: return@mapNotNull null
            m.groupValues[2].trim() to m.groupValues[1]
        }
        .filter { (name, _) -> name.isNotBlank() && name != "system_server" }
        .take(8)
        .map { (name, cpu) -> "$name  $cpu" }.toList()

    fun parseMemApps(meminfo: String): List<String> = meminfo.lineSequence()
        .mapNotNull { line ->
            val m = Regex("([0-9,]+)K:\\s+(\\S+)").find(line.trim()) ?: return@mapNotNull null
            val kb = m.groupValues[1].replace(",", "").toLongOrNull() ?: return@mapNotNull null
            val name = m.groupValues[2]
            if (kb < 8_192L || name.startsWith("[")) return@mapNotNull null
            name to kb
        }
        .sortedByDescending { it.second }
        .take(8)
        .map { (name, kb) -> "$name  ${kb / 1024} MB" }.toList()

    fun parseConnected(usb: String, bluetooth: String): List<String> {
        val names = mutableListOf<String>()
        Regex("(?i)(?:Device Name|product|Manufacturer):\\s*([^\\n]{1,60})").findAll(usb).forEach {
            val n = it.groupValues[1].trim()
            if (n.isNotBlank() && n !in names) names += n
        }
        Regex("(?i)name:\\s*([^\\n]{1,40})").findAll(bluetooth).forEach {
            val n = it.groupValues[1].trim()
            if (n.isNotBlank() && n !in names && !n.startsWith("android")) names += n
        }
        return names.take(8)
    }

    fun parseKeys(dumpsys: String, phoneKeyLine: String?): List<AllowedComputer>? {
        if ("debugging_manager" !in dumpsys && "user_keys=" !in dumpsys) return null
        val ours = phoneKeyLine?.substringBefore(' ')?.trim()?.takeIf { it.startsWith("QAAAA") }
        return KEY.findAll(dumpsys).map { m ->
            val key = m.groupValues[1]
            AllowedComputer(key, m.groupValues[2].trim().ifBlank { null }, fingerprint(key), ours != null && key == ours)
        }.distinctBy { it.key }.toList()
    }

    fun keptKeyFile(keys: List<AllowedComputer>): String? {
        val ours = keys.filter { it.ours }
        if (ours.isEmpty()) return null
        return ours.joinToString("\n") { c -> c.key + (c.name?.let { " $it" } ?: " cleanyourtv") } + "\n"
    }

    fun fingerprint(base64: String): String = runCatching {
        MessageDigest.getInstance("MD5").digest(Base64.getDecoder().decode(base64))
            .joinToString(":") { "%02X".format(it) }
    }.getOrElse { base64.take(11) }

    class ChangeLog(text: String = "") {
        private val baseline = mutableSetOf<String>()
        private val entries = mutableListOf<ChangeEntry>()

        init { parse(text) }

        fun hasBaseline(): Boolean = baselineReady
        private var baselineReady = false

        fun setBaseline(disabled: Set<String>) {
            if (baselineReady) return
            baseline += disabled.filter { PKG.matches(it) }
            baselineReady = true
        }

        fun record(at: Long, action: ChangeAction, pkg: String, label: String): ChangeEntry? {
            if (!PKG.matches(pkg)) return null
            val entry = ChangeEntry((entries.maxOfOrNull { it.id } ?: 0) + 1, at, action, pkg, label.take(80), null)
            entries += entry
            return entry
        }

        fun markUndone(id: Long, at: Long) {
            val i = entries.indexOfFirst { it.id == id }
            if (i >= 0) entries[i] = entries[i].copy(undoneAt = at)
        }

        fun openDisable(pkg: String): ChangeEntry? = entries
            .filter { it.pkg == pkg && it.action == ChangeAction.DISABLE && it.undoneAt == null }
            .maxByOrNull { it.id }

        fun restorable(): List<String> {
            val last = entries.filter { it.undoneAt == null }
                .groupBy { it.pkg }
                .mapValues { (_, list) -> list.maxBy { it.id } }
            return last.filter { (_, e) -> e.action == ChangeAction.DISABLE && e.pkg !in baseline }
                .values.sortedByDescending { it.id }.map { it.pkg }
        }

        fun recent(): List<ChangeEntry> = entries.sortedByDescending { it.id }.take(30)

        fun encode(): String {
            val head = if (baselineReady) "b\t" + baseline.sorted().joinToString(",") else "b\t"
            val body = entries.joinToString("\n") { e ->
                listOf("e", e.id, e.at, e.action.name, e.pkg, e.label.replace('\t', ' '), e.undoneAt?.toString().orEmpty())
                    .joinToString("\t")
            }
            return listOf("v1", head, body).filter { it.isNotEmpty() }.joinToString("\n") + "\n"
        }

        private fun parse(text: String) {
            if (text.isBlank()) return
            for (line in text.lineSequence()) {
                if (line.startsWith("b\t")) {
                    baseline += line.removePrefix("b\t").split(',').filter { PKG.matches(it) }
                    baselineReady = true
                } else if (line.startsWith("e\t")) {
                    val f = line.split('\t')
                    if (f.size < 6) continue
                    val action = runCatching { ChangeAction.valueOf(f[3]) }.getOrNull() ?: continue
                    if (!PKG.matches(f[4])) continue
                    entries += ChangeEntry(
                        id = f[1].toLongOrNull() ?: continue,
                        at = f[2].toLongOrNull() ?: continue,
                        action = action,
                        pkg = f[4],
                        label = f[5],
                        undoneAt = f.getOrNull(6)?.toLongOrNull(),
                    )
                }
            }
        }
    }

    fun usesUserZeroUninstall(line: String): Boolean =
        line.contains("uninstall") && (line.contains("'--user'") || line.contains("--user"))
}
