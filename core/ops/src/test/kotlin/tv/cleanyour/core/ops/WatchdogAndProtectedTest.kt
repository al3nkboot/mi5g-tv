package tv.cleanyour.core.ops

import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.Prior
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.ProfileMatch
import tv.cleanyour.core.model.Platform
import tv.cleanyour.core.model.Quirk
import tv.cleanyour.core.model.RunToken
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchdogTest {
    private val stock = Component(PackageName("com.google.android.tvlauncher"), ".MainActivity")
    private val token = RunToken("0123456789abcdef")

    // The two constants, pinned (PRODUCT-SPEC 3.4 step 1, D14).
    @Test fun templatesArePinned() {
        assertEquals(
            "nohup setsid sh -c 'sleep SECONDS & wait; [ -f /data/local/tmp/cleanyourtv/home-ok-TOKEN ] && exit 0; pm enable --user 0 STOCKPKG; " +
                "cmd package set-home-activity --user 0 STOCKCOMP; am start -W -a android.intent.action.MAIN -c android.intent.category.HOME' " +
                "cleanyourtv-rollback-TOKEN >/data/local/tmp/cleanyourtv/watchdog-TOKEN.log 2>&1 &",
            Watchdog.TEMPLATE_NOHUP_SETSID,
        )
        assertEquals(
            "( sh -c 'sleep SECONDS & wait; [ -f /data/local/tmp/cleanyourtv/home-ok-TOKEN ] && exit 0; pm enable --user 0 STOCKPKG; " +
                "cmd package set-home-activity --user 0 STOCKCOMP; am start -W -a android.intent.action.MAIN -c android.intent.category.HOME' " +
                "cleanyourtv-rollback-TOKEN >/data/local/tmp/cleanyourtv/watchdog-TOKEN.log 2>&1 & )",
            Watchdog.TEMPLATE_SUBSHELL,
        )
    }

    @Test fun renderFillsExactlyFourValuesAndNothingElse() {
        val a = Watchdog.render(WatchdogVariant.NOHUP_SETSID, stock, 120, token)
        assertEquals(Watchdog.TEMPLATE_NOHUP_SETSID.replace("SECONDS", "120").replace("STOCKPKG", stock.pkg.value).replace("STOCKCOMP", stock.flat).replace("TOKEN", token.value), a)
        assertEquals(2, a.count { it == '\'' })
        assertTrue(Watchdog.matches(WatchdogVariant.NOHUP_SETSID, a, stock, 120, token))
        assertTrue(!Watchdog.matches(WatchdogVariant.SUBSHELL, a, stock, 120, token))
        assertTrue(!Watchdog.matches(WatchdogVariant.NOHUP_SETSID, a.replace("sleep 120", "sleep 1"), stock, 120, token))
        // A package that contains a placeholder word is not rewritten (one-pass fill).
        val odd = Component(PackageName("com.TOKEN.STOCKCOMP"), ".SECONDS")
        val b = Watchdog.render(WatchdogVariant.SUBSHELL, odd, 30, token)
        assertTrue(b.contains("pm enable --user 0 com.TOKEN.STOCKCOMP;"), b)
        assertTrue(b.contains("set-home-activity --user 0 com.TOKEN.STOCKCOMP/.SECONDS;"), b)
        assertTrue(b.contains("sleep 30 & wait;"), b)
    }

    @Test fun boundsAreEnforced() {
        for (bad in listOf(0, 29, 301, 100_000)) assertFailsWith<IllegalArgumentException> { Watchdog.render(WatchdogVariant.NOHUP_SETSID, stock, bad, token) }
        Watchdog.render(WatchdogVariant.NOHUP_SETSID, stock, 30, token)
        Watchdog.render(WatchdogVariant.NOHUP_SETSID, stock, 300, token)
        val r = ShellRenderer(WatchdogVariant.SUBSHELL).render(Op.ArmHomeWatchdog(stock, 120, token))
        assertEquals(Watchdog.render(WatchdogVariant.SUBSHELL, stock, 120, token), r.lines.last().text)
        assertEquals(Privilege.TAKEOVER, r.privilege)
    }

    @Test fun disarmIsTheTokenFileThenPkill() {
        val r = ShellRenderer()
        assertEquals("'touch' '/data/local/tmp/cleanyourtv/home-ok-0123456789abcdef'", r.render(Op.ConfirmHome(token)).lines.single().text)
        assertEquals("'pkill' '-f' '[c]leanyourtv-rollback-0123456789abcdef'", r.render(Op.KillWatchdog(token)).lines.single().text)
        assertEquals("'pgrep' '-f' '[c]leanyourtv-rollback-0123456789abcdef'", r.render(Op.Read(tv.cleanyour.core.model.ProbeKind.WATCHDOG_ALIVE, tv.cleanyour.core.model.ReadArg.Token(token))).lines.single().text)
        // The bracket pattern matches the watchdog's own command line and nothing that carries the pattern itself.
        val pattern = Regex(Watchdog.processPattern(token))
        assertTrue(pattern.containsMatchIn(Watchdog.render(WatchdogVariant.NOHUP_SETSID, stock, 120, token)))
        assertTrue(!pattern.containsMatchIn("sh -c 'pkill' '-f' '" + Watchdog.processPattern(token) + "'"), "the adbd sh -c wrapper and pkill itself")
        assertTrue(!pattern.containsMatchIn("pgrep -f " + Watchdog.processPattern(token)))
        assertTrue(!pattern.containsMatchIn(Watchdog.render(WatchdogVariant.NOHUP_SETSID, stock, 120, RunToken("fedcba9876543210"))), "another run's watchdog")
        // Arming removes a stale token file from an earlier run before the template line.
        val arm = r.render(Op.ArmHomeWatchdog(stock, 120, token)).lines.map { it.text }
        assertEquals(2, arm.size)
        assertEquals("'rm' '-f' '/data/local/tmp/cleanyourtv/home-ok-0123456789abcdef'", arm[0])
        assertTrue(Watchdog.matches(WatchdogVariant.NOHUP_SETSID, arm[1], stock, 120, token))
        assertEquals(VerifyResult.Ok, r.check(Op.ConfirmHome(token), ShellRenderer.EMPTY_FILE_SHA256 + "  /data/local/tmp/cleanyourtv/home-ok-0123456789abcdef\n", null))
        assertTrue(r.check(Op.ConfirmHome(token), "sha256sum: /data/local/tmp/cleanyourtv/home-ok-0123456789abcdef: No such file or directory\n", null) is VerifyResult.Mismatch)
        assertEquals(VerifyResult.Ok, r.check(Op.KillWatchdog(token), "", null))
        assertTrue(r.check(Op.KillWatchdog(token), "1234\n", null) is VerifyResult.Mismatch)
        assertEquals(VerifyResult.Ok, r.check(Op.ArmHomeWatchdog(stock, 120, token), "1234\n", null))
        assertTrue(r.check(Op.ArmHomeWatchdog(stock, 120, token), "", null) is VerifyResult.Mismatch)
    }
}

