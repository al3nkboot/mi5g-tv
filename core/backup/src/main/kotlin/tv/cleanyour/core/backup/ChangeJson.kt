package tv.cleanyour.core.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.CurtainGroup
import tv.cleanyour.core.model.CurtainStage
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.InstallFlag
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.ProbeKind
import tv.cleanyour.core.model.ReadArg
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.model.SettingsNs
import tv.cleanyour.core.model.Sha256
import java.util.Base64

// JSON for ChangeRecord lists: changes.json in the restore zip and the "changes" array of
// restore-point.json. Element API only (no compiler plugin), keys in the order written here, one
// "type" discriminator per Op. Every value is decoded back through the model's own constructors,
// so a hand-edited file that breaks a grammar (a package name with a quote, say) fails to load.
object ChangeJson {
    const val SCHEMA = 1
    private val json = Json

    fun encode(changes: List<ChangeRecord>): ByteArray = buildJsonObject {
        put("schema", SCHEMA)
        put("changes", array(changes))
    }.toString().encodeToByteArray()

    fun decode(bytes: ByteArray): List<ChangeRecord> {
        val o = json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val schema = o.field("schema").jsonPrimitive.int
        require(schema == SCHEMA) { "changes schema $schema is not $SCHEMA" }
        return decodeArray(o.field("changes").jsonArray)
    }

    fun array(changes: List<ChangeRecord>): JsonArray = JsonArray(changes.map(::record))

    fun decodeArray(a: JsonArray): List<ChangeRecord> = a.map { decodeRecord(it.jsonObject) }

    fun record(c: ChangeRecord): JsonObject = buildJsonObject {
        put("planId", c.planId.value)
        put("step", c.step)
        put("atMs", c.atMs)
        put("op", op(c.op))
        put("prior", c.prior?.let(::prior) ?: JsonNull)
    }

    fun decodeRecord(o: JsonObject): ChangeRecord = ChangeRecord(
        planId = PlanId(o.str("planId")),
        step = o.int("step"),
        op = decodeOp(o.obj("op")),
        prior = o.field("prior").let { if (it is JsonNull) null else decodePrior(it.jsonObject) },
        atMs = o.long("atMs"),
    )

    fun op(op: Op): JsonObject = buildJsonObject {
        put("type", typeOf(op))
        when (op) {
            is Op.Disable -> put("pkg", op.pkg.value)
            is Op.Enable -> put("pkg", op.pkg.value)
            is Op.DefaultState -> put("pkg", op.pkg.value)
            is Op.Install -> {
                put("apk", path(op.apk)); put("sha256", op.sha256.hex); put("pkg", op.pkg.value)
                put("versionCode", op.versionCode); put("signer", op.signer.hex)
                put("flags", JsonArray(op.flags.map { it.name }.sorted().map(::JsonPrimitive)))
            }
            is Op.InstallStream -> {
                put("blob", blob(op.blob)); put("sha256", op.sha256.hex); put("pkg", op.pkg.value)
                put("versionCode", op.versionCode); put("signer", op.signer.hex)
            }
            is Op.Uninstall -> put("pkg", op.pkg.value)
            is Op.SettingsPut -> { put("key", op.key.name); put("value", op.value) }
            is Op.SetHome -> put("component", op.component.flat)
            is Op.VerifyHome -> put("expected", op.expected.flat)
            is Op.ArmHomeWatchdog -> { put("stock", op.stock.flat); put("seconds", op.seconds); put("token", op.token.value) }
            is Op.ConfirmHome -> put("token", op.token.value)
            is Op.KillWatchdog -> put("token", op.token.value)
            Op.PressHome -> {}
            is Op.Mkdir -> put("dir", op.dir.name)
            is Op.PushAsset -> { put("src", blob(op.src)); put("dest", path(op.dest)); put("sha256", op.sha256.hex) }
            is Op.RemoveFile -> put("path", path(op.path))
            is Op.RestartHome -> put("component", op.component.flat)
            is Op.StartActivity -> { put("component", op.component.flat); put("wait", op.wait) }
            is Op.Read -> { put("kind", op.kind.name); put("arg", op.arg?.let(::readArg) ?: JsonNull) }
            is Op.Settle -> put("seconds", op.seconds)
            Op.Reboot -> {}
            Op.DebugOff -> {}
            is Op.CurtainCue -> {
                put("receiver", op.receiver.flat); put("token", op.token.value); put("stage", op.stage.name)
                put("count", op.count); put("total", op.total); put("group", op.group.name); put("groupCount", op.groupCount); put("groupTotal", op.groupTotal)
            }
        }
    }

