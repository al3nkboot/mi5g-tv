package tv.cleanyour.testing.fixtures

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import tv.cleanyour.core.model.AdbError
import tv.cleanyour.core.model.AdbException
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.ShellLine
import tv.cleanyour.core.model.TvId
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// Recorded command outputs keyed by the command a line runs. Lookup normalises the line: single
// quotes removed and whitespace collapsed, so `'pm' 'list' 'packages' '-d'` and `pm list packages -d`
// hit the same recording whatever quoting the renderer settles on. Regex recordings are tried after
// exact ones, in the order added. Anything unrecorded answers exit 127, like a missing binary.
class Recording {
    private val exact = ConcurrentHashMap<String, ExecResult>()
    private val patterns = CopyOnWriteArrayList<Pair<Regex, (MatchResult) -> ExecResult>>()
    @Volatile var fallback: (String) -> ExecResult = { ExecResult(127, "", "fake: no recording for: $it", false, 1) }

    fun on(command: String, stdout: String, exit: Int = 0, stderr: String = ""): Recording = apply {
        exact[normalize(command)] = ExecResult(exit, stdout, stderr, false, 1)
    }

    fun on(command: Regex, respond: (MatchResult) -> ExecResult): Recording = apply { patterns += command to respond }

    fun resultFor(lineText: String): ExecResult {
        val key = normalize(lineText)
        exact[key]?.let { return it }
        for ((re, respond) in patterns) re.matchEntire(key)?.let { return respond(it) }
        return fallback(key)
    }

    companion object {
        fun normalize(line: String): String = line.replace("'", "").trim().split(Regex("\\s+")).joinToString(" ")

        // The reference TV's raw outputs under the plain commands that produced them (see the capture
        // README). W4's batched probe scripts (D40) add their own recordings on top.
        fun miTv(capture: MiTvCapture = Fixtures.miTv()): Recording = Recording()
            .on("getprop", capture.getpropText)
            .on("pm list packages -f", capture.packagesAllText)
            .on("pm list packages -3", capture.packagesThirdPartyText)
            .on("pm list packages -d", capture.packagesDisabledAfterText)
            .on("settings list global", capture.settingsGlobalText)
            .on("settings list secure", capture.settingsSecureText)
            .on("settings list system", capture.settingsSystemText)
            .on("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN", capture.homeActivityText)
    }
}

class FakeSessionHold(private val onClose: () -> Unit) : SessionHold {
    private var closed = false
    override fun close() { if (!closed) { closed = true; onClose() } }
}

