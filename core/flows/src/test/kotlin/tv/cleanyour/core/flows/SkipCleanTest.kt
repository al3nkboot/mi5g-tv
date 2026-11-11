package tv.cleanyour.core.flows

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.discovery.DiscoveryEvent
import tv.cleanyour.core.model.Level
import tv.cleanyour.core.model.WizardDraft
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.testing.fixtures.FakeEngine
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// 2.2.1: "Skip cleanup" leaves the wizard for Manage, remembers it per TV, and the clean stays
// reachable from Manage ("setup/<id>", SetupStart.Continue).
@OptIn(ExperimentalCoroutinesApi::class)
class SkipCleanTest {

    private suspend fun kotlinx.coroutines.test.TestScope.toFound(h: Harness, m: SetupMachine) {
        h.discovery.emit(DiscoveryEvent.Found(miTvFound)); settle()
        m.dispatch(SetupIntent.Start); settle()
        h.discovery.open5555[HOST] = true
        settle(2_100); settle(700)
    }

    @Test fun skipOnFoundOpensManageConnectedAndRemembersIt() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        val m = h.setup(backgroundScope)
        toFound(h, m)
        assertIs<SetupState.Found>(m.state.value)
        assertTrue(m.extras.value.canSkipClean)
        val startsBefore = h.engine.calls.filterIsInstance<FakeEngine.Call.Start>().size
        m.effects.test {
            m.dispatch(SetupIntent.SkipClean); settle()
            assertEquals(SetupEffect.Navigate("manage/${Samples.miTvId().value}"), drain().last())
        }
        val tv = h.tvs.tvs.value.single()
        assertEquals(true, tv.wizard?.cleanSkipped)
        // Nothing ran on the TV and debugging stays on, so the remote and tools work at once.
        assertEquals(startsBefore, h.engine.calls.filterIsInstance<FakeEngine.Call.Start>().size)
        assertFalse(h.planner.requests.any { it == PlanRequest.DebugOff })
        assertEquals(StartDestination.Manage(tv.id), resolveStart(listOf(tv), h.journal))
    }

    @Test fun aSkippedTvIsNotForcedIntoTheWizardAgain() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val draft = WizardDraft(3, Level.SIMPLE, emptySet(), emptySet(), null, null, false, null, 0)
        val skipped = Samples.tv().copy(wizard = draft.copy(cleanSkipped = true))
        assertEquals(StartDestination.Manage(skipped.id), resolveStart(listOf(skipped), h.journal))
        val fresh = Samples.tv().copy(wizard = draft)
        assertEquals(StartDestination.ContinueSetup(fresh.id), resolveStart(listOf(fresh), h.journal))
    }

    @Test fun aFreshConnectToASkippedTvGoesStraightToManage() = runTest(timeout = 30.seconds) {
        val h = Harness()
        h.sessions.allowPromptFor[HOST] = "AB:CD:7F:2A"
        h.tvs.upsert(Samples.tv().copy(wizard = WizardDraft(3, Level.SIMPLE, emptySet(), emptySet(), null, null, false, null, 0, cleanSkipped = true)))
        val m = h.setup(backgroundScope)
        m.effects.test {
            toFound(h, m)
            assertEquals(SetupEffect.Navigate("manage/${Samples.miTvId().value}"), drain().last())
        }
        assertFalse(m.state.value is SetupState.Found)
    }

    @Test fun cleaningFromManageStillShowsTheFoundScreen() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = Samples.tv().copy(wizard = WizardDraft(3, Level.SIMPLE, emptySet(), emptySet(), null, null, false, null, 0, cleanSkipped = true))
        h.tvs.upsert(tv)
        h.discovery.open5555[HOST] = true
        val m = h.setup(backgroundScope, SetupStart.Continue(tv.id))
        settle(); settle(700)
        assertIs<SetupState.Found>(m.state.value)
        assertTrue(m.extras.value.canSkipClean)
    }

    @Test fun theSkipIsKeptOnDisk() {
        val tv = Samples.tv().copy(wizard = WizardDraft(3, Level.SIMPLE, emptySet(), emptySet(), null, null, false, null, 0, cleanSkipped = true))
        assertEquals(tv, TvJson.decode(TvJson.encode(tv)))
        assertEquals(true, TvJson.decode(TvJson.encode(tv)).wizard?.cleanSkipped)
    }

    // 2.3: the remote's re-pair (SetupStart.Repair) connects and returns to Manage, never the
    // clean-up screen, also for a TV whose clean-up was never skipped.
    @Test fun theRemotesRePairReturnsToManageNotTheCleanUp() = runTest(timeout = 30.seconds) {
        val h = Harness()
        val tv = Samples.tv().copy(wizard = WizardDraft(3, Level.SIMPLE, emptySet(), emptySet(), null, null, false, null, 0))
        h.tvs.upsert(tv)
        h.discovery.open5555[HOST] = true
        val m = h.setup(backgroundScope, SetupStart.Repair(tv.id))
        m.effects.test {
            settle(); settle(700)
            assertEquals(SetupEffect.Navigate("manage/${tv.id.value}"), drain().last())
        }
        assertFalse(m.state.value is SetupState.Found)
        assertFalse(h.planner.requests.any { it == PlanRequest.DebugOff })
    }
}
