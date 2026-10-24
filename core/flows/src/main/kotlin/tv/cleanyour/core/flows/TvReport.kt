package tv.cleanyour.core.flows

import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.spec.ProfileResolution
import tv.cleanyour.core.store.DocumentStore

// 0.3.2: the plain text reports a person can post on GitHub or send
// themselves, built from what this phone already stored (the probe snapshot), so no TV connection is
// needed. The app itself sends nothing: the preview screen copies or shares the text, and opens the
// issue form in the browser only when the person taps it (with the report in the link when it fits,
// 0.3.3 D1; nothing is public until the person posts the form).
//
// What a report may hold: the app version and build, the phone model and Android version, the TV's
// brand, maker, model, device codename, SDK, Android release and build fingerprint, how the profile
// resolved, the home screen switch decision, the HOME resolver and claimants, and the system
// packages with their state. Everything else the app knows is left out on purpose: the TV's serial
// (DeviceFacts.serialLast4), its id (TvId is derived from the serial), its name and room, every
// address, the saved Wi-Fi, the settings the probe read (device_name is the person's own words),
// the input names, the key and every code. Apps the person installed are only counted: their names
// say something about the person, the factory's system apps do not. That holds for the HOME lines
// too: a launcher the person installed is named only as "an app you installed". A package whose
// system flag the probe could not read counts as the person's (the safe side).
//
// The text is English on purpose: it is for the maintainers, and a fixed shape keeps it easy to
// read and to compare. Sections and lines keep a fixed order (packages by name, claimants by
// priority), so the same TV gives the same report.

// The app and the phone the report comes from; the app fills it from its versionName, its store
// flavour and android.os.Build.
data class AppAbout(val version: String, val flavour: String, val phone: String, val phoneAndroid: String) {
    val appLine: String get() = "$version ($flavour)"
    val phoneLine: String get() = listOf(phone, phoneAndroid).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "unknown" }
}

// Why S3 did or did not plan the new home screen, as the wizard decided it (SetupMachine.homeBlock and
// the plan). Kept beside the TV record, so Manage's report knows it without the TV.
data class HomeDecision(val planned: Boolean, val block: String? = null, val alreadyOn: Boolean = false) {
    val line: String get() = when {
        planned -> "home switch planned"
        block != null -> "home switch blocked $block"
        alreadyOn -> "home switch not needed (the new home screen is on)"
        else -> "home switch not in the plan"
    }

    companion object {
        fun of(block: HomeBlock, planHasSwitch: Boolean, alreadyOn: Boolean): HomeDecision = when {
            block != HomeBlock.NONE -> HomeDecision(false, block.name)
            planHasSwitch -> HomeDecision(true)
            else -> HomeDecision(false, alreadyOn = alreadyOn)
        }
    }
}

// tvs/<id>/decision.txt, one line ("home=planned", "home=blocked:WIZARD_STAYS_ON", "home=on" or
// "home=none"). Forget this TV and Delete everything remove it with the TV's folder.
class HomeDecisions(private val store: DocumentStore) {
    private fun path(id: TvId) = "tvs/${id.value}/decision.txt"

    suspend fun save(id: TvId, d: HomeDecision) {
        val v = when {
            d.planned -> "planned"
            d.block != null -> "blocked:${d.block}"
            d.alreadyOn -> "on"
            else -> "none"
        }
        store.write(path(id), "home=$v\n".encodeToByteArray())
    }

    suspend fun load(id: TvId): HomeDecision? = store.read(path(id)) { bytes ->
        val v = bytes.decodeToString().lines().firstOrNull { it.startsWith("home=") }?.removePrefix("home=")?.trim()
        when {
            v == null -> null
            v == "planned" -> HomeDecision(true)
            v.startsWith("blocked:") -> v.removePrefix("blocked:").takeIf { b -> HomeBlock.entries.any { it.name == b } }?.let { HomeDecision(false, it) }
            v == "on" -> HomeDecision(false, alreadyOn = true)
            v == "none" -> HomeDecision(false)
            else -> null
        }
    }
}

