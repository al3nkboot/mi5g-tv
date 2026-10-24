package tv.cleanyour.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import tv.cleanyour.adb.wire.AdbSigner
import tv.cleanyour.adb.wire.AuthToken
import tv.cleanyour.adb.wire.KeyBacking
import tv.cleanyour.android.platform.KeystoreAdbSigner
import tv.cleanyour.core.flows.KeyInfo

// KeystoreAdbSigner.create is suspend and can take seconds on first launch (a StrongBox RSA-2048
// key), so the graph cannot build it on the main thread in Application.onCreate. This signer
// stands in for it: the key is made in the background (warmUp at start, or the first use), and
// sign() waits for it. A failed creation is retried on the next use.
//
// adb.wire reads publicKeyLine and fingerprint only after it has awaited sign() (the second AUTH
// token), so the blocking fallback in those getters is never reached on the connect path.
class DeferredAdbSigner(
    private val scope: CoroutineScope,
    private val create: suspend () -> AdbSigner,
) : AdbSigner {
    @Volatile private var deferred: Deferred<AdbSigner> = newDeferred()

    private fun newDeferred(): Deferred<AdbSigner> = scope.async(start = CoroutineStart.LAZY) { create() }

    private fun current(): Deferred<AdbSigner> = synchronized(this) {
        val d = deferred
        if (d.isCompleted && failed(d)) newDeferred().also { deferred = it } else d
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun failed(d: Deferred<AdbSigner>): Boolean = d.getCompletionExceptionOrNull() != null

    // Starts making (or loading) the key now, without waiting for it.
    fun warmUp() { current().start() }

    suspend fun get(): AdbSigner = current().await()

    // The signer when it is ready, without waiting; null while it is being made or after a failure.
    @OptIn(ExperimentalCoroutinesApi::class)
    fun readyOrNull(): AdbSigner? = deferred.takeIf { it.isCompleted && !failed(it) }?.getCompleted()

    private fun blocking(): AdbSigner = readyOrNull() ?: runBlocking { get() }

    override val publicKeyLine: String get() = blocking().publicKeyLine
    override val fingerprint: String get() = blocking().fingerprint
    override val backing: KeyBacking get() = blocking().backing
    override suspend fun sign(token: AuthToken): ByteArray = get().sign(token)
    override suspend fun reset() = get().reset()

    // Why the Keystore paths were skipped, for App settings > About (empty when StrongBox won).
    val provisioningNotes: List<String> get() = (readyOrNull() as? KeystoreAdbSigner)?.provisioningNotes.orEmpty()
}

// M8 About and "Reset my key" over the deferred signer. While the key is still being made the
// fingerprint reads empty rather than blocking the main thread.
class SignerKeyInfo(private val signer: DeferredAdbSigner) : KeyInfo {
    override val fingerprint: String get() = signer.readyOrNull()?.fingerprint.orEmpty()
    override val backing: String get() = signer.readyOrNull()?.backing?.name.orEmpty()
    override suspend fun reset() = signer.reset()
}
