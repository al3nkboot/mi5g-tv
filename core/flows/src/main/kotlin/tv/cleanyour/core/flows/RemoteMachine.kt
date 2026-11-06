package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.model.TvKeyEvent
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.probe.TvShellRunner

interface RemoteFavoritesStore {
    fun load(tvId: TvId): Set<String>
    suspend fun save(tvId: TvId, favorites: Set<String>)
}

class InMemoryRemoteFavoritesStore : RemoteFavoritesStore {
    private val values = mutableMapOf<TvId, Set<String>>()
    override fun load(tvId: TvId): Set<String> = values[tvId].orEmpty()
    override suspend fun save(tvId: TvId, favorites: Set<String>) { values[tvId] = favorites.toSet() }
}

sealed interface RemoteConnection { data object Connected : RemoteConnection; data object Connecting : RemoteConnection; data object NotConnected : RemoteConnection }
enum class RemoteMode { Buttons, Trackpad, Keyboard }
enum class RemoteTab { Remote, Apps, Cast, Settings }
data class RemoteApp(val pkg: String, val label: String = pkg)
data class RemoteState(
    val tvName: String = "", val connection: RemoteConnection = RemoteConnection.Connecting,
    val lastStatus: String? = null, val textInput: String = "", val mode: RemoteMode = RemoteMode.Buttons,
    val tab: RemoteTab = RemoteTab.Remote, val installedApps: List<RemoteApp> = emptyList(),
    val favorites: Set<String> = emptySet(), val appsLoading: Boolean = false, val unicodeWarning: Boolean = false, val showMoreSheet: Boolean = false,
)
sealed interface RemoteIntent {
    data class Press(val button: RemoteButton) : RemoteIntent
    data class SetMode(val mode: RemoteMode) : RemoteIntent
    data class SelectTab(val tab: RemoteTab) : RemoteIntent
    data class UpdateText(val text: String) : RemoteIntent
    data object SendText : RemoteIntent; data object Backspace : RemoteIntent; data object RefreshApps : RemoteIntent
    data class ToggleFavorite(val pkg: String) : RemoteIntent; data class LaunchApp(val pkg: String) : RemoteIntent
    data object Reconnect : RemoteIntent; data object OpenPairing : RemoteIntent
}

class RemoteMachine(private val d: ManageDeps, scope: CoroutineScope, private val tvId: TvId, private val favoritesStore: RemoteFavoritesStore = InMemoryRemoteFavoritesStore()) : Machine<RemoteState, RemoteIntent, ManageEffect>(RemoteState(favorites = favoritesStore.load(tvId)), scope) {
    private val scopeRef = scope
    private val known = listOf("com.google.android.youtube.tv", "com.netflix.ninja", "net.mbc.shahidTV", "com.teamsmart.videomanager.tv", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus")
    private val knownLabels = mapOf("com.google.android.youtube.tv" to "YouTube", "com.netflix.ninja" to "Netflix", "net.mbc.shahidTV" to "Shahid", "com.teamsmart.videomanager.tv" to "SmartTube", "com.amazon.amazonvideo.livingroom" to "Prime Video", "com.disney.disneyplus" to "Disney Plus")
    init {
        scope.launch { d.tvs.tvs.collect { tvs -> tvs.firstOrNull { it.id == tvId }?.let { send(NameUpdate(it.nickname)) } } }
        dispatch(RemoteIntent.Reconnect)
    }
    override fun reduce(state: RemoteState, event: Any): RemoteState = when (event) {
        is RemoteIntent.SetMode -> state.copy(mode = event.mode)
        is RemoteIntent.SelectTab -> state.copy(tab = event.tab)
        is RemoteIntent.UpdateText -> state.copy(textInput = event.text.take(ShellRenderer.MAX_INPUT_TEXT_LEN), unicodeWarning = event.text.any { it.code > 127 })
        is RemoteIntent.SendText -> state
        is RemoteIntent.ToggleFavorite -> state
        is ConnectionUpdate -> state.copy(connection = event.connection, lastStatus = event.status)
        is AppsUpdate -> state.copy(appsLoading = false, installedApps = event.apps)
        is FavoritesUpdate -> state.copy(favorites = event.values)
        is NameUpdate -> state.copy(tvName = event.name)
        is WarningUpdate -> state.copy(unicodeWarning = event.warning)
        is LoadingApps -> state.copy(appsLoading = true)
        is TextSent -> state.copy(textInput = "", unicodeWarning = false)
        else -> state
    }
    override fun handle(event: Any, before: RemoteState, state: RemoteState) {
        val intent = event as? RemoteIntent ?: return
        when (intent) {
            is RemoteIntent.Press -> key(intent.button)
            RemoteIntent.Backspace -> key(RemoteButton.BACKSPACE)
            RemoteIntent.SendText -> scopeRef.launch {
                val plan = runCatching { sanitizeForTv(state.textInput) }.getOrNull() ?: return@launch
                send(WarningUpdate(plan.droppedNonAscii))
                plan.latinToken?.let { send(ShellRenderer.inputTextLine(state.textInput)); send(TextSent) }
            }
            RemoteIntent.Reconnect -> probe()
            RemoteIntent.OpenPairing -> effect(ManageEffect.Navigate("setup/${tvId.value}"))
            RemoteIntent.RefreshApps -> refreshApps()
            is RemoteIntent.ToggleFavorite -> scopeRef.launch { val next = if (intent.pkg in before.favorites) before.favorites - intent.pkg else before.favorites + intent.pkg; favoritesStore.save(tvId, next); send(FavoritesUpdate(next)) }
            is RemoteIntent.LaunchApp -> scopeRef.launch { accessRun { TvShellRunner.run(it, ShellRenderer.launchPackageLine(intent.pkg)) } }
            else -> Unit
        }
    }
    private fun key(button: RemoteButton) { effect(ManageEffect.HapticLight); scopeRef.launch { accessRun { TvShellRunner.run(it, ShellRenderer.keyEventLine(keyFor(button))) } } }
    private suspend fun send(line: tv.cleanyour.core.model.ShellLine) { accessRun { TvShellRunner.run(it, line) } }
    private suspend fun accessRun(block: suspend (tv.cleanyour.core.session.TvSession) -> Unit) {
        when (val result = d.access.session(tvId)) {
            is AccessResult.Ok -> { runCatching { block(result.session) }.onSuccess { send(ConnectionUpdate(RemoteConnection.Connected, null)) }.onFailure { send(ConnectionUpdate(RemoteConnection.NotConnected, "failed")) } }
            else -> send(ConnectionUpdate(RemoteConnection.NotConnected, null))
        }
    }
    private fun probe() { scopeRef.launch { send(ConnectionUpdate(RemoteConnection.Connecting, null)); accessRun { }; } }
    private fun refreshApps() { scopeRef.launch {
        send(LoadingApps)
        val out = runCatching { var result = emptyList<String>(); accessRun { session -> result = ShellRenderer.parsePackageList(TvShellRunner.run(session, ShellRenderer.listThirdPartyPackagesLine()).stdout) }; result }.getOrDefault(emptyList()).toSet()
        send(AppsUpdate((out + known.filter { it in out }).map { RemoteApp(it, knownLabels[it] ?: it) }.sortedBy { it.label.lowercase() }))
    } }
    private data class ConnectionUpdate(val connection: RemoteConnection, val status: String?)
    private data class AppsUpdate(val apps: List<RemoteApp>); private data object LoadingApps; private data object TextSent; private data class FavoritesUpdate(val values: Set<String>); private data class NameUpdate(val name: String); private data class WarningUpdate(val warning: Boolean)
}
