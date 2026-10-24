package tv.cleanyour.core.flows

import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.LauncherState
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.TrustRecord
import tv.cleanyour.core.model.Tv
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.model.WizardDraft
import tv.cleanyour.core.store.DocumentStore
import tv.cleanyour.core.store.IdGen
import tv.cleanyour.core.store.TvRepository

// TvRepository over the DocumentStore: one tvs/<tvId>/tv.json per TV (ARCHITECTURE 5.1), schema 1,
// written atomically by the store on every upsert. `load()` runs once before the first frame so
// the start destination is decided from disk (ARCHITECTURE 2.2). A record that does not parse is
// skipped, never fatal: the rest of the TVs still open.
class DocumentTvRepository(private val store: DocumentStore) : TvRepository {
    private val state = MutableStateFlow<List<Tv>>(emptyList())
    override val tvs: StateFlow<List<Tv>> = state.asStateFlow()
    private val lock = Mutex()

    suspend fun load() = lock.withLock {
        val ids = store.list("tvs")
        state.value = ids.mapNotNull { id -> store.read("tvs/$id/tv.json") { runCatching { TvJson.decode(it) }.getOrNull() } }
    }

    override suspend fun upsert(tv: Tv) = lock.withLock {
        store.write("tvs/${tv.id.value}/tv.json", TvJson.encode(tv))
        state.update { list -> if (list.any { it.id == tv.id }) list.map { if (it.id == tv.id) tv else it } else list + tv }
    }

    override suspend fun forget(tvId: TvId) = lock.withLock {
        store.delete("tvs/${tvId.value}/tv.json")
        state.update { list -> list.filterNot { it.id == tvId } }
    }
}

object TvJson {
    const val SCHEMA = 1
    private val json = Json { prettyPrint = false }

    fun encode(tv: Tv): ByteArray = json.encodeToString(JsonElement.serializer(), obj(
        "schema" to p(SCHEMA), "id" to p(tv.id.value), "nickname" to p(tv.nickname), "room" to p(tv.room),
        "facts" to facts(tv.facts), "endpoints" to JsonArray(tv.endpoints.map(::endpoint)), "lastSubnet" to p(tv.lastSubnet),
        "trust" to obj("adbKeyId" to p(tv.trust.adbKeyId), "tlsCertSha256" to p(tv.trust.tlsCertSha256?.hex),
            "firstConnectedAt" to p(tv.trust.firstConnectedAt), "pairedAt" to p(tv.trust.pairedAt)),
        "profileId" to p(tv.profileId), "level" to p(tv.level.name),
        "launcher" to (tv.launcher?.let { obj("versionCode" to p(it.versionCode), "signer" to p(it.signer.hex),
            "overlayDigest" to p(it.overlayDigest?.hex), "ourOverlayDigest" to p(it.ourOverlayDigest?.hex)) } ?: JsonNull),
        "wizard" to (tv.wizard?.let { w -> obj("step" to p(w.step), "level" to p(w.level.name),
            "kept" to JsonArray(w.kept.map { p(it.value) }), "extraOff" to JsonArray(w.extraOff.map { p(it.value) }),
            "dock" to (w.dock?.let { d -> JsonArray(d.map { p(it.value) }) } ?: JsonNull), "themeId" to p(w.themeId),
            "keepDebuggingOn" to p(w.keepDebuggingOn), "planId" to p(w.planId?.value), "updatedAtMs" to p(w.updatedAtMs),
            "curtain" to p(w.curtain)) } ?: JsonNull),
        "lastSeenAt" to p(tv.lastSeenAt), "debugging" to p(tv.debugging.name),
    )).encodeToByteArray()

    fun decode(bytes: ByteArray): Tv {
        val o = json.parseToJsonElement(bytes.decodeToString()).jsonObject
        require(o.int("schema") == SCHEMA) { "unknown tv.json schema" }
        val trust = o.getValue("trust").jsonObject
        return Tv(
            id = TvId(o.str("id")!!), nickname = o.str("nickname")!!, room = o.str("room"),
            facts = facts(o.getValue("facts").jsonObject),
            endpoints = o.getValue("endpoints").jsonArray.map { endpoint(it.jsonObject) },
            lastSubnet = o.str("lastSubnet"),
            trust = TrustRecord(trust.str("adbKeyId") ?: "", trust.str("tlsCertSha256")?.let(::Sha256), trust.long("firstConnectedAt") ?: 0, trust.long("pairedAt")),
            profileId = o.str("profileId"), level = Level.valueOf(o.str("level")!!),
            launcher = o.objOrNull("launcher")?.let { LauncherState(it.long("versionCode") ?: 0, Sha256(it.str("signer")!!),
                it.str("overlayDigest")?.let(::Sha256), it.str("ourOverlayDigest")?.let(::Sha256)) },
            wizard = o.objOrNull("wizard")?.let { w -> WizardDraft(w.int("step") ?: 0, Level.valueOf(w.str("level")!!),
                w.strings("kept").map(::PackageName).toSet(), w.strings("extraOff").map(::PackageName).toSet(),
                if (w["dock"] is JsonArray) w.strings("dock").map(::PackageName) else null, w.str("themeId"),
                w.bool("keepDebuggingOn") ?: false, w.str("planId")?.let(::PlanId), w.long("updatedAtMs") ?: 0,
                curtain = w.bool("curtain") ?: true) },   // RFC-CURTAIN-1: a draft written before the switch existed shows the curtain
            lastSeenAt = o.long("lastSeenAt") ?: 0, debugging = DebugState.valueOf(o.str("debugging") ?: "UNKNOWN"),
        )
    }