class ProtectedTest {
    private fun state(persistent: Boolean = false, tvInput: Boolean = false) =
        PackageState(EnabledState.ENABLED, true, 1, null, persistent, tvInput, null, false)
    private val wizard = PackageName("com.google.android.tungsten.setupwraith")
    private fun profile(quirks: Set<Quirk>, neverDisable: Set<PackageName> = emptySet(), setupWraith: PackageName? = wizard) = Profile(
        "test", ProfileMatch(emptyList(), emptyList(), null, null, null, null), Platform.ANDROID_TV, listOf(Samples.stockLauncher),
        null, setupWraith, quirks, emptyList(), neverDisable, emptyList(), null, Sha256("0".repeat(64)),
    )

    @Test fun staticNamesAndPrefixes() {
        for (name in listOf("com.android.systemui", "com.android.tv.settings", "com.google.android.gms", "com.android.vending", "com.android.shell",
            "com.android.providers.media.module", "com.android.networkstack.tethering", "com.android.wifi.resources", "com.droidlogic.tvinput", "com.android.bluetooth")) {
            assertNotNull(Protected.isProtected(PackageName(name), null, null), name)
        }
        for (name in listOf("com.miui.tv.analytics", "com.google.android.backdrop", "com.google.android.tv.remote.service", "com.droidlogic.droidlivetv", "com.android.wifiexample")) {
            assertNull(Protected.isProtected(PackageName(name), null, null), name)
        }
    }

