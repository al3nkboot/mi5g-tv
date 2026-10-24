package tv.cleanyour.core.engine

import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.ops.ConfigTransportKind
import tv.cleanyour.testing.fixtures.Fixtures
import tv.cleanyour.testing.fixtures.Samples
import tv.cleanyour.testing.fixtures.sha256Of
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeRecordsTest {
    private val planId = PlanId("01FAKEPLAN0000000000000001")
    private val a = PackageName("com.a.b"); private val b = PackageName("com.c.d")
    private fun begin(i: Int, op: Op, at: Long = i.toLong()) = JournalRecord.Begin(planId, i, op, emptyList(), Prior(packageState = EnabledState.ENABLED), at)
    private fun done(i: Int) = JournalRecord.Done(planId, i, 0, true, 1)

    @Test fun doneWritesAreChangesUntilARollbackUndoesThem() {
        val records = listOf(
            begin(0, Op.Disable(a)), done(0),
            begin(1, Op.Read(tv.cleanyour.core.model.ProbeKind.PROPS)), done(1),   // a read is not a change
            begin(2, Op.Disable(b)), JournalRecord.Failed(planId, 2, 1, "exit 1", ""),   // failed: no change
            begin(3, Op.SetHome(Samples.launcherComponent)), done(3),
            begin(4, Op.Mkdir(RemoteDir.TMP)),   // in doubt: not a change
            JournalRecord.Rollback(planId, 3),
            begin(3, Op.SetHome(Samples.stockLauncher)), done(3),   // undoes step 3
            begin(-1, Op.PressHome), done(-1),
        )
        val changes = ChangeRecords.fromJournal(records)
        assertEquals(listOf(Op.Disable(a)), changes.map { it.op })
        assertEquals(0, changes.single().step); assertEquals(0L, changes.single().atMs); assertEquals(planId, changes.single().planId)
        assertEquals(listOf(Op.Disable(a), Op.SetHome(Samples.launcherComponent)), ChangeRecords.fromJournal(records.take(8)).map { it.op })
        val other = PlanId("01FAKEPLAN0000000000000000")
        val earlier = listOf(JournalRecord.Begin(other, 0, Op.Enable(b), emptyList(), null, -5L), JournalRecord.Done(other, 0, 0, true, 1))
        assertEquals(listOf(Op.Enable(b), Op.Disable(a)), ChangeRecords.fromJournals(listOf(records, earlier)).map { it.op }, "sorted by time across journals")
        assertTrue(ChangeRecords.isChange(Op.PushAsset(BlobRef(Sha256("a".repeat(64)), 1, "p"), FileOverlayTransport.CONFIG, Sha256("a".repeat(64)))))
        assertTrue(!ChangeRecords.isChange(Op.ArmHomeWatchdog(Samples.stockLauncher, 120, tv.cleanyour.core.model.RunToken("0123456789abcdef"))))
    }
}

class FileOverlayTransportTest {
    @Test fun stepsMatchThemeSh() = runTest(timeout = TEST_TIMEOUT) {
        val h = Harness(this)
        val t = FileOverlayTransport(h.prober)
        assertEquals(ConfigTransportKind.FILE_OVERLAY, t.kind)
        val cfg = BlobRef(Sha256("c".repeat(64)), 100, "cfg"); val wp = BlobRef(Sha256("d".repeat(64)), 200_000, "wp")
        assertEquals(listOf(
            Op.StartActivity(h.launcher, true), Op.Mkdir(RemoteDir.OVERLAY),
            Op.PushAsset(cfg, FileOverlayTransport.CONFIG, cfg.sha256), Op.PushAsset(wp, FileOverlayTransport.WALLPAPER, wp.sha256), Op.RestartHome(h.launcher),
        ), t.writePlanSteps(cfg, wp))
        assertEquals(Op.RemoveFile(FileOverlayTransport.WALLPAPER), t.writePlanSteps(cfg, null)[3])
        assertEquals(listOf(Op.RemoveFile(FileOverlayTransport.CONFIG), Op.RemoveFile(FileOverlayTransport.WALLPAPER), Op.RestartHome(h.launcher)), t.resetPlanSteps())
        assertFailsWith<IllegalArgumentException> { t.writePlanSteps(cfg.copy(size = 64L * 1024 + 1), null) }
        assertFailsWith<IllegalArgumentException> { t.writePlanSteps(cfg, wp.copy(size = 300L * 1024 + 1)) }
        assertEquals("/sdcard/Android/data/com.gathin.tvhome/files/config.json", FileOverlayTransport.CONFIG.absolute)
        assertNull(t.readDigest(h.tv))
        val bytes = "{}".encodeToByteArray()
        h.tv.files[FileOverlayTransport.CONFIG.absolute] = bytes
        assertEquals(sha256Of(bytes), t.readDigest(h.tv))
    }
}