// Everything a report is built from.
data class TvReportInput(
    val about: AppAbout,
    val snapshot: Snapshot,
    val profile: ProfileResolution?,
    val home: HomeDecision?,
    val switchedOffByApp: Set<PackageName> = emptySet(),
)

object TvReport {
    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

    // B1 "Help add this TV": the whole TV report, system packages included, one per line. Share sends it.
    fun text(input: TvReportInput): String = buildString {
        appendLine("Clean Your TV: TV report")
        appendHeader(input.about)
        appendTv(input, Packages.LINES)
    }.trimEnd() + "\n"

    // 0.3.3 D1: the same report in fewer characters, for the GitHub form's report box, which it
    // reaches inside the link (IssueLinks). The header and the HOME lines as in text(), then the
    // system packages grouped by state, each group a sorted, comma separated list wrapped at WRAP
    // characters. The same facts as text(): a package the app switched off and that is still off is
    // in the first group; one that came back on is in its state's group. Real reports of 110 to 130
    // system packages come to about 5,000 characters encoded, under the link's cap.
    fun compact(input: TvReportInput): String = buildString {
        appendLine("Clean Your TV: TV report")
        appendHeader(input.about)
        appendTv(input, Packages.GROUPED)
    }.trimEnd() + "\n"

    const val WRAP = 100

    private enum class Packages { NONE, LINES, GROUPED }

    // B2 "Report a problem": the TV summary (no package list) when a TV was read, then the redacted
    // diagnostics log (already redacted by the app's logger).
    fun problem(about: AppAbout, input: TvReportInput?, log: String): String = buildString {
        appendLine("Clean Your TV: problem report")
        appendHeader(about)
        if (input != null) appendTv(input, Packages.NONE) else { appendLine(); appendLine("TV: none read yet") }
        appendLine()
        appendLine("Diagnostics log (addresses, serial numbers, keys and codes removed)")
        append(log.trimEnd())
    }.trimEnd() + "\n"

    private fun StringBuilder.appendHeader(about: AppAbout) {
        appendLine("App: ${v(about.appLine)}")
        appendLine("Phone: ${v(about.phoneLine)}")
    }

    private fun StringBuilder.appendTv(input: TvReportInput, packages: Packages) {
        val s = input.snapshot
        val f = s.facts
        appendLine()
        appendLine("TV")
        appendLine("Brand: ${v(f.brand)}")
        appendLine("Manufacturer: ${v(f.manufacturer)}")
        appendLine("Model: ${v(f.model)}")
        appendLine("Device: ${v(f.device)}")
        appendLine("Android: ${v(f.release)} (SDK ${f.sdk})")
        appendLine("Build: ${Scrub.text(oneLine(f.fingerprint), codes = false).ifEmpty { "unknown" }}")
        appendLine("Google Play services: ${if (f.hasGms) "yes" else "no"}")
        appendLine("Home screen it came with: ${platformOf(s)}")
        appendLine("Read from the TV: ${DAY.format(Instant.ofEpochMilli(s.takenAtMs))}${if (s.reason == SnapshotReason.BASELINE) ", before any change" else ""}")
        appendLine()
        appendLine("Matching")
        appendLine("Profile: ${LogCodes.profile(input.profile)}")
        appendLine("Home switch: ${input.home?.line?.removePrefix("home switch ") ?: "not recorded"}")
        appendLine()
        appendLine("Home screen")
        appendLine("Resolves to: ${s.home.resolved?.let { named(s, it) } ?: "nothing (or the chooser)"}")
        // Named ones first by priority and name, then the person's own apps by priority only, so the
        // order never depends on a name the report leaves out.
        val claimants = s.home.claimants.sortedWith(
            compareByDescending<tv.cleanyour.core.model.HomeClaimant> { it.priority }
                .thenBy { if (nameable(s, it.component.pkg)) 0 else 1 }
                .thenBy { if (nameable(s, it.component.pkg)) it.component.flat else "" },
        )
        if (claimants.isEmpty()) appendLine("Claimants: none")
        for (c in claimants) appendLine("Claimant: ${named(s, c.component)} priority ${c.priority}")
        val system = s.packages.filter { it.value.system }.toSortedMap(compareBy { it.value })
        val others = s.packages.size - system.size
        appendLine()
        when (packages) {
            Packages.LINES -> {
                appendLine("System apps (${system.size})")
                for ((pkg, st) in system) {
                    val ours = if (pkg in input.switchedOffByApp) ", switched off by this app" else ""
                    appendLine("${pkg.value} ${state(st.enabled)}$ours")
                }
                appendLine()
                appendLine("Other apps: $others (not named)")
            }
            Packages.GROUPED -> {
                appendLine("System apps (${system.size})")
                val off = { e: EnabledState -> e == EnabledState.DISABLED_USER || e == EnabledState.DISABLED }
                val groups = linkedMapOf<String, MutableList<String>>(
                    "Switched off by this app" to mutableListOf(),
                    "Off, disabled-user" to mutableListOf(),
                    "Off, disabled" to mutableListOf(),
                    "On, enabled" to mutableListOf(),
                    "On, default" to mutableListOf(),
                )
                for ((pkg, st) in system) {
                    val group = when {
                        pkg in input.switchedOffByApp && off(st.enabled) -> "Switched off by this app"
                        st.enabled == EnabledState.DISABLED_USER -> "Off, disabled-user"
                        st.enabled == EnabledState.DISABLED -> "Off, disabled"
                        st.enabled == EnabledState.ENABLED -> "On, enabled"
                        else -> "On, default"
                    }
                    groups.getValue(group).add(pkg.value)
                }
                for ((label, names) in groups) {
                    if (names.isEmpty()) continue
                    appendLine("$label (${names.size}):")
                    for (line in wrap(names, WRAP)) appendLine(line)
                    appendLine()
                }
                if (system.isEmpty()) appendLine()
                appendLine("Other apps: $others (not named)")
            }
            Packages.NONE -> appendLine("Apps: ${system.size} system, $others other")
        }
    }