    private fun facts(f: DeviceFacts) = obj(
        "brand" to p(f.brand), "manufacturer" to p(f.manufacturer), "model" to p(f.model), "device" to p(f.device),
        "serialLast4" to p(f.serialLast4), "sdk" to p(f.sdk), "release" to p(f.release), "fingerprint" to p(f.fingerprint),
        "abis" to JsonArray(f.abis.map { p(it) }), "ramMb" to p(f.ramMb), "hasGms" to p(f.hasGms), "adbSecure" to p(f.adbSecure),
        "adbFeatures" to JsonArray(f.adbFeatures.sorted().map { p(it) }), "installRefusal" to p(f.installRefusal), "locale" to p(f.locale),
    )

    private fun facts(o: JsonObject) = DeviceFacts(
        o.str("brand") ?: "", o.str("manufacturer") ?: "", o.str("model") ?: "", o.str("device") ?: "", o.str("serialLast4") ?: "",
        o.int("sdk") ?: 0, o.str("release") ?: "", o.str("fingerprint") ?: "", o.strings("abis"), o.int("ramMb") ?: 0,
        o.bool("hasGms") ?: false, o.bool("adbSecure") ?: true, o.strings("adbFeatures").toSet(), o.bool("installRefusal") ?: false, o.str("locale") ?: "",
    )

    private fun endpoint(e: Endpoint): JsonObject = when (e) {
        is Endpoint.PlainTcp -> obj("kind" to p("plain"), "host" to p(e.host), "port" to p(e.port))
        is Endpoint.Tls -> obj("kind" to p("tls"), "host" to p(e.host), "port" to p(e.port), "serviceName" to p(e.serviceName))
    }

    private fun endpoint(o: JsonObject): Endpoint = when (o.str("kind")) {
        "tls" -> Endpoint.Tls(o.str("host")!!, o.int("port")!!, o.str("serviceName"))
        else -> Endpoint.PlainTcp(o.str("host")!!, o.int("port") ?: 5555)
    }

    private fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(linkedMapOf(*pairs))
    private fun p(v: String?): JsonElement = v?.let { JsonPrimitive(it) } ?: JsonNull
    private fun p(v: Number?): JsonElement = v?.let { JsonPrimitive(it) } ?: JsonNull
    private fun p(v: Boolean): JsonElement = JsonPrimitive(v)

    private fun JsonObject.prim(k: String): JsonPrimitive? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.str(k: String): String? = prim(k)?.content
    private fun JsonObject.int(k: String): Int? = prim(k)?.int
    private fun JsonObject.long(k: String): Long? = prim(k)?.long
    private fun JsonObject.bool(k: String): Boolean? = prim(k)?.boolean
    private fun JsonObject.objOrNull(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.strings(k: String): List<String> = (this[k] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
}

// IdGen for the planner (R0: 15): a ULID (48-bit millisecond time, 80 random bits, Crockford
// base32) and a 16 hex run token from SecureRandom.
class SecureIdGen(private val clock: tv.cleanyour.core.store.Clock, private val random: SecureRandom = SecureRandom()) : IdGen {
    override fun planId(): PlanId {
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        val sb = StringBuilder(26)
        var t = clock.nowMs()
        val time = CharArray(10)
        for (i in 9 downTo 0) { time[i] = alphabet[(t and 31).toInt()]; t = t ushr 5 }
        sb.append(time)
        repeat(16) { sb.append(alphabet[random.nextInt(32)]) }
        return PlanId(sb.toString())
    }

    override fun runToken(): RunToken {
        val b = ByteArray(8).also(random::nextBytes)
        return RunToken(b.joinToString("") { "%02x".format(it) })
    }
}