// SECURITY 3.4 item 5: the single-call-site lint. No main source outside the allowlist may call
// TvSession.exec or build a ShellLine (the @RawShell opt-in). Runs over the whole repo so it also
// guards the other streams' modules.
class SingleCallSiteLintTest {
    private val root = Fixtures.repoRoot
    private val mainSources: List<File> by lazy {
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.path.contains("/src/main/") && !it.path.contains("/build/") && !it.path.contains("/third_party/") }
            .toList()
    }

    private fun rel(f: File) = f.relativeTo(root).path

    @Test fun execIsCalledFromOnePlaceInTheEngineAndOnlyByTheProbeAndTheSessionElsewhere() {
        val allowed = listOf(
            "core/engine/src/main/kotlin/tv/cleanyour/core/engine/DefaultEngine.kt",   // the executor's one send()
            "core/probe/src/main/",                                                    // W4's fixed probe scripts
            "adb/session/src/main/",                                                   // W2 implements TvSession
        )
        // TvSession wrappers that only forward the line they were handed: one call each, and it must
        // pass the received `line` and `spec` straight through, so they cannot originate a command.
        val forwarders = setOf(
            "app/src/main/kotlin/tv/cleanyour/app/LiveSessionEngine.kt",   // T7: CurrentSession follows the live session
            "e2e/src/main/kotlin/tv/cleanyour/e2e/Harness.kt",             // W9: the e2e recording session (test harness)
        )
        val offenders = mutableListOf<String>()
        for (f in mainSources) {
            val text = f.readText()
            val calls = Regex("\\.exec\\(").findAll(text).count()
            if (calls == 0) continue
            val path = rel(f)
            if (path in forwarders) {
                val passThrough = Regex("\\.exec\\(line, spec\\)").findAll(text).count()
                if (calls != 1 || passThrough != 1) offenders += "$path ($calls, not a pure pass-through)"
                continue
            }
            if (allowed.none { path.startsWith(it) }) offenders += "$path ($calls)"
            if (path == allowed[0]) assertEquals(1, calls, "the engine has exactly one exec call site")
        }
        assertEquals(emptyList(), offenders, "exec called outside the allowlist")
        assertTrue(mainSources.any { rel(it) == allowed[0] })
    }

    @Test fun shellLinesAreBuiltOnlyByTheRendererTheProbeAndThePlanCodec() {
        val allowed = setOf(
            "core/model/src/main/kotlin/tv/cleanyour/core/model/Model.kt",            // the declaration
            "core/ops/src/main/kotlin/tv/cleanyour/core/ops/ShellRenderer.kt",       // the renderer
            "core/engine/src/main/kotlin/tv/cleanyour/core/engine/PlanStore.kt",    // the plan codec (R0: the third opt-in)
            "testing/fixtures/src/main/kotlin/tv/cleanyour/testing/fixtures/OpsFakes.kt",   // the fake renderer (test scope only, rule 5)
        )
        val prefixes = listOf("core/probe/src/main/")
        val offenders = mutableListOf<String>()
        val session = mutableListOf<Pair<String, String>>()
        for (f in mainSources) {
            val text = f.readText()
            if (!text.contains("RawShell")) continue
            val path = rel(f)
            if (path.startsWith(SESSION_MAIN)) { session += path to text; continue }
            if (path !in allowed && prefixes.none { path.startsWith(it) }) offenders += path
        }
        assertEquals(emptyList(), offenders, "RawShell opted in outside the allowlist")
        // RFC-W2-1: `:adb:session` has exactly one opt-in, on `internal object SessionLines`, for its
        // five constant lines (getprop, boot_id, pm path android, reboot, the push verify sha256sum).
        assertEquals(emptyList(), sessionOffenders(session), "the :adb:session opt-in is SessionLines only")
        val renderer = File(root, "core/ops/src/main/kotlin/tv/cleanyour/core/ops/ShellRenderer.kt").readText()
        assertTrue(!renderer.contains("String.format") && !renderer.contains("\"sh -c") && !renderer.contains("\$(") && !renderer.contains("exec("), "no templating in the renderer")
    }

    @Test fun theSessionLinesRuleAcceptsOneOptInAndRefusesAnyOther() {
        val good = "@OptIn(RawShell::class)\ninternal object SessionLines {\n    val GETPROP = ShellLine(\"getprop\")\n}\n"
        assertEquals(emptyList(), sessionOffenders(listOf("adb/session/src/main/S.kt" to good)))
        val second = good + "@OptIn(RawShell::class)\nfun other() = ShellLine(\"id\")\n"
        assertTrue(sessionOffenders(listOf("adb/session/src/main/S.kt" to second)).isNotEmpty())
        val elsewhere = "@OptIn(RawShell::class)\nclass Shell { fun x() = ShellLine(\"id\") }\n"
        assertTrue(sessionOffenders(listOf("adb/session/src/main/T.kt" to elsewhere)).isNotEmpty())
        val fileWide = "@file:OptIn(RawShell::class)\npackage x\n"
        assertTrue(sessionOffenders(listOf("adb/session/src/main/U.kt" to fileWide)).isNotEmpty())
        val tooMany = "@OptIn(RawShell::class)\ninternal object SessionLines {\n" + (1..6).joinToString("") { "    val L$it = ShellLine(\"x\")\n" } + "}\n"
        assertTrue(sessionOffenders(listOf("adb/session/src/main/S.kt" to tooMany)).isNotEmpty())
    }

    // Every file of :adb:session main that mentions RawShell: the module holds exactly one opt-in in
    // all, written `@OptIn(RawShell::class)` right above `internal object SessionLines`, never a
    // file-wide opt-in or a propagating `@RawShell`, and that file builds at most five lines.
    private fun sessionOffenders(files: List<Pair<String, String>>): List<String> {
        val out = mutableListOf<String>()
        var optIns = 0
        for ((path, source) in files) {
            val text = source.replace(Regex("/\\*[\\s\\S]*?\\*/"), "").replace(Regex("//[^\\n]*"), "")   // comments do not count
            val all = Regex("@(file:)?OptIn\\([^)]*RawShell").findAll(text).count()
            val onObject = Regex("@OptIn\\(RawShell::class\\)\\s*\\n\\s*internal object SessionLines\\b").findAll(text).count()
            optIns += all
            if (all != onObject) out += "$path: an opt-in outside SessionLines"
            if (Regex("(^|\\s)@RawShell\\b").containsMatchIn(text)) out += "$path: a propagating @RawShell"
            if (onObject > 0 && Regex("\\bShellLine\\(").findAll(text).count() > 5) out += "$path: more than five session lines"
        }
        if (optIns > 1) out += "$SESSION_MAIN: $optIns opt-ins, one allowed"
        return out
    }

    private companion object {
        const val SESSION_MAIN = "adb/session/src/main/"
    }
}
