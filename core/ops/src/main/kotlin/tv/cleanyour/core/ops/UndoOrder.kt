package tv.cleanyour.core.ops

import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.RemotePath
import tv.cleanyour.core.model.SettingKey

// The one ordering for putting a TV back from its change records, shared by the planner's
// UndoEverything and the laptop restore scripts of :core:backup (R5 of W6). It lives in :core:ops
// because both of those modules sit on layer 3 and may not depend on each other.
//
// Two rules:
// 1. Each item (a package's enabled state, a setting, the home screen, an installed app, a file)
//    goes back to its EARLIEST recorded state: only the first record that touched it counts,
//    because its prior is the state before this app ever changed it. Later records of the same
//    item are skipped, so a stale or partial history can never leave an item halfway.
// 2. Items are undone phase by phase: package states first (so the TV's own home screen is
//    switched back on), then settings, then the home screen itself, then the apps we installed
//    (so our launcher is never removed while it is still the home screen), then our files.
//    Inside a phase the item first touched most recently goes first.
object UndoOrder {
    // Chronological by atMs, then plan step; the sort is stable, so list order breaks the rest.
    val CHRONOLOGICAL: Comparator<ChangeRecord> = compareBy({ it.atMs }, { it.step })

    enum class Phase { PACKAGES, SETTINGS, HOME, APPS, FILES }

    // What a change record changed. Ops that leave nothing lasting on the TV have no target.
    sealed interface Target {
        val phase: Phase
        data class PackageEnabled(val pkg: PackageName) : Target { override val phase get() = Phase.PACKAGES }
        data class Setting(val key: SettingKey) : Target { override val phase get() = Phase.SETTINGS }
        data object Home : Target { override val phase get() = Phase.HOME }
        data class App(val pkg: PackageName) : Target { override val phase get() = Phase.APPS }
        data class File(val path: RemotePath) : Target { override val phase get() = Phase.FILES }
    }

    fun target(op: Op): Target? = when (op) {
        is Op.Disable -> Target.PackageEnabled(op.pkg)
        is Op.Enable -> Target.PackageEnabled(op.pkg)
        is Op.DefaultState -> Target.PackageEnabled(op.pkg)
        is Op.SettingsPut -> Target.Setting(op.key)
        is Op.SetHome -> Target.Home
        is Op.Install -> Target.App(op.pkg)
        is Op.InstallStream -> Target.App(op.pkg)
        is Op.Uninstall -> Target.App(op.pkg)
        is Op.PushAsset -> Target.File(op.dest)
        is Op.RemoveFile -> Target.File(op.path)
        is Op.VerifyHome, is Op.ArmHomeWatchdog, is Op.ConfirmHome, is Op.KillWatchdog, Op.PressHome, is Op.Mkdir,
        is Op.RestartHome, is Op.StartActivity, is Op.Read, is Op.Settle, Op.Reboot, Op.DebugOff, is Op.CurtainCue -> null
    }

    // The earliest record of every item, chronological by first touch.
    fun earliest(changes: List<ChangeRecord>): List<ChangeRecord> {
        val first = linkedMapOf<Target, ChangeRecord>()
        for (c in changes.sortedWith(CHRONOLOGICAL)) {
            val t = target(c.op) ?: continue
            first.putIfAbsent(t, c)
        }
        return first.values.toList()
    }

    // The earliest record of every item in the order to undo them (rules 1 and 2 above). The caller
    // turns each record into its inverse with OpRenderer.inverse(record.op, record.prior).
    fun undoOrder(changes: List<ChangeRecord>): List<ChangeRecord> {
        val first = earliest(changes)
        return Phase.entries.flatMap { phase -> first.filter { target(it.op)!!.phase == phase }.asReversed() }
    }
}
