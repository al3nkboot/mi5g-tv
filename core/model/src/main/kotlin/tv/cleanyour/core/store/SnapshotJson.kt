package tv.cleanyour.core.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.HdmiSource
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.HomeResolution
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.model.TvId
import java.security.MessageDigest
import java.util.Base64

// The one Snapshot encoder (R0: 15), behind SnapshotCodec. Owner: W1.
//
// Canonical form, schema 1: compact UTF-8 JSON, keys in the fixed order written below (never
// alphabetised by a library), map entries sorted by key (packages by name, settings by enum name),
// sets sorted, lists in their own order, components as "pkg/cls", the overlay as base64.
// contentHash = sha256 of the canonical bytes with "contentHash" written as the empty string.
// decode() rejects any other schema and any document whose contentHash does not match.
internal object SnapshotJson {
    const val SCHEMA = 1
    private val json = Json

    fun canonical(s: Snapshot): ByteArray = encode(s, blankHash = false)

    fun hash(s: Snapshot): Sha256 = Sha256(sha256Hex(encode(s, blankHash = true)))

    fun decode(bytes: ByteArray): Snapshot {
        val o = json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val schema = o.int("schema")
        require(schema == SCHEMA) { "snapshot schema $schema is not $SCHEMA" }
        val s = Snapshot(
            tvId = TvId(o.str("tvId")),
            takenAtMs = o.long("takenAtMs"),
            reason = SnapshotReason.valueOf(o.str("reason")),
            facts = facts(o.obj("facts")),
            packages = o.obj("packages").entries.associate { (k, v) -> PackageName(k) to pkgState(v.jsonObject) },
            home = HomeResolution(
                resolved = o.obj("home").strOrNull("resolved")?.let(::component),
                claimants = o.obj("home").arr("claimants").map {
                    HomeClaimant(component(it.jsonObject.str("component")), it.jsonObject.int("priority"))
                },
            ),
            settings = o.obj("settings").entries.associate { (k, v) ->
                SettingKey.valueOf(k) to (if (v is JsonNull) null else v.jsonPrimitive.content)
            },
            ime = o.strOrNull("ime")?.let(::component),
            screensaver = o.arr("screensaver").map { component(it.jsonPrimitive.content) },
            launcherOverlay = o.strOrNull("launcherOverlay")?.let { Base64.getDecoder().decode(it) },
            hdmi = o.arr("hdmi").map {
                val h = it.jsonObject
                HdmiSource(h.int("port"), h.strOrNull("cecName"), h.strOrNull("tifInputId"), h.bool("behindSwitch"))
            },
            contentHash = Sha256(o.str("contentHash")),
        )
        require(hash(s) == s.contentHash) { "snapshot contentHash does not match its content" }
        return s
    }

    private fun encode(s: Snapshot, blankHash: Boolean): ByteArray = buildJsonObject {
        put("schema", SCHEMA)
        put("tvId", s.tvId.value)
        put("takenAtMs", s.takenAtMs)
        put("reason", s.reason.name)
        put("facts", facts(s.facts))
        put("packages", buildJsonObject {
            s.packages.entries.sortedBy { it.key.value }.forEach { (k, v) -> put(k.value, pkgState(v)) }
        })
        put("home", buildJsonObject {
            put("resolved", s.home.resolved?.flat)
            put("claimants", buildJsonArray {
                s.home.claimants.forEach { c -> add(buildJsonObject { put("component", c.component.flat); put("priority", c.priority) }) }
            })
        })
        put("settings", buildJsonObject {
            s.settings.entries.sortedBy { it.key.name }.forEach { (k, v) -> put(k.name, v) }
        })
        put("ime", s.ime?.flat)
        put("screensaver", JsonArray(s.screensaver.map { JsonPrimitive(it.flat) }))
        put("launcherOverlay", s.launcherOverlay?.let { Base64.getEncoder().encodeToString(it) })
        put("hdmi", buildJsonArray {
            s.hdmi.forEach { h ->
                add(buildJsonObject {
                    put("port", h.port); put("cecName", h.cecName); put("tifInputId", h.tifInputId); put("behindSwitch", h.behindSwitch)
                })
            }
        })
        put("contentHash", if (blankHash) "" else s.contentHash.hex)
    }.toString().encodeToByteArray()

    private fun facts(f: DeviceFacts): JsonObject = buildJsonObject {
        put("brand", f.brand); put("manufacturer", f.manufacturer); put("model", f.model); put("device", f.device)
        put("serialLast4", f.serialLast4); put("sdk", f.sdk); put("release", f.release); put("fingerprint", f.fingerprint)
        put("abis", JsonArray(f.abis.map { JsonPrimitive(it) })); put("ramMb", f.ramMb); put("hasGms", f.hasGms)
        put("adbSecure", f.adbSecure); put("adbFeatures", JsonArray(f.adbFeatures.sorted().map { JsonPrimitive(it) }))
        put("installRefusal", f.installRefusal); put("locale", f.locale)
    }

    private fun facts(o: JsonObject) = DeviceFacts(
        brand = o.str("brand"), manufacturer = o.str("manufacturer"), model = o.str("model"), device = o.str("device"),
        serialLast4 = o.str("serialLast4"), sdk = o.int("sdk"), release = o.str("release"), fingerprint = o.str("fingerprint"),
        abis = o.arr("abis").map { it.jsonPrimitive.content }, ramMb = o.int("ramMb"), hasGms = o.bool("hasGms"),
        adbSecure = o.bool("adbSecure"), adbFeatures = o.arr("adbFeatures").map { it.jsonPrimitive.content }.toSet(),
        installRefusal = o.bool("installRefusal"), locale = o.str("locale"),
    )

    private fun pkgState(p: PackageState): JsonObject = buildJsonObject {
        put("enabled", p.enabled.name); put("system", p.system); put("versionCode", p.versionCode); put("signer", p.signer?.hex)
        put("persistent", p.persistent); put("tvInput", p.tvInput); put("label", p.label); put("installedByUs", p.installedByUs)
    }

    private fun pkgState(o: JsonObject) = PackageState(
        enabled = EnabledState.valueOf(o.str("enabled")), system = o.bool("system"), versionCode = o.long("versionCode"),
        signer = o.strOrNull("signer")?.let(::Sha256), persistent = o.bool("persistent"), tvInput = o.bool("tvInput"),
        label = o.strOrNull("label"), installedByUs = o.bool("installedByUs"),
    )

    private fun component(flat: String): Component {
        val slash = flat.indexOf('/')
        require(slash > 0) { "not a component: $flat" }
        return Component(PackageName(flat.substring(0, slash)), flat.substring(slash + 1))
    }

    private fun JsonObject.field(k: String): JsonElement = this[k] ?: throw IllegalArgumentException("snapshot field $k missing")
    private fun JsonObject.str(k: String) = field(k).jsonPrimitive.content
    private fun JsonObject.strOrNull(k: String) = field(k).let { if (it is JsonNull) null else it.jsonPrimitive.content }
    private fun JsonObject.int(k: String) = field(k).jsonPrimitive.int
    private fun JsonObject.long(k: String) = field(k).jsonPrimitive.long
    private fun JsonObject.bool(k: String) = field(k).jsonPrimitive.boolean
    private fun JsonObject.obj(k: String) = field(k).jsonObject
    private fun JsonObject.arr(k: String) = field(k).jsonArray

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
