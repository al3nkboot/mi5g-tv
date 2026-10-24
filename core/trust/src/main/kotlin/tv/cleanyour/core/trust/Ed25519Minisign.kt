package tv.cleanyour.core.trust

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.util.Base64

// minisign (Ed25519) verification in the format minisign documents. Adapted from the W5 author's
// own spike code (app-spike crypto/Minisign.kt, S8), with input bounds and the frozen 5.11 types.
//
//   public key: "untrusted comment: ..." then base64("Ed" | key id (8) | public key (32))
//   signature:  "untrusted comment: ...", base64(alg (2) | key id (8) | signature (64)),
//               "trusted comment: <text>", base64(global signature (64))
//
// alg "ED" signs BLAKE2b-512 of the file (minisign's default since 0.9); "Ed" signs the file itself.
// The global signature covers signature || trusted comment, so the trusted comment cannot be edited.
// Crypto is Bouncy Castle's lightweight API only (no JCA provider registration, ARCHITECTURE D13).
object Ed25519Minisign : Minisign {

    const val MAX_SIGNATURE_BYTES = 4096
    const val MAX_KEY_FILE_BYTES = 1024
    const val MAX_TRUSTED_COMMENT_BYTES = 1024

    class Signature(val prehashed: Boolean, val keyId: ByteArray, val signature: ByteArray, val trustedComment: String, val globalSignature: ByteArray)

    // The key whose id the signature names is looked up in `keys`; the first match is used.
    override fun verify(data: ByteArray, signature: ByteArray, keys: List<MinisignKey>): VerifyOutcome {
        val sig = try { parseSignature(signature) } catch (e: IllegalArgumentException) { return VerifyOutcome.Fail("unreadable signature: ${e.message}") }
        val key = keys.firstOrNull { it.id.contentEquals(sig.keyId) }
            ?: return VerifyOutcome.Fail("key id mismatch: signature is for key ${keyIdHex(sig.keyId)}")
        if (key.publicKey.size != Ed25519.PUBLIC_KEY_SIZE) return VerifyOutcome.Fail("public key is ${key.publicKey.size} bytes")
        val message = if (sig.prehashed) blake2b512(data) else data
        if (!ed25519Verify(key.publicKey, sig.signature, message)) return VerifyOutcome.Fail("signature does not verify")
        val global = sig.signature + sig.trustedComment.toByteArray(Charsets.UTF_8)
        if (!ed25519Verify(key.publicKey, sig.globalSignature, global)) return VerifyOutcome.Fail("global signature (trusted comment) does not verify")
        return VerifyOutcome.Ok(sig.trustedComment)
    }

    // A minisign public key file, or its bare base64 line.
    fun parsePublicKey(text: String): MinisignKey {
        require(text.length <= MAX_KEY_FILE_BYTES) { "public key file too large" }
        val line = text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("untrusted comment:") }
            ?: throw IllegalArgumentException("no key line")
        val raw = decode(line)
        require(raw.size == 42) { "public key is ${raw.size} bytes, expected 42" }
        require(raw[0] == 'E'.code.toByte() && raw[1] == 'd'.code.toByte()) { "unknown key algorithm" }
        return MinisignKey(raw.copyOfRange(2, 10), raw.copyOfRange(10, 42))
    }

    fun parseSignature(bytes: ByteArray): Signature {
        require(bytes.size <= MAX_SIGNATURE_BYTES) { "signature file too large" }
        val text = String(bytes, Charsets.UTF_8)
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        require(lines.size == 4) { "signature file needs exactly 4 lines, has ${lines.size}" }
        require(lines[0].startsWith("untrusted comment:")) { "line 1 must be the untrusted comment" }
        require(lines[2].startsWith("trusted comment: ")) { "line 3 must be the trusted comment" }
        val raw = decode(lines[1])
        require(raw.size == 74) { "signature block is ${raw.size} bytes, expected 74" }
        val prehashed = when {
            raw[0] == 'E'.code.toByte() && raw[1] == 'D'.code.toByte() -> true
            raw[0] == 'E'.code.toByte() && raw[1] == 'd'.code.toByte() -> false
            else -> throw IllegalArgumentException("unknown signature algorithm")
        }
        val global = decode(lines[3])
        require(global.size == 64) { "global signature is ${global.size} bytes, expected 64" }
        val trusted = lines[2].removePrefix("trusted comment: ")
        require(trusted.toByteArray(Charsets.UTF_8).size <= MAX_TRUSTED_COMMENT_BYTES) { "trusted comment too long" }
        return Signature(prehashed, raw.copyOfRange(2, 10), raw.copyOfRange(10, 74), trusted, global)
    }

    // The key id as minisign prints it (little endian, upper case hex).
    fun keyIdHex(id: ByteArray): String = id.reversedArray().joinToString("") { "%02X".format(it) }

    fun keyIdFromHex(hex: String): ByteArray {
        require(Regex("^[0-9A-Fa-f]{16}$").matches(hex)) { "key id must be 16 hex characters" }
        return hex.chunked(2).map { it.toInt(16).toByte() }.reversed().toByteArray()
    }

    fun blake2b512(data: ByteArray): ByteArray {
        val d = Blake2bDigest(512)
        d.update(data, 0, data.size)
        val out = ByteArray(64)
        d.doFinal(out, 0)
        return out
    }

    private fun ed25519Verify(pk: ByteArray, sig: ByteArray, msg: ByteArray): Boolean {
        if (pk.size != Ed25519.PUBLIC_KEY_SIZE || sig.size != Ed25519.SIGNATURE_SIZE) return false
        return try { Ed25519.verify(sig, 0, pk, 0, msg, 0, msg.size) } catch (e: RuntimeException) { false }
    }

    private fun decode(s: String): ByteArray = try {
        Base64.getDecoder().decode(s.trim())
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("bad base64")
    }
}
