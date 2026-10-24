package tv.cleanyour.testing.fixtures

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.model.Tv
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.store.BlobStore
import tv.cleanyour.core.store.Clock
import tv.cleanyour.core.store.CredentialGate
import tv.cleanyour.core.store.DocumentStore
import tv.cleanyour.core.store.GateIssuer
import tv.cleanyour.core.store.GateMethod
import tv.cleanyour.core.store.GatePass
import tv.cleanyour.core.store.GateResult
import tv.cleanyour.core.store.IdGen
import tv.cleanyour.core.store.SnapshotCodec
import tv.cleanyour.core.store.SnapshotStore
import tv.cleanyour.core.store.TvRepository
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

// Wall and monotonic time that only move when a test says so. setWall jumps the wall clock alone,
// the way a phone's clock can change under a running app.
class FakeClock(wallMs: Long = 1_760_000_000_000L, monotonicMs: Long = 0L) : Clock {
    private val wall = AtomicLong(wallMs)
    private val mono = AtomicLong(monotonicMs)
    override fun nowMs(): Long = wall.get()
    override fun monotonicMs(): Long = mono.get()
    fun advance(ms: Long) { require(ms >= 0); wall.addAndGet(ms); mono.addAndGet(ms) }
    fun setWall(ms: Long) { wall.set(ms) }
}

// Deterministic ids: PlanId "01FAKEPLAN" plus a 16 digit counter (26 characters like a ULID),
// RunToken the counter as 16 lowercase hex.
class FakeIdGen(start: Long = 1) : IdGen {
    private val next = AtomicLong(start)
    override fun planId(): PlanId = PlanId("01FAKEPLAN" + next.getAndIncrement().toString().padStart(16, '0'))
    override fun runToken(): RunToken = RunToken("%016x".format(next.getAndIncrement()))
}

// In-memory DocumentStore. Writes are all or nothing like the real atomic store: a fault armed with
// failNextWrite throws before anything changes. list(dir) returns the immediate children's names,
// sorted (a child folder appears once, without a trailing slash).
class MemoryDocumentStore : DocumentStore {
    private val files = ConcurrentHashMap<String, ByteArray>()
    val writes = CopyOnWriteArrayList<String>()
    @Volatile var failNextWrite: IOException? = null

    private fun norm(path: String): String {
        val p = path.trim('/')
        require(p.isNotEmpty() && p.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "bad document path: $path" }
        return p
    }

    override suspend fun <T> read(path: String, decode: (ByteArray) -> T): T? = files[norm(path)]?.let { decode(it.copyOf()) }

    override suspend fun write(path: String, bytes: ByteArray) {
        val p = norm(path)
        failNextWrite?.let { failNextWrite = null; throw it }
        files[p] = bytes.copyOf()
        writes += p
    }

    override suspend fun delete(path: String) { files.remove(norm(path)) }

    override suspend fun list(dir: String): List<String> {
        val prefix = dir.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        return files.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }.distinct().sorted()
    }

    fun paths(): Set<String> = files.keys.toSortedSet()
    fun bytes(path: String): ByteArray? = files[norm(path)]?.copyOf()
}

class MemoryTvRepository(initial: List<Tv> = emptyList()) : TvRepository {
    private val state = MutableStateFlow(initial)
    override val tvs: StateFlow<List<Tv>> = state.asStateFlow()
    override suspend fun upsert(tv: Tv) { state.update { list -> list.filterNot { it.id == tv.id } + tv } }
    override suspend fun forget(tvId: TvId) { state.update { list -> list.filterNot { it.id == tvId } } }
}

// Content-addressed blobs in memory. put verifies size and sha256 like FileBlobStore must.
class MemoryBlobStore(bundled: Map<String, ByteArray> = emptyMap()) : BlobStore {
    private val blobs = ConcurrentHashMap<Sha256, ByteArray>()
    private val bundledRefs = bundled.mapValues { (name, bytes) -> store(bytes, "bundled:$name") }