    // "a, b, c" in lines of at most `width` characters (a longer name gets a line of its own); every
    // line but the last ends with the comma that follows its last name.
    private fun wrap(names: List<String>, width: Int): List<String> {
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        for ((i, n) in names.withIndex()) {
            val piece = if (i < names.lastIndex) "$n," else n
            if (line.isNotEmpty() && line.length + 1 + piece.length > width) { lines.add(line.toString()); line.clear() }
            if (line.isNotEmpty()) line.append(' ')
            line.append(piece)
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        return lines
    }

    // A HOME activity is named only when it belongs to a system package the probe read as one, or to
    // the launcher this app installs. Any other launcher (Projectivy, Wolf and the like) is an app the
    // person chose, so it stays unnamed like every other app of theirs.
    const val NOT_NAMED = "an app you installed (not named)"

    private fun nameable(s: Snapshot, pkg: PackageName): Boolean = pkg == Launcher.pkg || s.packages[pkg]?.system == true

    private fun named(s: Snapshot, c: tv.cleanyour.core.model.Component): String = if (nameable(s, c.pkg)) c.flat else NOT_NAMED

    private fun state(e: EnabledState): String = when (e) {
        EnabledState.ENABLED -> "enabled"
        EnabledState.DEFAULT -> "default"
        EnabledState.DISABLED_USER -> "disabled-user"
        EnabledState.DISABLED -> "disabled"
    }

    private fun oneLine(s: String) = s.map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }.joinToString("").trim()

    // A TV-provided value, on one line, masked (LogCodes.clean) and never empty.
    private fun v(s: String): String = LogCodes.clean(s, 120).ifEmpty { "unknown" }

    // The home screen the TV came with, for the tv_support form's "platform" field, from the TV's own
    // packages and facts (never from a profile: the TVs that need a report have none). A guess, so
    // the form lets the person change it.
    fun platformOf(s: Snapshot): String {
        val f = s.facts
        val has = { p: String -> s.packages.keys.any { it.value == p } }
        val claimants = s.home.claimants.map { it.component.pkg.value }
        return when {
            f.manufacturer.equals("Amazon", true) || f.brand.equals("Amazon", true) || f.model.startsWith("AFT", true) ||
                has("com.amazon.tv.launcher") || has("com.amazon.firehomestarter") -> "Fire TV"
            has("com.google.android.apps.tv.launcherx") -> "Google TV"
            has("com.google.android.tvlauncher") || has("com.google.android.leanbacklauncher") -> "Android TV (Leanback)"
            claimants.any { it != Launcher.pkg.value && !it.startsWith("com.google.") && !it.startsWith("com.android.") && "setupwraith" !in it } -> "The maker's own launcher"
            else -> "Not sure"
        }
    }

