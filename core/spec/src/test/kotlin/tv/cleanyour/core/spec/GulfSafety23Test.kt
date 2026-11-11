package tv.cleanyour.core.spec

import kotlinx.coroutines.runBlocking
import tv.cleanyour.core.model.GroupDefault
import tv.cleanyour.core.model.PackageName
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Mi5G 2.3: TCL / Xiaomi safety from upstream Gathin23/cleanyourtv after pin 294d3d5 (MIT).
class GulfSafety23Test {
    private val usercenter = PackageName("com.tcl.usercenter")
    private val source = BundledProfileSource()
    private val profiles = runBlocking { source.all() }.associateBy { it.id }

    // #3533, 3c6e873: a TCL 43C641 (G03_4K_GB, BeyondTV4) reboots on every wake from standby with it off.
    @Test fun theTclAccountStaysOnForTheG03AndG05Class() {
        for (id in listOf("tcl-g03-google-tv", "tcl-g05-google-tv", "tcl-65c735")) {
            val p = profiles.getValue(id)
            assertTrue(usercenter in p.neverDisable, id)
            assertFalse(p.groups.filter { it.default == GroupDefault.DISABLE }.any { g -> g.packages.any { it.name == usercenter } }, id)
        }
    }

    @Test fun noTclSureSetSwitchesTheAccountOff() {
        val tcl = source.sureSets().filter { it.brand.equals("TCL", ignoreCase = true) }
        assertTrue(tcl.isNotEmpty())
        assertTrue(tcl.all { s -> s.groups.none { g -> g.packages.any { it.name == usercenter } } })
    }

    // PR #3656: MiTV-MOOR2's updateservice updates PatchWall, not the firmware; the label says so.
    @Test fun theXiaomiUpdaterIsNamedForWhatItUpdates() {
        val f = BundledFactsTable().lookup(PackageName("com.xiaomi.mitv.updateservice"))!!
        assertTrue("not firmware" in f.description, f.description)
        val wraith = BundledFactsTable().lookup(KnownLaunchers.SETUPWRAITH)!!
        assertTrue("casting" in wraith.description, wraith.description)   // #3446, ac182ab
    }
}
