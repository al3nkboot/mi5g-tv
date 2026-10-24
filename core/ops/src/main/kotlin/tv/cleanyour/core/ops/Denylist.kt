package tv.cleanyour.core.ops

import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.Quirk
import tv.cleanyour.core.model.Snapshot

// The protected denylist (PLAN 6.5 plus SECURITY 8.5), as data. It sits above every profile and no
// mode of 0.1 can override it. `Protected.isProtected` (the frozen entry point) applies the static
// names and the per-package facts; `Denylist.reason` adds the snapshot facts (the active keyboard
// and screensaver) for callers that have a snapshot: the planner at plan time and the executor
// with live facts (SECURITY 3.5, C4). Reasons are plain words for the "Why?" link (D2).
object Denylist {
    // The framework package. PACKAGE_RE needs a dot, so it is spelled out here and the planner and
    // the renderer refuse it whether or not the value class ever admits it (RFC-W1-2 ruling).
    const val FRAMEWORK = "android"

    val exact: Map<String, String> = linkedMapOf(
        FRAMEWORK to "it is the Android framework itself",
        "com.android.systemui" to "it draws the TV's status bar, dialogs and system UI",
        "com.android.settings" to "it is the TV's Settings app",
        "com.android.tv.settings" to "it is the TV's Settings app",
        "com.android.packageinstaller" to "it installs and removes apps",
        "com.google.android.packageinstaller" to "it installs and removes apps",
        "com.android.permissioncontroller" to "it manages app permissions",
        "com.google.android.permissioncontroller" to "it manages app permissions",
        "com.android.providers.settings" to "it stores the TV's settings",
        "com.android.providers.tv" to "it stores the TV's channels and inputs",
        "com.android.providers.media" to "it manages the TV's storage",
        "com.android.providers.media.module" to "it manages the TV's storage",
        "com.google.android.providers.media.module" to "it manages the TV's storage",
        "com.android.shell" to "it is the shell this app talks to",
        "com.android.adbd" to "it is the debugging service this app talks to",
        "com.android.keychain" to "it keeps the TV's certificates",
        "com.android.se" to "it is a secure element service",
        "com.android.location.fused" to "it is a core location service",
        "com.android.bluetooth" to "it pairs and talks to the remote",
        "com.google.android.tv.btservices" to "it pairs and talks to the remote",
        "com.droidlogic.ble" to "it pairs and talks to the remote",
        "com.droidlogic" to "it is a core service of this TV's chipset",
        "com.droidlogic.tvinput" to "it provides the TV's inputs",
        "com.droidlogic.tv.settings" to "it is the TV's Settings app",
        "com.droidlogic.overlay" to "it is a core service of this TV's chipset",
        "com.google.android.gms" to "it is Google Play services",
        "com.google.android.gsf" to "it is Google Play services",
        "com.android.vending" to "it is the Play Store",
    )

    val prefixes: Map<String, String> = linkedMapOf(
        "com.android.networkstack" to "it keeps the TV's network running",
        "com.android.wifi." to "it keeps the TV's Wi-Fi running",
        "com.android.providers.media." to "it manages the TV's storage",
    )

    // The first-run wizard: protected unless the profile names it with the takeover quirk, and
    // even then only the home screen switch may switch it off (the planner enforces the where).
    val setupWizards: Set<String> = setOf("com.google.android.tungsten.setupwraith")

    const val REASON_SETUP_WIZARD = "the first-run setup wizard holds on to the Home button on this TV"
    const val REASON_PERSISTENT = "the TV keeps it running at all times"
    const val REASON_TV_INPUT = "it provides a TV input (HDMI, tuner or CEC)"
    const val REASON_PROFILE = "this TV's profile says never switch it off"
    const val REASON_KEYBOARD = "it is the TV's keyboard"
    const val REASON_SCREENSAVER = "it is the TV's screensaver"

    fun staticReason(pkg: String): String? {
        exact[pkg]?.let { return it }
        for ((prefix, reason) in prefixes) if (pkg.startsWith(prefix)) return reason
        return null
    }

    // The full check for a caller with a snapshot: the frozen check plus the keyboard and the
    // screensaver read from the TV.
    fun reason(pkg: PackageName, state: PackageState?, profile: Profile?, snapshot: Snapshot?): String? {
        Protected.isProtected(pkg, state, profile)?.let { return it }
        if (snapshot == null) return null
        if (snapshot.ime?.pkg == pkg) return REASON_KEYBOARD
        if (snapshot.screensaver.any { it.pkg == pkg }) return REASON_SCREENSAVER
        return null
    }

    fun reason(pkg: PackageName, snapshot: Snapshot, profile: Profile?): String? =
        reason(pkg, snapshot.packages[pkg], profile, snapshot)

    fun isWizardAllowed(pkg: PackageName, profile: Profile?): Boolean =
        profile != null && profile.setupWraith == pkg && Quirk.HOME_TAKEOVER_SETUPWRAITH in profile.quirks

    fun isStockLauncher(pkg: PackageName, stockLaunchers: List<Component>): Boolean =
        stockLaunchers.any { it.pkg == pkg }
}
