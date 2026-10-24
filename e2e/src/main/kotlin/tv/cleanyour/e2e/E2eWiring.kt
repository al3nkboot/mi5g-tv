package tv.cleanyour.e2e

import kotlinx.coroutines.CoroutineScope
import tv.cleanyour.adb.wire.AdbSigner
import tv.cleanyour.adb.wire.SocketFactory
import tv.cleanyour.core.engine.Engine
import tv.cleanyour.core.engine.Journal
import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.ops.OpRenderer
import tv.cleanyour.core.planner.Planner
import tv.cleanyour.core.probe.Prober
import tv.cleanyour.core.session.SessionManager
import tv.cleanyour.core.spec.FactsTable
import tv.cleanyour.core.spec.ProfileSource
import tv.cleanyour.core.store.BlobStore
import tv.cleanyour.core.store.Clock
import tv.cleanyour.core.store.IdGen
import tv.cleanyour.core.store.SnapshotStore

// What the scenarios hand to the implementations. Everything here is a frozen section 5 type or
// a harness class; no implementation detail of another stream leaks into the scenarios.
class E2eDeps(
    val config: E2eConfig,
    val signer: AdbSigner,
    val sockets: SocketFactory,
    val blobs: BlobStore,
    val clock: Clock,
    val ids: IdGen,
    val journal: Journal,
    val snapshots: SnapshotStore,
    val scope: CoroutineScope,   // the engine's own scope: a run outlives the caller (5.9)
)

// The single place the harness meets the implementations. The scenarios are written against the
// frozen interfaces only; each function below returns null until its stream has merged, and a
// scenario that needs a null piece is reported as skipped with "PENDING: <piece>", not as a pass.
//
// At integration (I1 to I3) the owner of each piece replaces its `null` with the real
// constructor call. Nothing else in :e2e changes.
object E2eWiring {
    // W2: AdbSessionManager over :adb:wire, with d.signer, d.sockets, d.clock and d.blobs (push and
    // install read BlobRef bytes from the store).
    fun sessionManager(d: E2eDeps): SessionManager? = null

    // W4: the batched-script prober (D40).
    fun prober(d: E2eDeps): Prober? = null

    // W4: the bundled profiles and the facts table, built from the pinned upstream examples.
    fun profiles(d: E2eDeps): ProfileSource? = null
    fun factsTable(d: E2eDeps): FactsTable? = null

    // W3: renderer, planner (Clock, IdGen, OpRenderer) and engine (journal, snapshots, prober,
    // renderer, clock, scope; FileOverlayTransport for config pushes).
    fun renderer(d: E2eDeps): OpRenderer? = null
    fun planner(d: E2eDeps, renderer: OpRenderer): Planner? = null
    fun engine(d: E2eDeps, prober: Prober, renderer: OpRenderer): Engine? = null

    // W5: the bundled, pinned launcher (LauncherPin) as a blob in d.blobs.
    fun launcher(d: E2eDeps): LauncherArtifact? = null

    val pendingPieces: List<String> = listOf(
        "W2 AdbSessionManager", "W4 Prober", "W4 ProfileSource and FactsTable", "W3 OpRenderer, Planner and Engine",
        "W5 bundled LauncherArtifact",
    )
}
