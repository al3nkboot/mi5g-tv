package tv.cleanyour.app

import kotlinx.coroutines.flow.MutableStateFlow
import tv.cleanyour.core.flows.ConnectLink
import tv.cleanyour.core.flows.ConnectTarget

/**
 * 2.4 M3 (backlog 15): a TV picked from the Mi5G launcher's "Connect a phone" QR. [offered] waits for the
 * person's yes in AppRoot; [confirmed] is then taken by a fresh setup screen, which types the address in.
 */
object PendingConnect {
    val offered = MutableStateFlow<ConnectTarget?>(null)
    val confirmed = MutableStateFlow<ConnectTarget?>(null)

    /** The link's TV, offered once; anything else in the intent is ignored. */
    fun offer(data: String?): Boolean {
        val target = ConnectLink.parse(data) ?: return false
        offered.value = target
        return true
    }
}

/** The back stack once the person said yes: one fresh setup screen on top, where the address is entered. */
internal fun applyConnect(stack: MutableList<androidx.navigation3.runtime.NavKey>) {
    stack.removeAll { it is Dest.Setup && it.tvId == null && it.planId == null }
    stack.add(Dest.Setup())
}
