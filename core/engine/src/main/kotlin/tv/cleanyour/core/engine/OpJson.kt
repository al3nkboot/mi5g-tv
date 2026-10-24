package tv.cleanyour.core.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.CurtainGroup
import tv.cleanyour.core.model.CurtainStage
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.InstallFlag
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
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

// JSON for the op vocabulary (journal and plan files, ARCHITECTURE 5.2). Hand-written over the
// element API so the frozen types need no annotations; every value goes back through its
// validating constructor on read, so a tampered file fails before it can reach a renderer.
object OpJson {
    fun encode(op: Op): JsonObject = buildJsonObject {
        when (op) {
            is Op.Disable -> { put("v", "disable"); put("pkg", op.pkg.value) }
            is Op.Enable -> { put("v", "enable"); put("pkg", op.pkg.value) }
            is Op.DefaultState -> { put("v", "default_state"); put("pkg", op.pkg.value) }
            is Op.Install -> {
                put("v", "install"); put("apk", path(op.apk)); put("sha256", op.sha256.hex); put("pkg", op.pkg.value)
                put("versionCode", op.versionCode); put("signer", op.signer.hex)
                put("flags", JsonArray(op.flags.sortedBy { it.name }.map { JsonPrimitive(it.name) }))
            }
            is Op.InstallStream -> {
                put("v", "install_stream"); put("blob", blob(op.blob)); put("sha256", op.sha256.hex); put("pkg", op.pkg.value)
                put("versionCode", op.versionCode); put("signer", op.signer.hex)
            }
            is Op.Uninstall -> { put("v", "uninstall"); put("pkg", op.pkg.value) }
            is Op.SettingsPut -> { put("v", "settings_put"); put("key", op.key.name); if (op.value != null) put("value", op.value) }
            is Op.SetHome -> { put("v", "set_home"); put("component", op.component.flat) }
            is Op.VerifyHome -> { put("v", "verify_home"); put("expected", op.expected.flat) }
            is Op.ArmHomeWatchdog -> { put("v", "arm_home_watchdog"); put("stock", op.stock.flat); put("seconds", op.seconds); put("token", op.token.value) }
            is Op.ConfirmHome -> { put("v", "confirm_home"); put("token", op.token.value) }
            is Op.KillWatchdog -> { put("v", "kill_watchdog"); put("token", op.token.value) }
            Op.PressHome -> put("v", "press_home")
            is Op.Mkdir -> { put("v", "mkdir"); put("dir", op.dir.name) }
            is Op.PushAsset -> { put("v", "push_asset"); put("src", blob(op.src)); put("dest", path(op.dest)); put("sha256", op.sha256.hex) }
            is Op.RemoveFile -> { put("v", "remove_file"); put("path", path(op.path)) }
            is Op.RestartHome -> { put("v", "restart_home"); put("component", op.component.flat) }
            is Op.StartActivity -> { put("v", "start_activity"); put("component", op.component.flat); put("wait", op.wait) }
            is Op.Read -> {
                put("v", "read"); put("kind", op.kind.name)
                when (val a = op.arg) {
                    null -> {}
                    is ReadArg.Pkg -> put("pkg", a.pkg.value)
                    is ReadArg.Path -> put("path", path(a.path))
                    is ReadArg.Token -> put("token", a.token.value)
                    is ReadArg.Ns -> put("ns", a.ns.name)
                }
            }
            is Op.Settle -> { put("v", "settle"); put("seconds", op.seconds) }
            Op.Reboot -> put("v", "reboot")
            Op.DebugOff -> put("v", "debug_off")
            is Op.CurtainCue -> {
                put("v", "curtain_cue"); put("receiver", op.receiver.flat); put("token", op.token.value); put("stage", op.stage.name)
                put("count", op.count); put("total", op.total); put("group", op.group.name); put("groupCount", op.groupCount); put("groupTotal", op.groupTotal)
            }
        }
    }

