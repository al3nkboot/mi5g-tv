package tv.cleanyour.core.planner

import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.planner.HomeSwitchRisk.Risk
import tv.cleanyour.core.planner.PlannerFixtures.choices
import tv.cleanyour.core.planner.PlannerFixtures.plan
import tv.cleanyour.core.planner.PlannerFixtures.refused
import tv.cleanyour.core.planner.PlannerFixtures.setup
import tv.cleanyour.core.planner.PlannerFixtures.snapshot
import tv.cleanyour.core.planner.PlannerFixtures.wizard
import tv.cleanyour.core.spec.BundledProfileSource
import tv.cleanyour.core.spec.ProfileResolution
import tv.cleanyour.core.store.SnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// RF-SAFETY-250: the home screen switches skipped up front for 0.2.0, on the shipped profiles and on
// TVs no profile matches. The planner refuses such a switch asked for anyway; the clean-up alone
// is planned as usual.
class HomeSwitchRiskTest {
    private val source = BundledProfileSource()
    private val shipped: Map<String, Profile> = BundledProfileSource.bundled.profiles.associateBy { it.id }
    private val tv = snapshot()   // the reference TV, its setup wizard installed and on
    private val noWizard = tv.copy(packages = tv.packages - wizard).let { it.copy(contentHash = SnapshotCodec.hash(it)) }
    private fun facts(device: String, brand: String, manufacturer: String = brand, sdk: Int = 34): DeviceFacts =
        tv.facts.copy(device = device, brand = brand, manufacturer = manufacturer, model = "unknown", sdk = sdk)
    private fun withFacts(f: DeviceFacts): Snapshot = tv.copy(facts = f).let { it.copy(contentHash = SnapshotCodec.hash(it)) }
    private fun exact(f: DeviceFacts): Profile? = (source.resolveNow(f) as? ProfileResolution.Exact)?.profile

    // (a) The Google TV sets whose list switches the wizard off with the stock home: while the TV says
    // the wizard saved the Wi-Fi, or cannot tell, D3 keeps it on, so there is no switch.
    @Test fun theWizardWifiSetsGetNoSwitchWhileTheWizardMayOwnTheWifi() {
        val ids = listOf("tcl-43s5k", "tcl-g03-google-tv", "xgimi-mogo-3-pro", "eko-2k-google-tv-tennoji", "byintek-t5-max", "aiwa-ai-pont-sa", "polytron-2k-hayward")
        for (id in ids) {
            val p = shipped.getValue(id)
            assertEquals(wizard, p.setupWraith, id)
            assertEquals(Risk.WIZARD_WIFI, HomeSwitchRisk.of(tv.facts, p, tv, WizardWifi.OWNER), id)
            assertEquals(Risk.WIZARD_WIFI_UNKNOWN, HomeSwitchRisk.of(tv.facts, p, tv, WizardWifi.UNKNOWN), id)
            assertNull(HomeSwitchRisk.of(tv.facts, p, tv, WizardWifi.NOT_OWNER), id)
            assertTrue(Risk.WIZARD_WIFI.reason in refused(setup(profile = p, choices = choices(wizardWifi = WizardWifi.OWNER))), id)
            plan(setup(profile = p, choices = choices(installLauncher = false, takeover = false, wizardWifi = WizardWifi.OWNER)))
        }
        // A wizard that is already off on the TV keeps nothing: the switch may go ahead.
        val off = snapshot(disabled = setOf(wizard.value))
        assertNull(HomeSwitchRisk.of(off.facts, shipped.getValue("tcl-43s5k"), off, WizardWifi.OWNER))
        // A profile whose switch never touches the wizard is not held back by the Wi-Fi.
        assertNull(HomeSwitchRisk.of(tv.facts, PlannerFixtures.profile(), tv, WizardWifi.OWNER))
    }

    // (a) The generic path never switches the wizard off, so a setup wizard that is on would stay on
    // while the TV's own home screen goes off: no switch, whatever the Wi-Fi answer (the Wi-Fi line
    // when the TV says the wizard saved it). Without a wizard, or with it off, the switch goes ahead.
    @Test fun anUnknownTvWithItsSetupWizardOnGetsNoSwitch() {
        assertEquals(Risk.WIZARD_WIFI, HomeSwitchRisk.of(tv.facts, null, tv, WizardWifi.OWNER))
        assertEquals(Risk.WIZARD_STAYS_ON, HomeSwitchRisk.of(tv.facts, null, tv, WizardWifi.UNKNOWN))
        assertEquals(Risk.WIZARD_STAYS_ON, HomeSwitchRisk.of(tv.facts, null, tv, WizardWifi.NOT_OWNER))
        assertEquals(listOf(Risk.WIZARD_WIFI.reason), refused(setup(profile = null, choices = choices(wizardWifi = WizardWifi.OWNER))))
        assertEquals(listOf(Risk.WIZARD_STAYS_ON.reason), refused(setup(profile = null, choices = choices(wizardWifi = WizardWifi.NOT_OWNER))))
        for (w in WizardWifi.entries) assertNull(HomeSwitchRisk.of(noWizard.facts, null, noWizard, w), "$w")
        val off = snapshot(disabled = setOf(wizard.value))
        assertNull(HomeSwitchRisk.of(off.facts, null, off, WizardWifi.UNKNOWN), "a wizard already off stays off")
        assertTrue(Op.SetHome(PlannerFixtures.launcher) in plan(setup(profile = null, choices = choices(wizardWifi = WizardWifi.UNKNOWN)), snapshot = noWizard).steps.map { it.op })
    }

