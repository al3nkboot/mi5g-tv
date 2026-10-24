package tv.cleanyour.core.engine

import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.ops.ShellRenderer
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.testing.fixtures.Samples
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileJournalTest {
    private val tvId = Samples.miTvId()
    private val planId = PlanId("01FAKEPLAN0000000000000007")
    private val pkg = PackageName("com.miui.tv.analytics")

    private fun records(): List<JournalRecord> = listOf(
        JournalRecord.PlanStart(planId, tvId, Sha256("1".repeat(64)), 3, Sha256("2".repeat(64)), "0.1.0", 1_760_000_000_000L),
        JournalRecord.Begin(planId, 0, Op.Disable(pkg), listOf("'pm' 'disable-user' '--user' '0' 'com.miui.tv.analytics'"), Prior(packageState = EnabledState.ENABLED), 1_760_000_000_001L),
        JournalRecord.Done(planId, 0, 0, true, 812),
        JournalRecord.Section(planId, "home_takeover", RunToken("0123456789abcdef"), SectionState.ARMED, 1_760_000_120_000L),
        JournalRecord.Begin(planId, 1, Op.SetHome(Samples.launcherComponent), listOf("x"), Prior(home = Samples.stockLauncher), 1_760_000_000_002L),
        JournalRecord.Failed(planId, 1, 1, "exit 1", "Error: component not enabled"),
        JournalRecord.Rollback(planId, 1),
        JournalRecord.Paused(planId, 1, PauseReason.STEP_FAILED),
        JournalRecord.Done(planId, 2, 0, true, 5, resumed = true),
        JournalRecord.End(planId, RunStatus.ROLLED_BACK),
    )

    private fun tempRoot(): File = Files.createTempDirectory("cyt-journal").toFile().also { it.deleteOnExit() }

    @Test fun appendsForcesAndReadsBack() = runTest(timeout = TEST_TIMEOUT) {
        val root = tempRoot()
        val j = FileJournal(root, EmptyCoroutineContext)
        for (r in records()) j.append(r)
        assertEquals(records(), j.read(planId))
        val file = File(root, "tvs/${tvId.value}/journal/${planId.value}.jsonl")
        assertTrue(file.isFile)
        // R1 R-9: a new journal's directory is forced once; on Linux a directory channel can be forced.
        if (System.getProperty("os.name").lowercase().contains("linux")) assertTrue(FileJournal.syncDir(file.parentFile))
        val lines = file.readLines()
        assertEquals(records().size, lines.size)
        for (l in lines) assertTrue(Regex(".* [0-9a-f]{8}$").matches(l), l)
        assertTrue(lines[0].startsWith("{\"plan\":\"${planId.value}\",\"t\":\"plan\""))
        // A cold start finds the journal by scanning, and the run is open until End.
        val cold = FileJournal(root, EmptyCoroutineContext)
        assertEquals(records(), cold.read(planId))
        assertEquals(emptyList<PlanId>(), cold.openRuns(tvId))
        val open = PlanId("01FAKEPLAN0000000000000008")
        cold.append(JournalRecord.PlanStart(open, tvId, Sha256("1".repeat(64)), 1, Sha256("2".repeat(64)), "0.1.0", 1L))
        cold.append(JournalRecord.Begin(open, 0, Op.PressHome, listOf("'input' 'keyevent' '3'"), null, 2L))
        assertEquals(listOf(open), FileJournal(root, EmptyCoroutineContext).openRuns(tvId))
        assertFailsWith<IllegalStateException> { cold.append(JournalRecord.Done(PlanId("01FAKEPLAN0000000000000009"), 0, 0, true, 1)) }
    }

    // Truncate the file at every byte offset of the last record: the torn line is dropped and the
    // earlier records survive; a flipped byte in an earlier record stops the read there.
    @Test fun crashMatrixTruncationAndCorruption() = runTest(timeout = TEST_TIMEOUT) {
        val root = tempRoot()
        val j = FileJournal(root, EmptyCoroutineContext)
        val all = records()
        for (r in all) j.append(r)
        val file = File(root, "tvs/${tvId.value}/journal/${planId.value}.jsonl")
        val bytes = file.readBytes()
        val lastStart = bytes.size - (JournalJson.line(all.last()) + "\n").length
        for (cut in lastStart until bytes.size) {
            val truncated = bytes.copyOf(cut)
            val got = FileJournal.parse(truncated)
            assertEquals(all.dropLast(1), got, "cut at $cut")
        }
        assertEquals(all, FileJournal.parse(bytes))
        // Through the real reader too.
        file.writeBytes(bytes.copyOf(bytes.size - 3))
        assertEquals(all.dropLast(1), FileJournal(root, EmptyCoroutineContext).read(planId))
        assertEquals(listOf(planId), FileJournal(root, EmptyCoroutineContext).openRuns(tvId), "a torn End leaves the run open")
        // A flipped byte in the middle: everything from there is untrusted.
        val third = (JournalJson.line(all[0]) + "\n" + JournalJson.line(all[1]) + "\n").length + 5
        val corrupt = bytes.copyOf().also { it[third] = (it[third].toInt() xor 1).toByte() }
        assertEquals(all.take(2), FileJournal.parse(corrupt))
        // A line without a CRC, or with a wrong one.
        assertEquals(null, JournalJson.parseLine("{\"t\":\"end\"}"))
        assertEquals(null, JournalJson.parseLine(JournalJson.line(all.last()).dropLast(1) + "0"))
        assertEquals(all.last(), JournalJson.parseLine(JournalJson.line(all.last())))
    }

    @Test fun everyRecordRoundTripsThroughJson() {
        for (r in records()) {
            val line = JournalJson.line(r)
            assertTrue(!line.contains('\n'))
            assertEquals(r, JournalJson.parseLine(line), line)
        }
    }
}