    fun decode(o: JsonObject): Op = when (val v = o.str("v")) {
        "disable" -> Op.Disable(pkg(o.str("pkg")))
        "enable" -> Op.Enable(pkg(o.str("pkg")))
        "default_state" -> Op.DefaultState(pkg(o.str("pkg")))
        "install" -> Op.Install(path(o.obj("apk")), Sha256(o.str("sha256")), pkg(o.str("pkg")), o.long("versionCode"), Sha256(o.str("signer")),
            o.arr("flags").map { InstallFlag.valueOf(it.jsonPrimitive.content) }.toSet())
        "install_stream" -> Op.InstallStream(blob(o.obj("blob")), Sha256(o.str("sha256")), pkg(o.str("pkg")), o.long("versionCode"), Sha256(o.str("signer")))
        "uninstall" -> Op.Uninstall(pkg(o.str("pkg")))
        "settings_put" -> Op.SettingsPut(SettingKey.valueOf(o.str("key")), o.optStr("value"))
        "set_home" -> Op.SetHome(component(o.str("component")))
        "verify_home" -> Op.VerifyHome(component(o.str("expected")))
        "arm_home_watchdog" -> Op.ArmHomeWatchdog(component(o.str("stock")), o.int("seconds"), RunToken(o.str("token")))
        "confirm_home" -> Op.ConfirmHome(RunToken(o.str("token")))
        "kill_watchdog" -> Op.KillWatchdog(RunToken(o.str("token")))
        "press_home" -> Op.PressHome
        "mkdir" -> Op.Mkdir(RemoteDir.valueOf(o.str("dir")))
        "push_asset" -> Op.PushAsset(blob(o.obj("src")), path(o.obj("dest")), Sha256(o.str("sha256")))
        "remove_file" -> Op.RemoveFile(path(o.obj("path")))
        "restart_home" -> Op.RestartHome(component(o.str("component")))
        "start_activity" -> Op.StartActivity(component(o.str("component")), o.bool("wait"))
        "read" -> {
            val kind = ProbeKind.valueOf(o.str("kind"))
            val arg: ReadArg? = when {
                "pkg" in o -> ReadArg.Pkg(pkg(o.str("pkg")))
                "path" in o -> ReadArg.Path(path(o.obj("path")))
                "token" in o -> ReadArg.Token(RunToken(o.str("token")))
                "ns" in o -> ReadArg.Ns(SettingsNs.valueOf(o.str("ns")))
                else -> null
            }
            Op.Read(kind, arg)
        }
        "settle" -> Op.Settle(o.int("seconds"))
        "reboot" -> Op.Reboot
        "debug_off" -> Op.DebugOff
        "curtain_cue" -> Op.CurtainCue(component(o.str("receiver")), RunToken(o.str("token")), CurtainStage.valueOf(o.str("stage")),
            o.int("count"), o.int("total"), CurtainGroup.valueOf(o.str("group")), o.int("groupCount"), o.int("groupTotal"))
        else -> throw IllegalArgumentException("unknown op verb $v")
    }

    fun encodePrior(p: Prior): JsonObject = buildJsonObject {
        p.packageState?.let { put("packageState", it.name) }
        p.settingValue?.let { put("settingValue", it) }
        p.home?.let { put("home", it.flat) }
        p.overlay?.let { put("overlay", Base64.getEncoder().encodeToString(it)) }
        p.present?.let { put("present", it) }
    }

    fun decodePrior(o: JsonObject): Prior = Prior(
        packageState = o.optStr("packageState")?.let { EnabledState.valueOf(it) },
        settingValue = o.optStr("settingValue"),
        home = o.optStr("home")?.let { component(it) },
        overlay = o.optStr("overlay")?.let { Base64.getDecoder().decode(it) },
        present = o["present"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.toBooleanStrict(),
    )

    fun path(p: RemotePath): JsonObject = buildJsonObject { put("dir", p.dir.name); put("segments", JsonArray(p.segments.map { JsonPrimitive(it) })) }
    fun path(o: JsonObject): RemotePath = RemotePath(RemoteDir.valueOf(o.str("dir")), o.arr("segments").map { it.jsonPrimitive.content })
    fun blob(b: BlobRef): JsonObject = buildJsonObject { put("sha256", b.sha256.hex); put("size", b.size); put("path", b.path) }
    fun blob(o: JsonObject): BlobRef = BlobRef(Sha256(o.str("sha256")), o.long("size"), o.str("path"))
    fun component(flat: String): Component {
        val slash = flat.indexOf('/')
        require(slash > 0 && slash < flat.length - 1) { "component is not pkg/cls: $flat" }
        return Component(pkg(flat.substring(0, slash)), flat.substring(slash + 1))
    }
    fun pkg(s: String): PackageName = PackageName(s)

    // Small readers that fail loudly on a missing or mistyped field.
    fun JsonObject.str(k: String): String = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content ?: throw IllegalArgumentException("missing $k")
    fun JsonObject.optStr(k: String): String? = this[k]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
    fun JsonObject.long(k: String): Long = str(k).toLongOrNull() ?: throw IllegalArgumentException("$k is not a number")
    fun JsonObject.int(k: String): Int = str(k).toIntOrNull() ?: throw IllegalArgumentException("$k is not a number")
    fun JsonObject.bool(k: String): Boolean = str(k).toBooleanStrictOrNull() ?: throw IllegalArgumentException("$k is not a boolean")
    fun JsonObject.obj(k: String): JsonObject = this[k]?.takeIf { it !is JsonNull }?.jsonObject ?: throw IllegalArgumentException("missing $k")
    fun JsonObject.arr(k: String): JsonArray = this[k]?.takeIf { it !is JsonNull }?.jsonArray ?: throw IllegalArgumentException("missing $k")
    fun JsonObject.optObj(k: String): JsonObject? = this[k]?.takeIf { it !is JsonNull }?.jsonObject
    fun JsonObject.optLong(k: String): Long? = optStr(k)?.let { it.toLongOrNull() ?: throw IllegalArgumentException("$k is not a number") }
    fun strings(list: List<String>): JsonElement = JsonArray(list.map { JsonPrimitive(it) })
}
