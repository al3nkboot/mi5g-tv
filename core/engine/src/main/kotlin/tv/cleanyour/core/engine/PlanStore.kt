package tv.cleanyour.core.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import tv.cleanyour.core.engine.OpJson.arr
import tv.cleanyour.core.engine.OpJson.bool
import tv.cleanyour.core.engine.OpJson.int
import tv.cleanyour.core.engine.OpJson.long
import tv.cleanyour.core.engine.OpJson.obj
import tv.cleanyour.core.engine.OpJson.optObj
import tv.cleanyour.core.engine.OpJson.optStr
import tv.cleanyour.core.engine.OpJson.str
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.RawShell
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.ops.FailurePolicy
import tv.cleanyour.core.ops.Privilege
import tv.cleanyour.core.ops.Rendered
import tv.cleanyour.core.ops.Transfer
import tv.cleanyour.core.planner.GroupSummary
import tv.cleanyour.core.planner.GuardedSection
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.planner.PlanKind
import tv.cleanyour.core.planner.PlanSummary
import tv.cleanyour.core.planner.RowSummary
import tv.cleanyour.core.planner.Step
import tv.cleanyour.core.store.DocumentStore

// Where the plan as reviewed lives while its run is open (`tvs/<tvId>/plans/<planId>.json`,
// ARCHITECTURE 5.1), so a run can be resumed after process death. The engine never trusts the
// stored lines on their own: on load it re-renders every op and compares lines and hash exactly as
// `start` does, so a tampered plan file is refused before anything is sent.
interface PlanStore {
    suspend fun save(plan: Plan)
    suspend fun load(tvId: TvId, planId: PlanId): Plan?
}

class DocumentPlanStore(private val documents: DocumentStore) : PlanStore {
    private fun path(tvId: TvId, planId: PlanId) = "tvs/${tvId.value}/plans/${planId.value}.json"
    override suspend fun save(plan: Plan) = documents.write(path(plan.tvId, plan.id), PlanJson.encode(plan))
    override suspend fun load(tvId: TvId, planId: PlanId): Plan? = documents.read(path(tvId, planId)) { PlanJson.decode(it) }
}

// The Plan JSON codec, schema 1. The third and last @RawShell opt-in besides the renderer and the
// probe scripts (R0 "deliberately left open"): it rebuilds ShellLine values from disk, which is why
// the engine re-renders and compares them before use.
@OptIn(RawShell::class)
object PlanJson {
    const val SCHEMA = 1
    private val json = Json { prettyPrint = false }

    fun encode(plan: Plan): ByteArray = json.encodeToString(JsonObject.serializer(), toJson(plan)).encodeToByteArray()

    fun decode(bytes: ByteArray): Plan = fromJson(json.parseToJsonElement(bytes.decodeToString()).jsonObject)

    fun toJson(plan: Plan): JsonObject = buildJsonObject {
        put("schema", SCHEMA)
        put("id", plan.id.value); put("tvId", plan.tvId.value); put("kind", plan.kind.name); put("base", plan.baseSnapshot.hex)
        put("steps", JsonArray(plan.steps.map { step(it) }))
        put("sections", JsonArray(plan.sections.map { section(it) }))
        put("summary", summary(plan.summary))
        put("hash", plan.hash.hex); put("createdAtMs", plan.createdAtMs); put("appVersion", plan.appVersion)
    }

    fun fromJson(o: JsonObject): Plan {
        require(o.int("schema") == SCHEMA) { "plan schema ${o.optStr("schema")} is not $SCHEMA" }
        val steps = o.arr("steps").mapIndexed { i, e -> step(e.jsonObject).also { require(it.index == i) { "step index ${it.index} at position $i" } } }
        return Plan(
            id = PlanId(o.str("id")), tvId = TvId(o.str("tvId")), kind = PlanKind.valueOf(o.str("kind")), baseSnapshot = Sha256(o.str("base")),
            steps = steps, sections = o.arr("sections").map { section(it.jsonObject) }, summary = summary(o.obj("summary")),
            hash = Sha256(o.str("hash")), createdAtMs = o.long("createdAtMs"), appVersion = o.str("appVersion"),
        )
    }

    private fun step(s: Step): JsonObject = buildJsonObject {
        put("i", s.index); put("op", OpJson.encode(s.op)); put("rendered", rendered(s.rendered))
        s.prior?.let { put("prior", OpJson.encodePrior(it)) }
        s.group?.let { put("group", it) }
        put("narration", s.narration); put("onFailure", s.onFailure.name)
    }

