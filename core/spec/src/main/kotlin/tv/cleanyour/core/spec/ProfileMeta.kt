package tv.cleanyour.core.spec

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The curated half of a profile: what a person reading the upstream header decided (match
// strings, platform, stock launcher, quirks, keep lists, tested info) and the category of every
// upstream group title. Bundled as profile-meta.json next to this class; DebloatTxtParser falls
// back to header keywords for a folder that has no entry (a newer upstream example).
@Serializable
internal data class ProfileMetaFile(val schema: Int, val upstreamCommit: String? = null, val profiles: Map<String, FolderMeta>)

@Serializable
internal data class FolderMeta(
    val match: MatchMeta,
    val platform: String,
    val stockLaunchers: List<String> = emptyList(),
    val liveTv: String? = null,
    val setupWraith: String? = null,
    val quirks: List<String> = emptyList(),
    val neverDisable: List<String> = emptyList(),
    val suggestedDock: List<String> = emptyList(),
    val tested: TestedMeta? = null,
    val groups: Map<String, CategoryMeta> = emptyMap(),
    val packages: Map<String, CategoryMeta> = emptyMap(),
)

@Serializable
internal data class MatchMeta(
    val models: List<String> = emptyList(), val devices: List<String> = emptyList(), val brand: String? = null,
    val sdkMin: Int? = null, val sdkMax: Int? = null, val fingerprintPrefix: String? = null,
)

@Serializable
internal data class TestedMeta(val by: String? = null, val firmware: String? = null, val daysLivedWith: Int? = null, val rebootTested: Boolean = false)

@Serializable
internal data class CategoryMeta(val category: String, val ask: Boolean = false)

internal object ProfileMeta {
    const val RESOURCE = "/tv/cleanyour/core/spec/profile-meta.json"
    private val json = Json { ignoreUnknownKeys = false }

    fun decode(text: String): ProfileMetaFile = json.decodeFromString(ProfileMetaFile.serializer(), text).also {
        require(it.schema == 1) { "profile-meta schema ${it.schema} is not 1" }
    }

    val bundled: ProfileMetaFile by lazy {
        val text = ProfileMeta::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().decodeToString() }
        if (text == null) ProfileMetaFile(1, null, emptyMap()) else decode(text)
    }
}
