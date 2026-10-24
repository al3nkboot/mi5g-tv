package tv.cleanyour.core.ops

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.CurtainGroup
import tv.cleanyour.core.model.CurtainStage
import tv.cleanyour.core.model.InstallFlag
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.ProbeKind
import tv.cleanyour.core.model.ReadArg
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.model.SettingsNs
import tv.cleanyour.core.model.Sha256
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// SECURITY 3.4 item 4 as property tests over seeded generators (deterministic, no library): for any
// op built from arguments that pass the grammars, the rendered line is in the safe alphabet, has
// nothing outside quotes but single spaces, tokenizes back to its argv, and contains no quote except
// the wrapping ones; for any argument that fails a grammar the renderer is never reached (the value
// class refuses) or refuses itself.
class RendererPropertyTest {
    private val renderer = ShellRenderer()
    private val rnd = Random(20261019)

    private val lower = "abcdefghijklmnopqrstuvwxyz"
    private val alnum = lower + lower.uppercase() + "0123456789_"

    private fun word(min: Int = 1, max: Int = 12): String = lower[rnd.nextInt(26)] + buildString { repeat(rnd.nextInt(min, max)) { append(alnum[rnd.nextInt(alnum.length)]) } }
    private fun pkg(): PackageName {
        while (true) {
            val p = PackageName((1..rnd.nextInt(2, 5)).joinToString(".") { word() })
            if (Denylist.staticReason(p.value) == null && p.value !in Denylist.setupWizards) return p
        }
    }
    private fun cls(): String = (if (rnd.nextBoolean()) "." else "") + word().replaceFirstChar { it.uppercase() } + (if (rnd.nextBoolean()) "\$Inner" else "")
    private fun comp() = Component(pkg(), cls())
    private fun sha() = Sha256(buildString { repeat(64) { append("0123456789abcdef"[rnd.nextInt(16)]) } })
    private fun token() = RunToken(buildString { repeat(16) { append("0123456789abcdef"[rnd.nextInt(16)]) } })
    private fun fileName() = (lower + "0123456789")[rnd.nextInt(36)] + buildString { repeat(rnd.nextInt(0, 20)) { append((alnum + "._-")[rnd.nextInt(alnum.length + 3)]) } }
    private fun path(dir: RemoteDir = RemoteDir.entries[rnd.nextInt(3)]) = RemotePath(dir, (1..rnd.nextInt(1, 4)).map { fileName() }.let { if (it.any { s -> s.contains("..") }) listOf("x") else it })
    private fun blob(size: Long = rnd.nextLong(1, 200L * 1024 * 1024)) = sha().let { BlobRef(it, size, "blobs/" + it.hex) }
    private fun componentList() = (1..rnd.nextInt(1, 4)).joinToString(":") { comp().flat }
    private fun deviceName() = buildString { repeat(rnd.nextInt(1, 33)) { append((alnum + " ._-")[rnd.nextInt(alnum.length + 4)]) } }.trim().ifEmpty { "TV" }

    private fun settingsPut(): Op.SettingsPut {
        val key = SettingKey.entries[rnd.nextInt(SettingKey.entries.size)]
        val value: String? = when (key) {
            SettingKey.SCREENSAVER_ENABLED, SettingKey.ACCESSIBILITY_ENABLED -> listOf("0", "1", null)[rnd.nextInt(3)]
            SettingKey.SCREENSAVER_COMPONENTS, SettingKey.ENABLED_ACCESSIBILITY -> if (rnd.nextInt(4) == 0) null else componentList()
            SettingKey.ADB_ENABLED, SettingKey.ADB_WIFI_ENABLED -> "0"
            SettingKey.DEVICE_NAME -> if (rnd.nextInt(4) == 0) null else deviceName()
        }
        return Op.SettingsPut(key, value)
    }

    private fun read(): Op.Read {
        val kind = ProbeKind.entries[rnd.nextInt(ProbeKind.entries.size)]
        val arg = when (kind) {
            ProbeKind.SETTINGS -> ReadArg.Ns(SettingsNs.entries[rnd.nextInt(3)])
            ProbeKind.SHA256_OF_FILE -> ReadArg.Path(path())
            ProbeKind.SHA256_OF_INSTALLED, ProbeKind.PACKAGE_DUMP -> ReadArg.Pkg(pkg())
            ProbeKind.WATCHDOG_ALIVE -> ReadArg.Token(token())
            else -> null
        }
        return Op.Read(kind, arg)
    }