    // "Hisense 55A6H": the brand, then the model unless it already starts with the brand.
    fun tvName(s: Snapshot): String {
        val brand = v(s.facts.brand)
        val model = v(s.facts.model)
        return if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"
    }

    // "Android 11, RTM6.230109.155": the release and the build id from the fingerprint
    // (brand/product/device:release/id/incremental:type/tags).
    fun androidLine(s: Snapshot): String {
        val id = s.facts.fingerprint.split(':').getOrNull(1)?.split('/')?.getOrNull(1)?.let { LogCodes.clean(it, 40) }
        return listOfNotNull("Android ${v(s.facts.release)}", id?.takeIf { it.isNotEmpty() }).joinToString(", ")
    }
}

// The new-issue links the preview screen opens (B1, B2). GitHub issue forms take a field's id as a
// query parameter and prefill that field. 0.3.3 D1 (issues #8, #12, #13, #14, #16: the paste step was
// missed or went into the wrong box): the report itself goes into the form's report box through the
// link when the whole link stays at or under MAX_URL characters; otherwise the box stays empty (the
// form's placeholder asks for a paste, and the box is required) and the app copies the report, as
// the preview screen says before it opens GitHub. The short fields are cut to MAX_VALUE each; the
// report is all or nothing, never cut.
object IssueLinks {
    const val REPO = "https://github.com/al3nkboot/mi5g-tv"
    const val MAX_VALUE = 200
    // GitHub answers links of about 8 KB and more with an error page; 7,000 keeps a margin for the
    // browser and for a redirect through the sign-in page, which carries the link as a parameter.
    const val MAX_URL = 7000

    // The form fields that hold the report (the ids in .github/ISSUE_TEMPLATE).
    const val TV_REPORT_FIELD = "report"
    const val PROBLEM_REPORT_FIELD = "diagnostics"

    // `reportInLink`: the report rides in the link, so the form opens with it in the report box.
    data class Link(val url: String, val reportInLink: Boolean)

    // B1 with the compact TV report (TvReport.compact).
    fun tvSupport(input: TvReportInput, report: String? = TvReport.compact(input)): Link = link("tv_support.yml", listOf(
        "title" to "TV support: ${TvReport.tvName(input.snapshot)}",
        "model" to TvReport.tvName(input.snapshot),
        "android" to TvReport.androidLine(input.snapshot),
        "platform" to TvReport.platformOf(input.snapshot),
    ), TV_REPORT_FIELD, report)

    // B2 with the problem report (TvReport.problem).
    fun bugReport(about: AppAbout, input: TvReportInput?, report: String? = null): Link = link("bug_report.yml", listOfNotNull(
        "title" to (input?.let { "Problem: ${TvReport.tvName(it.snapshot)}" } ?: "Problem report"),
        "version" to about.appLine,
        "phone" to about.phoneLine,
        input?.let { "tv" to "${TvReport.tvName(it.snapshot)}, ${TvReport.androidLine(it.snapshot)} (${TvReport.platformOf(it.snapshot)})" },
    ), PROBLEM_REPORT_FIELD, report)

    private fun link(template: String, params: List<Pair<String, String>>, reportField: String, report: String?): Link {
        val query = (listOf("template" to template) + params).joinToString("&") { (k, v) -> "$k=${enc(v.take(MAX_VALUE))}" }
        val base = "$REPO/issues/new?$query"
        if (report.isNullOrBlank()) return Link(base, false)
        val full = "$base&$reportField=${enc(report.trimEnd())}"
        return if (full.length <= MAX_URL) Link(full, true) else Link(base, false)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
