package tv.cleanyour.core.flows

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.cleanyour.core.discovery.Discovery
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.Tv
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.core.session.SessionHold
import tv.cleanyour.core.session.SessionManager
import tv.cleanyour.core.session.TvSession
import tv.cleanyour.core.store.Clock
import tv.cleanyour.core.store.TvRepository

// What the app shows while a returning write waits for the TV (UX 4.3, screen M1a). The app
// observes `reach` and lays the debugging step over whatever screen asked.
sealed interface Reach {
    data object Idle : Reach
    data class Connecting(val tvId: TvId, val tvName: String) : Reach
    data class NeedsDebugging(val tvId: TvId, val tvName: String) : Reach      // M1a: port 5555 closed
    data class AwaitingAllow(val tvId: TvId, val tvName: String, val fingerprint: String) : Reach   // after "Reset my key"
    data class WrongNetwork(val tvId: TvId, val tvName: String) : Reach        // D19: by subnet, never by name
}

sealed interface AccessResult {
    data class Ok(val session: TvSession) : AccessResult
    data object Cancelled : AccessResult
    data class Failed(val error: AdbError) : AccessResult    // WrongTv (D37), NoShellV2, auth
    data object Unknown : AccessResult                        // no record for this TvId
}

// Connects to a TV the phone already knows, for any write outside the first-run wizard. A live
// session is reused; otherwise each remembered address is probed, and while port 5555 stays
// closed the debugging step shows and the probe repeats every 2 s until the TV answers or the
// person cancels. `expected` is always the TvId, so a different TV at the address never gets a
// line (D37).
class TvAccess(
    private val sessions: SessionManager,
    private val discovery: Discovery,
    private val tvs: TvRepository,
    private val clock: Clock,
    private val pollMs: Long = 2_000,
    private val giveUpMs: Long = 10 * 60_000,
) {
    private val mutableReach = MutableStateFlow<Reach>(Reach.Idle)
    val reach: StateFlow<Reach> = mutableReach.asStateFlow()
    private val cancelGen = MutableStateFlow(0L)

    fun cancelWaiting() { cancelGen.value = cancelGen.value + 1 }

    // T7 item 1: AdbTvSession closes its socket 60 s after the last hold, and the engine refuses
    // NOT_CONNECTED on an idle session. The wizard keeps one hold per TV from the connect until Done
    // or a stop card, so a slow read of "What we found" keeps the session (and its keepalive ping).
    private val held = HashMap<TvId, Pair<TvSession, SessionHold>>()

    fun keep(session: TvSession) {
        val old = synchronized(held) {
            val now = held[session.tvId]
            if (now?.first === session) return
            held.put(session.tvId, session to session.hold())
        }
        old?.second?.close()
    }

    fun release(tvId: TvId) { synchronized(held) { held.remove(tvId) }?.second?.close() }

    fun holding(tvId: TvId): Boolean = synchronized(held) { tvId in held }

    fun live(tvId: TvId): TvSession? = sessions.existing(tvId)?.takeIf { it.state.value is ConnectionState.Connected }

    suspend fun session(tvId: TvId): AccessResult {
        live(tvId)?.let { return AccessResult.Ok(it) }
        val tv = tvs.tvs.value.firstOrNull { it.id == tvId } ?: return AccessResult.Unknown
        val gen = cancelGen.value
        val hosts = tv.endpoints.filterIsInstance<Endpoint.PlainTcp>()
        try {
            var waited = 0L
            while (true) {
                if (cancelGen.value != gen) return AccessResult.Cancelled
                val net = discovery.network().value
                if (!net.onWifi || (tv.lastSubnet != null && net.subnet != null && net.subnet != tv.lastSubnet)) {
                    mutableReach.value = Reach.WrongNetwork(tvId, tv.nickname)
                } else {
                    val open = hosts.firstOrNull { discovery.probe5555(it.host) }
                    if (open != null) {
                        mutableReach.value = Reach.Connecting(tvId, tv.nickname)
                        when (val r = sessions.open(open, tvId) { fp -> mutableReach.value = Reach.AwaitingAllow(tvId, tv.nickname, fp) }) {
                            is OpenResult.Ok -> {
                                tvs.upsert(current(tvId, tv).copy(lastSeenAt = clock.nowMs(), debugging = DebugState.ON, lastSubnet = net.subnet ?: tv.lastSubnet))
                                return AccessResult.Ok(r.session)
                            }
                            is OpenResult.Failed -> when (r.error) {
                                is AdbError.Policy.WrongTv, AdbError.Transport.NoShellV2, AdbError.Auth.Denied -> return AccessResult.Failed(r.error)
                                else -> {}
                            }
                        }
                    }
                    mutableReach.value = Reach.NeedsDebugging(tvId, tv.nickname)
                }
                if (waited >= giveUpMs) return AccessResult.Cancelled
                delay(pollMs)
                waited += pollMs
            }
        } finally {
            mutableReach.value = Reach.Idle
        }
    }

    private fun current(tvId: TvId, fallback: Tv): Tv = tvs.tvs.value.firstOrNull { it.id == tvId } ?: fallback
}