    private val curtain = Component(PackageName("tv.cleanyour.curtain"), ".CueReceiver")
    private fun cue() = Op.CurtainCue(curtain, token(), CurtainStage.entries[rnd.nextInt(CurtainStage.entries.size)], rnd.nextInt(0, 1000), rnd.nextInt(0, 1000),
        CurtainGroup.entries[rnd.nextInt(CurtainGroup.entries.size)], rnd.nextInt(0, 1000), rnd.nextInt(0, 1000))

    private fun anyOp(): Op = when (rnd.nextInt(23)) {
        0 -> Op.Disable(pkg())
        1 -> Op.Enable(pkg())
        2 -> Op.DefaultState(pkg())
        3 -> sha().let { Op.Install(RemotePath(RemoteDir.TMP, listOf(it.hex.take(40) + ".apk")), it, pkg(), rnd.nextLong(0, 1_000_000), sha(), if (rnd.nextBoolean()) setOf(InstallFlag.REPLACE) else emptySet()) }
        4 -> blob().let { Op.InstallStream(it, it.sha256, pkg(), rnd.nextLong(0, 1_000_000), sha()) }
        5 -> Op.Uninstall(pkg())
        6 -> settingsPut()
        7 -> Op.SetHome(comp())
        8 -> Op.VerifyHome(comp())
        9 -> Op.ArmHomeWatchdog(comp(), rnd.nextInt(30, 301), token())
        10 -> Op.ConfirmHome(token())
        11 -> Op.KillWatchdog(token())
        12 -> Op.PressHome
        13 -> Op.Mkdir(RemoteDir.entries[rnd.nextInt(3)])
        14 -> blob().let { Op.PushAsset(it, path(), it.sha256) }
        15 -> Op.RemoveFile(path())
        16 -> Op.RestartHome(comp())
        17 -> Op.StartActivity(comp(), rnd.nextBoolean())
        18 -> read()
        19 -> Op.Settle(rnd.nextInt(1, 61))
        20 -> Op.Reboot
        21 -> cue()
        else -> Op.DebugOff
    }

    @Test fun everyRenderedLineIsSafeAndTokenizesBackToItsArgv() {
        var lines = 0
        repeat(5000) {
            val op = anyOp()
            val r = renderer.render(op)
            if (op is Op.ArmHomeWatchdog) {
                // Two lines: the stale token file removal (an ordinary quoted line), then the template.
                assertEquals(2, r.lines.size)
                ShellTokens.assertLine(r.lines[0].text)
                assertEquals(listOf("rm", "-f", "/data/local/tmp/cleanyourtv/home-ok-" + op.token.value), ShellTokens.tokenize(r.lines[0].text))
                assertTrue(ShellTokens.LINE_RE.matches(r.lines[1].text))
                assertTrue(Watchdog.matches(WatchdogVariant.NOHUP_SETSID, r.lines[1].text, op.stock, op.seconds, op.token))
                return@repeat
            }
            for (line in r.lines) {
                lines++
                val text = line.text
                assertTrue(ShellTokens.LINE_RE.matches(text), text)
                ShellTokens.assertLine(text)
                val argv = ShellTokens.tokenize(text)
                assertEquals(text, argv.joinToString(" ") { "'$it'" }, "argv quoted again is the line")
                assertTrue(argv.none { it.contains('\'') }, "no quote inside a token")
                assertTrue(argv.all { ShellTokens.isSafeToken(it) })
                // Outside the quotes: nothing but single spaces (the belt and braces of 3.4 item 3).
                val outside = text.replace(Regex("'[^']*'"), "")
                assertTrue(outside.all { it == ' ' } && outside.length == argv.size - 1, text)
            }
            if (op is Op.Settle) assertTrue(r.lines.isEmpty() && r.transfer == null)
            if (op is Op.PushAsset || op is Op.InstallStream) assertTrue(r.lines.isEmpty() && r.transfer != null)
            r.verify?.let { v -> renderer.render(v).lines.forEach { ShellTokens.assertLine(it.text) } }
        }
        assertTrue(lines > 3500)
    }

