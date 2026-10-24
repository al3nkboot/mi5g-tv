package tv.cleanyour.android.platform

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tv.cleanyour.core.discovery.Discovery
import tv.cleanyour.core.discovery.DiscoveryEvent
import tv.cleanyour.core.discovery.DiscoverySource
import tv.cleanyour.core.discovery.FoundTv
import tv.cleanyour.core.discovery.ServiceRef

// Discovery over NsdManager (F1): the Android TV remote, Cast and adb services, resolves one at a
// time (NsdManager refuses concurrent resolves on older releases), and a direct TCP probe of port
// 5555 on every host found. NsdManager runs in the system process and needs no runtime permission
// (D16); no multicast lock is taken here. No sweep: only hosts that announced themselves. The
// phone's own addresses are never a TV (its own adbd may announce itself, as on an emulator).
class NsdDiscovery(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : Discovery {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val net = NetworkState(app)

    override fun network(): StateFlow<DiscoveryEvent.NetworkState> = net.state

    override fun discover(): Flow<DiscoveryEvent> = callbackFlow {
        val byHost = LinkedHashMap<String, FoundTv>()
        val hostOf = HashMap<String, String>()            // "type/name" to host
        val resolveQueue = Channel<Triple<NsdServiceInfo, DiscoverySource, Boolean>>(Channel.UNLIMITED)   // (service, source, lost)
        val listeners = ArrayList<NsdManager.DiscoveryListener>()
        trySend(net.state.value)

        fun publish(tv: FoundTv, isNew: Boolean) { trySend(if (isNew) DiscoveryEvent.Found(tv) else DiscoveryEvent.Updated(tv)) }

        launch {
            for ((info, source, lost) in resolveQueue) {
                if (lost) {
                    val gone = hostOf.remove("${info.serviceType}/${info.serviceName}") ?: continue
                    if (hostOf.values.none { it == gone }) { byHost.remove(gone); trySend(DiscoveryEvent.Lost(gone)) }
                    continue
                }
                val resolved = withTimeoutOrNull(5_000) { resolve(info) } ?: continue
                val host = ipv4Of(resolved, OwnAddresses.current() + net.ownAddresses()) ?: continue
                hostOf["${info.serviceType}/${info.serviceName}"] = host
                val attrs = runCatching { resolved.attributes }.getOrNull().orEmpty()
                fun attr(k: String) = attrs[k]?.decodeToString()?.takeIf { it.isNotBlank() }
                val old = byHost[host]
                val ref = ServiceRef(resolved.serviceName, host, resolved.port)
                val tv = FoundTv(
                    host = host,
                    friendlyName = attr("fn") ?: old?.friendlyName ?: resolved.serviceName.takeIf { source == DiscoverySource.ANDROID_TV_REMOTE },
                    model = attr("md") ?: old?.model,
                    sources = (old?.sources.orEmpty()) + source,
                    port5555Open = old?.port5555Open,
                    pairingService = if (source == DiscoverySource.ADB_TLS_PAIRING) ref else old?.pairingService,
                    connectService = if (source == DiscoverySource.ADB_TLS_CONNECT) ref else old?.connectService,
                    lastSeenMs = now(),
                )
                byHost[host] = tv
                publish(tv, old == null)
                if (old?.port5555Open == null) {
                    val open = probe5555(host)
                    byHost[host]?.let { cur -> byHost[host] = cur.copy(port5555Open = open).also { publish(it, false) } }
                }
            }
        }

        for ((type, source) in SERVICES) {
            val l = object : NsdManager.DiscoveryListener {
                // NsdManager calls back on its own thread: hand everything to the one collector coroutine.
                override fun onServiceFound(info: NsdServiceInfo) { resolveQueue.trySend(Triple(info, source, false)) }
                override fun onServiceLost(info: NsdServiceInfo) { resolveQueue.trySend(Triple(info, source, true)) }
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l) }.onSuccess { listeners += l }
        }
        val netJob = launch { net.state.collect { trySend(it) } }

        awaitClose {
            netJob.cancel()
            resolveQueue.close()
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { cont ->
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { if (cont.isActive) cont.resume(null) }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) { if (cont.isActive) cont.resume(serviceInfo) }
        }
        runCatching { nsd.resolveService(info, listener) }.onFailure { if (cont.isActive) cont.resume(null) }
        cont.invokeOnCancellation { if (Build.VERSION.SDK_INT >= 34) runCatching { nsd.stopServiceResolution(listener) } }
    }

    // The first IPv4 address of the service that is not the phone itself.
    @Suppress("DEPRECATION")
    private fun ipv4Of(info: NsdServiceInfo, own: Set<String>): String? {
        val addresses = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        return addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.firstOrNull { !OwnAddresses.isOwn(it, own) }
    }

    // A literal IPv4 address only: never a name lookup, so a probe cannot leave the LAN by DNS.
    override suspend fun probe5555(host: String, timeoutMs: Long): Boolean {
        if (!IPV4.matches(host)) return false
        return withContext(Dispatchers.IO) {
            runCatching { Socket().use { it.connect(InetSocketAddress(host, 5555), timeoutMs.toInt()); true } }.getOrDefault(false)
        }
    }

    companion object {
        private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        val SERVICES = listOf(
            "_androidtvremote2._tcp" to DiscoverySource.ANDROID_TV_REMOTE,
            "_googlecast._tcp" to DiscoverySource.GOOGLE_CAST,
            "_adb._tcp" to DiscoverySource.ADB_MDNS,
            "_adb-tls-pairing._tcp" to DiscoverySource.ADB_TLS_PAIRING,
            "_adb-tls-connect._tcp" to DiscoverySource.ADB_TLS_CONNECT,
        )
    }
}

