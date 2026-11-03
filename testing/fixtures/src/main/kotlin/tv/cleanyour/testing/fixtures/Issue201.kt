package tv.cleanyour.testing.fixtures

import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.HomeClaimant
import tv.cleanyour.core.model.HomeResolution
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.model.SnapshotReason

// The 2.0.0 field report (plain data from the report, nothing captured from the TV): a MediaTek
// GAORUI box, model "AndroidTV", device m7332_eu_1g, Android 9 (SDK 28), no exact profile. Its own
// home is CTV's com.ctv.android.tvboot/.TvBoot (priority -1000) and HOME already answers to a
// com.gathin.tvhome launcher signed with another key (priority 0). Every probe ended "plan refused".
object Issue201 {
    val tvboot = Component(PackageName("com.ctv.android.tvboot"), ".TvBoot")
    val gathin = Component(PackageName("com.gathin.tvhome"), ".MainActivity")
    val foreignSigner = Sha256("f0".repeat(32))

    fun facts(): DeviceFacts = Samples.miTvFacts().copy(
        brand = "MediaTek", manufacturer = "GAORUI", model = "AndroidTV", device = "m7332_eu_1g", sdk = 28, release = "9",
        fingerprint = "MediaTek/m7332_eu_1g/m7332_eu_1g:9/PPR2.180905.006.A1/wangwei07061420:userdebug/release-keys", hasGms = true,
    )

    private fun system() = PackageState(EnabledState.ENABLED, system = true, versionCode = 1, signer = null, persistent = false, tvInput = false, label = null, installedByUs = false)

    val systemApps: List<String> = listOf(
        "android", "com.android.systemui", "com.android.tv.settings", "com.android.vending", "com.android.providers.tv",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.katniss", "com.google.android.youtube.tv",
        "com.mediatek.wwtv.tvcenter", "com.ctv.android.tvboot", "com.netflix.ninja",
    )

    fun snapshot(reason: SnapshotReason = SnapshotReason.BASELINE): Snapshot = Samples.snapshot(
        facts = facts(), reason = reason,
        packages = systemApps.associate { PackageName(it) to system() } + mapOf(
            gathin.pkg to PackageState(EnabledState.ENABLED, system = false, versionCode = 26, signer = foreignSigner, persistent = false,
                tvInput = false, label = "Clean Your TV", installedByUs = false),
        ),
        home = HomeResolution(gathin, listOf(HomeClaimant(gathin, 0), HomeClaimant(tvboot, -1000))),
    )
}
