package tv.cleanyour.core.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import tv.cleanyour.core.engine.OpJson.arr
import tv.cleanyour.core.engine.OpJson.int
import tv.cleanyour.core.engine.OpJson.long
import tv.cleanyour.core.engine.OpJson.obj
import tv.cleanyour.core.engine.OpJson.optLong
import tv.cleanyour.core.engine.OpJson.optObj
import tv.cleanyour.core.engine.OpJson.optStr
import tv.cleanyour.core.engine.OpJson.str
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.TvId
import java.util.zip.CRC32

// One journal record per JSON Lines line, `{"t":...} <crc32 hex>` (ARCHITECTURE 2.4). The CRC
// covers the JSON text; a line whose CRC does not match is torn and everything after it is
// untrusted (FileJournal.read stops there).
object JournalJson {
    private val json = Json { prettyPrint = false }

    fun encode(r: JournalRecord): JsonObject = buildJsonObject {
        put("plan", r.planId.value)
        when (r) {
            is JournalRecord.PlanStart -> { put("t", "plan"); put("tv", r.tvId.value); put("base", r.base.hex); put("steps", r.steps); put("hash", r.hash.hex); put("app", r.app); put("at", r.atMs) }
            is JournalRecord.Begin -> { put("t", "begin"); put("i", r.i); put("op", OpJson.encode(r.op)); put("lines", OpJson.strings(r.lines)); r.prior?.let { put("prior", OpJson.encodePrior(it)) }; put("at", r.atMs) }
            is JournalRecord.Done -> { put("t", "done"); put("i", r.i); put("exit", r.exit); put("verified", r.verified); put("ms", r.ms); if (r.resumed) put("resumed", true) }
            is JournalRecord.Failed -> { put("t", "failed"); put("i", r.i); r.exit?.let { put("exit", it) }; put("error", r.error); put("stderr", r.stderrHead) }
            is JournalRecord.Section -> { put("t", "section"); put("name", r.name); put("token", r.token.value); put("state", r.state.name); put("deadlineWall", r.deadlineWallMs) }
            is JournalRecord.Rollback -> { put("t", "rollback"); put("from", r.from) }
            is JournalRecord.Paused -> { put("t", "paused"); put("at", r.at); put("reason", r.reason.name) }
            is JournalRecord.End -> { put("t", "end"); put("status", r.status.name) }
        }
    }

    fun decode(o: JsonObject): JournalRecord {
        val planId = PlanId(o.str("plan"))
        return when (val t = o.str("t")) {
            "plan" -> JournalRecord.PlanStart(planId, TvId(o.str("tv")), Sha256(o.str("base")), o.int("steps"), Sha256(o.str("hash")), o.str("app"), o.long("at"))
            "begin" -> JournalRecord.Begin(planId, o.int("i"), OpJson.decode(o.obj("op")), o.arr("lines").map { it.jsonPrimitive.content },
                o.optObj("prior")?.let { OpJson.decodePrior(it) }, o.long("at"))
            "done" -> JournalRecord.Done(planId, o.int("i"), o.int("exit"), o.str("verified").toBooleanStrict(), o.long("ms"), o.optStr("resumed")?.toBooleanStrict() ?: false)
            "failed" -> JournalRecord.Failed(planId, o.int("i"), o.optLong("exit")?.toInt(), o.str("error"), o.str("stderr"))
            "section" -> JournalRecord.Section(planId, o.str("name"), RunToken(o.str("token")), SectionState.valueOf(o.str("state")), o.long("deadlineWall"))
            "rollback" -> JournalRecord.Rollback(planId, o.int("from"))
            "paused" -> JournalRecord.Paused(planId, o.int("at"), PauseReason.valueOf(o.str("reason")))
            "end" -> JournalRecord.End(planId, RunStatus.valueOf(o.str("status")))
            else -> throw IllegalArgumentException("unknown journal record type $t")
        }
    }

    // The on-disk line without its newline.
    fun line(r: JournalRecord): String {
        val text = json.encodeToString(JsonObject.serializer(), encode(r))
        require('\n' !in text) { "journal json contains a newline" }
        return text + " " + crc(text)
    }

    // Null for a torn line (no CRC, bad CRC, bad JSON).
    fun parseLine(line: String): JournalRecord? {
        val space = line.lastIndexOf(' ')
        if (space <= 0) return null
        val text = line.substring(0, space)
        val crcText = line.substring(space + 1)
        if (crcText.length != 8 || crc(text) != crcText) return null
        return runCatching { decode(json.parseToJsonElement(text) as JsonObject) }.getOrNull()
    }

    fun crc(text: String): String {
        val c = CRC32()
        c.update(text.encodeToByteArray())
        return "%08x".format(c.value)
    }
}
