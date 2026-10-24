package tv.cleanyour.core.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.Tv
import tv.cleanyour.core.ops.UndoOrder
import tv.cleanyour.core.store.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// The two files the WRITE_RESTORE_POINT plan pushes to /sdcard/cleanyourtv/ after every plan (D44).
//
// restore-point.json, schema 1: what we changed, for a later "Take over" (0.2) and for anyone with
// a computer. It names the TV by tvId and model only: no serial, no address, no nickname (any app on
// the TV can read /sdcard on Android 9 and 10).
//
// restore.txt: the packages we switched off in the upstream debloat.txt shape, so upstream
// tools/restore.sh can read it unchanged. Grouped and commented by the profile when there is one.
class DefaultTvRestoreFiles(
    private val clock: Clock,
    private val zone: ZoneId = ZoneOffset.UTC,
) : TvRestoreFiles {

    override fun restorePointJson(tv: Tv, changes: List<ChangeRecord>): ByteArray {
        val sum = ChangeSummary.of(changes)
        return buildJsonObject {
            put("schema", SCHEMA)
            put("kind", KIND)
            put("tvId", tv.id.value)
            put("model", TextSafety.ascii(tv.facts.model, 64))
            put("createdAtMs", clock.nowMs())
            put("switchedOff", JsonArray(sum.switchedOff.map { JsonPrimitive(it.value) }))
            put("homeBefore", sum.homeBefore?.flat)
            put("homeNow", sum.homeNow?.flat)
            put("settings", buildJsonArray {
                sum.settings.forEach { s ->
                    add(buildJsonObject {
                        put("key", s.key.name); put("ns", s.key.ns.name.lowercase(Locale.ROOT)); put("name", s.key.key)
                        put("before", s.before); put("now", s.now)
                    })
                }
            })
            put("installed", JsonArray(sum.installed.map { JsonPrimitive(it.value) }))
            put("files", JsonArray(sum.files.map(::JsonPrimitive)))
            put("changes", ChangeJson.array(changes.sortedWith(UndoOrder.CHRONOLOGICAL)))
        }.toString().encodeToByteArray()
    }

    override fun restoreTxt(tv: Tv, changes: List<ChangeRecord>, profile: Profile?): ByteArray {
        val sum = ChangeSummary.of(changes)
        val stock = sum.homeBefore?.pkg ?: profile?.stockLaunchers?.firstOrNull()?.pkg
        val off = sum.switchedOff.toSet()
        val notes = HashMap<PackageName, String>()
        val groups = ArrayList<Pair<String, List<PackageName>>>()
        val placed = HashSet<PackageName>()
        profile?.groups?.forEach { g ->
            g.packages.forEach { e -> e.note?.let { notes.putIfAbsent(e.name, it) } }
            val inGroup = g.packages.map { it.name }.filter { it in off && placed.add(it) }
            if (inGroup.isNotEmpty()) groups += g.title to inGroup
        }
        val rest = sum.switchedOff.filter { it !in placed }
        if (rest.isNotEmpty()) groups += (if (groups.isEmpty()) "Switched off" else "Also switched off") to rest

        val text = buildString {
            appendLine("# Clean Your TV restore list for ${TextSafety.ascii(tv.nickname, 40)} (${TextSafety.ascii(tv.facts.model, 40)}), ${date(clock.nowMs())}.")
            appendLine("# The apps Clean Your TV switched off on this TV, one per line. Nothing was uninstalled.")
            appendLine("# To switch them back on from a computer with upstream cleanyourtv tools:")
            appendLine("#   TV_HOST=<tv ip> tools/restore.sh restore.txt${stock?.let { " " + it.value } ?: ""}")
            appendLine("# Or with the remote: Settings > Apps > See all apps > Show system apps, pick the app, Enable.")
            if (sum.switchedOff.isEmpty()) appendLine("# Nothing is switched off right now.")
            for ((title, pkgs) in groups) {
                appendLine()
                appendLine("# ${TextSafety.ascii(title, 120)}")
                for (p in pkgs) {
                    val note = notes[p]?.let { TextSafety.ascii(it, 70) }?.takeIf { it.isNotEmpty() }
                    appendLine(if (note == null) p.value else p.value.padEnd(NOTE_COLUMN - 1) + " # " + note)
                }
            }
        }
        return text.encodeToByteArray()
    }

    private fun date(ms: Long): String = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(ms).atZone(zone))

    companion object {
        const val SCHEMA = 1
        const val KIND = "cleanyourtv-restore-point"
        const val RESTORE_POINT_FILE = "restore-point.json"
        const val RESTORE_TXT_FILE = "restore.txt"
        private const val NOTE_COLUMN = 40
    }
}
