package tv.cleanyour.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.ops.ShellRenderer

class Household16Test {
    @Test fun healthMatchesWebModel() {
        assertEquals(58, TvHealth.score(TvPlatform.ANDROID, false, false, false, false, true))
        assertEquals(98, TvHealth.score(TvPlatform.ANDROID, true, true, true, true, true))
        assertEquals(46, TvHealth.score(TvPlatform.ANDROID, false, false, false, false, false))
        assertNull(TvHealth.score(TvPlatform.TIZEN, true, true, true, true, true))
        assertNull(TvHealth.score(TvPlatform.WEBOS, true, true, true, true, true))
    }

    @Test fun port853DecidesDnsMode() {
        assertEquals(DnsMode.ENCRYPTED, DnsDecision.modeFor(Port853.OPEN))
        assertEquals(DnsMode.SAFE, DnsDecision.modeFor(Port853.BLOCKED))
        assertEquals(DnsMode.SAFE, DnsDecision.modeFor(Port853.UNKNOWN))
        assertTrue(ShellRenderer.familyDnsLines(false).none { "hostname" in it.text })
        assertTrue(ShellRenderer.familyDnsLines(true).any { "family.adguard-dns.com" in it.text })
    }

    @Test fun uninstallNeedsWordAndRestorePoint() {
        assertFalse(uninstallUnlocked("احذف", hasRestorePoint = false))
        assertFalse(uninstallUnlocked("حذف", hasRestorePoint = true))
        assertTrue(uninstallUnlocked(" احذف ", hasRestorePoint = true))
    }

    @Test fun streamingAppsAreProtected() {
        val shahid = TvInstalledApp(PackageName("net.mbc.shahid.tv"), "شاهد", system = false)
        assertTrue(shahid.protectedStreaming)
        assertFalse(shahid.canDisable)
        assertFalse(shahid.canUninstall)
        val prime = TvInstalledApp(PackageName("com.amazon.amazonvideo.livingroom"), "Prime Video", system = true)
        assertTrue(prime.canDisable)
    }

    @Test fun appsSplitIntoMineAndFactory() {
        val apps = listOf(
            TvInstalledApp(PackageName("org.xbmc.kodi"), "Kodi", system = false),
            TvInstalledApp(PackageName("com.netflix.ninja"), "Netflix", system = true),
        )
        assertEquals(listOf("Kodi"), TvAppManagerState(apps = apps, filter = TvAppFilter.USER).filteredApps.map { it.name })
        assertEquals(listOf("Netflix"), TvAppManagerState(apps = apps, filter = TvAppFilter.SYSTEM).filteredApps.map { it.name })
    }
}
