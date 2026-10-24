package tv.cleanyour.e2e

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import tv.cleanyour.core.engine.Engine
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.StartResult
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.ops.OpRenderer
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult
import tv.cleanyour.core.planner.Planner
import tv.cleanyour.core.probe.Prober
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.core.session.SessionManager
import tv.cleanyour.core.session.TvSession
import tv.cleanyour.core.spec.FactsTable
import tv.cleanyour.core.store.GatePass
import tv.cleanyour.testing.fixtures.FakeCredentialGate
import tv.cleanyour.testing.fixtures.FakeIdGen
import tv.cleanyour.testing.fixtures.MemoryBlobStore
import tv.cleanyour.testing.fixtures.MemoryJournal
import tv.cleanyour.testing.fixtures.MemorySnapshotStore

// Shared plumbing for the TV scenarios. One harness per JVM (the suite runs in one fork): the
// config from scripts/emulator-session.sh, the host key, the oracle and the wiring. A scenario
// that needs a piece another stream has not merged yet calls need(...), which reports the test as
// skipped with "PENDING: <piece>"; nothing pending ever counts as a pass.
abstract class E2eScenario {

    companion object {
        val config: E2eConfig? by lazy { E2eConfig.fromEnvironment() }
        private val cfgOrFail: E2eConfig get() = config ?: throw AssertionError(E2eConfig.NOT_IN_SESSION)

        val signer: HostKeySigner by lazy { HostKeySigner.load(cfgOrFail.adbKey) }
        val timings: Timings by lazy { Timings(cfgOrFail.outDir, cfgOrFail.api) }
        val oracle: HostAdb by lazy { HostAdb(cfgOrFail.adb, cfgOrFail.serial) }
        val blobs = MemoryBlobStore()
        val journal = MemoryJournal()
        val snapshots = MemorySnapshotStore()
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val deps: E2eDeps by lazy {
            E2eDeps(cfgOrFail, signer, JvmSocketFactory(), blobs, SystemClock, FakeIdGen(System.currentTimeMillis()),
                journal, snapshots, engineScope)
        }
        val gate = FakeCredentialGate(SystemClock)

        // The stock HOME of the API 28 Android TV image (captures/api28-atv-home.txt).
        val STOCK_HOME_PKG = "com.google.android.tvlauncher"
        const val LAUNCHER_PKG = "com.gathin.tvhome"
    }

    protected val cfg: E2eConfig get() = cfgOrFail

    protected fun <T : Any> need(value: T?, what: String): T {
        pending("PENDING: $what", value != null)
        return value!!
    }

    // CYT_E2E_REQUIRE_ALL=1 (set once every stream is wired, I5) turns PENDING into a failure, so a
    // green run cannot be a run of skips.
    private fun pending(message: String, ok: Boolean) {
        if (ok) return
        if (System.getenv("CYT_E2E_REQUIRE_ALL") == "1") throw AssertionError("$message (CYT_E2E_REQUIRE_ALL=1)")
        assumeTrue(message, false)
    }

    protected fun sessionManager(): SessionManager = need(E2eWiring.sessionManager(deps), "W2 AdbSessionManager (E2eWiring.sessionManager)")
    protected fun prober(): Prober = need(E2eWiring.prober(deps), "W4 Prober (E2eWiring.prober)")
    protected fun renderer(): OpRenderer = need(E2eWiring.renderer(deps), "W3 OpRenderer (E2eWiring.renderer)")
    protected fun planner(): Planner = need(E2eWiring.planner(deps, renderer()), "W3 Planner (E2eWiring.planner)")
    protected fun facts(): FactsTable = need(E2eWiring.factsTable(deps), "W4 FactsTable (E2eWiring.factsTable)")
    protected fun engine(): Engine = need(E2eWiring.engine(deps, prober(), renderer()), "W3 Engine (E2eWiring.engine)")

    // Runs a scenario body. A frozen stub that still says TODO("Wn") (W1's placeholder bodies)
    // makes the scenario PENDING, like a null wiring piece.
    protected fun e2e(timeoutMs: Long = 300_000, block: suspend CoroutineScope.() -> Unit) = runBlocking {
        try {
            withTimeout(timeoutMs) { block() }
        } catch (e: NotImplementedError) {
            pending("PENDING: a frozen stub is not implemented yet (${e.message})", false)
        }
    }

    protected suspend fun open(manager: SessionManager = sessionManager(), expected: TvId? = null, onAllow: suspend (String) -> Unit = {}): TvSession =
        when (val r = manager.open(cfg.endpoint, expected, onAllow)) {
            is OpenResult.Ok -> r.session
            is OpenResult.Failed -> throw AssertionError("open ${cfg.endpoint} failed: ${r.error}")
        }

    protected suspend fun baseline(session: TvSession): Snapshot = prober().snapshot(session, SnapshotReason.BASELINE)

    protected fun plan(request: PlanRequest, snapshot: Snapshot): Plan =
        when (val r = planner().plan(request, snapshot, facts(), emptyList())) {
            is PlanResult.Ok -> r.plan
            is PlanResult.Refused -> throw AssertionError("planner refused $request: ${r.reasons}")
        }

    // The launcher under test: W5's pinned artifact once merged, else the APK nightly builds from
    // the upstream submodule at the pinned commit (CYT_E2E_LAUNCHER_APK).
    protected fun launcherBlob(): BlobRef {
        E2eWiring.launcher(deps)?.let { return it.blob }
        val apk = need(cfg.launcherApk, "launcher APK (W5 LauncherArtifact, or CYT_E2E_LAUNCHER_APK)")
        return blobs.add(apk.readBytes())
    }

    protected fun pass(tvId: TvId): GatePass = gate.mint(tvId)

    // Starts a plan and waits until the run settles (Finished or Paused), answering a guarded
    // section with `onConfirm` when it asks.
    protected suspend fun runToEnd(
        engine: Engine,
        plan: Plan,
        session: TvSession,
        pass: GatePass?,
        onConfirm: suspend (RunState.AwaitingConfirm) -> Unit = {},
    ): RunState {
        val started = engine.start(plan, session, plan.hash, pass)
        assertTrue("engine refused the plan: $started", started is StartResult.Started)
        var asked = false
        return engine.runState(plan.tvId).first { s ->
            if (s is RunState.AwaitingConfirm && !asked) { asked = true; onConfirm(s) }
            s is RunState.Finished || s is RunState.Paused
        }
    }

    protected fun component(flat: String): Component {
        val (pkg, cls) = flat.split("/", limit = 2)
        return Component(PackageName(pkg), cls)
    }

    // "pkg/.Cls" and "pkg/pkg.Cls" name the same activity.
    protected fun fullClass(flat: String): String {
        val (pkg, cls) = flat.split("/", limit = 2)
        return pkg + "/" + if (cls.startsWith(".")) pkg + cls else cls
    }
}
