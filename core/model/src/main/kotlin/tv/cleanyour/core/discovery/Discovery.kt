// FROZEN INTERFACE (PRODUCT-SPEC section 5.4, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W1 (interface; W8 implements in :android:platform).

package tv.cleanyour.core.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class DiscoverySource { ANDROID_TV_REMOTE, GOOGLE_CAST, ADB_MDNS, ADB_TLS_CONNECT, ADB_TLS_PAIRING, MANUAL }
data class ServiceRef(val name: String, val host: String, val port: Int)
data class FoundTv(val host: String, val friendlyName: String?, val model: String?, val sources: Set<DiscoverySource>,
    val port5555Open: Boolean?, val pairingService: ServiceRef?, val connectService: ServiceRef?, val lastSeenMs: Long)
sealed interface DiscoveryEvent { data class Found(val tv: FoundTv) : DiscoveryEvent; data class Updated(val tv: FoundTv) : DiscoveryEvent
    data class Lost(val host: String) : DiscoveryEvent; data class NetworkState(val onWifi: Boolean, val subnet: String?) : DiscoveryEvent }
interface Discovery {
    fun discover(): Flow<DiscoveryEvent>              // cold; collection starts NSD and the 5555 probes, cancellation stops them
    suspend fun probe5555(host: String, timeoutMs: Long = 1_500): Boolean
    fun network(): StateFlow<DiscoveryEvent.NetworkState> }
