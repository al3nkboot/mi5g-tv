package tv.cleanyour.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import tv.cleanyour.core.engine.Engine
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.StartResult
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.session.BootOutcome
import tv.cleanyour.core.session.ExecResult
import tv.cleanyour.core.session.ExecSpec
import tv.cleanyour.core.session.InstallOutcome
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.core.session.PushResult
import tv.cleanyour.core.session.SessionHold
import tv.cleanyour.core.session.SessionManager
import tv.cleanyour.core.session.TvIdentity
import tv.cleanyour.core.session.TvSession
import tv.cleanyour.core.store.GatePass
import tv.cleanyour.core.store.TvRepository

// The engine as the app wires it (T7). Two things DefaultEngine cannot do on its own:
//
// 1. A run keeps the TvSession it was started with, and continueRun, undoRun and abandon refuse
//    quietly unless that object is Connected. AdbTvSession reconnects in place (a new open of the
//    same TV is adopted by the same object), except after close(): "Reset my key", "Forget this TV"
//    and "Delete everything" close sessions for good, and the next open makes a new object. The
//    engine is therefore handed a CurrentSession, which always talks to the manager's session for
//    that TV, so a run paused before a key reset is carried on, undone or ended on the new one.
//
// 2. AdbTvSession drops to Idle 60 s after its last hold, and the engine refuses NOT_CONNECTED on
//    an idle session, so a write that follows a slow read of a screen, a credential prompt or a
//    long download would fail. Before every start, resume, carry on, undo and abandon the session
//    is re-opened through sessions.open(endpoint, tvId) when it is idle, failed on the transport,
//    or gone: the same open the flows use, with the expected TvId (D37), adopted in place.
//    In-progress states, a refused key and a different TV are left to the flows' own cards.
class LiveSessionEngine(
    private val delegate: Engine,
    private val sessions: SessionManager,
    private val tvs: TvRepository,
    private val reopenTimeoutMs: Long = 20_000,
) : Engine {

    override fun runState(tvId: TvId): StateFlow<RunState> = delegate.runState(tvId)

    override suspend fun start(plan: Plan, session: TvSession, confirmed: Sha256, pass: GatePass?): StartResult {
        ensureConnected(session.tvId)
        return delegate.start(plan, follow(session), confirmed, pass)
    }

    override suspend fun resume(tvId: TvId, session: TvSession) {
        ensureConnected(tvId)
        delegate.resume(tvId, follow(session))
    }

    override suspend fun confirmHome(tvId: TvId) = delegate.confirmHome(tvId)
    override suspend fun rejectHome(tvId: TvId) = delegate.rejectHome(tvId)
    override suspend fun stop(tvId: TvId) = delegate.stop(tvId)

    override suspend fun continueRun(tvId: TvId, pass: GatePass?) {
        ensureConnected(tvId)
        delegate.continueRun(tvId, pass)
    }

    override suspend fun undoRun(tvId: TvId, pass: GatePass?) {
        ensureConnected(tvId)
        delegate.undoRun(tvId, pass)
    }

    override suspend fun abandon(tvId: TvId) {
        ensureConnected(tvId)
        delegate.abandon(tvId)
    }

    private fun follow(session: TvSession): TvSession = if (session is CurrentSession) session else CurrentSession(sessions, session)

    // One quiet re-open attempt per remembered address. Never throws: a TV that does not answer
    // leaves the session as it was, and the engine's own refusal reaches the flows.
    suspend fun ensureConnected(tvId: TvId) {
        if (!needsReopen(sessions.existing(tvId)?.state?.value)) return
        val tv = tvs.tvs.value.firstOrNull { it.id == tvId } ?: return
        for (endpoint in tv.endpoints.filterIsInstance<Endpoint.PlainTcp>()) {
            val r = try {
                withTimeoutOrNull(reopenTimeoutMs) { sessions.open(endpoint, tvId) { } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            when {
                r is OpenResult.Ok -> return
                r is OpenResult.Failed && isFinal(r.error) -> return
            }
        }
    }

    companion object {
        fun needsReopen(state: ConnectionState?): Boolean = when (state) {
            null, ConnectionState.Idle -> true
            is ConnectionState.Failed -> !isFinal(state.error)
            is ConnectionState.Connected, is ConnectionState.Connecting, is ConnectionState.AwaitingAllow,
            is ConnectionState.Reconnecting, is ConnectionState.WaitingForBoot -> false
        }

        // A refused key, a different TV or a TV without shell v2: another try would not help.
        private fun isFinal(e: AdbError): Boolean = e is AdbError.Policy || e is AdbError.Auth || e == AdbError.Transport.NoShellV2
    }
}

// A TvSession that always talks to the session the manager holds for this TV now, or to the one
// it was made from while the manager holds none. Same TvId by construction, and every open checks
// the identity against it (D37), so following the manager never reaches another TV.
class CurrentSession(private val sessions: SessionManager, private val initial: TvSession) : TvSession {
    override val tvId: TvId = initial.tvId

    fun current(): TvSession = sessions.existing(tvId) ?: initial

    override val identity: TvIdentity get() = current().identity
    override val state: StateFlow<ConnectionState> = FollowedState()

    override suspend fun exec(line: ShellLine, spec: ExecSpec): ExecResult = current().exec(line, spec)
    override fun lines(line: ShellLine, spec: ExecSpec): Flow<String> = current().lines(line, spec)
    override suspend fun push(src: BlobRef, dest: RemotePath, onProgress: (Long) -> Unit): PushResult = current().push(src, dest, onProgress)
    override suspend fun pull(src: RemotePath, maxBytes: Long): ByteArray = current().pull(src, maxBytes)
    override suspend fun installStream(apk: BlobRef, size: Long, onProgress: (Long) -> Unit): InstallOutcome = current().installStream(apk, size, onProgress)
    override suspend fun reboot() = current().reboot()
    override suspend fun awaitBoot(timeoutMs: Long): BootOutcome = current().awaitBoot(timeoutMs)
    override fun hold(): SessionHold = current().hold()
    override suspend fun close() = current().close()

    // The state of whichever session is current. `value` is read straight through (the engine only
    // reads it); collectors switch to a new session when the manager's map changes.
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private inner class FollowedState : StateFlow<ConnectionState> {
        override val value: ConnectionState get() = current().state.value
        override val replayCache: List<ConnectionState> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<ConnectionState>): Nothing {
            sessions.states.map { current() }.distinctUntilChanged { a, b -> a === b }
                .flatMapLatest { it.state }.distinctUntilChanged().collect(collector)
            awaitCancellation()
        }
    }
}