class PlanJsonTest {
    private val renderer = ShellRenderer()

    private fun assertSamePlan(a: Plan, b: Plan) {
        assertEquals(a.id, b.id); assertEquals(a.tvId, b.tvId); assertEquals(a.kind, b.kind); assertEquals(a.baseSnapshot, b.baseSnapshot)
        assertEquals(a.sections, b.sections); assertEquals(a.summary, b.summary); assertEquals(a.hash, b.hash)
        assertEquals(a.createdAtMs, b.createdAtMs); assertEquals(a.appVersion, b.appVersion)
        assertEquals(a.steps.size, b.steps.size)
        for ((x, y) in a.steps.zip(b.steps)) {
            assertEquals(x.index, y.index); assertEquals(x.op, y.op); assertEquals(x.group, y.group); assertEquals(x.narration, y.narration); assertEquals(x.onFailure, y.onFailure)
            assertEquals(x.prior?.copy(overlay = null), y.prior?.copy(overlay = null))
            assertTrue((x.prior?.overlay == null) == (y.prior?.overlay == null) && (x.prior?.overlay?.contentEquals(y.prior?.overlay ?: ByteArray(0)) ?: true))
            assertEquals(x.rendered.lines.map { it.text }, y.rendered.lines.map { it.text })
            assertEquals(x.rendered.transfer, y.rendered.transfer); assertEquals(x.rendered.privilege, y.rendered.privilege)
            assertEquals(x.rendered.verify, y.rendered.verify); assertEquals(x.rendered.timeoutMs, y.rendered.timeoutMs); assertEquals(x.rendered.stdoutCap, y.rendered.stdoutCap)
        }
    }

