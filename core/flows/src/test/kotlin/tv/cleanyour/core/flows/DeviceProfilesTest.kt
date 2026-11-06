package tv.cleanyour.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.GroupDefault
import tv.cleanyour.core.model.PackageEntry
import tv.cleanyour.core.model.PackageGroup
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Platform
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.ProfileMatch
import tv.cleanyour.core.model.Sha256

class DeviceProfilesTest {
    private fun p(vararg g: Pair<GroupDefault, List<String>>) = Profile(
        "xiaomi-tv-box-s-3rd-gen", ProfileMatch(listOf("MDZ-32-AA"), emptyList(), "Xiaomi", 33, 35, null), Platform.GOOGLE_TV,
        listOf(Component(PackageName("com.google.android.apps.tv.launcherx"), ".Main")), null, PackageName("com.google.android.tungsten.setupwraith"),
        emptySet(), g.mapIndexed { i, (d, pk) -> PackageGroup("g$i", "G$i", d, pk.map { PackageEntry(PackageName(it), null) }) },
        setOf(PackageName("com.mi.bluetooth")), emptyList(), null, Sha256("a".repeat(64)))

    @Test fun onlyDisableGroupsMinusProtected() {
        val s = DeviceProfiles.suggestions(p(
            GroupDefault.DISABLE to listOf("com.miui.tv.analytics", "com.mi.bluetooth", "com.google.android.tungsten.setupwraith", "com.google.android.apps.tv.launcherx"),
            GroupDefault.ASK to listOf("com.xiaomi.mitv.updateservice"),
            GroupDefault.KEEP to listOf("com.google.android.katniss"),
        ))
        assertEquals(setOf(PackageName("com.miui.tv.analytics")), s)
    }

    @Test fun mi5gNamesTheDevice() {
        assertEquals("Xiaomi TV Box S الجيل الثالث (MDZ-32-AA)", DeviceProfiles.label("xiaomi-tv-box-s-3rd-gen", "MDZ-32-AA"))
        assertEquals("Haier X", DeviceProfiles.label("unknown", "Haier X"))
    }

    @Test fun suggestedFilterShowsEnabledSuggestedOnly() {
        val apps = listOf(
            TvInstalledApp(PackageName("com.miui.tv.analytics"), "Analytics", system = true),
            TvInstalledApp(PackageName("com.xm.webcontent"), "Web", system = true, enabled = false),
            TvInstalledApp(PackageName("org.xbmc.kodi"), "Kodi", system = false),
        )
        val st = TvAppManagerState(apps = apps, filter = TvAppFilter.SUGGESTED, suggested = setOf(PackageName("com.miui.tv.analytics"), PackageName("com.xm.webcontent")))
        assertEquals(listOf("Analytics"), st.filteredApps.map { it.name })
    }
}