    fun decodeOp(o: JsonObject): Op = when (val type = o.str("type")) {
        "disable" -> Op.Disable(o.pkg("pkg"))
        "enable" -> Op.Enable(o.pkg("pkg"))
        "default_state" -> Op.DefaultState(o.pkg("pkg"))
        "install" -> Op.Install(
            decodePath(o.obj("apk")), o.sha("sha256"), o.pkg("pkg"), o.long("versionCode"), o.sha("signer"),
            o.arr("flags").map { InstallFlag.valueOf(it.jsonPrimitive.content) }.toSet(),
        )
        "install_stream" -> Op.InstallStream(decodeBlob(o.obj("blob")), o.sha("sha256"), o.pkg("pkg"), o.long("versionCode"), o.sha("signer"))
        "uninstall" -> Op.Uninstall(o.pkg("pkg"))
        "settings_put" -> Op.SettingsPut(SettingKey.valueOf(o.str("key")), o.strOrNull("value"))
        "set_home" -> Op.SetHome(component(o.str("component")))
        "verify_home" -> Op.VerifyHome(component(o.str("expected")))
        "arm_home_watchdog" -> Op.ArmHomeWatchdog(component(o.str("stock")), o.int("seconds"), RunToken(o.str("token")))
        "confirm_home" -> Op.ConfirmHome(RunToken(o.str("token")))
        "kill_watchdog" -> Op.KillWatchdog(RunToken(o.str("token")))
        "press_home" -> Op.PressHome
        "mkdir" -> Op.Mkdir(RemoteDir.valueOf(o.str("dir")))
        "push_asset" -> Op.PushAsset(decodeBlob(o.obj("src")), decodePath(o.obj("dest")), o.sha("sha256"))
        "remove_file" -> Op.RemoveFile(decodePath(o.obj("path")))
        "restart_home" -> Op.RestartHome(component(o.str("component")))
        "start_activity" -> Op.StartActivity(component(o.str("component")), o.bool("wait"))
        "read" -> Op.Read(ProbeKind.valueOf(o.str("kind")), o.field("arg").let { if (it is JsonNull) null else decodeReadArg(it.jsonObject) })
        "settle" -> Op.Settle(o.int("seconds"))
        "reboot" -> Op.Reboot
        "debug_off" -> Op.DebugOff
        "curtain_cue" -> Op.CurtainCue(component(o.str("receiver")), RunToken(o.str("token")), CurtainStage.valueOf(o.str("stage")),
            o.int("count"), o.int("total"), CurtainGroup.valueOf(o.str("group")), o.int("groupCount"), o.int("groupTotal"))
        else -> throw IllegalArgumentException("unknown op type $type")
    }

    fun typeOf(op: Op): String = when (op) {
        is Op.Disable -> "disable"
        is Op.Enable -> "enable"
        is Op.DefaultState -> "default_state"
        is Op.Install -> "install"
        is Op.InstallStream -> "install_stream"
        is Op.Uninstall -> "uninstall"
        is Op.SettingsPut -> "settings_put"
        is Op.SetHome -> "set_home"
        is Op.VerifyHome -> "verify_home"
        is Op.ArmHomeWatchdog -> "arm_home_watchdog"
        is Op.ConfirmHome -> "confirm_home"
        is Op.KillWatchdog -> "kill_watchdog"
        Op.PressHome -> "press_home"
        is Op.Mkdir -> "mkdir"
        is Op.PushAsset -> "push_asset"
        is Op.RemoveFile -> "remove_file"
        is Op.RestartHome -> "restart_home"
        is Op.StartActivity -> "start_activity"
        is Op.Read -> "read"
        is Op.Settle -> "settle"
        Op.Reboot -> "reboot"
        Op.DebugOff -> "debug_off"
        is Op.CurtainCue -> "curtain_cue"
    }

