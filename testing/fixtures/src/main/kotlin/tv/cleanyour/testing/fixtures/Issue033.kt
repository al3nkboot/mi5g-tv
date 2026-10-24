package tv.cleanyour.testing.fixtures

import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.HomeResolution
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.SnapshotReason

// The TVs of the 0.3.3 problem reports, as plain data from what their reports say (nothing here
// came from a TV). #10: a Xiaomi TV Box S (MiTV-AFKR0, device jaws) updated to Google TV 14, which
// no exact profile matches (the 2nd gen profile is for Android 11), so it gets the Xiaomi brand
// sure set. #8: a TCL G07 Google TV 11 set (exact profile tcl-g07-google-tv) whose own home screen
// was switched off before and whose Home answers to a launcher the person installed.
object Issue033 {
    val launcherx = Component(PackageName("com.google.android.apps.tv.launcherx"), ".home.HomeActivity")
    val recovery = Component(PackageName("com.google.android.tungsten.setupwraith"), ".RecoveryActivity")
    val fallbackHome = Component(PackageName("com.android.tv.settings"), ".system.FallbackHome")
    val patchwall = PackageName("com.mitv.tvhome.atv")
    // A launcher from the Play Store stands in for "an app you installed (not named)".
    val personsLauncher = Component(PackageName("com.spocky.projengmenu"), ".ui.home.HomeActivity")

    private fun system(enabled: EnabledState = EnabledState.ENABLED, label: String? = null) =
        PackageState(enabled, system = true, versionCode = 1, signer = null, persistent = false, tvInput = false, label = label, installedByUs = false)

    private fun theirs(label: String? = null) =
        PackageState(EnabledState.ENABLED, system = false, versionCode = 1, signer = null, persistent = false, tvInput = false, label = label, installedByUs = false)

    // #10: "Brand: Xiaomi, Model: MiTV-AFKR0, Device: jaws, Android: 14 (SDK 34)".
    fun jawsFacts(): DeviceFacts = Samples.miTvFacts().copy(
        brand = "Xiaomi", manufacturer = "Xiaomi", model = "MiTV-AFKR0", device = "jaws", sdk = 34, release = "14",
        fingerprint = "Xiaomi/jaws/jaws:14/UKG3.250107.001/V816.0.11.0.UZFAABX:user/release-keys", hasGms = true,
    )

    // The box's apps as far as the report and the Xiaomi lists name them: the Xiaomi extras and
    // Google's preloads the sure set switches off, the home screens, the wizard and the system parts.
    val jawsSystem: List<String> = listOf(
        "android", "com.android.systemui", "com.android.tv.settings", "com.android.vending", "com.android.providers.tv",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.katniss", "com.google.android.apps.mediashell",
        "com.google.android.tv.remote.service", "com.google.android.feedback", "com.google.android.partnersetup",
        "com.google.android.play.games", "com.google.android.videos", "com.google.android.youtube.tv",
        "com.miui.tv.analytics", "com.xiaomi.statistic", "com.mitv.tvhome.atv", "com.mitv.tvhome.michannel",
        "com.mitv.tvhome.mitvplus", "com.mitv.tvhome.oemtab", "com.xm.webcontent", "com.xiaomi.mitv.updateservice",
        "com.xiaomi.android.tvsetup.partnercustomizer", "mitv.service", "com.mi.bluetooth", "com.xiaomi.gtv.settings",
        "com.amazon.amazonvideo.livingroom", "com.android.printspooler", "com.netflix.ninja",
    )

    fun jawsSnapshot(reason: SnapshotReason = SnapshotReason.BASELINE): Snapshot = Samples.snapshot(
        facts = jawsFacts(), reason = reason,
        packages = jawsSystem.associate { PackageName(it) to system() } +
            mapOf(launcherx.pkg to system(label = "Google TV Home"), recovery.pkg to system(label = "Setup")),
        home = HomeResolution(launcherx, listOf(HomeClaimant(launcherx, 2), HomeClaimant(recovery, 1), HomeClaimant(fallbackHome, -1000))),
    )

    // #8: "Brand: TCL, Model: Smart TV, Device: G07, Android: 11 (SDK 30)".
    fun g07Facts(): DeviceFacts = Samples.miTvFacts().copy(
        brand = "TCL", manufacturer = "TCL", model = "Smart TV", device = "G07", sdk = 30, release = "11",
        fingerprint = "TCL/G07_4K_GB_NF/G07:11/RP1A.200622.001/AR20:user/release-keys", hasGms = true,
    )

    val g07System: List<String> = listOf(
        "android", "com.android.systemui", "com.android.tv.settings", "com.android.vending", "com.android.providers.tv",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.katniss", "com.google.android.apps.mediashell",
        "com.google.android.feedback", "com.google.android.partnersetup", "com.google.android.play.games",
        "com.google.android.youtube.tv", "com.tcl.tv", "com.netflix.ninja",
    )

    // HOME: "an app you installed (not named) priority 0" and FallbackHome; Google's launcherx is
    // on the TV but switched off (not among the claimants).
    fun g07Snapshot(reason: SnapshotReason = SnapshotReason.BASELINE): Snapshot = Samples.snapshot(
        facts = g07Facts(), reason = reason,
        packages = g07System.associate { PackageName(it) to system() } + mapOf(
            launcherx.pkg to system(EnabledState.DISABLED_USER, "Google TV Home"),
            recovery.pkg to system(label = "Setup"),
            personsLauncher.pkg to theirs("Projectivy Launcher"),
        ),
        home = HomeResolution(personsLauncher, listOf(HomeClaimant(personsLauncher, 0), HomeClaimant(fallbackHome, -1000))),
    )
}
