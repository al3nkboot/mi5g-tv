package tv.cleanyour.core.flows

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