// TvSession over a Recording. Every call is logged in `calls`; pushes land in `files`, which pull
// reads back; failNext makes the next call throw AdbException like a real transport failure; a call
// while the state is not Connected throws Transport.Closed (the session contract of 5.3).
class FakeTvSession(
    override val identity: TvIdentity = Fixtures.miTv().identity(),
    val recording: Recording = Recording(),
    private val blobs: (BlobRef) -> ByteArray = { error("FakeTvSession has no blob source for ${it.path}") },
    endpoint: Endpoint = Endpoint.PlainTcp("192.0.2.10"),
) : TvSession {
    sealed interface Call {
        data class Exec(val line: String, val spec: ExecSpec) : Call
        data class Lines(val line: String, val spec: ExecSpec) : Call
        data class Push(val src: BlobRef, val dest: RemotePath) : Call
        data class Pull(val src: RemotePath, val maxBytes: Long) : Call
        data class InstallStream(val apk: BlobRef, val size: Long) : Call
        data object Reboot : Call
        data class AwaitBoot(val timeoutMs: Long) : Call
        data object Close : Call
    }

    override val tvId: TvId get() = identity.tvId
    val calls = CopyOnWriteArrayList<Call>()
    val files = ConcurrentHashMap<String, ByteArray>()
    @Volatile var failNext: AdbError? = null
    @Volatile var installOutcome: InstallOutcome = InstallOutcome.Installed
    @Volatile var bootOutcome: BootOutcome = BootOutcome.Back("fake-boot-2")
    private val holds = AtomicInteger()
    val openHolds: Int get() = holds.get()

    private val connected = ConnectionState.Connected(endpoint, identity.features, identity.maxPayload)
    private val mutableState = MutableStateFlow<ConnectionState>(connected)
    override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

    val execLines: List<String> get() = calls.mapNotNull { (it as? Call.Exec)?.line }

    fun setState(s: ConnectionState) { mutableState.value = s }

    private fun gate(call: Call) {
        calls += call
        failNext?.let { failNext = null; throw AdbException(it) }
        if (mutableState.value !is ConnectionState.Connected && call !is Call.Close && call !is Call.AwaitBoot) {
            throw AdbException(AdbError.Transport.Closed)
        }
    }

    override suspend fun exec(line: ShellLine, spec: ExecSpec): ExecResult {
        gate(Call.Exec(line.text, spec))
        val r = recording.resultFor(line.text)
        return if (r.stdout.length > spec.stdoutCap) r.copy(stdout = r.stdout.take(spec.stdoutCap), truncated = true) else r
    }

    override fun lines(line: ShellLine, spec: ExecSpec): Flow<String> = flow {
        gate(Call.Lines(line.text, spec))
        val out = recording.resultFor(line.text).stdout
        out.split('\n').let { if (out.endsWith('\n')) it.dropLast(1) else it }.forEach { emit(it) }
    }

    override suspend fun push(src: BlobRef, dest: RemotePath, onProgress: (Long) -> Unit): PushResult {
        gate(Call.Push(src, dest))
        val bytes = blobs(src)
        files[dest.absolute] = bytes
        onProgress(bytes.size.toLong())
        return PushResult(bytes.size.toLong(), 1, sha256Of(bytes))
    }

    override suspend fun pull(src: RemotePath, maxBytes: Long): ByteArray {
        gate(Call.Pull(src, maxBytes))
        val bytes = files[src.absolute] ?: throw AdbException(AdbError.NonZeroExit(1, "remote object '${src.absolute}' does not exist"))
        if (bytes.size > maxBytes) throw AdbException(AdbError.Transport.Protocol("pull larger than $maxBytes bytes"))
        return bytes.copyOf()
    }

    override suspend fun installStream(apk: BlobRef, size: Long, onProgress: (Long) -> Unit): InstallOutcome {
        gate(Call.InstallStream(apk, size))
        onProgress(size)
        return installOutcome
    }

    override suspend fun reboot() {
        gate(Call.Reboot)
        mutableState.value = ConnectionState.WaitingForBoot(0, 0)
    }

    override suspend fun awaitBoot(timeoutMs: Long): BootOutcome {
        gate(Call.AwaitBoot(timeoutMs))
        if (bootOutcome is BootOutcome.Back) mutableState.value = connected
        return bootOutcome
    }

    override fun hold(): SessionHold {
        holds.incrementAndGet()
        return FakeSessionHold { holds.decrementAndGet() }
    }

    override suspend fun close() {
        gate(Call.Close)
        mutableState.value = ConnectionState.Idle
    }
}

// SessionManager over registered fake sessions, keyed by host. allowPromptFor makes open() report
// the Allow dialog for that host (D38); a registered session with another TvId than `expected`
// gives Policy.WrongTv and never touches the session (D37).
class FakeSessionManager : SessionManager {
    private val byHost = ConcurrentHashMap<String, TvSession>()
    private val live = ConcurrentHashMap<TvId, TvSession>()
    private val mutableStates = MutableStateFlow<Map<TvId, ConnectionState>>(emptyMap())
    val allowPromptFor = ConcurrentHashMap<String, String>()   // host to fingerprint
    val opens = CopyOnWriteArrayList<Endpoint>()
    override val states: StateFlow<Map<TvId, ConnectionState>> = mutableStates.asStateFlow()

    fun register(host: String, session: TvSession) { byHost[host] = session }

    override suspend fun open(endpoint: Endpoint, expected: TvId?, onAwaitingAllow: suspend (fingerprint: String) -> Unit): OpenResult {
        opens += endpoint
        val s = byHost[endpoint.host] ?: return OpenResult.Failed(AdbError.Transport.Unreachable)
        if (expected != null && expected != s.tvId) return OpenResult.Failed(AdbError.Policy.WrongTv(expected, s.tvId))
        allowPromptFor[endpoint.host]?.let { onAwaitingAllow(it) }
        live[s.tvId] = s
        mutableStates.update { it + (s.tvId to s.state.value) }
        return OpenResult.Ok(s)
    }

    override fun existing(tvId: TvId): TvSession? = live[tvId]

    override suspend fun closeAll() {
        live.values.forEach { it.close() }
        live.clear()
        mutableStates.value = emptyMap()
    }
}
