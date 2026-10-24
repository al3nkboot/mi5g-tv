package tv.cleanyour.core.backup

import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Component
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemoteDir
import tv.cleanyour.core.model.SettingKey
import tv.cleanyour.core.ops.UndoOrder

// What the change records add up to now, replayed oldest first: the packages that are off because
// of us, HOME before and now, the settings we changed, the apps we installed and the files we
// wrote. restore.txt, restore-point.json and the restore note all read it, so they always agree.
data class ChangeSummary(
    val switchedOff: List<PackageName>,          // off now, and on (or unknown) before our first change to it; in the order we switched them off
    val homeBefore: Component?,                  // the prior of our first SetHome
    val homeNow: Component?,                     // the target of our last SetHome
    val settings: List<SettingChange>,           // value before our first write and after our last, sorted by key
    val installed: List<PackageName>,            // installed by us and not removed since
    val files: List<String>,                     // absolute TV paths we wrote and did not delete, restore point files excluded
) {
    data class SettingChange(val key: SettingKey, val before: String?, val now: String?)

    companion object {
        fun of(changes: List<ChangeRecord>): ChangeSummary {
            val firstPrior = LinkedHashMap<PackageName, EnabledState?>()
            val off = LinkedHashMap<PackageName, Boolean>()
            var homeBefore: Component? = null
            var homeSeen = false
            var homeNow: Component? = null
            val settingBefore = LinkedHashMap<SettingKey, String?>()
            val settingNow = LinkedHashMap<SettingKey, String?>()
            val installed = LinkedHashSet<PackageName>()
            val files = LinkedHashSet<String>()

            fun touch(pkg: PackageName, c: ChangeRecord) { if (pkg !in firstPrior) firstPrior[pkg] = c.prior?.packageState }

            for (c in changes.sortedWith(UndoOrder.CHRONOLOGICAL)) {
                when (val op = c.op) {
                    is Op.Disable -> { touch(op.pkg, c); off.remove(op.pkg); off[op.pkg] = true }
                    is Op.Enable -> { touch(op.pkg, c); off[op.pkg] = false }
                    is Op.DefaultState -> { touch(op.pkg, c); off[op.pkg] = false }
                    is Op.Install -> if (c.prior?.present != true) installed += op.pkg
                    is Op.InstallStream -> if (c.prior?.present != true) installed += op.pkg
                    is Op.Uninstall -> { installed -= op.pkg; off.remove(op.pkg) }
                    is Op.SetHome -> {
                        if (!homeSeen) { homeBefore = c.prior?.home; homeSeen = true }
                        homeNow = op.component
                    }
                    is Op.SettingsPut -> {
                        if (op.key !in settingBefore) settingBefore[op.key] = c.prior?.settingValue
                        settingNow[op.key] = op.value
                    }
                    is Op.PushAsset -> if (op.dest.dir != RemoteDir.RESTORE) files += op.dest.absolute
                    is Op.RemoveFile -> files -= op.path.absolute
                    else -> {}
                }
            }
            val wasOff = setOf(EnabledState.DISABLED, EnabledState.DISABLED_USER)
            return ChangeSummary(
                switchedOff = off.filter { (pkg, isOff) -> isOff && firstPrior[pkg] !in wasOff }.keys.toList(),
                homeBefore = homeBefore,
                homeNow = homeNow,
                settings = settingBefore.keys.sortedBy { it.name }.map { SettingChange(it, settingBefore[it], settingNow[it]) },
                installed = installed.toList(),
                files = files.toList(),
            )
        }
    }
}
