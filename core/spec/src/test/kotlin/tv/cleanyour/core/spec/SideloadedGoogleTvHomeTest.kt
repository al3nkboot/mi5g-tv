package tv.cleanyour.core.spec

import org.junit.Test
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.HomeResolution
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 2.3 (cleanyourtv-app 0.5.0, issues #5539, #5744, #5911, #4177, #4551): Google TV's home screen the
// person installed on an Android TV set. Cases after upstream's SideloadedGoogleTvHomeTest (MIT).
class SideloadedGoogleTvHomeTest {
    private val tvlauncher = KnownLaunchers.TVLAUNCHER
    private val launcherx = KnownLaunchers.LAUNCHERX
    private val fallback = Component(PackageName("com.android.tv.settings"), ".system.FallbackHome")

    private fun state(system: Boolean, enabled: EnabledState = EnabledState.DEFAULT, version: Long = 1_000) =
        PackageState(enabled, system, version, null, persistent = false, tvInput = false, label = null, installedByUs = false)

    private fun leanbackHome(launcherxPriority: Int = 0) = HomeResolution(tvlauncher,
        listOf(HomeClaimant(tvlauncher, 2), HomeClaimant(launcherx, launcherxPriority), HomeClaimant(fallback, -1000)))

    private fun packages(lx: PackageState? = state(system = false), tv: PackageState? = state(system = true)) = buildMap {
        tv?.let { put(tvlauncher.pkg, it) }
        lx?.let { put(launcherx.pkg, it) }
        put(fallback.pkg, state(system = true))
    }

    @Test fun aSideloadedLauncherxNextToTheTvsOwnTvlauncherIsThePersons() {
        assertTrue(KnownLaunchers.sideloadedGoogleTvHome(leanbackHome(), packages()))
        // tvlauncher's flags read wrong after an update: its claim above 0 says it is the TV's own.
        assertTrue(KnownLaunchers.sideloadedGoogleTvHome(leanbackHome(), packages(tv = state(system = false))))
    }

    @Test fun aRealGoogleTvIsNeverTakenForOne() {
        assertFalse(KnownLaunchers.sideloadedGoogleTvHome(leanbackHome(), packages(lx = state(system = true))))
        assertFalse(KnownLaunchers.sideloadedGoogleTvHome(leanbackHome(launcherxPriority = 2), packages()))
        assertFalse(KnownLaunchers.sideloadedGoogleTvHome(leanbackHome(), packages(lx = state(system = false, version = 0))))
        assertFalse(KnownLaunchers.sideloadedGoogleTvHome(leanbackHome(), packages(lx = state(system = false, enabled = EnabledState.DISABLED_USER))))
        assertFalse(KnownLaunchers.sideloadedGoogleTvHome(HomeResolution(launcherx, listOf(HomeClaimant(launcherx, 0))), packages(tv = null)))
        assertFalse(KnownLaunchers.sideloadedGoogleTvHome(leanbackHome(), null))
        // No TV's own Android TV home at all: not one.
        assertFalse(KnownLaunchers.sideloadedGoogleTvHome(HomeResolution(null, listOf(HomeClaimant(launcherx, 0))), packages(tv = null)))
    }
}
