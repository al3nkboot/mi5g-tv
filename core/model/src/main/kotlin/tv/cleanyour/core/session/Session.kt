// FROZEN INTERFACE (PRODUCT-SPEC section 5.3, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W1 (interfaces; W2 implements in :adb:session).

package tv.cleanyour.core.session   // R0: 1 these interfaces live in :core:model; :adb:session implements them as AdbTvSession and AdbSessionManager

import tv.cleanyour.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class RetryClass { IDEMPOTENT_READ, IDEMPOTENT_WRITE, NEVER }
data class ExecSpec(val timeoutMs: Long = 30_000, val idleTimeoutMs: Long = 10_000, val stdoutCap: Int = 256 * 1024,
    val stderrCap: Int = 16 * 1024, val retry: RetryClass = RetryClass.NEVER) { companion object { val Default = ExecSpec(); val Stream = ExecSpec(timeoutMs = 120_000, idleTimeoutMs = 5_000, stdoutCap = 4 * 1024 * 1024, retry = RetryClass.IDEMPOTENT_READ) } }
data class ExecResult(val exit: Int, val stdout: String, val stderr: String, val truncated: Boolean, val millis: Long)
data class PushResult(val bytes: Long, val millis: Long, val remoteSha256: Sha256?)
sealed interface InstallOutcome { data object Installed : InstallOutcome; data class Failed(val code: String, val detail: String) : InstallOutcome }
sealed interface BootOutcome { data class Back(val bootId: String) : BootOutcome; data object Timeout : BootOutcome }
interface SessionHold : AutoCloseable
// R0: 12 what the session itself reads at every connect (D37, D38); Prober.facts builds the full DeviceFacts from it
data class TvIdentity(val tvId: TvId, val serialLast4: String, val device: String, val model: String, val sdk: Int,
    val adbSecure: Boolean, val features: Set<String>, val maxPayload: Int)

interface TvSession {
    val tvId: TvId
    val identity: TvIdentity                          // R0: 12 (was facts: DeviceFacts, which the session cannot fill)
    val state: StateFlow<ConnectionState>
    suspend fun exec(line: ShellLine, spec: ExecSpec = ExecSpec.Default): ExecResult
    fun lines(line: ShellLine, spec: ExecSpec = ExecSpec.Stream): Flow<String>
    suspend fun push(src: BlobRef, dest: RemotePath, onProgress: (Long) -> Unit = {}): PushResult
    suspend fun pull(src: RemotePath, maxBytes: Long): ByteArray     // R0: 3
    suspend fun installStream(apk: BlobRef, size: Long, onProgress: (Long) -> Unit = {}): InstallOutcome
    suspend fun reboot()
    suspend fun awaitBoot(timeoutMs: Long): BootOutcome              // R0: 16 relative to the call, not a wall-clock deadline
    fun hold(): SessionHold                           // keeps the socket open; last hold closed starts the 60 s idle close
    suspend fun close() }

sealed interface OpenResult { data class Ok(val session: TvSession) : OpenResult; data class Failed(val error: AdbError) : OpenResult }
interface SessionManager {
    val states: StateFlow<Map<TvId, ConnectionState>>
    suspend fun open(endpoint: Endpoint, expected: TvId?, onAwaitingAllow: suspend (fingerprint: String) -> Unit): OpenResult
    fun existing(tvId: TvId): TvSession?
    suspend fun closeAll() }
