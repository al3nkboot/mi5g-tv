package tv.cleanyour.testing.fixtures

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import tv.cleanyour.core.discovery.Discovery
import tv.cleanyour.core.discovery.DiscoveryEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

// Scripted discovery. emit() pushes events to every collector (and replays the last 64 to new
// ones); activeCollectors lets a test assert that leaving Welcome stopped discovery (F1).
class FakeDiscovery(
    network: DiscoveryEvent.NetworkState = DiscoveryEvent.NetworkState(onWifi = true, subnet = "192.0.2.0/24"),
) : Discovery {
    private val events = MutableSharedFlow<DiscoveryEvent>(replay = 64, extraBufferCapacity = 64)
    private val net = MutableStateFlow(network)
    private val active = AtomicInteger()
    val open5555 = ConcurrentHashMap<String, Boolean>()
    val probes = CopyOnWriteArrayList<String>()
    val activeCollectors: Int get() = active.get()

    fun emit(event: DiscoveryEvent) {
        check(events.tryEmit(event)) { "discovery buffer full" }
        if (event is DiscoveryEvent.NetworkState) net.value = event
    }

    override fun discover(): Flow<DiscoveryEvent> = flow {
        active.incrementAndGet()
        try { emitAll(events) } finally { active.decrementAndGet() }
    }

    override suspend fun probe5555(host: String, timeoutMs: Long): Boolean {
        probes += host
        return open5555[host] ?: false
    }

    override fun network(): StateFlow<DiscoveryEvent.NetworkState> = net.asStateFlow()
}