    private fun step(o: JsonObject): Step {
        val op = OpJson.decode(o.obj("op"))
        return Step(o.int("i"), op, rendered(op, o.obj("rendered")), o.optObj("prior")?.let { OpJson.decodePrior(it) },
            o.optStr("group"), o.str("narration"), FailurePolicy.valueOf(o.str("onFailure")))
    }

    private fun rendered(r: Rendered): JsonObject = buildJsonObject {
        put("lines", OpJson.strings(r.lines.map { it.text }))
        r.transfer?.let { put("transfer", transfer(it)) }
        put("privilege", r.privilege.name)
        r.verify?.let { put("verify", OpJson.encode(it)) }
        put("timeoutMs", r.timeoutMs); put("stdoutCap", r.stdoutCap)
    }

    private fun rendered(op: Op, o: JsonObject): Rendered = Rendered(
        op, o.arr("lines").map { ShellLine(it.jsonPrimitive.content) }, o.optObj("transfer")?.let { transfer(it) },
        Privilege.valueOf(o.str("privilege")), o.optObj("verify")?.let { OpJson.decode(it) as Op.Read }, o.long("timeoutMs"), o.int("stdoutCap"),
    )

    private fun transfer(t: Transfer): JsonObject = buildJsonObject {
        when (t) {
            is Transfer.Push -> { put("t", "push"); put("src", OpJson.blob(t.src)); put("dest", OpJson.path(t.dest)); put("mode", t.mode) }
            is Transfer.Pull -> { put("t", "pull"); put("src", OpJson.path(t.src)); put("maxBytes", t.maxBytes) }
            is Transfer.StreamInstall -> { put("t", "stream"); put("src", OpJson.blob(t.src)); put("size", t.size) }
        }
    }

    private fun transfer(o: JsonObject): Transfer = when (val t = o.str("t")) {
        "push" -> Transfer.Push(OpJson.blob(o.obj("src")), OpJson.path(o.obj("dest")), o.int("mode"))
        "pull" -> Transfer.Pull(OpJson.path(o.obj("src")), o.long("maxBytes"))
        "stream" -> Transfer.StreamInstall(OpJson.blob(o.obj("src")), o.long("size"))
        else -> throw IllegalArgumentException("unknown transfer $t")
    }

    private fun section(s: GuardedSection): JsonObject = buildJsonObject {
        put("name", s.name); put("from", s.from); put("to", s.to); put("token", s.token.value); put("confirmWindowMs", s.confirmWindowMs)
        put("expectedHome", s.expectedHome.flat)
        put("rollbackIfFocus", OpJson.strings(s.rollbackIfFocus.map { it.value }.sorted()))
        put("rollback", JsonArray(s.rollback.map { OpJson.encode(it) }))
    }

    private fun section(o: JsonObject): GuardedSection = GuardedSection(
        o.str("name"), o.int("from"), o.int("to"), RunToken(o.str("token")), o.long("confirmWindowMs"), OpJson.component(o.str("expectedHome")),
        o.arr("rollbackIfFocus").map { PackageName(it.jsonPrimitive.content) }.toSet(), o.arr("rollback").map { OpJson.decode(it.jsonObject) },
    )

    private fun summary(s: PlanSummary): JsonObject = buildJsonObject {
        put("groups", JsonArray(s.groups.map { g ->
            buildJsonObject {
                put("id", g.id); put("title", g.title); put("off", g.off); put("total", g.total)
                put("rows", JsonArray(g.rows.map { r ->
                    buildJsonObject {
                        put("pkg", r.pkg.value); put("friendlyName", r.friendlyName); put("description", r.description)
                        put("off", r.off); put("protected", r.protected); r.whyProtected?.let { put("whyProtected", it) }
                    }
                }))
            }
        }))
        put("keepNames", OpJson.strings(s.keepNames)); put("dock", OpJson.strings(s.dock))
        put("homeScreenPossible", s.homeScreenPossible); put("replacesLauncher", s.replacesLauncher)
    }

    private fun summary(o: JsonObject): PlanSummary = PlanSummary(
        groups = o.arr("groups").map { g ->
            val go = g.jsonObject
            GroupSummary(go.str("id"), go.str("title"), go.int("off"), go.int("total"), go.arr("rows").map { r ->
                val ro = r.jsonObject
                RowSummary(PackageName(ro.str("pkg")), ro.str("friendlyName"), ro.str("description"), ro.bool("off"), ro.bool("protected"), ro.optStr("whyProtected"))
            })
        },
        keepNames = o.arr("keepNames").map { it.jsonPrimitive.content },
        dock = o.arr("dock").map { it.jsonPrimitive.content },
        homeScreenPossible = o.bool("homeScreenPossible"), replacesLauncher = o.bool("replacesLauncher"),
    )

}
