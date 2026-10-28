package tv.cleanyour.core.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TellyFeaturesTest {
    @Test fun bundlesAndInstallKindAreNamedBeforeAnythingIsSent() {
        assertEquals(TellyFeatures.BundleKind.XAPK, TellyFeatures.bundleKind("app.xapk"))
        assertEquals(TellyFeatures.BundleKind.APKS, TellyFeatures.bundleKind("App.apks"))
        assertTrue(TellyFeatures.isBundle("show.apkm"))
        assertEquals(TellyFeatures.InstallKind.NEW, TellyFeatures.installKind(3, null))
        assertEquals(TellyFeatures.InstallKind.UPDATE, TellyFeatures.installKind(4, 3))
        assertEquals(TellyFeatures.InstallKind.OLDER, TellyFeatures.installKind(2, 5))
        val msg = TellyFeatures.installMessage(TellyFeatures.InstallKind.OLDER, "com.example.app", arabic = true)
        assertTrue(msg.contains("أقدم"))
        assertTrue(msg.contains("أكدت"))
    }

    @Test fun hiddenFactoryCopyDoesNotCountAsTheInstalledVersion() {
        val dump = """
            Package [com.example.app] (1):
              versionCode=9
            Hidden system packages:
              Package [com.example.app] (1):
                versionCode=1
        """.trimIndent()
        assertEquals(9L, TellyFeatures.parseInstalledVersion(dump, "com.example.app"))
        assertNull(TellyFeatures.parseInstalledVersion(dump, "com.other.app"))
    }

    @Test fun pictureOffStopsAtAndroid14AndDockStopsAtFour() {
        assertTrue(TellyFeatures.screenOffAllowed(33))
        assertFalse(TellyFeatures.screenOffAllowed(34))
        assertTrue(TellyFeatures.screenOffRefusal(34, arabic = true)!!.contains("لم يُرسل أمر"))
        var dock = emptyList<String>()
        repeat(4) { dock = TellyFeatures.addDock(dock, "com.example.app$it")!! }
        assertNull(TellyFeatures.addDock(dock, "com.example.extra"))
        assertNull(TellyFeatures.addDock(dock, "../pm"))
    }

    @Test fun fileNamesCannotLeaveTheSharedFolders() {
        assertEquals("clip.mp4", TellyFeatures.safeFileName("clip.mp4"))
        assertFalse(TellyFeatures.safeFileName("../../etc/passwd").contains("/"))
        assertEquals("file", TellyFeatures.safeFileName(".."))
        assertEquals(TellyFeatures.DOWNLOAD, TellyFeatures.folderPath("download"))
        assertNull(TellyFeatures.folderPath("/data"))
        val names = TellyFeatures.parseNames("movie.mp4\n../secret\n.hidden\nclip's.mp4\n")
        assertEquals(listOf("movie.mp4"), names)
    }

    @Test fun restoreTurnsOnOnlyWhatThisAppDisabled() {
        val log = TellyFeatures.ChangeLog()
        log.setBaseline(setOf("com.factory.already"))
        log.record(1, TellyFeatures.ChangeAction.DISABLE, "com.factory.already", "was off")
        log.record(2, TellyFeatures.ChangeAction.DISABLE, "com.vendor.bloat", "Bloat")
        log.record(3, TellyFeatures.ChangeAction.DISABLE, "com.vendor.other", "Other")
        val undone = log.openDisable("com.vendor.other")!!
        log.markUndone(undone.id, 4)
        log.record(5, TellyFeatures.ChangeAction.ENABLE, "com.vendor.other", "Other")
        assertEquals(listOf("com.vendor.bloat"), log.restorable())
        val again = TellyFeatures.ChangeLog(log.encode())
        assertEquals(listOf("com.vendor.bloat"), again.restorable())
        val enable = ShellRenderer.enableUserPackageLines(tv.cleanyour.core.model.PackageName("com.vendor.bloat"))
            .joinToString(" ") { it.text }
        assertTrue(enable.contains("enable"))
        assertFalse(TellyFeatures.usesUserZeroUninstall(enable))
    }

    @Test fun otherAdbKeysCanBeListedButNotRemovedWithoutThisPhone() {
        val dumpsys = """
            debugging_manager:
              user_keys=QAAAAPhoneKey cleanyourtv
            QAAAALaptopKey alex@laptop
        """.trimIndent()
        val keys = TellyFeatures.parseKeys(dumpsys, "QAAAAPhoneKey cleanyourtv")!!
        assertEquals(2, keys.size)
        assertTrue(keys.single { it.ours }.name == "cleanyourtv")
        val kept = TellyFeatures.keptKeyFile(keys)!!
        assertTrue(kept.contains("QAAAAPhoneKey"))
        assertFalse(kept.contains("QAAAALaptopKey"))
        assertNull(TellyFeatures.keptKeyFile(keys.map { it.copy(ours = false) }))
        assertNull(TellyFeatures.parseKeys("no adb here", "QAAAAPhoneKey cleanyourtv"))
    }

    @Test fun monitorReadsTemperatureSpeedAndApps() {
        assertEquals("cpu 46.5°", TellyFeatures.parseThermal("Temperature{mValue=46.5, mType=3, mName=cpu}"))
        val a = "  eth0: 1000 0 0 0 0 0 0 0 100 0 0 0 0 0 0 0\n  lo: 9 0 0 0 0 0 0 0 9 0 0 0 0 0 0 0\n"
        val b = "  eth0: 5000 0 0 0 0 0 0 0 100 0 0 0 0 0 0 0\n"
        assertEquals("4 KB/s", TellyFeatures.netSpeed(TellyFeatures.parseNetBytes(a)!!, TellyFeatures.parseNetBytes(b)!!, 1000))
        assertEquals(listOf("com.example.app  12%"), TellyFeatures.parseCpuApps("12% 10/com.example.app: 8% user + 4% kernel"))
        assertTrue(TellyFeatures.parseMemApps("    20,480K: com.example.app (pid 3)").single().contains("20 MB"))
    }

    @Test fun userUninstallDoesNotUseUserZero() {
        val line = ShellRenderer.uninstallUserPackageLine(tv.cleanyour.core.model.PackageName("org.xbmc.kodi")).text
        assertFalse(TellyFeatures.usesUserZeroUninstall(line))
        assertEquals(listOf("pm", "uninstall", "org.xbmc.kodi"), ShellTokens.tokenize(line))
    }
}
