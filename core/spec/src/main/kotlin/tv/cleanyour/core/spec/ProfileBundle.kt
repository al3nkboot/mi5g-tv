package tv.cleanyour.core.spec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tv.cleanyour.core.model.Component
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

// profiles.json, schema 1: every upstream example converted at build time (D28) plus the brand
// sure sets computed from them (D26). Written by ProfileConverter, read by BundledProfileSource.
data class SureSet(val brand: String, val profileIds: List<String>, val groups: List<PackageGroup>, val stockLaunchers: List<Component>)
data class ProfileBundle(val upstreamCommit: String?, val profiles: List<Profile>, val sureSets: List<SureSet>)

internal object ProfileBundleJson {
    const val RESOURCE = "/tv/cleanyour/core/spec/profiles.json"
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable data class BundleDto(val schema: Int, val upstreamCommit: String?, val profiles: List<ProfileDto>, val sureSets: List<SureSetDto>)
    @Serializable data class ProfileDto(
        val id: String, val match: MatchDto, val platform: String, val stockLaunchers: List<String>, val liveTv: String?,
        val setupWraith: String?, val quirks: List<String>, val groups: List<GroupDto>, val neverDisable: List<String>,
        val suggestedDock: List<String>, val tested: TestedDto?, val sourceSha256: String,
    )
    @Serializable data class MatchDto(val models: List<String>, val devices: List<String>, val brand: String?, val sdkMin: Int?, val sdkMax: Int?, val fingerprintPrefix: String?)
    @Serializable data class TestedDto(val by: String?, val firmware: String?, val daysLivedWith: Int?, val rebootTested: Boolean)
    @Serializable data class GroupDto(val id: String, val title: String, val default: String, val packages: List<EntryDto>)
    @Serializable data class EntryDto(val name: String, val note: String? = null)
    @Serializable data class SureSetDto(val brand: String, val profiles: List<String>, val groups: List<GroupDto>, val stockLaunchers: List<String>)

    private fun component(flat: String): Component {
        val i = flat.indexOf('/')
        require(i > 0) { "not a component: $flat" }
        return Component(PackageName(flat.substring(0, i)), flat.substring(i + 1))
    }

    private fun PackageGroup.dto() = GroupDto(id, title, default.name, packages.map { EntryDto(it.name.value, it.note) })
    private fun GroupDto.model() = PackageGroup(id, title, GroupDefault.valueOf(default), packages.map { PackageEntry(PackageName(it.name), it.note) })

    fun encode(b: ProfileBundle): String = json.encodeToString(BundleDto.serializer(), BundleDto(
        schema = 1, upstreamCommit = b.upstreamCommit,
        profiles = b.profiles.map { p ->
            ProfileDto(
                p.id, p.match.let { MatchDto(it.models, it.devices, it.brand, it.sdkMin, it.sdkMax, it.fingerprintPrefix) }, p.platform.name,
                p.stockLaunchers.map { it.flat }, p.liveTv?.value, p.setupWraith?.value, p.quirks.map { it.name }.sorted(), p.groups.map { it.dto() },
                p.neverDisable.map { it.value }, p.suggestedDock.map { it.value },
                p.tested?.let { TestedDto(it.by, it.firmware, it.daysLivedWith, it.rebootTested) }, p.sourceSha256.hex,
            )
        },
        sureSets = b.sureSets.map { s -> SureSetDto(s.brand, s.profileIds, s.groups.map { it.dto() }, s.stockLaunchers.map { it.flat }) },
    )) + "\n"

    fun decode(text: String): ProfileBundle {
        val d = json.decodeFromString(BundleDto.serializer(), text)
        require(d.schema == 1) { "profiles.json schema ${d.schema} is not 1" }
        return ProfileBundle(
            d.upstreamCommit,
            d.profiles.map { p ->
                Profile(
                    id = p.id, match = p.match.let { ProfileMatch(it.models, it.devices, it.brand, it.sdkMin, it.sdkMax, it.fingerprintPrefix) },
                    platform = Platform.valueOf(p.platform), stockLaunchers = p.stockLaunchers.map(::component),
                    liveTv = p.liveTv?.let(::PackageName), setupWraith = p.setupWraith?.let(::PackageName),
                    quirks = p.quirks.map { Quirk.valueOf(it) }.toSet(), groups = p.groups.map { it.model() },
                    neverDisable = p.neverDisable.map(::PackageName).toSet(), suggestedDock = p.suggestedDock.map(::PackageName),
                    tested = p.tested?.let { TestedInfo(it.by, it.firmware, it.daysLivedWith, it.rebootTested) }, sourceSha256 = Sha256(p.sourceSha256),
                )
            },
            d.sureSets.map { s -> SureSet(s.brand, s.profiles, s.groups.map { it.model() }, s.stockLaunchers.map(::component)) },
        )
    }
}

// D26: for a model we have no profile for, the brand's "sure set" is every package that sits in
// a default-disable group in at least `minProfiles` profiles of that brand and in no profile's
// never-disable list. Brands with fewer profiles get no sure set (launcher path only).
object SureSetGenerator {
    const val MIN_PROFILES = 3

    fun generate(profiles: List<Profile>, minProfiles: Int = MIN_PROFILES): List<SureSet> {
        val keep = profiles.flatMap { it.neverDisable }.toSet()
        return profiles.filter { it.match.brand != null }
            .groupBy { it.match.brand!!.lowercase() }
            .filterValues { it.size >= minProfiles }
            .toSortedMap()
            .map { (_, ps) ->
                val brand = ps.first().match.brand!!
                val count = LinkedHashMap<PackageName, Int>()
                val firstSeen = LinkedHashMap<PackageName, Pair<PackageGroup, PackageEntry>>()
                val groupVotes = LinkedHashMap<PackageName, MutableMap<String, Int>>()
                for (p in ps) for (g in p.groups.filter { it.default == GroupDefault.DISABLE }) for (e in g.packages) {
                    count[e.name] = (count[e.name] ?: 0) + 1
                    firstSeen.putIfAbsent(e.name, g to e)
                    groupVotes.getOrPut(e.name) { LinkedHashMap() }.let { v -> v[g.id] = (v[g.id] ?: 0) + 1 }
                }
                val sure = count.filter { (pkg, n) -> n >= minProfiles && pkg !in keep }.keys
                // Each package goes to the group id most of the brand's profiles file it under.
                val byGroup = LinkedHashMap<String, MutableList<PackageEntry>>()
                val titles = HashMap<String, String>()
                for (pkg in sure) {
                    val votes = groupVotes.getValue(pkg)
                    val id = votes.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { order(it.key) }).first().key
                    byGroup.getOrPut(id) { mutableListOf() } += PackageEntry(pkg, firstSeen.getValue(pkg).second.note)
                    titles.putIfAbsent(id, ps.asSequence().flatMap { it.groups }.first { it.id == id }.title)
                }
                val groups = byGroup.entries.sortedBy { order(it.key) }.map { (id, es) -> PackageGroup(id, titles.getValue(id), GroupDefault.DISABLE, es.sortedBy { it.name.value }) }
                val launchers = (ps.flatMap { it.stockLaunchers } + KnownLaunchers.generic).distinct()
                SureSet(brand, ps.map { it.id }.sorted(), groups, launchers)
            }
    }

    private fun order(groupId: String): Int = ProfileCategory.entries.indexOfFirst { groupId.removeSuffix("-optional") == it.id }
}
