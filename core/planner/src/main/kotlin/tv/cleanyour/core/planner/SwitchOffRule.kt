package tv.cleanyour.core.planner

import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.Snapshot
import tv.cleanyour.core.ops.Denylist
import tv.cleanyour.core.spec.KnownLaunchers

// What may be switched off (0.3.3, issue #10): one rule for the planner at plan time and for the
// engine just before each switch-off, with the run's live facts. Before 0.3.3 each side had its own
// copy, and a rule added to one (the stock launchers, the setup wizard) was missing from the other,
// so a plan could hold a switch-off the engine then refused. Both sides now ask this object, so for
// the same facts they give the same answer. Not a frozen interface.
//
// The order: the protected denylist with the package's facts (Denylist.reason: static names, the
// wizard rule, persistent, a TV input, the profile's never_disable, the keyboard, the screensaver);
// a package that asks for TV_INPUT_HARDWARE (only the engine reads that, from the dump it reads just
// before the step); Fire OS's own home screens, which the firmware never lets anyone switch off; a
// home screen app the person installed (not one of the stock launchers), never switched off, not
// even by the home screen switch; and,
// outside the home screen switch only, the TV's own home screens (the profile's stock launchers, the
// current HOME and every HOME claimant) and the first-run setup wizard, which only that switch may
// switch off (section 3.4).
object SwitchOffRule {
    const val REASON_YOUR_LAUNCHER = "it is a home screen app you installed, and we never switch those off"
    // Our own new home screen, the planner's launcher package. The probe cannot tell who installed
    // a package (installedByUs always reads false), so it is named here: never the person's.
    val OUR_LAUNCHER = PackageName("com.gathin.tvhome")

    // The reason `pkg` may not be switched off, or null when it may. `state` is what the caller knows
    // of the package (the engine's live dump, the planner's snapshot); `snapshot` gives HOME, the
    // keyboard and the screensaver (null: not known, those checks are left out). `inHomeSwitch`: the
    // switch-off is part of the open home screen switch. `requestsTvInputHardware`: the package's
    // dump asks for TV_INPUT_HARDWARE (upstream #421).
    fun reason(
        pkg: PackageName,
        state: PackageState?,
        profile: Profile?,
        snapshot: Snapshot?,
        stockLaunchers: List<Component>,
        inHomeSwitch: Boolean = false,
        requestsTvInputHardware: Boolean = false,
    ): String? {
        Denylist.reason(pkg, state, profile, snapshot)?.let { return it }
        if (requestsTvInputHardware) return Denylist.REASON_TV_INPUT
        if (FireOs.isHome(pkg)) return DefaultPlanner.REASON_FIRE_OS_HOME
        val stock = stockLaunchers.any { it.pkg == pkg }   // the TV's own by the allowlist, whatever its system flag reads
        if (!stock && snapshot != null && isPersonsLauncher(pkg, snapshot, state)) return REASON_YOUR_LAUNCHER
        if (inHomeSwitch) return null
        if (stock || (snapshot != null && isHomeScreen(pkg, snapshot))) return DefaultPlanner.REASON_HOME_SCREEN
        if (pkg.value in Denylist.setupWizards || profile?.setupWraith == pkg) return DefaultPlanner.REASON_WIZARD_SECTION_ONLY
        return null
    }

    // The TV's current HOME, whatever it is (R5-W4 O5: PatchWall on an unknown Xiaomi), or any HOME
    // claimant (PLAN 6.5: the current HOME resolver is protected).
    fun isHomeScreen(pkg: PackageName, snapshot: Snapshot): Boolean =
        snapshot.home.resolved?.pkg == pkg || snapshot.home.claimants.any { it.component.pkg == pkg }

    // A home screen app the person installed (issue #8: Projectivy, Wolf and the like): HOME or a
    // HOME claimant, not a system package, not our own new home screen, and not put there by this
    // app. A package whose system flag is not known counts as theirs, the safe side (as the TV
    // report does).
    fun isPersonsLauncher(pkg: PackageName, snapshot: Snapshot, state: PackageState? = snapshot.packages[pkg]): Boolean {
        if (!isHomeScreen(pkg, snapshot)) return false
        // Google's and Amazon's own TV home screens are never the person's, even when an update put
        // them under /data and the system flag reads wrong.
        if (pkg in KnownLaunchers.byPackage) return false
        if (pkg == OUR_LAUNCHER) return false
        val known = state ?: snapshot.packages[pkg]
        return known?.system != true && known?.installedByUs != true
    }
}

// The TV's own home screen the home screen switch hands HOME back to, shared by the planner's
// takeover and SetupMachine's decision to offer the switch (0.3.3, issue #8), so the wizard never
// asks the planner for a switch it then refuses as a whole. Not a frozen interface.
object StockHome {
    // Matched by component first, then by package: only the package of each allowlisted stock
    // launcher is verified on a real TV for every profile, the class is not. Either way the component
    // is the claimant the TV itself reported. Null: no HOME claimant is one of `stockLaunchers`.
    fun of(snapshot: Snapshot, stockLaunchers: List<Component>): Component? {
        val claimants = snapshot.home.claimants.map { it.component }
        val resolved = snapshot.home.resolved
        fun stockPkg(c: Component) = stockLaunchers.any { it.pkg == c.pkg }
        return claimants.firstOrNull { it == resolved && it in stockLaunchers }
            ?: claimants.firstOrNull { it in stockLaunchers }
            ?: claimants.firstOrNull { it == resolved && stockPkg(it) }
            ?: claimants.firstOrNull { stockPkg(it) }
    }

    // HOME answers to a home screen app the person installed, and the TV's own is not among the
    // claimants: the home screen was changed before (by another app or an earlier change), so the
    // switch has nothing to hand HOME back to and the person's choice stays.
    fun changedByPerson(snapshot: Snapshot, stockLaunchers: List<Component>): Boolean {
        val resolved = snapshot.home.resolved ?: return false
        return of(snapshot, stockLaunchers) == null && SwitchOffRule.isPersonsLauncher(resolved.pkg, snapshot)
    }
}
