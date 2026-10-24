package tv.cleanyour.core.spec

import tv.cleanyour.core.model.GroupDefault
import tv.cleanyour.core.model.PackageEntry
import tv.cleanyour.core.model.PackageGroup
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Platform
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.ProfileMatch
import tv.cleanyour.core.model.Quirk
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.TestedInfo
import java.security.MessageDigest

// Upstream examples/<folder>/debloat.txt: '#' comment lines (a header with model, SDK, firmware,
// stock launcher, Live TV app and quirks), then groups. A group is a run of package lines; its
// title is the first line of the comment block directly above the run. A package line is a name
// with an optional trailing "# note"; an indented comment right under it continues the note.
// Commented-out packages are kept by the owner and are not part of the profile.
class DebloatTxtException(val folder: String, val line: Int, detail: String) : IllegalArgumentException("$folder/debloat.txt:$line: $detail")

internal object DebloatTxt {
    data class Entry(val pkg: PackageName, var note: String?)
    data class Group(val title: String, val entries: MutableList<Entry> = mutableListOf())
    data class Scan(val header: List<String>, val groups: List<Group>)

    private val PACKAGE_LINE = Regex("^\\s*([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)\\s*(?:#\\s?(.*))?$")
    private val DECORATION = Regex("^[-=*\\s]+|[-=*\\s]+$")

    fun commentText(line: String): String = line.trimStart().removePrefix("#").trim()

    private fun title(block: List<String>): String =
        block.asSequence().map { commentText(it).replace(DECORATION, "") }.firstOrNull { it.isNotEmpty() } ?: ""

    fun scan(text: String, folder: String): Scan {
        val header = mutableListOf<String>()
        val groups = mutableListOf<Group>()
        val block = mutableListOf<String>()
        var inPackages = false
        var seenPackage = false
        text.split('\n').forEachIndexed { i, raw ->
            val line = raw.removeSuffix("\r")
            if (line.isBlank()) {
                if (!seenPackage) header += block
                block.clear(); inPackages = false
                return@forEachIndexed
            }
            if (line.trimStart().startsWith("#")) {
                if (inPackages && (line[0] == ' ' || line[0] == '\t')) {
                    val last = groups.last().entries.last()
                    val more = commentText(line)
                    if (more.isNotEmpty()) last.note = listOfNotNull(last.note, more).joinToString(" ")
                    return@forEachIndexed
                }
                if (inPackages) { inPackages = false; block.clear() }
                block += line
                return@forEachIndexed
            }
            val m = PACKAGE_LINE.matchEntire(line) ?: throw DebloatTxtException(folder, i + 1, "neither a package nor a comment")
            val pkg = runCatching { PackageName(m.groupValues[1]) }.getOrElse { throw DebloatTxtException(folder, i + 1, "invalid package name ${m.groupValues[1]}") }
            if (!inPackages) {
                when {
                    block.isNotEmpty() -> groups += Group(title(block))
                    groups.isEmpty() -> groups += Group("")
                }
                if (!seenPackage) seenPackage = true
                block.clear(); inPackages = true
            }
            groups.last().entries += Entry(pkg, m.groupValues[2].trim().ifEmpty { null })
        }
        if (!seenPackage) header += block
        return Scan(header, groups)
    }

