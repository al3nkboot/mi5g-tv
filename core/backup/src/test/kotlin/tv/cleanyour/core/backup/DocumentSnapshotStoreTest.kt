package tv.cleanyour.core.backup

import kotlinx.coroutines.runBlocking
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.store.SnapshotCodec
import tv.cleanyour.testing.fixtures.MemoryDocumentStore
import tv.cleanyour.testing.fixtures.Samples
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentSnapshotStoreTest {
    private val docs = MemoryDocumentStore()
    private val store = DocumentSnapshotStore(docs)
    private val tvId = Samples.miTvId()

    private fun snap(reason: SnapshotReason, at: Long, id: TvId = tvId): Snapshot = Samples.snapshot(tvId = id, reason = reason, takenAtMs = at)

    private fun canon(s: Snapshot?) = SnapshotCodec.canonical(assertNotNull(s))

    @Test fun savesGzipJsonAndLoadsByHash() = runBlocking<Unit> {
        val s = snap(SnapshotReason.BASELINE, 1_000)
        assertEquals(s.contentHash, store.save(s))
        val path = docs.paths().single()
        assertTrue(path.startsWith("tvs/${tvId.value}/snapshots/0000000001000-BASELINE-${s.contentHash.hex}.json.gz"), path)
        val raw = assertNotNull(docs.bytes(path))
        assertEquals(0x1f, raw[0].toInt() and 0xff)
        assertContentEquals(canon(s), canon(store.load(tvId, s.contentHash)))
        assertNull(store.load(TvId("ffffffffffffffff"), s.contentHash))
    }

    @Test fun baselineIsTheFirstAndLatestIsTheNewest() = runBlocking<Unit> {
        val base = snap(SnapshotReason.BASELINE, 1_000)
        store.save(base)
        store.save(snap(SnapshotReason.PRE_PLAN, 2_000))
        val newest = snap(SnapshotReason.POST_PLAN, 3_000)
        store.save(newest)
        store.save(snap(SnapshotReason.BASELINE, 2_500))
        assertContentEquals(canon(base), canon(store.baseline(tvId)))
        assertContentEquals(canon(newest), canon(store.latest(tvId)))
        assertNull(store.baseline(TvId("ffffffffffffffff")))
    }

    @Test fun pruneKeepsTheBaselineForeverAndTheNewestRest() = runBlocking<Unit> {
        val small = DocumentSnapshotStore(docs, keep = 3)
        val base = snap(SnapshotReason.BASELINE, 1_000)
        small.save(base)
        (1..10).forEach { small.save(snap(SnapshotReason.PRE_PLAN, 1_000L + it * 100)) }
        val names = docs.list(DocumentSnapshotStore.dir(tvId))
        assertEquals(4, names.size, names.toString())
        assertContentEquals(canon(base), canon(small.baseline(tvId)))
        assertEquals(listOf(1_800L, 1_900L, 2_000L), names.filter { "PRE_PLAN" in it }.map { it.substringBefore('-').toLong() })
        small.prune(tvId, 0)
        assertEquals(1, docs.list(DocumentSnapshotStore.dir(tvId)).size)
        assertContentEquals(canon(base), canon(small.baseline(tvId)))
    }

    @Test fun twentyByDefault() = runBlocking<Unit> {
        store.save(snap(SnapshotReason.BASELINE, 1))
        (1..25).forEach { store.save(snap(SnapshotReason.PRE_PLAN, 1L + it)) }
        assertEquals(21, docs.list(DocumentSnapshotStore.dir(tvId)).size)
    }

    @Test fun savingTwiceWritesOnce() = runBlocking<Unit> {
        val s = snap(SnapshotReason.PRE_PLAN, 5)
        store.save(s); store.save(s)
        assertEquals(1, docs.writes.size)
    }

    @Test fun aWrongContentHashIsRefusedBeforeAnyWrite() {
        val s = snap(SnapshotReason.PRE_PLAN, 5).copy(contentHash = Samples.zeroHash)
        assertFailsWith<IllegalArgumentException> { runBlocking { store.save(s) } }
        assertTrue(docs.paths().isEmpty())
    }

    @Test fun aFailedWriteLeavesNothing() {
        docs.failNextWrite = IOException("disk full")
        assertFailsWith<IOException> { runBlocking { store.save(snap(SnapshotReason.BASELINE, 5)) } }
        assertTrue(docs.paths().isEmpty())
    }

    @Test fun tvsAreKeptApartAndForeignFilesIgnored() = runBlocking<Unit> {
        val other = TvId("0123456789abcdef")
        store.save(snap(SnapshotReason.BASELINE, 1))
        store.save(snap(SnapshotReason.BASELINE, 2, other))
        docs.write(DocumentSnapshotStore.dir(tvId) + "/notes.txt", byteArrayOf(1))
        assertEquals(tvId, store.latest(tvId)?.tvId)
        assertEquals(other, store.latest(other)?.tvId)
        store.prune(tvId, 0)
        assertTrue(docs.paths().any { it.endsWith("notes.txt") })
    }

    @Test fun aTamperedDocumentDoesNotLoad() = runBlocking<Unit> {
        val s = snap(SnapshotReason.PRE_PLAN, 5)
        store.save(s)
        val path = docs.paths().single()
        val json = DocumentSnapshotStore.gunzip(docs.bytes(path)!!).decodeToString().replace("\"versionCode\":0", "\"versionCode\":1")
        docs.write(path, DocumentSnapshotStore.gzip(json.encodeToByteArray()))
        assertFailsWith<IllegalArgumentException> { store.load(tvId, s.contentHash) }
        Unit
    }
}