    @Test fun liveFactsAndProfile() {
        val pkg = PackageName("com.vendor.service")
        assertNull(Protected.isProtected(pkg, state(), null))
        assertEquals(Denylist.REASON_PERSISTENT, Protected.isProtected(pkg, state(persistent = true), null))
        assertEquals(Denylist.REASON_TV_INPUT, Protected.isProtected(pkg, state(tvInput = true), null))
        assertEquals(Denylist.REASON_PROFILE, Protected.isProtected(pkg, state(), profile(emptySet(), neverDisable = setOf(pkg))))
    }

    @Test fun theSetupWizardNeedsTheQuirk() {
        assertEquals(Denylist.REASON_SETUP_WIZARD, Protected.isProtected(wizard, state(), null))
        assertEquals(Denylist.REASON_SETUP_WIZARD, Protected.isProtected(wizard, state(), profile(emptySet())))
        assertEquals(Denylist.REASON_SETUP_WIZARD, Protected.isProtected(wizard, state(), profile(setOf(Quirk.HOME_TAKEOVER_SETUPWRAITH), setupWraith = null)))
        assertNull(Protected.isProtected(wizard, state(), profile(setOf(Quirk.HOME_TAKEOVER_SETUPWRAITH))))
    }

    @Test fun keyboardAndScreensaverComeFromTheSnapshot() {
        val snap = Samples.miTvSnapshot().copy(
            ime = Component(PackageName("com.google.android.inputmethod.latin"), "com.android.inputmethod.latin.LatinIME"),
            screensaver = listOf(Component(PackageName("com.google.android.backdrop"), ".Backdrop")),
        )
        assertEquals(Denylist.REASON_KEYBOARD, Denylist.reason(PackageName("com.google.android.inputmethod.latin"), snap, null))
        assertEquals(Denylist.REASON_SCREENSAVER, Denylist.reason(PackageName("com.google.android.backdrop"), snap, null))
        assertNull(Denylist.reason(PackageName("com.miui.tv.analytics"), snap, null))
    }

    // RFC-W1-2 ruling: `android` is on the hard-coded protected list; the renderer refuses it as a
    // target of any op.
    @Test fun theFrameworkPackageIsRefusedByTheRenderer() {
        assertNotNull(Denylist.staticReason("android"))
        assertTrue("android" in Denylist.exact)
        val android = PackageName("android")
        val r = ShellRenderer()
        for (op in listOf(Op.Disable(android), Op.Enable(android), Op.DefaultState(android), Op.Uninstall(android),
            Op.SetHome(Component(android, "com.android.internal.app.ResolverActivity")))) {
            assertFailsWith<RenderRefused>(op.toString()) { r.render(op) }
        }
        assertNotNull(Protected.isProtected(android, null, null))
    }

    @Test fun inverseNeverTurnsDebuggingOn() {
        val r = ShellRenderer()
        assertNull(r.inverse(Op.SettingsPut(tv.cleanyour.core.model.SettingKey.ADB_ENABLED, "0"), Prior(settingValue = "1")))
        assertNull(r.inverse(Op.DebugOff, Prior(settingValue = "1")))
    }

    @Test fun arabicStreamingAppsAreProtectedAndCannotBeDisabledOrUninstalled() {
        val arabicPkgs = listOf(
            "net.mbc.shahid", "net.mbc.shahid.tv", "com.mbc.shahid",
            "tv.tod.android", "com.todtv.tod",
            "com.intigral.jawwytv", "com.stctv.app", "tv.stc.android",
            "com.parsifal.starz", "com.starzplay.arabia",
            "com.osn.go", "com.anghami.osnplus",
            "com.watchit.android", "com.watchit.tv",
            "com.yango.play", "sa.sba.aloula", "ae.dmi.awaan",
            "com.anghami", "com.bein.connect",
        )
        val r = ShellRenderer()
        for (name in arabicPkgs) {
            val pkg = PackageName(name)
            assertEquals(Denylist.REASON_ARABIC_STREAMING, Protected.isProtected(pkg, null, null), name)
            assertEquals(Denylist.REASON_ARABIC_STREAMING, Denylist.reason(pkg, Samples.miTvSnapshot(), null), name)
            assertFailsWith<RenderRefused>(name) { r.render(Op.Disable(pkg)) }
            assertFailsWith<RenderRefused>(name) { r.render(Op.Uninstall(pkg)) }
        }
    }

