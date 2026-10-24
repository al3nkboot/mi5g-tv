package tv.cleanyour.core.spec

import java.io.File

// Build-time converter (D28), run by :core:spec's convertProfiles task:
//   ProfileConverter <examples dir> <profile-meta.json> <upstream commit> <output resources dir> [unconverted.txt]
// Parses every examples/<folder>/debloat.txt, refuses a folder without curated metadata or a
// group title the metadata does not name, computes the brand sure sets and writes profiles.json.
// Owner decision D2: the folders listed in upstream-feedback/unconverted.txt are skipped (no
// curated entry yet); any other folder without an entry still fails the build, and so does a
// listed folder that has an entry or is not upstream.
object ProfileConverter {
    // unconverted.txt: one folder per line; # comments and blank lines are ignored.
    fun readUnconverted(file: File?): Set<String> =
        file?.readLines().orEmpty().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()

    fun convert(examples: File, metaFile: File, commit: String, unconverted: Set<String> = emptySet()): Pair<ProfileBundle, List<String>> {
        val meta = ProfileMeta.decode(metaFile.readText())
        val problems = mutableListOf<String>()
        val all = examples.listFiles().orEmpty().filter { File(it, "debloat.txt").isFile }.sortedBy { it.name }
        (unconverted - all.map { it.name }.toSet()).forEach { problems += "$it: in unconverted.txt but not upstream" }
        (unconverted intersect meta.profiles.keys).forEach { problems += "$it: in both unconverted.txt and profile-meta.json" }
        val folders = all.filter { it.name !in unconverted }
        val profiles = folders.map { dir ->
            val text = File(dir, "debloat.txt").readText()
            val m = meta.profiles[dir.name]
            if (m == null) problems += "${dir.name}: no entry in profile-meta.json"
            else {
                val titles = DebloatTxt.scan(text, dir.name).groups.map { it.title }.toSet()
                (titles - m.groups.keys).forEach { problems += "${dir.name}: group title not in profile-meta.json: $it" }
                (m.groups.keys - titles).forEach { problems += "${dir.name}: profile-meta.json names a group that is not there: $it" }
            }
            DebloatTxt.parse(text, dir.name, m)
        }
        (meta.profiles.keys - all.map { it.name }.toSet()).forEach { problems += "$it: in profile-meta.json but not upstream" }
        return ProfileBundle(commit, profiles, SureSetGenerator.generate(profiles)) to problems
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size in 4..5) { "usage: ProfileConverter <examples dir> <profile-meta.json> <commit> <out dir> [unconverted.txt]" }
        val (bundle, problems) = convert(File(args[0]), File(args[1]), args[2], readUnconverted(args.getOrNull(4)?.let(::File)))
        if (problems.isNotEmpty()) {
            System.err.println(problems.joinToString("\n"))
            throw IllegalStateException("${problems.size} profile conversion problem(s)")
        }
        val out = File(args[3], ProfileBundleJson.RESOURCE.removePrefix("/"))
        out.parentFile.mkdirs()
        out.writeText(ProfileBundleJson.encode(bundle))
        println("profiles.json: ${bundle.profiles.size} profiles (${readUnconverted(args.getOrNull(4)?.let(::File)).size} upstream folders not converted), sure sets for ${bundle.sureSets.joinToString { "${it.brand} (${it.groups.sumOf { g -> g.packages.size }})" }}")
    }
}
