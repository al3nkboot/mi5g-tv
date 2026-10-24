package tv.cleanyour.e2e.scenarios

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.adb.session.AdbSessionManager
import tv.cleanyour.core.engine.DefaultEngine
import tv.cleanyour.core.engine.DocumentPlanStore
import tv.cleanyour.core.engine.FileOverlayTransport
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.RunStatus
import tv.cleanyour.core.engine.StartResult
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.planner.DefaultPlanner
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult
import tv.cleanyour.core.probe.AdbProber
import tv.cleanyour.core.session.TvSession
import tv.cleanyour.core.spec.BundledFactsTable
import tv.cleanyour.core.spec.BundledProfileSource
import tv.cleanyour.core.spec.DecodeResult
import tv.cleanyour.core.spec.LauncherConfigCodec
import tv.cleanyour.e2e.E2eScenario
import tv.cleanyour.testing.fixtures.Fixtures
import tv.cleanyour.testing.fixtures.MemoryDocumentStore
import java.io.File

// D15 on a real adbd: the Aurora theme from the pinned upstream themes goes through the config
// codec, the planner and FileOverlayTransport; the overlay on the TV then holds exactly the
// bytes the phone pushed and the launcher was restarted to read them. Launcher 2.4: the restart's
// force-stop drops the launcher's accessibility service, and the plan puts it back (tools/theme.sh;
// launcher 2.5 clears the list, switches accessibility off, writes the list back and switches it
// on, upstream #424). RF-LAUNCHER-250 F3: "Stop here" inside that restore finishes it first.
//
// E2eWiring still returns null for every piece on main, so this scenario builds the real pieces
// itself, as S09 and AppGraph do, with the harness's host key and sockets.
class S06ThemePush : E2eScenario() {
    private val renderer = ShellRenderer()
    private val prober by lazy { AdbProber(deps.clock) }
    private val manager by lazy { AdbSessionManager(deps.sockets, deps.signer, tls = null, clock = deps.clock, scope = deps.scope) }
    private val planner by lazy { DefaultPlanner(deps.clock, deps.ids, renderer, FileOverlayTransport(prober), "e2e", profiles = BundledProfileSource()) }
    private val engine by lazy {
        DefaultEngine(renderer, prober, journal, snapshots, DocumentPlanStore(MemoryDocumentStore()), deps.clock, deps.scope, BundledProfileSource())
    }
    private val service = "$LAUNCHER_PKG/$LAUNCHER_PKG.InputKeyService"
    private val out: File get() = cfg.outDir.also { it.mkdirs() }

    @After fun tidy() {
        oracle.shell("settings delete secure enabled_accessibility_services")
        oracle.shell("settings put secure accessibility_enabled 0")
        oracle.uninstall(LAUNCHER_PKG)
    }

