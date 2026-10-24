package tv.cleanyour.core.backup

import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.store.DocumentStore
import tv.cleanyour.core.store.SnapshotCodec
import tv.cleanyour.core.store.SnapshotStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// SnapshotStore over the app's DocumentStore (ARCHITECTURE 5.1): one gzip JSON document per
// snapshot under tvs/<tvId>/snapshots/, encoded by SnapshotCodec. The file name carries what the
// queries need, so baseline, latest and prune never decode a document:
//
//   <takenAtMs, 13 digits>-<reason>-<contentHash>.json.gz
//
// The first BASELINE of a TV is kept forever (F10); any later BASELINE is pruned like the rest.
// After every save the non-protected snapshots are pruned to the newest `keep` (20 by default),
// so a caller that never calls prune still stays bounded. Saving the same snapshot twice is a
// no-op; a snapshot whose contentHash does not match its content is refused before any write.
class DocumentSnapshotStore(
    private val docs: DocumentStore,
    private val keep: Int = DEFAULT_KEEP,
) : SnapshotStore {
    init { require(keep >= 0) }

    override suspend fun save(snapshot: Snapshot): Sha256 {
        val hash = SnapshotCodec.hash(snapshot)
        require(hash == snapshot.contentHash) { "snapshot contentHash does not match its content" }
        if (entries(snapshot.tvId).none { it.hash == hash }) {
            val name = Entry(snapshot.takenAtMs, snapshot.reason, hash).fileName
            docs.write(dir(snapshot.tvId) + "/" + name, gzip(SnapshotCodec.canonical(snapshot)))
        }
        prune(snapshot.tvId, keep)
        return hash
    }

    override suspend fun load(tvId: TvId, hash: Sha256): Snapshot? =
        entries(tvId).firstOrNull { it.hash == hash }?.let { read(tvId, it) }

    override suspend fun baseline(tvId: TvId): Snapshot? = protectedBaseline(entries(tvId))?.let { read(tvId, it) }

    override suspend fun latest(tvId: TvId): Snapshot? =
        entries(tvId).maxWithOrNull(compareBy<Entry> { it.takenAtMs }.thenBy { it.fileName })?.let { read(tvId, it) }

    override suspend fun prune(tvId: TvId, keep: Int) {
        require(keep >= 0)
        val all = entries(tvId)
        val baseline = protectedBaseline(all)
        val rest = all.filter { it != baseline }.sortedWith(compareByDescending<Entry> { it.takenAtMs }.thenByDescending { it.fileName })
        for (e in rest.drop(keep)) docs.delete(dir(tvId) + "/" + e.fileName)
    }

    private fun protectedBaseline(all: List<Entry>): Entry? =
        all.filter { it.reason == SnapshotReason.BASELINE }.minWithOrNull(compareBy<Entry> { it.takenAtMs }.thenBy { it.fileName })

    private suspend fun entries(tvId: TvId): List<Entry> = docs.list(dir(tvId)).mapNotNull(Entry::parse)

    private suspend fun read(tvId: TvId, e: Entry): Snapshot? =
        docs.read(dir(tvId) + "/" + e.fileName) { SnapshotCodec.decode(gunzip(it)) }
            ?.also { check(it.contentHash == e.hash && it.tvId == tvId) { "snapshot ${e.fileName} does not match its name" } }

    private data class Entry(val takenAtMs: Long, val reason: SnapshotReason, val hash: Sha256) {
        val fileName: String get() = String.format(Locale.ROOT, "%013d-%s-%s%s", takenAtMs, reason.name, hash.hex, SUFFIX)

        companion object {
            private val NAME = Regex("^(\\d{13,19})-([A-Z_]+)-([0-9a-f]{64})\\.json\\.gz$")
            fun parse(name: String): Entry? {
                val m = NAME.matchEntire(name) ?: return null
                val reason = SnapshotReason.entries.firstOrNull { it.name == m.groupValues[2] } ?: return null
                val at = m.groupValues[1].toLongOrNull() ?: return null
                return Entry(at, reason, Sha256(m.groupValues[3]))
            }
        }
    }

    companion object {
        const val DEFAULT_KEEP = 20
        private const val SUFFIX = ".json.gz"
        // Upper bound for one decompressed snapshot; 500 packages is about 200 KB of JSON.
        private const val MAX_JSON_BYTES = 16 * 1024 * 1024

        fun dir(tvId: TvId): String = "tvs/${tvId.value}/snapshots"

        internal fun gzip(bytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(bytes) }
            return out.toByteArray()
        }

        internal fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                check(out.size() <= MAX_JSON_BYTES) { "snapshot larger than $MAX_JSON_BYTES bytes" }
            }
            out.toByteArray()
        }
    }
}
