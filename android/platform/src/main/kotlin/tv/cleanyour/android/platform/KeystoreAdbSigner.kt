package tv.cleanyour.android.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.cleanyour.adb.wire.AdbProtocol
import tv.cleanyour.adb.wire.AdbPublicKeyCodec
import tv.cleanyour.adb.wire.AdbSignature
import tv.cleanyour.adb.wire.AdbSigner
import tv.cleanyour.adb.wire.AuthToken
import tv.cleanyour.adb.wire.KeyBacking
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec

// The phone's ADB identity (SECURITY 4.1, spike S2). In order: an RSA-2048 Keystore key in
// StrongBox, the same in the TEE, and a software key sealed by SecretBox. Every path signs only
// DigestInfo(SHA-1) || a 20-byte token through NONEwithRSA (PKCS#1 v1.5), so there is no general
// signing oracle: the Keystore key has PURPOSE_SIGN alone, DIGEST_NONE and PKCS1 padding, and the
// raw RSA cipher path of the spike is not used. A path is used only after its self-test passes.
class KeystoreAdbSigner private constructor(
    private val context: Context,
    @Volatile private var engine: Engine,
) : AdbSigner {

    private sealed interface Engine {
        val publicKey: RSAPublicKey
        val backing: KeyBacking
        fun sign(token: AuthToken): ByteArray
    }

    private class KeystoreEngine(private val privateKey: PrivateKey, override val publicKey: RSAPublicKey, override val backing: KeyBacking) : Engine {
        override fun sign(token: AuthToken): ByteArray = AdbSignature.sign(privateKey, token, PROVIDER)
    }

    private class SoftwareEngine(private val keyPair: KeyPair) : Engine {
        override val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey
        override val backing: KeyBacking get() = KeyBacking.WRAPPED_SOFTWARE
        override fun sign(token: AuthToken): ByteArray = AdbSignature.sign(keyPair.private, token)
    }

    private val mutex = Mutex()

    override val publicKeyLine: String get() = AdbPublicKeyCodec.line(engine.publicKey)
    override val fingerprint: String get() = AdbPublicKeyCodec.fingerprint(engine.publicKey)
    override val backing: KeyBacking get() = engine.backing

    // Why the Keystore paths were skipped, for the key details screen; empty when StrongBox won.
    val provisioningNotes: List<String> get() = readNotes(context)

    override suspend fun sign(token: AuthToken): ByteArray = withContext(Dispatchers.IO) {
        require(token.bytes.size == AdbProtocol.TOKEN_BYTES) { "adb tokens are 20 bytes" }
        engine.sign(token)
    }

    // "Reset my key": both aliases and the sealed file go; every TV asks again.
    override suspend fun reset() = withContext(Dispatchers.IO) {
        mutex.withLock {
            deleteAll(context)
            engine = provision(context)
        }
    }

    companion object {
        const val ALIAS = "cleanyourtv.adb.rsa.v1"
        private const val PROVIDER = "AndroidKeyStore"
        private const val BACKING_FILE = "adbkey.backing"
        private const val SEALED_FILE = "adbkey.p8.sealed"
        private const val NOTES_FILE = "adbkey.notes"

        suspend fun create(context: Context): KeystoreAdbSigner = withContext(Dispatchers.IO) {
            val app = context.applicationContext
            KeystoreAdbSigner(app, load(app) ?: provision(app))
        }

        private fun dir(context: Context): File = context.noBackupFilesDir

        private fun readNotes(context: Context): List<String> =
            File(dir(context), NOTES_FILE).takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() } ?: emptyList()

        private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

        private fun load(context: Context): Engine? {
            val backing = File(dir(context), BACKING_FILE).takeIf { it.isFile }?.readText()?.trim()?.let { runCatching { KeyBacking.valueOf(it) }.getOrNull() }
                ?: return null
            return try {
                when (backing) {
                    KeyBacking.STRONGBOX, KeyBacking.TEE -> {
                        val ks = keyStore()
                        if (!ks.containsAlias(ALIAS)) return null
                        val engine = KeystoreEngine(ks.getKey(ALIAS, null) as PrivateKey, ks.getCertificate(ALIAS).publicKey as RSAPublicKey, backing)
                        engine.takeIf { selfTest(it) == null }
                    }
                    KeyBacking.WRAPPED_SOFTWARE -> {
                        val sealed = File(dir(context), SEALED_FILE).takeIf { it.isFile }?.readBytes() ?: return null
                        val pkcs8 = SecretBox().open(sealed)
                        val kf = KeyFactory.getInstance("RSA")
                        val priv = kf.generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as RSAPrivateCrtKey
                        val pub = kf.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent)) as RSAPublicKey
                        val engine = SoftwareEngine(KeyPair(pub, priv))
                        engine.takeIf { selfTest(it) == null }
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

        // StrongBox, then TEE, then sealed software; the first path whose self-test passes wins.
        private fun provision(context: Context): Engine {
            deleteAll(context)
            val notes = ArrayList<String>()
            val ks = keyStore()
            for (strongBox in listOf(true, false)) {
                val backing = if (strongBox) KeyBacking.STRONGBOX else KeyBacking.TEE
                try {
                    if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
                    val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER)
                    val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setKeySize(AdbPublicKeyCodec.KEY_BITS)
                        .setDigests(KeyProperties.DIGEST_NONE)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setUserAuthenticationRequired(false)
                    if (strongBox) spec.setIsStrongBoxBacked(true)
                    kpg.initialize(spec.build())
                    kpg.generateKeyPair()
                } catch (e: StrongBoxUnavailableException) {
                    notes += "$backing: StrongBox unavailable"
                    continue
                } catch (e: Exception) {
                    notes += "$backing: generate failed: ${e.javaClass.simpleName}"
                    continue
                }
                val engine = try {
                    KeystoreEngine(ks.getKey(ALIAS, null) as PrivateKey, ks.getCertificate(ALIAS).publicKey as RSAPublicKey, backing)
                } catch (e: Exception) {
                    notes += "$backing: load failed: ${e.javaClass.simpleName}"
                    runCatching { ks.deleteEntry(ALIAS) }
                    continue
                }
                val failure = selfTest(engine)
                if (failure == null) {
                    if (!insideSecureHardware(engine)) notes += "$backing: key is not inside secure hardware on this phone"
                    persist(context, backing, notes)
                    return engine
                }
                notes += "$backing: self-test failed: $failure"
                runCatching { ks.deleteEntry(ALIAS) }
            }
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(AdbPublicKeyCodec.KEY_BITS)
            val keyPair = kpg.generateKeyPair()
            val engine = SoftwareEngine(keyPair)
            selfTest(engine)?.let { throw IllegalStateException("software key self-test failed: $it") }
            File(dir(context), SEALED_FILE).writeBytes(SecretBox().seal(keyPair.private.encoded))
            persist(context, KeyBacking.WRAPPED_SOFTWARE, notes)
            return engine
        }

        private fun persist(context: Context, backing: KeyBacking, notes: List<String>) {
            dir(context).mkdirs()
            File(dir(context), BACKING_FILE).writeText(backing.name)
            File(dir(context), NOTES_FILE).writeText(notes.joinToString("\n"))
        }

        private fun deleteAll(context: Context) {
            val ks = keyStore()
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
            runCatching { SecretBox().delete() }
            File(dir(context), SEALED_FILE).delete()
            File(dir(context), BACKING_FILE).delete()
            File(dir(context), NOTES_FILE).delete()
        }

        // A random token signed the way the handshake signs it verifies under the public key in
        // adbd's own terms (PKCS#1 v1.5 over DigestInfo(SHA-1) || token), the struct round-trips,
        // and the fingerprint matches. Returns the failure, or null when the engine is usable.
        private fun selfTest(engine: Engine): String? {
            val token = ByteArray(AdbProtocol.TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
            val signature = try { engine.sign(AuthToken(token)) } catch (e: Exception) { return "sign: ${e.javaClass.simpleName}" }
            if (signature.size != AdbProtocol.RSA_2048_SIGNATURE_BYTES) return "signature is ${signature.size} bytes"
            if (!AdbSignature.verify(engine.publicKey, token, signature)) return "signature does not verify"
            val decoded = try { AdbPublicKeyCodec.parseLine(AdbPublicKeyCodec.line(engine.publicKey)) } catch (e: Exception) { return "public key struct: ${e.message}" }
            if (decoded.modulus != engine.publicKey.modulus) return "public key struct does not round-trip"
            if (AdbPublicKeyCodec.fingerprint(decoded) != AdbPublicKeyCodec.fingerprint(engine.publicKey)) return "fingerprint mismatch"
            return null
        }

        private fun insideSecureHardware(engine: KeystoreEngine): Boolean = try {
            val ks = keyStore()
            val key = ks.getKey(ALIAS, null) as PrivateKey
            val info = KeyFactory.getInstance(key.algorithm, PROVIDER).getKeySpec(key, KeyInfo::class.java)
            @Suppress("DEPRECATION")
            info.isInsideSecureHardware
        } catch (e: Exception) {
            false
        }
    }
}