    @Test fun aSetupPlanWithASectionRoundTrips() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val plan = h.setupPlan().let { p ->
            // Add a prior with an overlay and a theme push so every codec branch is exercised.
            p.copy(steps = p.steps + listOf(
                tv.cleanyour.core.planner.Step(p.steps.size, Op.SettingsPut(tv.cleanyour.core.model.SettingKey.DEVICE_NAME, "Living room TV"),
                    renderer.render(Op.SettingsPut(tv.cleanyour.core.model.SettingKey.DEVICE_NAME, "Living room TV")), Prior(settingValue = null, overlay = byteArrayOf(1, 2, 3), present = true), "theme", "Naming the TV", tv.cleanyour.core.ops.FailurePolicy.CONTINUE),
            ))
        }
        val bytes = PlanJson.encode(plan)
        assertTrue(bytes.decodeToString().startsWith("{\"schema\":1,"))
        assertSamePlan(plan, PlanJson.decode(bytes))
        // The store round trip and the schema guard.
        h.plans.save(plan)
        assertSamePlan(plan, h.plans.load(h.tv.tvId, plan.id)!!)
        assertEquals(null, h.plans.load(h.tv.tvId, PlanId("01FAKEPLAN0000000000000099")))
        assertFailsWith<IllegalArgumentException> { PlanJson.decode(bytes.decodeToString().replace("\"schema\":1", "\"schema\":2").encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { PlanJson.decode(bytes.decodeToString().replace("com.miui.tv.analytics", "com.miui;rm").encodeToByteArray()) }
    }

    @Test fun everyOpRoundTrips() {
        val launcher = Samples.launcherComponent
        val token = RunToken("0123456789abcdef")
        val sha = Sha256("a".repeat(64))
        val blob = tv.cleanyour.core.model.BlobRef(sha, 10, "p")
        val path = tv.cleanyour.core.model.RemotePath(tv.cleanyour.core.model.RemoteDir.TMP, listOf("a", "b.apk"))
        val ops = listOf<Op>(
            Op.Disable(launcher.pkg), Op.Enable(launcher.pkg), Op.DefaultState(launcher.pkg),
            Op.Install(path, sha, launcher.pkg, 7, sha, setOf(tv.cleanyour.core.model.InstallFlag.REPLACE)), Op.Install(path, sha, launcher.pkg, 7, sha, emptySet()),
            Op.InstallStream(blob, sha, launcher.pkg, 7, sha), Op.Uninstall(launcher.pkg),
            Op.SettingsPut(tv.cleanyour.core.model.SettingKey.DEVICE_NAME, "Mi TV 4A"), Op.SettingsPut(tv.cleanyour.core.model.SettingKey.DEVICE_NAME, null),
            Op.SetHome(launcher), Op.VerifyHome(launcher), Op.ArmHomeWatchdog(Samples.stockLauncher, 120, token), Op.ConfirmHome(token), Op.KillWatchdog(token),
            Op.PressHome, Op.Mkdir(tv.cleanyour.core.model.RemoteDir.OVERLAY), Op.PushAsset(blob, path, sha), Op.RemoveFile(path), Op.RestartHome(launcher),
            Op.StartActivity(launcher, true), Op.StartActivity(launcher, false),
            Op.Read(tv.cleanyour.core.model.ProbeKind.PROPS), Op.Read(tv.cleanyour.core.model.ProbeKind.SETTINGS, tv.cleanyour.core.model.ReadArg.Ns(tv.cleanyour.core.model.SettingsNs.SECURE)),
            Op.Read(tv.cleanyour.core.model.ProbeKind.SHA256_OF_FILE, tv.cleanyour.core.model.ReadArg.Path(path)), Op.Read(tv.cleanyour.core.model.ProbeKind.PACKAGE_DUMP, tv.cleanyour.core.model.ReadArg.Pkg(launcher.pkg)),
            Op.Read(tv.cleanyour.core.model.ProbeKind.WATCHDOG_ALIVE, tv.cleanyour.core.model.ReadArg.Token(token)),
            Op.Settle(15), Op.Reboot, Op.DebugOff,
            Op.CurtainCue(tv.cleanyour.core.model.Component(PackageName("tv.cleanyour.curtain"), ".CueReceiver"), token, tv.cleanyour.core.model.CurtainStage.OFF, 3, 12,
                tv.cleanyour.core.model.CurtainGroup.BRAND_EXTRAS, 1, 2),
            Op.CurtainCue(tv.cleanyour.core.model.Component(PackageName("tv.cleanyour.curtain"), ".CueReceiver"), token, tv.cleanyour.core.model.CurtainStage.FINALE, 0, 0),
        )
        for (op in ops) assertEquals(op, OpJson.decode(OpJson.encode(op)), op.toString())
        assertEquals(Prior(), OpJson.decodePrior(OpJson.encodePrior(Prior())))
        val p = Prior(EnabledState.DEFAULT, "v", launcher, byteArrayOf(9), false)
        val back = OpJson.decodePrior(OpJson.encodePrior(p))
        assertEquals(p.copy(overlay = null), back.copy(overlay = null)); assertTrue(back.overlay!!.contentEquals(byteArrayOf(9)))
        assertFailsWith<IllegalArgumentException> { OpJson.decode(OpJson.encode(Op.Disable(launcher.pkg)).let { kotlinx.serialization.json.JsonObject(it + ("v" to kotlinx.serialization.json.JsonPrimitive("raw_shell"))) }) }
        assertNotNull(OpJson.encode(Op.Reboot)["v"])
    }
}