    fun sha256(text: String): Sha256 =
        Sha256(MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) })

    private val SDK_OF_RELEASE = mapOf(9 to 28, 10 to 29, 11 to 30, 12 to 31, 13 to 33, 14 to 34, 15 to 35, 16 to 36)
    private val PKG_TOKEN = Regex("(?<![A-Za-z0-9_./-])([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+)(?![A-Za-z0-9_*/-]|\\.[A-Za-z*])")
    private val LIKELY_PACKAGE = Regex("^(com|org|net|tv|android|in|co|io|de|jp|cn|air|mitv|ai)\\..+")

    private fun packagesIn(s: String): List<PackageName> = PKG_TOKEN.findAll(s).map { it.groupValues[1] }
        .filter { LIKELY_PACKAGE.matches(it) }
        .mapNotNull { runCatching { PackageName(it) }.getOrNull() }.toList()

    // Header facts by keyword, for a folder without curated metadata.
    private data class HeaderGuess(val platform: Platform, val sdk: Int?, val stock: List<PackageName>, val liveTv: PackageName?, val quirks: Set<Quirk>, val keep: Set<PackageName>)

    private fun guessHeader(header: List<String>, folder: String): HeaderGuess {
        val text = header.joinToString("\n") { commentText(it) }
        val lower = text.lowercase()
        val platform = when {
            "fire os" in lower -> Platform.FIRE_OS
            folder == "tcl-ak30a5" || ("tcl" in folder && "china" in lower) -> Platform.TCL_CN
            "google tv" in lower -> Platform.GOOGLE_TV
            else -> Platform.ANDROID_TV
        }
        val sdk = Regex("\\((?:SDK|API) (\\d{2})\\)").find(text)?.groupValues?.get(1)?.toInt()
            ?: Regex("(?:Android TV|Google TV|Android) (\\d{1,2})\\b").find(text)?.groupValues?.get(1)?.toInt()?.let { SDK_OF_RELEASE[it] }
        val stock = header.filter { "stock launcher" in it.lowercase() }.flatMap { packagesIn(it) }.distinct()
        val liveTv = header.firstNotNullOfOrNull { l -> if (Regex("live tv( app)?:", RegexOption.IGNORE_CASE).containsMatchIn(l)) packagesIn(l.substringAfter(':')).firstOrNull() else null }
        val quirks = buildSet {
            if ("svc power reboot" in lower) add(Quirk.REBOOT_COMMAND)
            if ("re-enabled by the firmware" in lower || "at every boot" in lower) add(Quirk.REENABLED_AT_BOOT)
            if ("wireless debugging" in lower && "pair" in lower) add(Quirk.WIRELESS_DEBUG_PAIRING_REQUIRED)
            if ("setupwraith" in lower) add(Quirk.HOME_TAKEOVER_SETUPWRAITH)
            if ("claims home" in lower || "declares the home" in lower) add(Quirk.EXTRA_HOME_CLAIMERS)
        }
        return HeaderGuess(platform, sdk, stock, liveTv, quirks, keepListIn(header))
    }

    // Packages a header line says to keep ("Keep:", "KEEP", "Do NOT disable", "must stay
    // enabled"); continuation lines of the same paragraph count too, up to a line that starts
    // "Already" (a note on what the owner or the factory had already switched off, which names
    // packages the list then disables anyway) or a numbered note. Used in addition to the curated list, since an
    // extra protected name only makes the plan smaller.
    fun keepListIn(lines: List<String>): Set<PackageName> {
        val out = linkedSetOf<PackageName>()
        var inKeep = false
        var bulletStart = false
        for (l in lines) {
            val t = commentText(l)
            val lower = t.lowercase().removePrefix("- ")
            val starts = KEEP_WORD.containsMatchIn(lower) || "do not disable" in lower || "must stay enabled" in lower || "keep enabled" in lower
            if (starts) { inKeep = true; bulletStart = t.startsWith("- ") }
            else if (inKeep && (t.isEmpty() || (bulletStart && t.startsWith("- ")) || HEADING.containsMatchIn(t) || lower.startsWith("already ") || NUMBERED_NOTE.containsMatchIn(t))) inKeep = false
            if (inKeep) out += packagesIn(t)
        }
        return out
    }
    private val HEADING = Regex("^[A-Z][A-Za-z ]{2,40}:")
    // "Keep" as a word: a wrapped sentence that goes on "keeps X; re-enable ..." starts no keep list.
    private val KEEP_WORD = Regex("^keep\\b")
    // A numbered note ("2. Play-Fi could not be kept disabled") starts a new paragraph: philips-55oled707
    // mentions another list's DO NOT DISABLE block inside note 1, and its stock launcher in note 3.
    private val NUMBERED_NOTE = Regex("^\\d+\\.\\s")

    fun parse(text: String, folder: String, meta: FolderMeta?): Profile {
        val scan = scan(text, folder)
        if (scan.groups.isEmpty()) throw DebloatTxtException(folder, 1, "no package lines")
        val guess = guessHeader(scan.header, folder)

        val brand = meta?.match?.brand ?: folder.substringBefore('-').let { if (it.length <= 3) it.uppercase() else it.replaceFirstChar(Char::uppercaseChar) }
        val stockPkgs = linkedSetOf<PackageName>()
        (meta?.stockLaunchers?.map(::PackageName) ?: guess.stock).forEach { stockPkgs += it }
        var setupWraith: PackageName? = meta?.setupWraith?.let(::PackageName)
        val quirks = (meta?.quirks?.map { Quirk.valueOf(it) }?.toMutableSet() ?: guess.quirks.toMutableSet())

        // Category per package: curated package override, else the D22 updater rule, else the
        // curated group category, else the title keywords.
        data class Placed(val category: ProfileCategory, val ask: Boolean, val entry: PackageEntry)
        val placed = mutableListOf<Placed>()
        val seen = mutableSetOf<PackageName>()
        for (g in scan.groups) {
            val (gc, gask) = meta?.groups?.get(g.title)?.let { ProfileCategory.of(it.category) to it.ask } ?: ProfileCategory.guess(g.title)
            for (e in g.entries) {
                if (!seen.add(e.pkg)) continue
                if (e.pkg == KnownLaunchers.SETUPWRAITH) { setupWraith = e.pkg; quirks += Quirk.HOME_TAKEOVER_SETUPWRAITH; continue }
                if (e.pkg in KnownLaunchers.byPackage) { stockPkgs += e.pkg; continue }
                val override = meta?.packages?.get(e.pkg.value)
                val (c, ask) = when {
                    override != null -> ProfileCategory.of(override.category) to override.ask
                    ProfileCategory.isUpdater(e.pkg) -> ProfileCategory.UPDATES to true
                    else -> gc to gask
                }
                placed += Placed(c, ask, PackageEntry(e.pkg, e.note))
            }
        }
        if (setupWraith != null) quirks += Quirk.HOME_TAKEOVER_SETUPWRAITH

        val groups = placed.groupBy { it.category.groupId(it.ask) }.entries
            .sortedWith(compareBy<Map.Entry<String, List<Placed>>>({ (_, v) -> if (v.first().category.default(v.first().ask) == GroupDefault.DISABLE) 0 else 1 }, { (_, v) -> v.first().category.ordinal }))
            .map { (id, v) ->
                val c = v.first().category
                val ask = v.first().ask
                PackageGroup(id, c.title(ask, brand.replaceFirstChar(Char::uppercaseChar)), c.default(ask), v.map { it.entry })
            }

        val match = meta?.match?.let { ProfileMatch(it.models, it.devices, it.brand, it.sdkMin, it.sdkMax, it.fingerprintPrefix) }
            ?: ProfileMatch(emptyList(), emptyList(), null, guess.sdk, guess.sdk, null)
        val neverDisable = linkedSetOf<PackageName>().apply {
            meta?.neverDisable?.forEach { add(PackageName(it)) }
            addAll(guess.keep)
        }
        val tested = meta?.tested?.let { TestedInfo(it.by, it.firmware, it.daysLivedWith, it.rebootTested) }
        return Profile(
            id = folder,
            match = match,
            platform = meta?.platform?.let { Platform.valueOf(it) } ?: guess.platform,
            stockLaunchers = stockPkgs.mapNotNull { KnownLaunchers.byPackage[it] },
            liveTv = meta?.liveTv?.let(::PackageName) ?: if (meta == null) guess.liveTv else null,
            setupWraith = setupWraith,
            quirks = quirks,
            groups = groups,
            neverDisable = neverDisable,
            suggestedDock = meta?.suggestedDock?.map(::PackageName) ?: emptyList(),
            tested = tested,
            sourceSha256 = sha256(text),
        )
    }
}
