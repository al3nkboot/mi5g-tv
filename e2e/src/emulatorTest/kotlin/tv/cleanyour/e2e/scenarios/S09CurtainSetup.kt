package tv.cleanyour.e2e.scenarios

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.adb.session.AdbSessionManager
import tv.cleanyour.core.engine.DefaultEngine
import tv.cleanyour.core.engine.DocumentPlanStore
import tv.cleanyour.core.engine.FileOverlayTransport
import tv.cleanyour.core.engine.JournalRecord
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.RunStatus
import tv.cleanyour.core.engine.StartResult
import tv.cleanyour.core.model.CurtainStage
import tv.cleanyour.core.model.LauncherArtifact
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.SnapshotReason
import tv.cleanyour.core.ops.FailurePolicy
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.planner.Choices
import tv.cleanyour.core.planner.DefaultPlanner
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult
import tv.cleanyour.core.probe.AdbProber
import tv.cleanyour.core.spec.BundledFactsTable
import tv.cleanyour.core.spec.BundledProfileSource
import tv.cleanyour.core.spec.DockLayers
import tv.cleanyour.core.spec.DockRanking
import tv.cleanyour.core.spec.LauncherConfigCodec
import tv.cleanyour.core.spec.LauncherConfigDoc
import tv.cleanyour.core.trust.PinnedCurtain
import tv.cleanyour.core.trust.PinnedLauncher
import tv.cleanyour.e2e.E2eScenario
import tv.cleanyour.testing.fixtures.Fixtures
import tv.cleanyour.testing.fixtures.MemoryDocumentStore
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

// RFC-CURTAIN-1 step 8: a Setup plan with the TV curtain, run by the real stack against the TV
// emulator, with the pinned launcher and the pinned curtain from the app's assets. The oracle (host
// adb) watches the TV: the curtain's package comes and goes, its activity is resumed between its
// start and the launcher's first start, every cue line exits 0, the journal holds the install and
// the removal, and a screenshot mid run is the curtain, not the stock home. Two 30 s screen
// recordings and the screenshots go to build/e2e for the lead.
//
// E2eWiring still returns null for every piece on main (no stream has wired it), so this scenario
// builds the real pieces itself, exactly as AppGraph does, with the harness's host key and sockets.
class S09CurtainSetup : E2eScenario() {
    private val curtainPkg = PinnedCurtain.PACKAGE
    private val renderer = ShellRenderer()
    private val prober by lazy { AdbProber(deps.clock) }
    private val manager by lazy { AdbSessionManager(deps.sockets, deps.signer, tls = null, clock = deps.clock, scope = deps.scope) }
    private val planner by lazy {
        DefaultPlanner(deps.clock, deps.ids, renderer, FileOverlayTransport(prober), "e2e", profiles = BundledProfileSource(),
            curtainSigner = Sha256(PinnedCurtain.SIGNER_SHA256))
    }
    private val engine by lazy {
        DefaultEngine(renderer, prober, journal, snapshots, DocumentPlanStore(MemoryDocumentStore()), deps.clock, deps.scope, BundledProfileSource())
    }
    // Two of the emulator image's own apps (its dock candidates): the switch-offs the curtain shows.
    private val switchOff = listOf("com.lego.android.tvleanback", "com.android.traceur")
    private val out: File get() = cfg.outDir.also { it.mkdirs() }