    fun prior(p: Prior): JsonObject = buildJsonObject {
        put("packageState", p.packageState?.name)
        put("settingValue", p.settingValue)
        put("home", p.home?.flat)
        put("overlay", p.overlay?.let { Base64.getEncoder().encodeToString(it) })
        put("present", p.present)
    }

    fun decodePrior(o: JsonObject): Prior = Prior(
        packageState = o.strOrNull("packageState")?.let { EnabledState.valueOf(it) },
        settingValue = o.strOrNull("settingValue"),
        home = o.strOrNull("home")?.let(::component),
        overlay = o.strOrNull("overlay")?.let { Base64.getDecoder().decode(it) },
        present = o.field("present").let { if (it is JsonNull) null else it.jsonPrimitive.boolean },
    )

    fun component(flat: String): Component {
        val slash = flat.indexOf('/')
        require(slash > 0) { "not a component: $flat" }
        return Component(PackageName(flat.substring(0, slash)), flat.substring(slash + 1))
    }

    private fun path(p: RemotePath): JsonObject = buildJsonObject {
        put("dir", p.dir.name)
        put("segments", JsonArray(p.segments.map(::JsonPrimitive)))
    }

    private fun decodePath(o: JsonObject) = RemotePath(RemoteDir.valueOf(o.str("dir")), o.arr("segments").map { it.jsonPrimitive.content })

    private fun blob(b: BlobRef): JsonObject = buildJsonObject { put("sha256", b.sha256.hex); put("size", b.size); put("path", b.path) }

    private fun decodeBlob(o: JsonObject) = BlobRef(o.sha("sha256"), o.long("size"), o.str("path"))

    private fun readArg(a: ReadArg): JsonObject = buildJsonObject {
        when (a) {
            is ReadArg.Pkg -> { put("type", "pkg"); put("pkg", a.pkg.value) }
            is ReadArg.Path -> { put("type", "path"); put("path", path(a.path)) }
            is ReadArg.Token -> { put("type", "token"); put("token", a.token.value) }
            is ReadArg.Ns -> { put("type", "ns"); put("ns", a.ns.name) }
        }
    }

    private fun decodeReadArg(o: JsonObject): ReadArg = when (val t = o.str("type")) {
        "pkg" -> ReadArg.Pkg(o.pkg("pkg"))
        "path" -> ReadArg.Path(decodePath(o.obj("path")))
        "token" -> ReadArg.Token(RunToken(o.str("token")))
        "ns" -> ReadArg.Ns(SettingsNs.valueOf(o.str("ns")))
        else -> throw IllegalArgumentException("unknown read argument type $t")
    }

    private fun JsonObject.field(k: String): JsonElement = this[k] ?: throw IllegalArgumentException("field $k missing")
    private fun JsonObject.str(k: String) = field(k).jsonPrimitive.also { require(it.isString) { "field $k is not a string" } }.content
    private fun JsonObject.strOrNull(k: String) = field(k).let { if (it is JsonNull) null else str(k) }
    private fun JsonObject.int(k: String) = field(k).jsonPrimitive.int
    private fun JsonObject.long(k: String) = field(k).jsonPrimitive.long
    private fun JsonObject.bool(k: String) = field(k).jsonPrimitive.boolean
    private fun JsonObject.obj(k: String) = field(k).jsonObject
    private fun JsonObject.arr(k: String) = field(k).jsonArray
    private fun JsonObject.pkg(k: String) = PackageName(str(k))
    private fun JsonObject.sha(k: String) = Sha256(str(k))
}
