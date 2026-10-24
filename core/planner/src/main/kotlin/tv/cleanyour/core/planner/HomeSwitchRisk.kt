package tv.cleanyour.core.planner

import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.ops.Denylist

// RF-SAFETY-250, owner decisions for 0.2.0: TVs where the home screen switch is skipped up front, so
// Setup is the clean-up alone (like RF-PROFILES-63 P1 and P2). SetupMachine asks before it plans and
// S3 says why; the planner refuses a switch asked for anyway. Not a frozen interface.
object HomeSwitchRisk {
    enum class Risk(val reason: String) {
        // The switch needs the setup wizard off, the Wi-Fi guard (D3) keeps it on, and with the wizard
        // on and the TV's own home screen off its RecoveryActivity takes Home and the system can
        // crash-loop (upstream #506, #422). OWNER: the wizard saved the Wi-Fi; UNKNOWN: the TV could not tell.
        WIZARD_WIFI("this TV's setup wizard saved its Wi-Fi, so the wizard has to stay on, and with it on the home screen switch can crash the TV"),
        WIZARD_WIFI_UNKNOWN("the TV could not say who saved its Wi-Fi, so its setup wizard has to stay on, and with it on the home screen switch can crash the TV"),
        // A TV no profile knows: the switch never switches the setup wizard off, so it would stay on
        // while the TV's own home screen goes off, the same RecoveryActivity risk (RF-SAFETY-250).
        WIZARD_STAYS_ON("this TV's setup wizard would stay on while its own home screen goes off, and the wizard can then take the Home button and crash the TV"),
        // Google's own hardware: the Cast receiver reads its credentials from the setup wizard, so the
        // switch (which needs the wizard off) costs casting after every restart (upstream #361, #422).
        CASTING("on this Google device the home screen switch needs the setup wizard off, and casting from phones then stops working after every restart"),
        // The MOKA family (kalley-43g200): the firmware turns the wizard back on at every boot, and with
        // the TV's own home screen off it boot-looped a set that needed a USB reflash.
        BOOT_LOOP("on this TV family the setup wizard comes back at every restart, and with the TV's own home screen off the TV can fail to start"),
    }

    // Google's own Google TV hardware, by maker and device: Chromecast with Google TV 4K (sabrina)
    // and HD (boreal), the Google TV Streamer (kirkwood). The profiles for sabrina and kirkwood match
    // the same keys; boreal is where upstream first saw the casting break.
    val GOOGLE_MAKER = "google"
    val GOOGLE_DEVICES: Set<String> = setOf("sabrina", "boreal", "kirkwood")
    val CASTING_PROFILES: Set<String> = setOf("google-chromecast-gtv-4k", "google-tv-streamer-4k-kirkwood")

    // The MOKA family by the exact device and maker pairs its three profiles match on (kalley-43g200:
    // R3_GTV by MOKA; prismplus-q65-ultra-r10g: R10G by MOKA; kalley-r10g: R10G under the Kalley
    // brand), whatever the Android version. A device name alone could be another maker's, and the
    // maker alone is too wide: MOKA also builds the Philips R8G (whitefield), which is not this family.
    val MOKA_FAMILY: Set<Pair<String, String>> = setOf("R3_GTV" to "MOKA", "R10G" to "MOKA", "R10G" to "Kalley")
    val MOKA_PROFILES: Set<String> = setOf("kalley-43g200", "kalley-r10g", "prismplus-q65-ultra-r10g")

    // The first risk that applies, or null when the switch may be planned. `profile` is the exact
    // profile (null on a brand sure set or the generic path); `wizardWifi` the D3 answer.
    fun of(facts: DeviceFacts, profile: Profile?, snapshot: Snapshot, wizardWifi: WizardWifi): Risk? = when {
        isMokaFamily(facts) || profile?.id in MOKA_PROFILES -> Risk.BOOT_LOOP
        isGoogleHardware(facts) || profile?.id in CASTING_PROFILES -> Risk.CASTING
        else -> wizardWifiRisk(profile, snapshot, wizardWifi)
    }

    fun isMokaFamily(f: DeviceFacts): Boolean =
        MOKA_FAMILY.any { (device, maker) -> device.equals(f.device, ignoreCase = true) && (maker.equals(f.brand, ignoreCase = true) || maker.equals(f.manufacturer, ignoreCase = true)) }

    fun isGoogleHardware(f: DeviceFacts): Boolean =
        GOOGLE_DEVICES.any { it.equals(f.device, ignoreCase = true) } && (GOOGLE_MAKER.equals(f.brand, ignoreCase = true) || GOOGLE_MAKER.equals(f.manufacturer, ignoreCase = true))

    // With an exact profile, the wizard the switch would switch off (its setupWraith with the takeover
    // quirk) while D3 keeps it on: the TV said the wizard saved the Wi-Fi, or could not tell. Without
    // one the switch never touches the wizard, so a setup wizard that is on stays on while the stock
    // home goes off, whatever the Wi-Fi answer: no switch (the Wi-Fi line when the wizard saved it).
    private fun wizardWifiRisk(profile: Profile?, snapshot: Snapshot, wizardWifi: WizardWifi): Risk? {
        fun on(p: PackageName) = snapshot.packages[p]?.enabled.let { it != null && it != EnabledState.DISABLED_USER && it != EnabledState.DISABLED }
        if (profile == null) {
            val wizard = snapshot.packages.keys.firstOrNull { it.value in Denylist.setupWizards && on(it) } ?: return null
            return if (wizardWifi == WizardWifi.OWNER && on(wizard)) Risk.WIZARD_WIFI else Risk.WIZARD_STAYS_ON
        }
        if (wizardWifi == WizardWifi.NOT_OWNER) return null
        val wizard = profile.setupWraith?.takeIf { Denylist.isWizardAllowed(it, profile) } ?: return null
        if (!on(wizard)) return null
        return if (wizardWifi == WizardWifi.OWNER) Risk.WIZARD_WIFI else Risk.WIZARD_WIFI_UNKNOWN
    }
}