    private fun store(bytes: ByteArray, path: String?): BlobRef {
        val sha = sha256Of(bytes)
        blobs[sha] = bytes.copyOf()
        return BlobRef(sha, bytes.size.toLong(), path ?: "mem:${sha.hex}")
    }

    // Test helper: store bytes and get their ref without the expected-hash ceremony.
    fun add(bytes: ByteArray): BlobRef = store(bytes, null)

    override suspend fun put(sha256: Sha256, source: InputStream, expectedSize: Long): BlobRef {
        val bytes = source.use { it.readBytes() }
        if (bytes.size.toLong() != expectedSize) throw IOException("size ${bytes.size} is not $expectedSize")
        if (sha256Of(bytes) != sha256) throw IOException("sha256 mismatch")
        return store(bytes, null)
    }

    override fun get(sha256: Sha256): BlobRef? = blobs[sha256]?.let { BlobRef(sha256, it.size.toLong(), "mem:${sha256.hex}") }
    override suspend fun delete(sha256: Sha256) { blobs.remove(sha256) }
    override fun bundled(name: String): BlobRef = bundledRefs[name] ?: error("no bundled blob $name")

    fun bytes(ref: BlobRef): ByteArray = blobs[ref.sha256]?.copyOf() ?: error("blob ${ref.sha256.hex} not in store")
}

// Stores snapshots through SnapshotCodec, so a snapshot that does not round trip fails here first.
class MemorySnapshotStore : SnapshotStore {
    private val docs = ConcurrentHashMap<Pair<TvId, Sha256>, ByteArray>()
    private val order = CopyOnWriteArrayList<Pair<TvId, Sha256>>()

    override suspend fun save(snapshot: Snapshot): Sha256 {
        val key = snapshot.tvId to snapshot.contentHash
        if (docs.putIfAbsent(key, SnapshotCodec.canonical(snapshot)) == null) order += key
        return snapshot.contentHash
    }

    override suspend fun load(tvId: TvId, hash: Sha256): Snapshot? = docs[tvId to hash]?.let(SnapshotCodec::decode)

    private fun all(tvId: TvId) = order.filter { it.first == tvId }.mapNotNull { docs[it]?.let(SnapshotCodec::decode) }

    override suspend fun baseline(tvId: TvId): Snapshot? = all(tvId).firstOrNull { it.reason == SnapshotReason.BASELINE }
    override suspend fun latest(tvId: TvId): Snapshot? = all(tvId).maxByOrNull { it.takenAtMs }

    // The BASELINE is kept forever (F10); of the rest the newest `keep` survive.
    override suspend fun prune(tvId: TvId, keep: Int) {
        val rest = all(tvId).filter { it.reason != SnapshotReason.BASELINE }.sortedByDescending { it.takenAtMs }
        for (s in rest.drop(keep)) {
            val key = tvId to s.contentHash
            docs.remove(key); order.remove(key)
        }
    }
}

// Mints passes the way AndroidCredentialGate will (the only other @GateIssuer). Default: Passed.
class FakeCredentialGate(
    private val clock: Clock = FakeClock(),
    var mode: Mode = Mode.PASS,
    private val method: GateMethod = GateMethod.BIOMETRIC,
    private val validityMs: Long = 10 * 60 * 1000L,
) : CredentialGate {
    enum class Mode { PASS, FAIL, NO_SCREEN_LOCK }
    data class Prompt(val tvId: TvId, val title: String, val subtitle: String)
    val prompts = CopyOnWriteArrayList<Prompt>()

    @OptIn(GateIssuer::class)
    fun mint(tvId: TvId, method: GateMethod = this.method, issuedAtMs: Long = clock.nowMs()): GatePass =
        GatePass(tvId, method, issuedAtMs, issuedAtMs + validityMs)

    override suspend fun confirm(tvId: TvId, title: String, subtitle: String): GateResult {
        prompts += Prompt(tvId, title, subtitle)
        return when (mode) {
            Mode.PASS -> GateResult.Passed(mint(tvId))
            Mode.FAIL -> GateResult.Failed
            Mode.NO_SCREEN_LOCK -> GateResult.NotAvailable(mint(tvId, GateMethod.NONE))
        }
    }
}
