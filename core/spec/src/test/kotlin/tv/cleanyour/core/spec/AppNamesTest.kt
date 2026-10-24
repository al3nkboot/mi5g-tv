package tv.cleanyour.core.spec

import org.junit.Test
import tv.cleanyour.core.model.PackageName
import kotlin.test.assertEquals

// Round 3 of the emulator run: the dock sheet showed package names. The facts come first, then the
// TV's label, then a name read from the package, never the raw package.
class AppNamesTest {
    private val facts = BundledFactsTable()
    private fun name(pkg: String, label: String? = null) = AppNames.of(PackageName(pkg), facts, label)

    @Test fun theFactsTableComesFirst() {
        assertEquals("Netflix", name("com.netflix.ninja", label = "Netflix TV"))
        assertEquals("Android TV home", name("com.google.android.tvlauncher"))
    }

    @Test fun thenTheTvsOwnLabel() {
        assertEquals("System Tracing", name("com.android.traceur", label = "System Tracing"))
        assertEquals("Traceur", name("com.android.traceur", label = "  "))
    }

    @Test fun otherwiseANameFromThePackageWithoutAShellCall() {
        // The emulator TV's two launchable apps.
        assertEquals("Lego", name("com.lego.android.tvleanback"))
        assertEquals("Traceur", name("com.android.traceur"))
        assertEquals("Spotify", AppNames.fromPackage(PackageName("com.spotify.tv.android")))
        assertEquals("Settings", AppNames.fromPackage(PackageName("com.android.tv.settings")))
        assertEquals("Youtube", AppNames.fromPackage(PackageName("com.google.android.youtube.tv")))
        assertEquals("Whatsapp", AppNames.fromPackage(PackageName("com.whatsapp")))
        assertEquals("My Player", AppNames.fromPackage(PackageName("com.example.myPlayer")))
        assertEquals("Retro Arch", AppNames.fromPackage(PackageName("com.retro_arch")))
        assertEquals("Android", AppNames.fromPackage(PackageName("android")))
        assertEquals("Miui Analytics", AppNames.fromPackage(PackageName("com.miui.tv.analytics")))
        assertEquals("Play Games", AppNames.fromPackage(PackageName("com.google.android.play.games")))
        assertEquals("Tv", AppNames.fromPackage(PackageName("com.google.android.tv")))
    }
}