    // RFC-CURTAIN-1: every cue is one fixed argv: constant words, the token, two enum names and four
    // decimal numbers; nothing out of range or aimed at another package renders.
    @Test fun everyCueIsOneFixedArgv() {
        repeat(2000) {
            val op = cue()
            val r = renderer.render(op)
            val argv = ShellTokens.tokenize(r.lines.single().text)
            assertEquals(listOf("am", "broadcast", "-n", "tv.cleanyour.curtain/.CueReceiver", "-a", "tv.cleanyour.curtain.CUE",
                "--es", "token", op.token.value, "--es", "stage", op.stage.name, "--es", "group", op.group.name,
                "--ei", "count", "${op.count}", "--ei", "total", "${op.total}", "--ei", "gcount", "${op.groupCount}", "--ei", "gtotal", "${op.groupTotal}", "--receiver-foreground"), argv)
            assertEquals(Privilege.PROFILE_STEP, r.privilege)
            assertEquals(null, r.verify)
            assertEquals(null, r.transfer)
            assertEquals(null, renderer.inverse(op, tv.cleanyour.core.model.Prior(present = false)))
        }
        val t = RunToken("0123456789abcdef")
        val bad = listOf(
            Op.CurtainCue(curtain, t, CurtainStage.OFF, -1, 3), Op.CurtainCue(curtain, t, CurtainStage.OFF, 1, 1000),
            Op.CurtainCue(curtain, t, CurtainStage.OFF, 1, 3, CurtainGroup.ADVERTS, 1000, 1), Op.CurtainCue(curtain, t, CurtainStage.OFF, 1, 3, CurtainGroup.ADVERTS, 1, -5),
            Op.CurtainCue(Component(PackageName("com.gathin.tvhome"), ".CueReceiver"), t, CurtainStage.SCAN, 0, 0),
            Op.CurtainCue(Component(PackageName("tv.cleanyour.curtainx"), ".CueReceiver"), t, CurtainStage.SCAN, 0, 0),
        )
        for (op in bad) assertFailsWith<RenderRefused>(op.toString()) { renderer.render(op) }
    }

    // A plan with a cue hashes differently from the same plan without it (the cue lines are in the hash).
    @Test fun cueLinesAreInThePlanHash() {
        val base = listOf(Op.Disable(PackageName("com.miui.tv.analytics")), Op.Settle(3)).map { renderer.render(it) }
        val withCue = base + renderer.render(Op.CurtainCue(curtain, RunToken("0123456789abcdef"), CurtainStage.FINALE, 0, 0))
        assertTrue(renderer.planHash(base) != renderer.planHash(withCue))
        val otherStage = base + renderer.render(Op.CurtainCue(curtain, RunToken("0123456789abcdef"), CurtainStage.INSTALL, 0, 0))
        assertTrue(renderer.planHash(withCue) != renderer.planHash(otherStage))
    }

    @Test fun inverseOfAnInverseRendersToo() {
        repeat(2000) {
            val op = anyOp()
            val prior = tv.cleanyour.core.model.Prior(
                packageState = tv.cleanyour.core.model.EnabledState.entries[rnd.nextInt(4)],
                settingValue = listOf(null, "0", "1", deviceName())[rnd.nextInt(4)].takeIf { op !is Op.SettingsPut || (op.key.valueShape.matches(it ?: "0")) },
                home = comp(), present = rnd.nextBoolean(),
            )
            val inverse = renderer.inverse(op, prior) ?: return@repeat
            val r = renderer.render(inverse)
            r.lines.forEach { ShellTokens.assertLine(it.text) }
        }
    }

