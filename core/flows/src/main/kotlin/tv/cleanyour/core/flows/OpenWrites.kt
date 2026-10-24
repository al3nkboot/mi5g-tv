package tv.cleanyour.core.flows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.cleanyour.core.engine.PauseReason
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Tv
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.PlanKind

// R5-W8 O2: a Manage write that paused (the TV stopped answering, a step failed) leaves its run
// open, and the engine refuses every later write on that TV (RUN_OPEN) until the run is carried
// on, undone or ended. TvWriter publishes the open run here instead of a generic "failed"
// snackbar, and the app lays the card over whatever screen asked, the way it shows the debugging
// step (Reach). One card at a time: the person acts on one TV at a time.
data class OpenWrite(
    val tvId: TvId,
    val tvName: String,
    val planId: PlanId,
    val kind: PlanKind,              // SETUP: the card opens the wizard's resume card instead of acting here
    val reason: PauseReason,
    val busy: Boolean = false,       // carry on, undo or stop is running
    val notTaken: Boolean = false,   // the engine did not take the last action (the TV went away again)
)

enum class OpenWriteAction { CARRY_ON, UNDO, STOP_HERE }

class OpenWrites {
    private val state = MutableStateFlow<OpenWrite?>(null)
    val current: StateFlow<OpenWrite?> = state.asStateFlow()

    fun show(tv: Tv, paused: RunState.Paused) {
        state.value = OpenWrite(tv.id, tv.nickname, paused.plan.id, paused.plan.kind, paused.reason)
    }

    fun update(tvId: TvId, change: (OpenWrite) -> OpenWrite) {
        state.value?.takeIf { it.tvId == tvId }?.let { state.value = change(it) }
    }

    // The card's close button. The run stays open; the next write on that TV shows the card again.
    fun dismiss() { state.value = null }

    fun clear(tvId: TvId) { if (state.value?.tvId == tvId) state.value = null }
}
