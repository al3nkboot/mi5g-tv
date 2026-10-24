package tv.cleanyour.core.backup

import tv.cleanyour.core.model.ChangeRecord
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Tv
import tv.cleanyour.core.spec.FactsTable

// The restore note for the family (UX 4.10 layer 1, D47): plain words, one page, shared through the
// share sheet to print or send. It names the TV and the date, gives the TV's Settings path in
// English and the three steps to switch an app back on, names the old home screen when we replaced
// it, and lists what was switched off by friendly name, one per line. More than 40 names collapse
// to "and N more", pointing at the list on the TV. dateText comes from the caller, spelled out in
// the phone's locale (UX 7.2).
class PlainRestoreNote : RestoreNote {

    override fun text(tv: Tv, changes: List<ChangeRecord>, facts: FactsTable, stockHomeName: String, dateText: String): String {
        val sum = ChangeSummary.of(changes)
        val tvName = TextSafety.oneLine(tv.nickname, 40).ifEmpty { TextSafety.oneLine(tv.facts.model, 40) }
        val room = tv.room?.let { TextSafety.oneLine(it, 30) }?.takeIf { it.isNotEmpty() }
        val names = sum.switchedOff.map { friendly(it, facts) }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        val replacedHome = sum.homeNow != null && sum.homeNow != sum.homeBefore
        val home = TextSafety.oneLine(stockHomeName, 40).ifEmpty { "the TV's own home screen" }
        val addedCards = sum.installed.any { it != LAUNCHER }

        return buildString {
            appendLine("Restore note for $tvName" + (room?.let { " ($it)" } ?: ""))
            appendLine()
            appendLine("This TV was set up on ${TextSafety.oneLine(dateText, 40)} with Clean Your TV.")
            when (names.size) {
                0 -> appendLine("Nothing on it is switched off.")
                1 -> appendLine("One app was switched off. Nothing was deleted, and it can be switched back on with the remote.")
                else -> appendLine("${names.size} apps were switched off. Nothing was deleted, and each one can be switched back on with the remote.")
            }
            if (names.isNotEmpty()) {
                appendLine()
                appendLine("To switch an app back on:")
                appendLine("1. On the TV, open Settings > Apps > See all apps > Show system apps.")
                appendLine("2. Pick the app from the list below. It is marked Disabled.")
                appendLine("3. Choose Enable.")
            }
            if (replacedHome) {
                appendLine()
                appendLine("To get the old home screen back, enable \"$home\" first, then press Home on the remote.")
            }
            if (addedCards) {
                appendLine()
                appendLine("If the TV asks about a card on the home screen, Keep hidden is always a safe answer.")
            }
            if (names.isNotEmpty()) {
                appendLine()
                appendLine("Switched off:")
                names.take(MAX_NAMES).forEach { appendLine("- $it") }
                if (names.size > MAX_NAMES) {
                    // this line already points at the list on the TV; the note stays under 60 lines
                    appendLine("- and ${names.size - MAX_NAMES} more, listed on the TV under Files > cleanyourtv")
                } else {
                    appendLine()
                    appendLine("The same list is on the TV under Files > cleanyourtv.")
                }
            }
            appendLine()
            append("The Clean Your TV app on the phone can also put everything back: Restore point > Undo everything.")
        }
    }

    private fun friendly(pkg: PackageName, facts: FactsTable): String =
        facts.lookup(pkg)?.friendlyName?.let { TextSafety.oneLine(it, 60) }?.takeIf { it.isNotEmpty() } ?: pkg.value

    companion object {
        const val MAX_NAMES = 40
        private val LAUNCHER = PackageName("com.gathin.tvhome")
    }
}
