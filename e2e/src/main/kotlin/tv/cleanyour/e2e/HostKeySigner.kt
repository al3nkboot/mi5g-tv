package tv.cleanyour.e2e

import tv.cleanyour.adb.wire.AdbSigner
import tv.cleanyour.adb.wire.AuthToken
import tv.cleanyour.adb.wire.KeyBacking
import tv.cleanyour.testing.fixtures.AdbKeyFormat
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

// The frozen AdbSigner over the host's own adb key (~/.android/adbkey, PKCS#8 PEM), which the
// emulator trusts from boot (ARCHITECTURE 6.6). It lets the app's :adb:session code reach a real
// adbd without an Allow tap. The key is the session's throwaway key: scripts/emulator-session.sh
// gives every session a fresh HOME, so it is never a key that any real TV trusts.
class HostKeySigner private constructor(private val private: PrivateKey, val publicKey: RSAPublicKey) : AdbSigner {
    override val publicKeyLine: String get() = AdbKeyFormat.publicKeyLine(publicKey)
    override val fingerprint: String get() = AdbKeyFormat.fingerprint(publicKey)
    override val backing: KeyBacking get() = KeyBacking.WRAPPED_SOFTWARE

    // PKCS#1 v1.5 over DigestInfo(SHA1) || token, exactly what adbd verifies.
    override suspend fun sign(token: AuthToken): ByteArray = signBlocking(token)

    fun signBlocking(token: AuthToken): ByteArray {
        require(token.bytes.size == 20) { "adb tokens are 20 bytes" }
        return Signature.getInstance("NONEwithRSA").run {
            initSign(private); update(AdbKeyFormat.SHA1_DIGEST_INFO + token.bytes); sign()
        }
    }

    override suspend fun reset() = throw UnsupportedOperationException("the host key is not ours to reset")

    companion object {
        fun load(file: File): HostKeySigner = fromPem(file.readText())

        fun fromPem(pem: String): HostKeySigner {
            val body = pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("").trim()
            require(pem.contains("BEGIN PRIVATE KEY")) { "expected a PKCS#8 PEM private key (adb's adbkey format)" }
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(body)))
            val crt = key as? RSAPrivateCrtKey ?: error("adbkey must be an RSA CRT private key")
            val public = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(crt.modulus, crt.publicExponent)) as RSAPublicKey
            return HostKeySigner(key, public)
        }
    }
}
