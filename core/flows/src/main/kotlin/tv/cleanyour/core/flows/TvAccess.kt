package tv.cleanyour.core.flows

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.cleanyour.core.discovery.Discovery
import tv.cleanyour.core.discovery.DiscoveryEvent
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
//
// RFC-RECONNECT-1 (Clean Your TV 0.4.6.1, issue #1578): a mesh Wi-Fi or a TV update can give the
// same TV a new address. When no saved address answers, the TV is looked for on the network the
// way "Add a TV" finds TVs. Every other address with port 5555 open is tried with openKnown: the
// app's key signs and nothing more, so a TV that never allowed this phone sees no Allow dialog,
// and one that takes the key must have this TvId. The TV found is saved at its new address.
@OptIn(ExperimentalCoroutinesApi::class)   // CompletableDeferred.getCompleted, after isCompleted
class TvAccess(
    private val sessions: SessionManager,
    private val discovery: Discovery,
    private val tvs: TvRepository,
    private val clock: Clock,
    private val pollMs: Long = 2_000,
    private val giveUpMs: Long = 10 * 60_000,
    private val searchMs: Long = 8_000,
    private val searchEveryMs: Long = 30_000,
    private val log: FlowLog = FlowLog { },
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
        fun rightNetwork(net: DiscoveryEvent.NetworkState) =
            net.onWifi && (tv.lastSubnet == null || net.subnet == null || net.subnet == tv.lastSubnet)
        try {
            return coroutineScope {
                // After one round at the saved addresses, the network is searched beside the polling,
                // so the debugging step and a TV that answers at its saved address are as before.
                val moved = CompletableDeferred<Pair<Endpoint, TvSession>>()
                val searcher = launch {
                    delay(pollMs)
                    while (true) {
                        if (rightNetwork(discovery.network().value)) {
                            findMoved(tv, hosts.map { it.host }.toSet())?.let { moved.complete(it); return@launch }
                        }
                        delay(searchEveryMs)
                    }
                }
                try {
                    poll(tv, gen, hosts, moved)
                } finally {
                    searcher.cancel()
                }
            }
        } finally {
            mutableReach.value = Reach.Idle
        }
    }

    private suspend fun poll(
        tv: Tv,
        gen: Long,
        hosts: List<Endpoint.PlainTcp>,
        moved: CompletableDeferred<Pair<Endpoint, TvSession>>,
    ): AccessResult {
        val tvId = tv.id
        var waited = 0L
        while (true) {
            if (cancelGen.value != gen) return AccessResult.Cancelled
            val net = discovery.network().value
            if (moved.isCompleted) {
                val (endpoint, session) = moved.getCompleted()
                val now = current(tvId, tv)
                // The new address replaces the saved ones: the old one may be another device's now.
                tvs.upsert(
                    now.copy(
                        endpoints = listOf(endpoint) + now.endpoints.filter { it !is Endpoint.PlainTcp },
                        lastSeenAt = clock.nowMs(),
                        debugging = DebugState.ON,
                        lastSubnet = net.subnet ?: tv.lastSubnet,
                    ),
                )
                log.line("access: the TV answered at a new address and was saved there")
                return AccessResult.Ok(session)
            }
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
            withTimeoutOrNull(pollMs) { moved.await() }
            waited += pollMs
        }
    }

    // Each host is tried once per search, when it is found with port 5555 open or not yet probed.
    // openKnown refuses a TV that has not allowed this phone (no dialog) and any other TV (WrongTv).
    private suspend fun findMoved(tv: Tv, saved: Set<String>): Pair<Endpoint, TvSession>? {
        val tried = HashSet<String>()
        var hit: Pair<Endpoint, TvSession>? = null
        var others = 0
        withTimeoutOrNull(searchMs) {
            discovery.discover().firstOrNull { e ->
                val found = when (e) {
                    is DiscoveryEvent.Found -> e.tv
                    is DiscoveryEvent.Updated -> e.tv
                    else -> null
                }
                if (found == null || found.host in saved || found.port5555Open == false || found.host in tried) return@firstOrNull false
                tried += found.host
                if (found.port5555Open != true && !discovery.probe5555(found.host)) return@firstOrNull false
                val endpoint = Endpoint.PlainTcp(found.host)
                when (val r = attempt { sessions.openKnown(endpoint, tv.id) }.getOrNull()) {
                    is OpenResult.Ok -> {
                        hit = endpoint to r.session
                        true
                    }
                    else -> {
                        others++
                        false
                    }
                }
            }
        }
        if (hit == null) {
            log.line("access: looked for the TV on the network: ${tried.size} other addresses, $others answered but were not this TV or had not allowed this phone")
        }
        return hit
    }

    private fun current(tvId: TvId, fallback: Tv): Tv = tvs.tvs.value.firstOrNull { it.id == tvId } ?: fallback
}
