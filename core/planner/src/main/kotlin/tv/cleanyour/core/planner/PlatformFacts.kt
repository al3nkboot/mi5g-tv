package tv.cleanyour.core.planner

import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Platform
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.Snapshot

// Firmware that refuses another home screen, told from the TV's own facts so that an unlisted set
// is caught as surely as one with an exact profile (issue #2: an Insignia Fire TV no profile knew
// resolved to the Amazon sure set, a takeover was planned and Fire OS refused it half way).
// Not a frozen interface.
object FireOs {
    // Fire OS's own home screens. Both are protected packages on Fire OS 7 and 8 (pm disable-user and
    // pm enable answer "Cannot disable a protected package"), their HOME filter has priority 950 and
    // set-home-activity is ignored (upstream examples/insignia-ns55f301na25 and
    // amazon-fire-tv-stick-4k-2023). They are never switched off, in or out of the home screen switch.
    val HOMES: Set<PackageName> = setOf(PackageName("com.amazon.tv.launcher"), PackageName("com.amazon.firehomestarter"))
    const val MAKER = "amazon"
    const val MODEL_PREFIX = "AFT"

    fun isHome(pkg: PackageName): Boolean = pkg in HOMES

    // Amazon as the maker or brand (Fire TV Edition sets from Insignia, Toshiba or Pioneer report
    // Amazon as one of them), an Amazon model code (AFTKM, AFTDCT31), or one of Fire OS's home
    // screens installed or answering Home.
    fun isFireOs(facts: DeviceFacts, snapshot: Snapshot?): Boolean {
        if (MAKER.equals(facts.manufacturer.trim(), ignoreCase = true) || MAKER.equals(facts.brand.trim(), ignoreCase = true)) return true
        if (facts.model.trim().startsWith(MODEL_PREFIX, ignoreCase = true)) return true
        val s = snapshot ?: return false
        if (HOMES.any { it in s.packages }) return true
        return s.home.resolved?.pkg?.let(::isHome) == true || s.home.claimants.any { isHome(it.component.pkg) }
    }
}

// China-market TCL and FFalcon sets (the TCL_CN profiles: ak30a5, tcl_mt5879_cn): no Google
// services at all and a firmware that takes no other launcher. The international TCL Android and
// Google TV sets always carry Google services, so the pair of facts names the China firmware only.
object TclChina {
    val BRANDS: Set<String> = setOf("tcl", "ffalcon")

    fun isTclChina(facts: DeviceFacts): Boolean =
        !facts.hasGms && (facts.brand.trim().lowercase() in BRANDS || facts.manufacturer.trim().lowercase() in BRANDS)

    // Whether the rule decides for this TV: only when no exact profile knows it, or the exact profile
    // is a TCL_CN one. An exact TCL Android TV or Google TV profile keeps its own behaviour (a set
    // whose Google services the probe could not see is not turned into a China-market one).
    fun applies(facts: DeviceFacts, exact: Profile?): Boolean =
        (exact == null || exact.platform == Platform.TCL_CN) && isTclChina(facts)
}
