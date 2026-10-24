// FROZEN INTERFACE (PRODUCT-SPEC section 5.13, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W1 (seams; implementations in :android:platform, W5 and W6 as noted).

package tv.cleanyour.core.store

import tv.cleanyour.core.model.*
import java.io.InputStream
import kotlinx.coroutines.flow.StateFlow

interface DocumentStore { suspend fun <T> read(path: String, decode: (ByteArray) -> T): T?; suspend fun write(path: String, bytes: ByteArray); suspend fun delete(path: String); suspend fun list(dir: String): List<String> }  // atomic: tmp, fsync, rename, fsync dir
interface Clock { fun nowMs(): Long; fun monotonicMs(): Long }
interface IdGen { fun planId(): PlanId; fun runToken(): RunToken }   // R0: 15 ULID and 16 random hex; the planner's only source of ids
interface TvRepository { val tvs: StateFlow<List<Tv>>; suspend fun upsert(tv: Tv); suspend fun forget(tvId: TvId) }
interface BlobStore { suspend fun put(sha256: Sha256, source: InputStream, expectedSize: Long): BlobRef; fun get(sha256: Sha256): BlobRef?; suspend fun delete(sha256: Sha256); fun bundled(name: String): BlobRef }   // R0: 1 moved from :core:registry; W5 implements FileBlobStore
interface SnapshotStore { suspend fun save(snapshot: Snapshot): Sha256; suspend fun load(tvId: TvId, hash: Sha256): Snapshot?
    suspend fun baseline(tvId: TvId): Snapshot?; suspend fun latest(tvId: TvId): Snapshot?; suspend fun prune(tvId: TvId, keep: Int = 20) }   // R0: 1 moved from :core:backup; W6 implements it
object SnapshotCodec { fun canonical(snapshot: Snapshot): ByteArray = SnapshotJson.canonical(snapshot); fun decode(bytes: ByteArray): Snapshot = SnapshotJson.decode(bytes); fun hash(snapshot: Snapshot): Sha256 = SnapshotJson.hash(snapshot) }   // R0: 15 one encoder for contentHash, W1 owns it

// R0: 6 D17 enforced by type: only gate implementations mint a pass, the engine refuses a write plan without a live one
@RequiresOptIn(level = RequiresOptIn.Level.ERROR) annotation class GateIssuer
enum class GateMethod { BIOMETRIC, DEVICE_CREDENTIAL, NONE }   // NONE: no screen lock on the phone (the D17 grey line)
class GatePass @GateIssuer constructor(val tvId: TvId, val method: GateMethod, val issuedAtMs: Long, val expiresAtMs: Long)   // expires 10 min after issue
sealed interface GateResult { data class Passed(val pass: GatePass) : GateResult; data object Failed : GateResult
    data class NotAvailable(val pass: GatePass) : GateResult }   // R0: 6 no lock screen: a NONE pass plus the grey line
interface CredentialGate { suspend fun confirm(tvId: TvId, title: String, subtitle: String): GateResult }   // BiometricPrompt on Android; the fake returns Passed