    @Test fun remoteKeyEventAndTextEscapingLinesAreSafeAndTokenizable() {
        val expectedCodes = mapOf(
            tv.cleanyour.core.model.TvKeyEvent.DPAD_UP to "19",
            tv.cleanyour.core.model.TvKeyEvent.DPAD_DOWN to "20",
            tv.cleanyour.core.model.TvKeyEvent.DPAD_LEFT to "21",
            tv.cleanyour.core.model.TvKeyEvent.DPAD_RIGHT to "22",
            tv.cleanyour.core.model.TvKeyEvent.DPAD_CENTER to "23",
            tv.cleanyour.core.model.TvKeyEvent.BACK to "4",
            tv.cleanyour.core.model.TvKeyEvent.HOME to "3",
            tv.cleanyour.core.model.TvKeyEvent.MENU to "82",
            tv.cleanyour.core.model.TvKeyEvent.PLAY_PAUSE to "85",
            tv.cleanyour.core.model.TvKeyEvent.VOLUME_UP to "24",
            tv.cleanyour.core.model.TvKeyEvent.VOLUME_DOWN to "25",
            tv.cleanyour.core.model.TvKeyEvent.VOLUME_MUTE to "164",
        )
        for ((key, code) in expectedCodes) {
            val line = ShellRenderer.keyEventLine(key)
            ShellTokens.assertLine(line.text)
            assertEquals(listOf("input", "keyevent", code), ShellTokens.tokenize(line.text))
        }
        val settingsLine = ShellRenderer.openSettingsLine()
        ShellTokens.assertLine(settingsLine.text)
        assertEquals(listOf("am", "start", "-a", "android.settings.SETTINGS"), ShellTokens.tokenize(settingsLine.text))

        val textLine = ShellRenderer.inputTextLine("Shahid VIP 2026")
        ShellTokens.assertLine(textLine.text)
        assertEquals("'input' 'text' 'Shahid%sVIP%s2026'", textLine.text)
        assertEquals(listOf("input", "text", "Shahid%sVIP%s2026"), ShellTokens.tokenize(textLine.text))

        // Quotes and shell metacharacters stay inert inside single quotes or are stripped
        val quoted = ShellRenderer.inputTextLine(" user's; rm -rf / ")
        ShellTokens.assertLine(quoted.text)
        assertEquals(listOf("input", "text", "%susers;%srm%s-rf%s/%s"), ShellTokens.tokenize(quoted.text))

        assertFailsWith<IllegalArgumentException> { ShellRenderer.inputTextLine("   \n\r") }
        assertFailsWith<IllegalArgumentException> { ShellRenderer.inputTextLine("'''") }

        val apkPath = tv.cleanyour.core.model.RemotePath(tv.cleanyour.core.model.RemoteDir.TMP, listOf("sideload.apk"))
        assertEquals(listOf("mkdir", "-p", "/data/local/tmp/cleanyourtv"), ShellTokens.tokenize(ShellRenderer.mkdirTmpLine().text))
        assertEquals(listOf("pm", "install", "-r", "--user", "0", "/data/local/tmp/cleanyourtv/sideload.apk"), ShellTokens.tokenize(ShellRenderer.pmInstallLine(apkPath).text))
        assertEquals(listOf("rm", "-f", "/data/local/tmp/cleanyourtv/sideload.apk"), ShellTokens.tokenize(ShellRenderer.removeTmpLine(apkPath).text))
    }

