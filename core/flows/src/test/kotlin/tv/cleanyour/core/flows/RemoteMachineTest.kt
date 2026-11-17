package tv.cleanyour.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.ops.ShellTokens

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteMachineTest {
    @Test fun sendsPhysicalKeyAndPersistsFavorites() = runTest {
        val h = Harness()
        val tv = h.knownTv()
        val favorites = InMemoryRemoteFavoritesStore()
        val machine = RemoteMachine(h.manageDeps(), backgroundScope, tv.id, favorites)
        runCurrent()

        machine.dispatch(RemoteIntent.Press(RemoteButton.LEFT))
        runCurrent()
        assertEquals(RemoteConnection.Connected, machine.state.value.connection)
        assertEquals(listOf("input", "keyevent", "21"), ShellTokens.tokenize(h.session.execLines.last()))

        machine.dispatch(RemoteIntent.ToggleFavorite("com.netflix.ninja"))
        runCurrent()
        assertTrue("com.netflix.ninja" in machine.state.value.favorites)
        assertEquals(setOf("com.netflix.ninja"), favorites.load(tv.id))
    }

    // 2.3: Connect on the remote opens the re-pair route, not "setup/<id>" (the clean-up screen).
    @Test fun connectOpensThePairRoute() = runTest {
        val h = Harness()
        val tv = h.knownTv()
        val machine = RemoteMachine(h.manageDeps(), backgroundScope, tv.id, InMemoryRemoteFavoritesStore())
        runCurrent()
        val seen = mutableListOf<ManageEffect>()
        backgroundScope.launch { machine.effects.collect { seen += it } }
        runCurrent()
        machine.dispatch(RemoteIntent.OpenPairing)
        runCurrent()
        assertEquals(ManageEffect.Navigate("pair/${tv.id.value}"), seen.last())
    }

    // 2.4: the Play Store button opens the store on the TV through its APP_MARKET category.
    @Test fun playStoreButtonOpensTheStore() = runTest {
        val h = Harness()
        val tv = h.knownTv()
        val machine = RemoteMachine(h.manageDeps(), backgroundScope, tv.id, InMemoryRemoteFavoritesStore())
        runCurrent()
        machine.dispatch(RemoteIntent.OpenPlayStore)
        runCurrent()
        assertTrue(h.session.execLines.any { ShellTokens.tokenize(it).containsAll(listOf("am", "start", "android.intent.category.APP_MARKET")) })
    }

    // 2.4 M3 (backlog 20): every other saved TV is offered, and picking one opens its remote in place.
    @Test fun remoteSwitchesBetweenSavedTvs() = runTest {
        val h = Harness()
        val tv = h.knownTv()
        val other = tv.copy(id = TvId("fedcba9876543210"), nickname = "Bedroom")
        h.tvs.upsert(other)
        val machine = RemoteMachine(h.manageDeps(), backgroundScope, tv.id, InMemoryRemoteFavoritesStore())
        runCurrent()
        assertEquals(listOf(RemoteTvChoice("fedcba9876543210", "Bedroom")), machine.state.value.otherTvs)
        val seen = mutableListOf<ManageEffect>()
        backgroundScope.launch { machine.effects.collect { seen += it } }
        runCurrent()
        machine.dispatch(RemoteIntent.SwitchTv("fedcba9876543210"))
        runCurrent()
        assertEquals(ManageEffect.Navigate("remoteswitch/fedcba9876543210"), seen.last())
        val count = seen.size
        machine.dispatch(RemoteIntent.SwitchTv("not-saved"))
        machine.dispatch(RemoteIntent.SwitchTv(tv.id.value))
        runCurrent()
        assertEquals(count, seen.size)
    }

    // 2.4 M3 (backlog 4): the TV's All apps grid with search opens from the remote.
    @Test fun allAppsButtonOpensTheLaunchersSearchGrid() = runTest {
        val h = Harness()
        val tv = h.knownTv()
        val machine = RemoteMachine(h.manageDeps(), backgroundScope, tv.id, InMemoryRemoteFavoritesStore())
        runCurrent()
        machine.dispatch(RemoteIntent.OpenTvAllApps)
        runCurrent()
        assertEquals(listOf("am", "start", "-n", "com.gathin.tvhome/.AllAppsActivity"), ShellTokens.tokenize(h.session.execLines.last()))
    }
}