    // The session streams a blob from its path on disk, so every blob here is a real file.
    private fun fileBlob(name: String, bytes: ByteArray): BlobRef {
        val f = File(out, name).also { it.writeBytes(bytes) }
        val sha = Sha256(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        return BlobRef(sha, bytes.size.toLong(), f.absolutePath)
    }

    // The launcher installed and started, its key service on and accessibility on: what the
    // restore after the restart puts back.
    private fun launcherWithItsServiceOn() {
        val apk = need(cfg.launcherApk, "launcher APK (CYT_E2E_LAUNCHER_APK)")
        assertTrue(oracle.install(apk).ok)
        oracle.shell("am start -W -n $LAUNCHER_PKG/.MainActivity")
        oracle.shell("settings put secure enabled_accessibility_services $service")
        oracle.shell("settings put secure accessibility_enabled 1")
    }

    private suspend fun auroraPlan(session: TvSession, tag: String): Plan {
        val aurora = Fixtures.upstreamThemes().first { it.name == "aurora" }
        val decoded = LauncherConfigCodec.decode(aurora.configJson)
        assertTrue("aurora config rejected: $decoded", decoded is DecodeResult.Ok)
        val doc = (decoded as DecodeResult.Ok).doc
        val config = fileBlob("s06-$tag-config.json", LauncherConfigCodec.encode(doc))
        val wallpaper = aurora.wallpaperJpg?.let { fileBlob("s06-$tag-wallpaper.jpg", it) }
        val base = prober.snapshot(session, SnapshotReason.BASELINE)
        snapshots.save(base)
        return when (val r = planner.plan(PlanRequest.ApplyTheme(doc, config, wallpaper), base, BundledFactsTable(), emptyList())) {
            is PlanResult.Ok -> r.plan
            is PlanResult.Refused -> throw AssertionError("planner refused the theme: ${r.reasons}")
        }
    }

    @Test fun auroraLandsOnTheOverlayAndTheLauncherRestarts() = e2e(timeoutMs = 600_000) {
        launcherWithItsServiceOn()
        val pidBefore = oracle.shell("pidof $LAUNCHER_PKG").stdout.trim()
        val session = open(manager)
        try {
            val p = auroraPlan(session, "push")
            val end = runToEnd(engine, p, session, pass(session.tvId))
            assertTrue("run ended $end", end is RunState.Finished && end.status == RunStatus.COMPLETED)

            // Every file pushed to the overlay is on the TV byte for byte.
            val pushes = p.steps.mapNotNull { it.op as? Op.PushAsset }.filter { it.dest.dir == RemoteDir.OVERLAY }
            assertTrue("no config.json push", pushes.any { it.dest == FileOverlayTransport.CONFIG })
            for (push in pushes) assertEquals(push.sha256.hex, oracle.sha256(push.dest.absolute))
            delay(2_000)
            val pidAfter = oracle.shell("pidof $LAUNCHER_PKG").stdout.trim()
            assertTrue("launcher not running after the push", pidAfter.isNotEmpty())
            assertNotEquals("launcher was not restarted", pidBefore, pidAfter)
            assertEquals(service, oracle.shell("settings get secure enabled_accessibility_services").stdout.trim())
            assertEquals("1", oracle.shell("settings get secure accessibility_enabled").stdout.trim())
            stopHereInsideTheRestore(session)
        } finally {
            session.close()
        }
    }

    // RF-LAUNCHER-250 F3 on a real adbd, in the same session: the same push again, stopped inside
    // the restore once the list is cleared; "Stop here" then runs the rest of the restore before it
    // gives the run up, and the TV ends with its list and accessibility on.
    private suspend fun stopHereInsideTheRestore(session: TvSession) {
        run {
            val p = auroraPlan(session, "stop")
            val clear = p.steps.single { it.op == Op.SettingsPut(SettingKey.ENABLED_ACCESSIBILITY, null) }.index
            val last = p.steps.last { it.op == Op.SettingsPut(SettingKey.ACCESSIBILITY_ENABLED, "1") }.index
            val started = engine.start(p, session, p.hash, pass(session.tvId))
            assertTrue("engine refused the plan: $started", started is StartResult.Started)
            // Stop is taken after the current step: asked once the clear has begun (the second
            // Settle gives a full second there), the run pauses with the rest of the restore to do.
            // The states of this run only (the flow still holds the first push's Finished at the start).
            fun ours(s: RunState) = when (s) { is RunState.Running -> s.plan.id; is RunState.Paused -> s.plan.id; is RunState.Finished -> s.plan.id; else -> null } == p.id
            engine.runState(p.tvId).first { ours(it) && ((it is RunState.Running && it.step >= clear) || it is RunState.Paused || it is RunState.Finished) }
            engine.stop(p.tvId)
            val paused = engine.runState(p.tvId).first { ours(it) && (it is RunState.Paused || it is RunState.Finished) }
            assertTrue("expected a pause inside the restore, got $paused", paused is RunState.Paused && paused.at in clear + 1..last)
            val at = (paused as RunState.Paused).at
            val listBefore = oracle.shell("settings get secure enabled_accessibility_services").stdout.trim()
            val onBefore = oracle.shell("settings get secure accessibility_enabled").stdout.trim()
            engine.abandon(p.tvId)
            val end = engine.runState(p.tvId).first { ours(it) && it is RunState.Finished }
            assertEquals(RunStatus.ABANDONED, (end as RunState.Finished).status)
            val listAfter = oracle.shell("settings get secure enabled_accessibility_services").stdout.trim()
            val onAfter = oracle.shell("settings get secure accessibility_enabled").stdout.trim()
            File(out, "s06-stop-here.txt").writeText(
                "paused at step $at (restore $clear to $last of ${p.steps.size}); before Stop here: list '$listBefore', " +
                    "accessibility_enabled $onBefore; after: list '$listAfter', accessibility_enabled $onAfter\n")
            assertEquals(service, listAfter)
            assertEquals("1", onAfter)
        }
    }
}
