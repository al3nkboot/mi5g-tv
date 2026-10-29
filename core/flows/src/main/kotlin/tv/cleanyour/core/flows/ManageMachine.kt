package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.TvAnimationScale
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.probe.TvShellRunner

// M1, the app's home once a TV exists (UX 4.11 case A).
data class TvCard(
    val id: TvId,
    val name: String,
    val room: String?,
    val clean: Boolean,               // "Clean" when this phone switched things off, "Original" after Undo everything
    val checkedAtMs: Long,
    val level: Level,
    val chip: ChipState,
    val thumbnail: ByteArray?,
    val switchedOff: Int,
    val dockNames: List<String>,
    val restorePointAtMs: Long?,
    val debugging: DebugState,
    // 0.3.2 B1: no exact profile and a stored snapshot, so "Help add this TV" can make its report.
    val helpAddTv: Boolean = false,
    // 0.3.3 D2: the new home screen is not on this TV (HomeScreenMachine says why), so the Home
    // screen row says the TV keeps its own instead of naming a dock.
    val ownHome: Boolean = false,
    // 1.6: this TV's own evening mode, DNS mode and live stats (each paired TV keeps its own).
    val platform: TvPlatform = TvPlatform.ANDROID,
    val evening: Boolean = false,
    val dns: DnsMode = DnsMode.NONE,
    val ramFreed: Boolean = false,
    val online: Boolean = false,
) {
    // 1.6 item 1: one score for the dashboard ring.
    val health: Int? get() = TvHealth.score(platform, clean, ramFreed, dns != DnsMode.NONE, evening, online)
}

data class ManageState(
    val cards: List<TvCard> = emptyList(),
    val selected: TvId? = null,
    val offline: Boolean = false,     // "Lost the TV. Is it still on? Reconnect"
    val reconnecting: Boolean = false,
    val loading: Boolean = true,
    val addOns: Boolean = true,       // STORES-PLAN 1: the "Cards and modules" row (off in the Play build)
    // 1.6 item 7: the port 853 result for the current network, and live stats of the current TV.
    val port853: Port853 = Port853.UNKNOWN,
    val stats: TvPerformanceStats? = null,
    val busy16: Boolean = false,
) {
    val current: TvCard? get() = cards.firstOrNull { it.id == selected } ?: cards.firstOrNull()
}

enum class ManageSection(val key: String) {
    HOME_SCREEN("home"), MODULES("modules"), SWITCHED_OFF("switchedoff"), LEFTOVERS("leftovers"),
    RESTORE_POINT("restore"), TV_SETTINGS("tvsettings"), TV_TOOLS("tools"), TV_APPS("apps"),
}

sealed interface ManageIntent {
    data class Select(val tvId: TvId) : ManageIntent
    data object Reconnect : ManageIntent
    data object RefreshThumbnail : ManageIntent
    data class Open(val section: ManageSection) : ManageIntent
    data object SetUpAnother : ManageIntent
    data object CleanAgain : ManageIntent
    data object AppSettings : ManageIntent
    data object HelpAddTv : ManageIntent
    // 1.6
    data object ToggleEvening : ManageIntent
    data object ApplyDns : ManageIntent
    data object CheckPort853 : ManageIntent
}

class ManageMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    initial: TvId? = null,
) : Machine<ManageState, ManageIntent, ManageEffect>(ManageState(selected = initial, addOns = d.store.addOns), scope) {

    private data class Loaded(val cards: List<TvCard>)
    private data class Thumb(val tvId: TvId, val png: ByteArray?)
    private data class Reconnected(val ok: Boolean)
    private data object Reconnecting
    private data class Port(val result: Port853)
    private data class Stats(val tvId: TvId, val stats: TvPerformanceStats?)
    private data class Prefs(val tvId: TvId, val prefs: TvPrefs16)
    private data class Busy(val on: Boolean)

    init {
        scope.launch {
            combine(d.tvs.tvs, d.sessions.states) { tvs, states -> tvs to states }.collect { (tvs, states) ->
                val cards = tvs.sortedByDescending { it.lastSeenAt }.map { tv ->
                    val run = d.engine.runState(tv.id).value
                    val off = d.ledger.switchedOffByUs(tv.id).size
                    val home = d.homePrefs.load(tv.id)
                    val latest = d.snapshots.latest(tv.id)
                    val p16 = d.prefs16.load(tv.id)
                    TvCard(
                        id = tv.id, name = tv.nickname, room = tv.room, clean = off > 0, checkedAtMs = tv.lastSeenAt, level = tv.level,
                        chip = chipFor(states[tv.id]?.takeIf { d.access.live(tv.id) != null }, run, tv.debugging),
                        thumbnail = state.value.cards.firstOrNull { it.id == tv.id }?.thumbnail,
                        switchedOff = off, dockNames = home?.dock?.map { d.nameOf(it) }.orEmpty(),
                        restorePointAtMs = latest?.takenAtMs, debugging = tv.debugging,
                        helpAddTv = tv.profileId == null && latest != null,
                        ownHome = tv.launcher == null && latest != null && Launcher.pkg !in latest.packages,
                        evening = p16.evening, dns = p16.dns, ramFreed = p16.ramFreed,
                        online = d.access.live(tv.id) != null,
                    )
                }
                send(Loaded(cards))
            }
        }
        scope.launch { thumbnail() }
    }

    override fun reduce(state: ManageState, event: Any): ManageState = when (event) {
        is Loaded -> {
            val selected = state.selected?.takeIf { id -> event.cards.any { it.id == id } } ?: event.cards.firstOrNull()?.id
            val cur = event.cards.firstOrNull { it.id == selected }
            state.copy(cards = event.cards, selected = selected, loading = false,
                offline = cur != null && cur.debugging == DebugState.ON && cur.chip == ChipState.OFFLINE)
        }
        is ManageIntent.Select -> state.copy(selected = event.tvId, stats = null)
        is Port -> state.copy(port853 = event.result)
        is Stats -> if (event.tvId == state.current?.id) state.copy(stats = event.stats) else state
        is Prefs -> state.copy(cards = state.cards.map { if (it.id == event.tvId) it.copy(evening = event.prefs.evening, dns = event.prefs.dns, ramFreed = event.prefs.ramFreed) else it })
        is Busy -> state.copy(busy16 = event.on)
        is Thumb -> state.copy(cards = state.cards.map { if (it.id == event.tvId && event.png != null) it.copy(thumbnail = event.png) else it })
        Reconnecting -> state.copy(reconnecting = true)
        is Reconnected -> state.copy(reconnecting = false, offline = !event.ok && state.offline)
        else -> state
    }

    override fun handle(event: Any, before: ManageState, after: ManageState) {
        val id = after.current?.id
        when (event) {
            is ManageIntent.Select -> scope.launch { thumbnail() }
            ManageIntent.RefreshThumbnail -> scope.launch { thumbnail() }
            ManageIntent.Reconnect -> if (id != null && !after.reconnecting) scope.launch {
                send(Reconnecting)
                val ok = d.access.session(id) is AccessResult.Ok
                send(Reconnected(ok))
                if (ok) thumbnail()
            }
            is ManageIntent.Open -> if (event.section != ManageSection.MODULES || d.store.addOns) id?.let { effect(ManageEffect.Navigate("${event.section.key}/${it.value}")) }
            ManageIntent.SetUpAnother -> effect(ManageEffect.Navigate("setup"))
            ManageIntent.CleanAgain -> id?.let { effect(ManageEffect.Navigate("setup/${it.value}")) }
            ManageIntent.AppSettings -> effect(ManageEffect.Navigate("appsettings"))
            ManageIntent.HelpAddTv -> id?.let { effect(ManageEffect.Navigate("report/tv/${it.value}")) }
            ManageIntent.CheckPort853 -> scope.launch { send(Port(d.dotProbe.check())) }
            ManageIntent.ToggleEvening -> if (id != null && !before.busy16) scope.launch { toggleEvening(id) }
            ManageIntent.ApplyDns -> if (id != null && !before.busy16) scope.launch { applyDns(id) }
            else -> {}
        }
    }

    // 1.6 item 7: always check 853 first, then write the encrypted family host or the safe mode.
    private suspend fun dnsFor(id: TvId): DnsMode? {
        val s = d.access.live(id) ?: return null
        val port = d.dotProbe.check()
        send(Port(port))
        val mode = DnsDecision.modeFor(port)
        var ok = true
        for (line in ShellRenderer.familyDnsLines(mode == DnsMode.ENCRYPTED)) {
            val r = attempt { TvShellRunner.run(s, line) }.getOrNull()
            if (r == null || r.exit != 0) ok = false
        }
        return if (ok) mode else null
    }

    private suspend fun applyDns(id: TvId) {
        send(Busy(true))
        try {
            val mode = dnsFor(id)
            if (mode != null) {
                val p = d.prefs16.load(id).copy(dns = mode)
                d.prefs16.save(id, p)
                send(Prefs(id, p))
                effect(ManageEffect.HapticDouble)
            }
        } finally { send(Busy(false)) }
    }

    // 1.6 item 5: evening = menus at 0.5x and family DNS on this TV only. The Adhan is never touched.
    private suspend fun toggleEvening(id: TvId) {
        send(Busy(true))
        try {
            val s = d.access.live(id) ?: return
            val cur = d.prefs16.load(id)
            val on = !cur.evening
            val scale = if (on) TvAnimationScale.FAST_0_5X else TvAnimationScale.DEFAULT_1X
            ShellRenderer.setAnimationScaleLines(scale).forEach { attempt { TvShellRunner.run(s, it) } }
            val dns = if (on) dnsFor(id) ?: cur.dns else cur.dns
            val p = cur.copy(evening = on, dns = dns)
            d.prefs16.save(id, p)
            send(Prefs(id, p))
            effect(ManageEffect.HapticDouble)
        } finally { send(Busy(false)) }
    }

    private suspend fun loadStats(id: TvId) {
        val s = d.access.live(id) ?: return
        val mem = attempt { TvShellRunner.run(s, ShellRenderer.memInfoLine()) }.getOrNull()
        val df = attempt { TvShellRunner.run(s, ShellRenderer.dfDataLine()) }.getOrNull()
        if (mem != null && df != null && mem.exit == 0 && df.exit == 0) send(Stats(id, TvPerformanceStats.parse(mem.stdout, df.stdout)))
    }

    // The card's thumbnail is the last screenshot; tapping refreshes it when the TV is connected.
    private suspend fun thumbnail() {
        val id = state.value.current?.id ?: state.value.selected ?: return
        val s = d.access.live(id) ?: return
        loadStats(id)
        val png = attempt { d.prober.screenshot(s) }.getOrNull() ?: return
        send(Thumb(id, png))
    }

    // For the TV card: true while a run is open on this TV (the chip says "Working").
    fun running(tvId: TvId): Boolean = d.engine.runState(tvId).value.let { it !is RunState.Idle && it !is RunState.Finished }
}
