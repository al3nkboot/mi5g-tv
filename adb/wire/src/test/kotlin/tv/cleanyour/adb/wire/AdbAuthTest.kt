package tv.cleanyour.adb.wire

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdbAuthTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)
    private val signer = TestKeys.signer
    private val publicKey = signer.keyPair.public as RSAPublicKey

    @Test fun structIs524BytesWithTheAdbdLayout() {
        val s = AdbPublicKeyCodec.encode(publicKey)
        assertEquals(524, s.size)
        assertEquals(64, FrameCodec.getIntLe(s, 0))
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = publicKey.modulus.mod(r32).modInverse(r32).negate().mod(r32)
        assertEquals(n0inv.toLong(), FrameCodec.getIntLe(s, 4).toLong() and 0xffffffffL)
        assertEquals(publicKey.publicExponent.toInt(), FrameCodec.getIntLe(s, 520))
        // n[0] is the least significant word of the modulus.
        assertEquals(publicKey.modulus.mod(r32).toLong(), FrameCodec.getIntLe(s, 8).toLong() and 0xffffffffL)
    }

    @Test fun structRoundTrips() {
        val decoded = AdbPublicKeyCodec.decode(AdbPublicKeyCodec.encode(publicKey))
        assertEquals(publicKey.modulus, decoded.modulus)
        assertEquals(publicKey.publicExponent, decoded.publicExponent)
    }

    @Test fun decodeChecksTheRedundantFields() {
        val s = AdbPublicKeyCodec.encode(publicKey)
        s[4] = (s[4].toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException> { AdbPublicKeyCodec.decode(s) }
        val short = s.copyOf(500)
        assertFailsWith<IllegalArgumentException> { AdbPublicKeyCodec.decode(short) }
        val rr = AdbPublicKeyCodec.encode(publicKey)
        rr[300] = (rr[300].toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException> { AdbPublicKeyCodec.decode(rr) }
    }

    @Test fun lineCarriesTheD20CommentAndParsesBack() {
        val line = signer.publicKeyLine
        assertTrue(line.endsWith(" cleanyourtv"))
        assertFalse(line.contains("@"))
        val b64 = line.substringBefore(' ')
        assertEquals(524, Base64.getDecoder().decode(b64).size)
        assertEquals(publicKey.modulus, AdbPublicKeyCodec.parseLine(line).modulus)
        assertEquals(publicKey.modulus, AdbPublicKeyCodec.parseLine(line + "\u0000").modulus)
    }

    @Test fun fingerprintIsColonHexMd5OfTheStruct() {
        val fp = signer.fingerprint
        assertTrue(Regex("^([0-9A-F]{2}:){15}[0-9A-F]{2}$").matches(fp), fp)
        assertEquals(AdbPublicKeyCodec.fingerprint(publicKey), fp)
    }

    @Test fun signatureIsPkcs1OverDigestInfoAndToken() = runBlocking<Unit> {
        val token = ByteArray(20) { it.toByte() }
        val sig = signer.sign(AuthToken(token))
        assertEquals(256, sig.size)
        assertTrue(AdbSignature.verify(publicKey, token, sig))
        // Recover the padded block with the public key: 00 01 FF..FF 00 DigestInfo token.
        val raw = BigInteger(1, sig).modPow(publicKey.publicExponent, publicKey.modulus).toByteArray()
        val src = if (raw.size > 256) raw.copyOfRange(raw.size - 256, raw.size) else raw
        val m = ByteArray(256).also { System.arraycopy(src, 0, it, 256 - src.size, src.size) }
        assertEquals(0x00, m[0].toInt())
        assertEquals(0x01, m[1].toInt())
        for (i in 2 until 220) assertEquals(0xff, m[i].toInt() and 0xff, "pad byte $i")
        assertEquals(0x00, m[220].toInt())
        assertContentEquals(AdbSignature.SHA1_DIGEST_INFO, m.copyOfRange(221, 236))
        assertContentEquals(token, m.copyOfRange(236, 256))
    }

    @Test fun verifyRejectsTheBareTokenFormAndWrongSizes() = runBlocking<Unit> {
        val token = ByteArray(20) { 7 }
        val sig = signer.sign(AuthToken(token))
        assertFalse(AdbSignature.verify(publicKey, token + byteArrayOf(0), sig))
        assertFalse(AdbSignature.verify(publicKey, token, sig.copyOf(255)))
        assertFalse(AdbSignature.verify(publicKey, ByteArray(20) { 8 }, sig))
        // A signature over the bare 20 bytes (what a naive signer would produce) does not verify.
        val bare = java.security.Signature.getInstance("NONEwithRSA").run { initSign(signer.keyPair.private); update(token); sign() }
        assertFalse(AdbSignature.verify(publicKey, token, bare))
    }

    @Test fun authTokenAdmitsExactlyTwentyBytes() {
        AuthToken(ByteArray(20))
        assertFailsWith<IllegalArgumentException> { AuthToken(ByteArray(19)) }
        assertFailsWith<IllegalArgumentException> { AuthToken(ByteArray(21)) }
        assertFailsWith<IllegalArgumentException> { AuthToken(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { AuthToken(ByteArray(256)) }
    }

    @Test fun selfTestPassesForTheSoftwareSigner() = runBlocking<Unit> {
        assertIs<SignerSelfTest.Pass>(signer.selfTest())
    }

    @Test fun selfTestCatchesALyingSigner() = runBlocking<Unit> {
        val liar = object : AdbSigner by signer {
            override suspend fun sign(token: AuthToken): ByteArray = ByteArray(256)
        }
        val r = assertIs<SignerSelfTest.Fail>(liar.selfTest())
        assertTrue(r.reason.contains("does not verify"), r.reason)
        val wrongComment = object : AdbSigner by signer {
            override val publicKeyLine: String get() = AdbPublicKeyCodec.line(publicKey, "cleanyourtv@phone")
        }
        assertIs<SignerSelfTest.Fail>(wrongComment.selfTest())
    }

    @Test fun resetMakesANewKey() = runBlocking<Unit> {
        val s = TestKeys.fresh()
        val before = s.fingerprint
        s.reset()
        assertTrue(before != s.fingerprint)
    }
}
