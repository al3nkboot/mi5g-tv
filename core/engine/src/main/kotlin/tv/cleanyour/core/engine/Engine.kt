// FROZEN INTERFACE (PRODUCT-SPEC section 5.9, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W3.

package tv.cleanyour.core.engine

import tv.cleanyour.core.model.*
import tv.cleanyour.core.session.TvSession
import tv.cleanyour.core.store.GatePass
import tv.cleanyour.core.planner.GuardedSection
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.planner.Step
import kotlinx.coroutines.flow.StateFlow

sealed interface JournalRecord { val planId: PlanId
    data class PlanStart(override val planId: PlanId, val tvId: TvId, val base: Sha256, val steps: Int, val hash: Sha256, val app: String, val atMs: Long) : JournalRecord   // R0: 10
    data class Begin(override val planId: PlanId, val i: Int, val op: Op, val lines: List<String>, val prior: Prior?, val atMs: Long) : JournalRecord       // R0: 10
    data class Done(override val planId: PlanId, val i: Int, val exit: Int, val verified: Boolean, val ms: Long, val resumed: Boolean = false) : JournalRecord
    data class Failed(override val planId: PlanId, val i: Int, val exit: Int?, val error: String, val stderrHead: String) : JournalRecord
    data class Section(override val planId: PlanId, val name: String, val token: RunToken, val state: SectionState, val deadlineWallMs: Long) : JournalRecord   // R0: 10
    data class Rollback(override val planId: PlanId, val from: Int) : JournalRecord
    data class Paused(override val planId: PlanId, val at: Int, val reason: PauseReason) : JournalRecord   // R0: 10
    data class End(override val planId: PlanId, val status: RunStatus) : JournalRecord }
enum class SectionState { ARMED, CONFIRMED, ROLLED_BACK }                       // R0: 10
enum class PauseReason { USER_STOP, CONNECTION_LOST, STEP_FAILED, IN_DOUBT, STALE }   // R0: 10
enum class RunStatus { COMPLETED, COMPLETED_WITH_SKIPS, ROLLED_BACK, ABANDONED }
interface Journal {
    suspend fun append(record: JournalRecord)          // append, CRC32, force before returning
    suspend fun read(planId: PlanId): List<JournalRecord>   // torn last line dropped
    suspend fun openRuns(tvId: TvId): List<PlanId> }

sealed interface RunState {
    data object Idle : RunState
    data class Running(val plan: Plan, val step: Int, val narration: List<NarrationLine>, val percent: Int, val thumbnail: ByteArray?) : RunState   // R0: 9
    data class AwaitingConfirm(val plan: Plan, val section: GuardedSection, val deadlineMs: Long, val homeCheck: Boolean?, val thumbnail: ByteArray?) : RunState   // R0: 9
    data class Paused(val plan: Plan, val at: Int, val reason: PauseReason, val error: AdbError?) : RunState   // R0: 10
    data class RollingBack(val plan: Plan, val step: Int) : RunState
    data class InDoubt(val plan: Plan, val step: Int) : RunState
    data class Finished(val plan: Plan, val status: RunStatus, val skipped: List<Step>, val home: HomeOutcome?) : RunState }   // R0: 9
enum class HomeOutcome { CONFIRMED, USER_SAID_NO, TIMED_OUT, VERIFY_FAILED, WRONG_FOCUS, CONNECTION_LOST }   // R0: 9 the UX 4.7 case
enum class NarrationState { DOING, DONE, UPCOMING, SKIPPED, FAILED }   // R0: 10
data class NarrationLine(val text: String, val state: NarrationState)
sealed interface StartResult { data object Started : StartResult; data class Refused(val reason: StartRefusal) : StartResult }   // R0: 5, 7
enum class StartRefusal { RUN_OPEN, NOT_CONNECTED, WRONG_TV, HASH_MISMATCH, GATE_REQUIRED, GATE_EXPIRED }
interface Engine {
    fun runState(tvId: TvId): StateFlow<RunState>
    suspend fun start(plan: Plan, session: TvSession, confirmed: Sha256, pass: GatePass?): StartResult   // R0: 5, 6
    suspend fun resume(tvId: TvId, session: TvSession): Unit    // safety only: re-verifies the in-doubt step, runs step R for open sections, lands in Paused
    suspend fun confirmHome(tvId: TvId): Unit                   // never gated (D17)
    suspend fun rejectHome(tvId: TvId): Unit
    suspend fun stop(tvId: TvId): Unit                          // after the current step
    suspend fun continueRun(tvId: TvId, pass: GatePass?): Unit  // R0: 6 same gate rule as start
    suspend fun undoRun(tvId: TvId, pass: GatePass?): Unit      // R0: 6
    suspend fun abandon(tvId: TvId): Unit }