    // (b) Google's own hardware: the switch costs casting after every restart.
    @Test fun googleHardwareGetsNoSwitch() {
        for ((device, id) in listOf("sabrina" to "google-chromecast-gtv-4k", "kirkwood" to "google-tv-streamer-4k-kirkwood")) {
            val f = facts(device, "google", "Google")
            assertEquals(id, exact(f)?.id, device)
            assertEquals(Risk.CASTING, HomeSwitchRisk.of(f, exact(f), withFacts(f), WizardWifi.NOT_OWNER), device)
            assertEquals(listOf(Risk.CASTING.reason), refused(setup(profile = exact(f)), snapshot = withFacts(f)), device)
        }
        // The Chromecast HD has no profile and the same casting break.
        assertEquals(Risk.CASTING, HomeSwitchRisk.of(facts("boreal", "google", "Google"), null, tv, WizardWifi.NOT_OWNER))
        // Not by the maker alone (an emulator image says Google too), nor by the device name alone.
        assertNull(HomeSwitchRisk.of(facts("generic_x86", "google", "Google"), null, noWizard, WizardWifi.NOT_OWNER))
        assertNull(HomeSwitchRisk.of(facts("sabrina", "Acme"), null, noWizard, WizardWifi.NOT_OWNER))
    }

    // (c) The MOKA family: the wizard comes back at boot and the set can boot-loop. The three shipped
    // profiles, and a set of the family that no profile matches (an Android update, another brand
    // build of the same device), get no switch; the Philips R8G, also made by MOKA, does.
    @Test fun theMokaFamilyGetsNoSwitch() {
        val pairs = HomeSwitchRisk.MOKA_PROFILES.flatMap { id -> shipped.getValue(id).match.let { m -> m.devices.map { it to m.brand!! } } }.toSet()
        assertEquals(HomeSwitchRisk.MOKA_FAMILY, pairs, "the family is exactly what its profiles match on")
        for ((f, id) in listOf(facts("R3_GTV", "Kalley", "MOKA") to "kalley-43g200", facts("R10G", "Kalley", "MOKA") to "kalley-r10g",
            facts("R10G", "PRISMPLUS", "MOKA") to "prismplus-q65-ultra-r10g")) {
            assertEquals(id, exact(f)?.id)
            assertEquals(Risk.BOOT_LOOP, HomeSwitchRisk.of(f, exact(f), withFacts(f), WizardWifi.NOT_OWNER), id)
            assertTrue(Risk.BOOT_LOOP.reason in refused(setup(profile = exact(f)), snapshot = withFacts(f)), id)
        }
        val android15 = facts("R10G", "PRISMPLUS", "MOKA", sdk = 35)
        assertNull(exact(android15))
        assertEquals(Risk.BOOT_LOOP, HomeSwitchRisk.of(android15, null, withFacts(android15), WizardWifi.NOT_OWNER))
        assertEquals(listOf(Risk.BOOT_LOOP.reason), refused(setup(profile = null), snapshot = withFacts(android15)))
        assertEquals(Risk.BOOT_LOOP, HomeSwitchRisk.of(facts("r10g", "Kalley", "Kalley"), null, tv, WizardWifi.NOT_OWNER), "case does not matter")
        // Never by the maker alone or the device alone.
        val philips = facts("whitefield", "Philips", "MOKA")
        assertEquals("philips-r8g-google-tv", exact(philips.copy(model = "R8G"))?.id)
        assertNull(HomeSwitchRisk.of(philips, null, noWizard, WizardWifi.NOT_OWNER))
        assertNull(HomeSwitchRisk.of(facts("R10G", "Acme"), null, noWizard, WizardWifi.NOT_OWNER))
        assertNull(HomeSwitchRisk.of(facts("R3_GTV", "Acme"), null, noWizard, WizardWifi.NOT_OWNER))
        // The clean-up alone is planned for the family, with the stock home and the wizard left alone.
        val f = facts("R10G", "Kalley", "MOKA")
        val clean = plan(setup(profile = exact(f), choices = choices(installLauncher = false, takeover = false)), snapshot = withFacts(f))
        assertTrue(clean.sections.isEmpty() && clean.steps.none { it.op == Op.Disable(wizard) })
    }

    // The reference TV keeps its switch.
    @Test fun theReferenceTvKeepsItsSwitch() {
        assertNull(HomeSwitchRisk.of(tv.facts, PlannerFixtures.profile(), tv, WizardWifi.NOT_OWNER))
        assertTrue(plan(setup()).sections.isNotEmpty())
        assertEquals(EnabledState.ENABLED, tv.packages.getValue(wizard).enabled)
    }
}