    @Test fun speedBoostQuickCastAndAppManagerLinesAreSafe() {
        assertEquals(listOf("cat", "/proc/meminfo"), ShellTokens.tokenize(ShellRenderer.memInfoLine().text))
        assertEquals(listOf("df", "-k", "/data"), ShellTokens.tokenize(ShellRenderer.dfDataLine().text))
        assertEquals(listOf("pm", "trim-caches", "999999999G"), ShellTokens.tokenize(ShellRenderer.trimCachesLine().text))
        assertEquals(listOf("pm", "list", "packages", "-3"), ShellTokens.tokenize(ShellRenderer.listThirdPartyPackagesLine().text))
        assertEquals(listOf("settings", "get", "global", "window_animation_scale"), ShellTokens.tokenize(ShellRenderer.getWindowAnimationScaleLine().text))

        val animLines = ShellRenderer.setAnimationScaleLines(tv.cleanyour.core.model.TvAnimationScale.FAST_0_5X)
        assertEquals(3, animLines.size)
        assertEquals(listOf("settings", "put", "global", "window_animation_scale", "0.5"), ShellTokens.tokenize(animLines[0].text))
        assertEquals(listOf("settings", "put", "global", "transition_animation_scale", "0.5"), ShellTokens.tokenize(animLines[1].text))
        assertEquals(listOf("settings", "put", "global", "animator_duration_scale", "0.5"), ShellTokens.tokenize(animLines[2].text))

        val kodi = PackageName("org.xbmc.kodi")
        assertEquals(listOf("am", "force-stop", "org.xbmc.kodi"), ShellTokens.tokenize(ShellRenderer.forceStopLine(kodi).text))
        for (prot in listOf("android", "com.android.systemui", "com.android.adbd", "com.gathin.tvhome", "com.midroid.mi5gtv.files")) {
            assertFailsWith<IllegalArgumentException>(prot) { ShellRenderer.forceStopLine(PackageName(prot)) }
        }

        val urlLine = ShellRenderer.openUrlOnTvLine("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(
            listOf("am", "start", "-a", "android.intent.action.VIEW", "-d", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
            ShellTokens.tokenize(urlLine.text),
        )
        for (badUrl in listOf("", "ftp://example.com/a", "javascript:alert(1)", "https://example.com/a b", "https://example.com/a'b")) {
            assertFailsWith<IllegalArgumentException>(badUrl) { ShellRenderer.openUrlOnTvLine(badUrl) }
        }

        val staging = tv.cleanyour.core.model.RemotePath(tv.cleanyour.core.model.RemoteDir.RESTORE, listOf("mi5g_cast.jpg"))
        assertEquals(listOf("cp", "/sdcard/cleanyourtv/mi5g_cast.jpg", "/sdcard/Download/mi5g_cast.jpg"), ShellTokens.tokenize(ShellRenderer.copyCastMediaToDownloadLine(staging, "jpg").text))
        assertEquals(
            listOf("am", "start", "-a", "android.intent.action.VIEW", "-d", "file:///sdcard/Download/mi5g_cast.jpg", "-t", "image/jpeg"),
            ShellTokens.tokenize(ShellRenderer.viewCastMediaLine("jpg", "image/jpeg").text),
        )

        assertEquals(listOf("monkey", "-p", "org.xbmc.kodi", "-c", "android.intent.category.LAUNCHER", "1"), ShellTokens.tokenize(ShellRenderer.launchPackageLine(kodi).text))
        assertEquals(listOf("pm", "clear", "--user", "0", "org.xbmc.kodi"), ShellTokens.tokenize(ShellRenderer.clearPackageDataLine(kodi).text))
        assertEquals(listOf("pm", "uninstall", "org.xbmc.kodi"), ShellTokens.tokenize(ShellRenderer.uninstallUserPackageLine(kodi).text))
        assertEquals(listOf("pm", "path", "org.xbmc.kodi"), ShellTokens.tokenize(ShellRenderer.pmPathLine(kodi).text))

        val pmOut = "package:/data/app/~~AbCdEf==/org.xbmc.kodi-1234==/split_config.ar.apk\npackage:/data/app/~~AbCdEf==/org.xbmc.kodi-1234==/base.apk\n"
        val parsedPath = ShellRenderer.parsePmPathOutput(pmOut)
        assertEquals("/data/app/~~AbCdEf==/org.xbmc.kodi-1234==/base.apk", parsedPath)
        val tmpApk = tv.cleanyour.core.model.RemotePath(tv.cleanyour.core.model.RemoteDir.TMP, listOf("pulled-app.apk"))
        assertEquals(
            listOf("cp", "/data/app/~~AbCdEf==/org.xbmc.kodi-1234==/base.apk", "/data/local/tmp/cleanyourtv/pulled-app.apk"),
            ShellTokens.tokenize(ShellRenderer.copyRemoteApkToTmpLine(parsedPath!!, tmpApk).text),
        )
        assertEquals(
            listOf("cat", "/data/app/~~AbCdEf==/org.xbmc.kodi-1234==/base.apk"),
            ShellTokens.tokenize(ShellRenderer.catRemoteApkLine(parsedPath).text),
        )
    }
}
