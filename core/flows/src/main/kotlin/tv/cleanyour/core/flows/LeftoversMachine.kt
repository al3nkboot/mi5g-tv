package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.Plan
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult

// M5 "Check for leftovers" (D22, F6): a fresh read of the TV against what this phone switched off;
// "3 things came back after an update. Switch them off again?" or "Nothing came back."
sealed interface LeftoversState {
    data object Checking : LeftoversState
    data class Found(val names: List<String>, val plan: Plan) : LeftoversState
    data object Nothing : LeftoversState
    data class Working(val percent: Int) : LeftoversState
    data class Done(val count: Int) : LeftoversState
    data object Failed : LeftoversState
}

sealed interface LeftoversIntent {
    data object SwitchOff : LeftoversIntent
    data object NotNow : LeftoversIntent
    data object CheckAgain : LeftoversIntent
}

class LeftoversMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
) : Machine<LeftoversState, LeftoversIntent, ManageEffect>(LeftoversState.Checking, scope) {

    private data class Show(val s: LeftoversState)

    init { scope.launch { check() } }

    override fun reduce(state: LeftoversState, event: Any): LeftoversState = when (event) {
        is Show -> event.s
        LeftoversIntent.CheckAgain -> LeftoversState.Checking
        else -> state
    }

    override fun handle(event: Any, before: LeftoversState, after: LeftoversState) {
        when (event) {
            LeftoversIntent.SwitchOff -> if (before is LeftoversState.Found) scope.launch { switchOff(before) }
            LeftoversIntent.NotNow -> effect(ManageEffect.Navigate("back"))
            LeftoversIntent.CheckAgain -> scope.launch { check() }
            else -> {}
        }
    }

    private suspend fun check() {
        val tv = d.tv(tvId) ?: return send(Show(LeftoversState.Failed))
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session ?: return send(Show(LeftoversState.Failed))
        val r = attempt {
            val snapshot = d.runner.freshSnapshot(session)
            val expected = d.ledger.switchedOffByUs(tvId).toSet()
            d.runner.plan(tv, PlanRequest.Leftovers(expected), snapshot)
        }.getOrNull()
        val plan = (r as? PlanResult.Ok)?.plan ?: return send(Show(LeftoversState.Failed))
        val back = plan.steps.mapNotNull { (it.op as? Op.Disable)?.pkg }
        send(Show(if (back.isEmpty()) LeftoversState.Nothing else LeftoversState.Found(back.map { d.nameOf(it) }, plan)))
    }

    private suspend fun switchOff(found: LeftoversState.Found) {
        send(Show(LeftoversState.Working(0)))
        val expected = d.ledger.switchedOffByUs(tvId).toSet()
        when (val r = TvWriter(d).write(tvId, PlanRequest.Leftovers(expected)) { b -> if (b is Busy.Working) send(Show(LeftoversState.Working(b.percent))) }) {
            // Only what really went off is counted (a switch-off kept for the TV's inputs is skipped, RF-LAUNCHER-250 F1).
            is WriteResult.Done -> send(Show(LeftoversState.Done(found.names.size - r.finished.skipped.count { it.op is Op.Disable })))
            WriteResult.GateCancelled, WriteResult.NoTv, is WriteResult.Paused -> send(Show(found))   // Paused: the open-run card shows (O2)
            else -> send(Show(LeftoversState.Failed))
        }
    }
}
