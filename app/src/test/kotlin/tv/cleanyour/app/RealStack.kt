package tv.cleanyour.app

import kotlinx.coroutines.test.TestScope
import tv.cleanyour.adb.session.AdbSessionManager
import tv.cleanyour.adb.wire.SoftwareRsaAdbSigner
import tv.cleanyour.core.engine.DefaultEngine
import tv.cleanyour.core.engine.DocumentPlanStore
import tv.cleanyour.core.flows.CopyKey
import tv.cleanyour.core.flows.FlowCopy
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.planner.PlanKind
import tv.cleanyour.core.session.OpenResult
import tv.cleanyour.core.session.TvSession
import tv.cleanyour.core.store.Clock
import tv.cleanyour.core.store.GateIssuer
import tv.cleanyour.core.store.GateMethod
import tv.cleanyour.core.store.GatePass
import tv.cleanyour.testing.fakeadbd.FakeAdbd
import tv.cleanyour.testing.fakeadbd.FakeAdbdSocketFactory
import tv.cleanyour.testing.fakeadbd.FakeTv
import tv.cleanyour.testing.fakeadbd.Faults
import tv.cleanyour.testing.fakeadbd.Personas
import tv.cleanyour.testing.fixtures.FakeProber
import tv.cleanyour.testing.fixtures.MemoryDocumentStore
import tv.cleanyour.testing.fixtures.MemoryJournal
import tv.cleanyour.testing.fixtures.MemorySnapshotStore
import tv.cleanyour.testing.fixtures.MemoryTvRepository
import tv.cleanyour.testing.fixtures.Samples
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertIs

const val TV_HOST = "192.0.2.10"

object TestKeys {
    val signer: SoftwareRsaAdbSigner by lazy { SoftwareRsaAdbSigner(SoftwareRsaAdbSigner.generateKeyPair()) }
}

// The objects the graph wires, over the fake adbd and the test scheduler: the real
// AdbSessionManager, AdbTvSession, ShellRenderer and DefaultEngine, and the graph's
// LiveSessionEngine in front of the engine. The probe is the fixtures' FakeProber (the reference
// TV's snapshot): W4's batched probe scripts are tested against recordings, not the fake adbd.
class RealStack(val test: TestScope, tv: FakeTv = Personas.miTv()) {
    val adbd = FakeAdbd(tv, Faults(), test.backgroundScope).also { it.trust(TestKeys.signer) }
    val clock = object : Clock {
        override fun nowMs(): Long = Samples.NOW_MS + test.testScheduler.currentTime
        override fun monotonicMs(): Long = test.testScheduler.currentTime
    }
    val sessions = AdbSessionManager(FakeAdbdSocketFactory(mapOf(TV_HOST to adbd)), TestKeys.signer, null, clock, test.backgroundScope, EmptyCoroutineContext)
    val endpoint = Endpoint.PlainTcp(TV_HOST)
    val renderer = ShellRenderer()
    val prober = FakeProber()
    val journal = MemoryJournal()
    val snapshots = MemorySnapshotStore()
    val documents = MemoryDocumentStore()
    val tvs = MemoryTvRepository()
    val raw = DefaultEngine(renderer, prober, journal, snapshots, DocumentPlanStore(documents), clock, test.backgroundScope)
    val engine = LiveSessionEngine(raw, sessions, tvs)

    suspend fun open(): TvSession = assertIs<OpenResult.Ok>(sessions.open(endpoint, null) {}).session

    // The TV record the flows keep after the first connect (its address is what a re-open uses).
    suspend fun remember(tvId: TvId) = tvs.upsert(Samples.tv(id = tvId).copy(endpoints = listOf(endpoint)))

    // A one-step plan as the planner hands it over, rendered by the real renderer.
    fun plan(tvId: TvId, id: String = "01JT7PLAN00000000000000001"): Plan =
        Samples.plan(listOf(Op.Disable(PackageName("com.xiaomi.floatingframe"))), tvId = tvId, renderer = renderer,
            kind = PlanKind.SWITCH_OFF, id = tv.cleanyour.core.model.PlanId(id))

    @OptIn(GateIssuer::class)
    fun pass(tvId: TvId): GatePass = GatePass(tvId, GateMethod.NONE, clock.nowMs(), clock.nowMs() + 3_600_000)
}

object PlainCopy : FlowCopy {
    override fun text(key: CopyKey, args: Map<String, Any>): String = key.name
    override fun date(epochMs: Long): String = epochMs.toString()
    override fun dateTime(epochMs: Long): String = epochMs.toString()
}