    @Test fun argumentsThatFailAGrammarNeverReachALine() {
        // The value classes refuse before the renderer is called.
        for (bad in listOf("nodots", "com.x;rm", "com.x y", "-com.x", "com.x'", "com.\$x", "com.x\n", "", "com.x.")) {
            assertFailsWith<IllegalArgumentException>(bad) { PackageName(bad) }
        }
        for (bad in listOf("", "a'b", "a b", "a;b", "Main\$Activity ", ".Main/Other")) {
            assertFailsWith<IllegalArgumentException>(bad) { Component(PackageName("com.a.b"), bad) }
        }
        for (bad in listOf("", "0123", "0123456789ABCDEF", "0123456789abcdefg", "'123456789abcdef")) {
            assertFailsWith<IllegalArgumentException>(bad) { RunToken(bad) }
        }
        for (bad in listOf(listOf(""), listOf("a..b"), listOf("-x"), listOf("a b"), listOf("a'b"), listOf("a", "b", "c", "d"), emptyList(), listOf("a/b"))) {
            assertFailsWith<IllegalArgumentException>(bad.toString()) { RemotePath(RemoteDir.TMP, bad) }
        }
        // And the renderer refuses what the types cannot express as a bad combination.
        val launcher = Component(PackageName("com.gathin.tvhome"), ".MainActivity")
        val refused = listOf<Op>(
            Op.SettingsPut(SettingKey.ADB_ENABLED, "1"),
            Op.SettingsPut(SettingKey.SCREENSAVER_ENABLED, "2"),
            Op.SettingsPut(SettingKey.DEVICE_NAME, "a'b"),
            Op.SettingsPut(SettingKey.DEVICE_NAME, "x".repeat(33)),
            Op.SettingsPut(SettingKey.SCREENSAVER_COMPONENTS, "notacomponent"),
            Op.ArmHomeWatchdog(launcher, 29, RunToken("0123456789abcdef")),
            Op.ArmHomeWatchdog(launcher, 301, RunToken("0123456789abcdef")),
            Op.Settle(0), Op.Settle(61),
            Op.Install(RemotePath(RemoteDir.OVERLAY, listOf("x.apk")), Sha256("a".repeat(64)), launcher.pkg, 1, Sha256("b".repeat(64)), emptySet()),
            Op.Install(RemotePath(RemoteDir.TMP, listOf("x.bin")), Sha256("a".repeat(64)), launcher.pkg, 1, Sha256("b".repeat(64)), emptySet()),
            Op.InstallStream(BlobRef(Sha256("a".repeat(64)), 10, "p"), Sha256("c".repeat(64)), launcher.pkg, 1, Sha256("b".repeat(64))),
            Op.InstallStream(BlobRef(Sha256("a".repeat(64)), 0, "p"), Sha256("a".repeat(64)), launcher.pkg, 1, Sha256("b".repeat(64))),
            Op.PushAsset(BlobRef(Sha256("a".repeat(64)), 10, "p"), RemotePath(RemoteDir.TMP, listOf("f")), Sha256("c".repeat(64))),
            Op.PushAsset(BlobRef(Sha256("a".repeat(64)), 201L * 1024 * 1024, "p"), RemotePath(RemoteDir.TMP, listOf("f")), Sha256("a".repeat(64))),
            Op.Read(ProbeKind.PROPS, ReadArg.Pkg(launcher.pkg)),
            Op.Read(ProbeKind.SETTINGS),
            Op.Read(ProbeKind.SHA256_OF_FILE, ReadArg.Pkg(launcher.pkg)),
            Op.Read(ProbeKind.PACKAGE_DUMP, ReadArg.Ns(SettingsNs.SECURE)),
            Op.Read(ProbeKind.WATCHDOG_ALIVE, ReadArg.Pkg(launcher.pkg)),
            Op.Read(ProbeKind.WATCHDOG_ALIVE),
            Op.Disable(PackageName("com.android.systemui")),
            Op.Disable(PackageName("com.android.providers.media.module")),
            Op.Disable(PackageName("com.android.wifi.resources")),
            Op.Uninstall(PackageName("com.google.android.gms")),
            Op.DefaultState(PackageName("com.android.settings")),
            // Installing over a protected package is refused too (a signed index entry naming one).
            Op.Install(RemotePath(RemoteDir.TMP, listOf("x.apk")), Sha256("a".repeat(64)), PackageName("com.android.vending"), 1, Sha256("b".repeat(64)), setOf(InstallFlag.REPLACE)),
            Op.InstallStream(BlobRef(Sha256("a".repeat(64)), 10, "p"), Sha256("a".repeat(64)), PackageName("com.google.android.gms"), 1, Sha256("b".repeat(64))),
        )
        for (op in refused) {
            val e = assertFailsWith<RenderRefused>(op.toString()) { renderer.render(op) }
            assertEquals(op, e.op)
            assertTrue(e.reason.isNotBlank())
        }
    }
}
