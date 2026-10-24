package tv.cleanyour.testing.fixtures

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import tv.cleanyour.adb.wire.AdbSigner
import tv.cleanyour.adb.wire.AdbSocket
import tv.cleanyour.adb.wire.AdbStream
import tv.cleanyour.adb.wire.AuthToken
import tv.cleanyour.adb.wire.KeyBacking
import tv.cleanyour.adb.wire.SocketFactory
import tv.cleanyour.adb.wire.TlsUpgrader
import tv.cleanyour.core.model.Endpoint
import tv.cleanyour.core.model.Sha256
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.ConnectException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

// The adbd public key line and fingerprint, computed the way adbd reads them: the RSAPublicKey
// struct (word count, n0inv, modulus and R^2 mod n as little-endian 32-bit words, exponent),
// base64, a space and the comment (D20). Fingerprint: MD5 of the struct, upper-case hex with colons.
// Reference code for fakes; W2's codec in :adb:wire is authoritative.
object AdbKeyFormat {
    const val COMMENT = "cleanyourtv"
    // DigestInfo prefix for SHA-1 (the form adbd's RSA_verify expects in front of the 20-byte token).
    val SHA1_DIGEST_INFO: ByteArray = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

    fun struct(key: RSAPublicKey): ByteArray {
        val words = 2048 / 32
        require(key.modulus.bitLength() <= 2048) { "adbd keys are RSA-2048" }
        val two32 = BigInteger.ONE.shiftLeft(32)
        val n = key.modulus
        val n0inv = two32.subtract(n.mod(two32).modInverse(two32)).mod(two32).toLong()
        val rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.TWO, n)
        val buf = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(words)
        buf.putInt(n0inv.toInt())
        for (i in 0 until words) buf.putInt(n.shiftRight(32 * i).mod(two32).toLong().toInt())
        for (i in 0 until words) buf.putInt(rr.shiftRight(32 * i).mod(two32).toLong().toInt())
        buf.putInt(key.publicExponent.toInt())
        return buf.array()
    }

    fun publicKeyLine(key: RSAPublicKey): String = Base64.getEncoder().encodeToString(struct(key)) + " " + COMMENT

    fun fingerprint(key: RSAPublicKey): String =
        MessageDigest.getInstance("MD5").digest(struct(key)).joinToString(":") { "%02X".format(it) }

    // What adbd does: PKCS#1 v1.5 verify of DigestInfo(SHA1) || token.
    fun verify(key: RSAPublicKey, token: ByteArray, signature: ByteArray): Boolean =
        Signature.getInstance("NONEwithRSA").run { initVerify(key); update(SHA1_DIGEST_INFO + token); verify(signature) }
}

// A software RSA-2048 signer with the real signature format, so a fake adbd can verify it.
class FakeAdbSigner(
    keyPair: KeyPair = sharedKeyPair,
    override val backing: KeyBacking = KeyBacking.WRAPPED_SOFTWARE,
) : AdbSigner {
    @Volatile var keyPair: KeyPair = keyPair
        private set
    val signedTokens = CopyOnWriteArrayList<ByteArray>()
    @Volatile var resets = 0
        private set

    private val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey
    override val publicKeyLine: String get() = AdbKeyFormat.publicKeyLine(publicKey)
    override val fingerprint: String get() = AdbKeyFormat.fingerprint(publicKey)

    override suspend fun sign(token: AuthToken): ByteArray {
        require(token.bytes.size == 20) { "adb tokens are 20 bytes" }   // re-check, as real signers must
        signedTokens += token.bytes.copyOf()
        return Signature.getInstance("NONEwithRSA").run {
            initSign(keyPair.private); update(AdbKeyFormat.SHA1_DIGEST_INFO + token.bytes); sign()
        }
    }

    override suspend fun reset() { resets++; keyPair = generate() }

    companion object {
        // Key generation costs a few hundred ms, so tests share one unless they need their own.
        val sharedKeyPair: KeyPair by lazy { generate() }
        fun generate(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}

// One direction of an in-memory byte pipe.
private class Pipe {
    val chunks = Channel<ByteArray>(Channel.UNLIMITED)
    var current: ByteArray? = null
    var pos = 0
}

// In-memory AdbSocket. pair() gives two connected ends (what one writes the other reads), which is
// how an in-process fake adbd plugs in; scripted() gives a socket that reads fixed bytes and then
// end of stream. Everything written is also kept in `sent`.
class FakeAdbSocket private constructor(private val inbound: Pipe, private val outbound: Pipe) : AdbSocket {
    private val sentBytes = ByteArrayOutputStream()
    val sent: ByteArray get() = synchronized(sentBytes) { sentBytes.toByteArray() }
    @Volatile var closed = false
        private set

    override suspend fun read(buf: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var cur = inbound.current
        if (cur == null || inbound.pos >= cur.size) {
            cur = try { inbound.chunks.receive() } catch (e: ClosedReceiveChannelException) { return -1 }
            inbound.current = cur
            inbound.pos = 0
        }
        val n = minOf(len, cur.size - inbound.pos)
        System.arraycopy(cur, inbound.pos, buf, off, n)
        inbound.pos += n
        return n
    }

    override suspend fun write(buf: ByteArray, off: Int, len: Int) {
        check(!closed) { "socket closed" }
        val copy = buf.copyOfRange(off, off + len)
        synchronized(sentBytes) { sentBytes.write(copy) }
        outbound.chunks.trySend(copy)
    }

    override suspend fun flush() {}

    override fun close() {
        closed = true
        outbound.chunks.close()
        inbound.chunks.close()
    }

    companion object {
        fun pair(): Pair<FakeAdbSocket, FakeAdbSocket> {
            val a = Pipe()
            val b = Pipe()
            return FakeAdbSocket(a, b) to FakeAdbSocket(b, a)
        }

        fun scripted(incoming: ByteArray): FakeAdbSocket {
            val inbound = Pipe().apply { if (incoming.isNotEmpty()) chunks.trySend(incoming); chunks.close() }
            return FakeAdbSocket(inbound, Pipe())
        }
    }
}

// Connects through a lambda (default: connection refused) and logs every attempt.
class FakeSocketFactory(
    private val connector: suspend (Endpoint) -> AdbSocket = { throw ConnectException("fake: connection refused") },
) : SocketFactory {
    val attempts = CopyOnWriteArrayList<Endpoint>()
    override suspend fun connect(endpoint: Endpoint, timeoutMs: Long): AdbSocket {
        attempts += endpoint
        return connector(endpoint)
    }
}

// 0.1 ships no TLS (D9): the default refuses like the NotSupported implementation; passThrough
// returns the socket unchanged for tests of the upgrade hook.
class FakeTlsUpgrader(private val passThrough: Boolean = false) : TlsUpgrader {
    val upgrades = CopyOnWriteArrayList<Sha256?>()
    override suspend fun upgrade(socket: AdbSocket, pin: Sha256?): AdbSocket {
        upgrades += pin
        if (!passThrough) throw UnsupportedOperationException("TLS is 0.2")
        return socket
    }
}

// Reads the given chunks then null (end of stream); writes are kept.
class FakeAdbStream(chunks: List<ByteArray> = emptyList()) : AdbStream {
    private val pending = ArrayDeque(chunks)
    val written = CopyOnWriteArrayList<ByteArray>()
    @Volatile var closed = false
        private set
    override suspend fun read(): ByteArray? = synchronized(pending) { pending.removeFirstOrNull() }
    override suspend fun write(bytes: ByteArray) { check(!closed); written += bytes.copyOf() }
    override fun close() { closed = true }
}