// Finding 1 of the emulator run: every IPv4 address of the phone's own interfaces (Wi-Fi, the
// emulator's 10.0.2.x links, loopback, any VPN or hotspot link), read fresh on each resolve.
object OwnAddresses {
    fun current(): Set<String> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .flatMap { runCatching { it.inetAddresses.toList() }.getOrDefault(emptyList()) }
            .filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.toSet()
    }.getOrDefault(emptySet())

    // Loopback and the unspecified address are always the phone, listed or not.
    fun isOwn(host: String, own: Set<String>): Boolean = host in own || host.startsWith("127.") || host == "0.0.0.0"
}

// Network state for D19: Wi-Fi (or Ethernet) or not, and the link's IPv4 subnet ("192.168.1.0/24"),
// read from ConnectivityManager with ACCESS_NETWORK_STATE only (declared by :app). Never the SSID.
@SuppressLint("MissingPermission")
class NetworkState(context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mutable = MutableStateFlow(DiscoveryEvent.NetworkState(onWifi = false, subnet = null))
    val state: StateFlow<DiscoveryEvent.NetworkState> = mutable.asStateFlow()
    private var caps: NetworkCapabilities? = null
    private var link: LinkProperties? = null

    init {
        runCatching {
            cm.activeNetwork?.let { n -> caps = cm.getNetworkCapabilities(n); link = cm.getLinkProperties(n); publish() }
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) { caps = c; publish() }
                override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) { link = lp; publish() }
                override fun onLost(network: Network) { caps = null; link = null; publish() }
            })
        }
    }

    // The default network's own IPv4 addresses, as ConnectivityManager reports them.
    fun ownAddresses(): Set<String> = link?.linkAddresses.orEmpty().map { it.address }.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.toSet()

    private fun publish() {
        val c = caps
        val local = c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        mutable.value = DiscoveryEvent.NetworkState(local, if (local) subnetOf(link) else null)
    }

    companion object {
        fun subnetOf(lp: LinkProperties?): String? {
            val la = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address } ?: return null
            return subnet(la.address.address, la.prefixLength)
        }

        fun subnet(addr: ByteArray, prefix: Int): String {
            val ip = addr.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val n = ip and mask
            return "${(n shr 24) and 255}.${(n shr 16) and 255}.${(n shr 8) and 255}.${n and 255}/$prefix"
        }
    }
}