    // The session streams a blob from its path on disk, so every blob here is a real file.
    private fun fileBlob(f: File): tv.cleanyour.core.model.BlobRef {
        val bytes = f.readBytes()
        val sha = Sha256(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        return tv.cleanyour.core.model.BlobRef(sha, bytes.size.toLong(), f.absolutePath)
    }

    private fun bundled(name: String) = fileBlob(File(Fixtures.repoRoot, "android/platform/src/main/assets/bundled/$name"))

    private fun launcher() = LauncherArtifact(PackageName(PinnedLauncher.PACKAGE), PinnedLauncher.VERSION_CODE, PinnedLauncher.VERSION_NAME,
        Sha256(PinnedLauncher.SHA256), Sha256(PinnedLauncher.SIGNER_SHA256), bundled(PinnedLauncher.ASSET_NAME))

    private fun curtain() = LauncherArtifact(PackageName(PinnedCurtain.PACKAGE), PinnedCurtain.VERSION_CODE, PinnedCurtain.VERSION_NAME,
        Sha256(PinnedCurtain.SHA256), Sha256(PinnedCurtain.SIGNER_SHA256), bundled(PinnedCurtain.ASSET_NAME))

    @After fun putTheTvBack() {
        oracle.shell("settings put global animator_duration_scale 0")   // as scripts/emulator-session.sh leaves it
        oracle.uninstall(curtainPkg)
        oracle.uninstall(LAUNCHER_PKG)
        for (p in switchOff) oracle.enable(p)
        oracle.enable(STOCK_HOME_PKG)
        oracle.enable("com.google.android.tungsten.setupwraith")
        oracle.shell("cmd package set-home-activity $STOCK_HOME_PKG/.HomeScreen")
        oracle.shell("pkill -f cleanyourtv-rollback")
        oracle.pressHome()
    }

    private data class Sample(val atMs: Long, val resumed: String, val curtainInstalled: Boolean)

    // A screenshot on the TV, pulled to build/e2e; null when the pull failed.
    private fun screencap(name: String): File? {
        oracle.shell("screencap -p /sdcard/$name.png")
        val f = File(out, "$name.png")
        oracle.run("pull", "/sdcard/$name.png", f.path)
        oracle.shell("rm -f /sdcard/$name.png")
        return f.takeIf { it.isFile && it.length() > 0 }
    }

    // CYT_E2E_CURTAIN_RECORD=1: one 30 s screen recording from the curtain's start. Off by default:
    // on the API 28 emulator with software graphics, screenrecord running while the launcher starts
    // has taken surfaceflinger down (and the adb connection with it), the emulator's fault, not the
    // plan's; CYT_E2E_CURTAIN_RECORD=2 adds a second one from the INSTALL cue.
    private val recording = System.getenv("CYT_E2E_CURTAIN_RECORD") ?: "0"

    // A 30 s screen recording on the TV, in the background; pulled by `pullRecording`.
    private fun record(name: String): Process? = if (recording == "0") null else
        ProcessBuilder(cfg.adb.path, "-s", cfg.serial, "shell", "screenrecord", "--time-limit", "30", "--size", "1280x720",
            "--bit-rate", "2000000", "/sdcard/$name.mp4").redirectErrorStream(true).start()

    private fun pullRecording(p: Process?, name: String) {
        if (p == null) return
        p.waitFor(45, TimeUnit.SECONDS)
        Thread.sleep(1_500)   // the file is finalised a moment after screenrecord exits
        oracle.run("pull", "/sdcard/$name.mp4", File(out, "$name.mp4").path, timeoutS = 120)
        oracle.shell("rm -f /sdcard/$name.mp4")
    }

    // The share of sampled pixels that differ clearly between two screenshots of the same size.
    private fun differs(a: File, b: File): Double {
        val x = ImageIO.read(a); val y = ImageIO.read(b)
        if (x.width != y.width || x.height != y.height) return 1.0
        var n = 0; var d = 0
        for (j in 0 until x.height step 8) for (i in 0 until x.width step 8) {
            n++
            val p = x.getRGB(i, j); val q = y.getRGB(i, j)
            val diff = Math.abs((p shr 16 and 255) - (q shr 16 and 255)) + Math.abs((p shr 8 and 255) - (q shr 8 and 255)) + Math.abs((p and 255) - (q and 255))
            if (diff > 40) d++
        }
        return d.toDouble() / n
    }

    private suspend fun setupPlan(session: tv.cleanyour.core.session.TvSession): Plan {
        // RF-SAFETY-250: no profile matches the emulator, so its setup wizard would stay on while the
        // stock home goes off and the planner gives no switch; switched off here (and on again after).
        oracle.shell("pm disable-user --user 0 com.google.android.tungsten.setupwraith")
        val base = prober.snapshot(session, SnapshotReason.BASELINE)
        snapshots.save(base)
        val resolution = BundledProfileSource().resolve(base.facts)
        val off = switchOff.map(::PackageName).filter { it in base.packages }.toSet()
        assertTrue("the emulator image lacks $switchOff", off.isNotEmpty())
        val dock = DockRanking.pick(null, base.packages.keys, base.hdmi.isNotEmpty(), null, "en")
        val config = fileBlob(File(out, "curtain-config.json").also { it.writeBytes(LauncherConfigCodec.encode(DockLayers.applyTo(LauncherConfigDoc(), dock))) })
        val choices = Choices(kept = emptySet(), extraOff = off, dock = dock, theme = null, installLauncher = true, takeover = true,
            replaceLauncherSigner = false, keepDebuggingOn = true, curtain = true)
        val request = PlanRequest.Setup(resolution, choices, launcher(), Level.CUSTOM, config, null, curtain())
        return when (val r = planner.plan(request, base, BundledFactsTable(), emptyList())) {
            is PlanResult.Ok -> r.plan
            is PlanResult.Refused -> throw AssertionError("planner refused the Setup: ${r.reasons}")
        }
    }

    @Test fun setupWithTheCurtain() = e2e(timeoutMs = 900_000) {
        // The session turns animations off; CYT_E2E_CURTAIN_MOTION=1 shows the Lantern in full motion
        // (otherwise the curtain runs its reduced motion path, the same stages without movement).
        if (System.getenv("CYT_E2E_CURTAIN_MOTION") == "1") oracle.shell("settings put global animator_duration_scale 1")
        oracle.uninstall(curtainPkg)
        assertFalse("the curtain is on the TV before the run", oracle.isInstalled(curtainPkg))
        // The TV's log, streamed to the host as it is written, so it survives the emulator going away.
        oracle.shell("logcat -c")
        val logcat = ProcessBuilder(cfg.adb.path, "-s", cfg.serial, "logcat", "-v", "threadtime", "-b", "main,system,crash")
            .redirectOutput(File(out, "curtain-logcat.txt")).redirectErrorStream(true).start()
        oracle.pressHome()
        delay(3_000)
        val stockShot = screencap("curtain-0-stock-home")

        val session = open(manager)
        val tvId = session.tvId
        val plan = setupPlan(session)
        // D3 on a real adbd (RF-LAUNCHER-240 F14): the saved Wi-Fi read finds WifiConfigManager's list,
        // so the TV can tell; only owners and counts come back, never a network name.
        val wifi = prober.savedWifi(session)
        File(out, "curtain-saved-wifi.txt").writeText("$wifi ${wifi.verdict("com.google.android.tungsten.setupwraith")}\n")
        assertTrue("saved Wi-Fi list not found: $wifi", wifi.listed)
        assertTrue(wifi.verdict("com.google.android.tungsten.setupwraith") != tv.cleanyour.core.probe.SavedWifi.Verdict.CANNOT_TELL)
        val curtainSteps = plan.steps.filter { it.group == DefaultPlanner.GROUP_CURTAIN }
        assertTrue("no curtain in the plan", curtainSteps.isNotEmpty())
        assertTrue(curtainSteps.all { it.onFailure == FailurePolicy.CONTINUE })
        File(out, "curtain-plan.txt").writeText(plan.steps.joinToString("\n") { s ->
            "${s.index} ${s.group ?: "-"} ${s.op::class.simpleName}" + ((s.op as? Op.CurtainCue)?.let { " ${it.stage} ${it.count}/${it.total} ${it.group}" } ?: "") +
                " | ${s.narration}"
        } + "\n")

        // The oracle samples the TV while the run goes: the resumed activity and the curtain package.
        val samples = CopyOnWriteArrayList<Sample>()
        val shots = CopyOnWriteArrayList<File>()
        val metrics = CopyOnWriteArrayList<String>()
        val watcher = launch(Dispatchers.IO) {
            var firstCurtainAt = 0L
            while (isActive) {
                val line = oracle.shell("dumpsys activity activities").stdout.lineSequence()
                    .firstOrNull { it.contains("mResumedActivity") || it.contains("topResumedActivity=") }?.trim() ?: ""
                val now = System.currentTimeMillis()
                samples += Sample(now, line, oracle.isInstalled(curtainPkg))
                if ("CurtainActivity" in line) {
                    if (firstCurtainAt == 0L) firstCurtainAt = now
                    if (shots.isEmpty() && now - firstCurtainAt > 4_000) screencap("curtain-1-scan")?.let { shots += it; metrics += oracle.shell("dumpsys meminfo $curtainPkg").stdout }
                    if (shots.size == 1 && now - firstCurtainAt > 12_000) screencap("curtain-2-cleaning")?.let { shots += it; metrics += oracle.shell("dumpsys gfxinfo $curtainPkg").stdout }
                    if (shots.size == 2 && now - firstCurtainAt > 29_000) screencap("curtain-3-installing")?.let { shots += it }
                }
                delay(400)
            }
        }
        // CYT_E2E_CURTAIN_BURST=1: screenshots back to back for up to 30 s from the curtain's start, with
        // their times, for a recording put together off the TV (screenrecord takes the emulator down).
        val burstTimes = CopyOnWriteArrayList<String>()
        val burst = if (System.getenv("CYT_E2E_CURTAIN_BURST") != "1") null else launch(Dispatchers.IO) {
            val dir = File(out, "burst").also { it.mkdirs() }
            while (isActive && samples.none { "CurtainActivity" in it.resumed }) delay(100)
            val t0 = System.currentTimeMillis()
            var n = 0
            // Only while the curtain is in front, straight to the host (exec-out), about 3 a second.
            while (isActive && System.currentTimeMillis() - t0 < 30_000 && "CurtainActivity" in (samples.lastOrNull()?.resumed ?: "")) {
                val at = System.currentTimeMillis() - t0
                val f = File(dir, "%03d.png".format(n))
                ProcessBuilder(cfg.adb.path, "-s", cfg.serial, "exec-out", "screencap", "-p").redirectOutput(f).redirectErrorStream(false)
                    .start().waitFor(10, TimeUnit.SECONDS)
                burstTimes += "%03d %d".format(n, at)
                n++
                delay(150)
            }
        }
        val first = record("curtain-run-1")
        val started = engine.start(plan, session, plan.hash, pass(tvId))
        assertTrue("engine refused the plan: $started", started is StartResult.Started)
        // The second recording starts with the INSTALL cue: the band, the finale, the hand off.
        val installCue = plan.steps.first { (it.op as? Op.CurtainCue)?.stage == CurtainStage.INSTALL }.index
        var second: Process? = null
        val end = engine.runState(tvId).first { s ->
            if (second == null && recording == "2" && s is RunState.Running && s.step >= installCue) second = record("curtain-run-2")
            if (s is RunState.AwaitingConfirm) engine.confirmHome(tvId)   // the person says "Yes" on the check screen
            s is RunState.Finished || s is RunState.Paused
        }
        delay(2_000)
        watcher.cancel()
        burst?.join()
        if (burst != null) File(out, "burst/times.txt").writeText(burstTimes.joinToString("\n") + "\n")
        withContext(Dispatchers.IO) {
            pullRecording(first, "curtain-run-1")
            second?.let { pullRecording(it, "curtain-run-2") }
        }
        File(out, "curtain-samples.txt").writeText(samples.joinToString("\n") { "${it.atMs} installed=${it.curtainInstalled} ${it.resumed}" } + "\n")
        File(out, "curtain-metrics.txt").writeText(metrics.joinToString("\n\n"))
        val crash = oracle.shell("logcat -d -b crash").stdout
        File(out, "curtain-crash-log.txt").writeText(crash)

        logcat.destroy()
        assertTrue("run ended $end", end is RunState.Finished)
        assertEquals(RunStatus.COMPLETED, (end as RunState.Finished).status)
        val records = journal.read(plan.id)
        val begins = records.filterIsInstance<JournalRecord.Begin>().associateBy { it.i }
        val done = records.filterIsInstance<JournalRecord.Done>().associateBy { it.i }
        // Every curtain step ran and exited 0: the install, the start, every cue, the removal.
        for (s in curtainSteps) assertTrue("curtain step ${s.index} ${s.op::class.simpleName} not done: ${records.filter { (it as? JournalRecord.Failed)?.i == s.index }}",
            done[s.index]?.exit == 0)
        val install = curtainSteps.first { DefaultPlanner.packageOf(it.op)?.value == curtainPkg && (it.op is Op.InstallStream || it.op is Op.Install) }
        assertTrue("the journal holds the curtain's install", install.index in done)
        assertEquals(Op.Uninstall(PackageName(curtainPkg)), plan.steps.last().op)
        assertTrue("the journal holds the curtain's removal", plan.steps.last().index in done)
        // pm list packages gained the curtain during the run and lost it at the end.
        assertTrue("the curtain was never on the TV", samples.any { it.curtainInstalled })
        assertFalse("the curtain is still on the TV", oracle.isInstalled(curtainPkg))
        assertFalse("the curtain is still listed", curtainPkg in oracle.allPackages())
        // The curtain was in front between its start and the launcher's first start.
        val curtainStart = curtainSteps.first { it.op is Op.StartActivity }.index
        val launcherStart = plan.steps.first { (it.op as? Op.StartActivity)?.component?.pkg?.value == LAUNCHER_PKG }.index
        val from = begins.getValue(curtainStart).atMs
        val to = begins.getValue(launcherStart).atMs
        assertTrue("CurtainActivity was not resumed between its start and the launcher's start",
            samples.any { it.atMs in from..to && "$curtainPkg/.CurtainActivity" in it.resumed })
        timings.record("curtain.visibleSeconds", (to - from) / 1000.0, "s")
        // A screenshot mid run is the curtain, not the stock home.
        assertTrue("no screenshot of the curtain was taken", shots.isNotEmpty())
        if (stockShot != null) {
            val d = differs(stockShot, shots.first())
            timings.record("curtain.screenDiffersFromStockHome", d, "share")
            assertTrue("the curtain screenshot looks like the stock home (${"%.2f".format(d)})", d > 0.2)
        }
        assertFalse("the curtain crashed: $crash", crash.contains(curtainPkg))
        // The launcher is HOME at the end, as without the curtain.
        assertEquals("$LAUNCHER_PKG/$LAUNCHER_PKG.MainActivity", oracle.homeActivity()?.let(::fullClass))
        session.close()
    }
}
