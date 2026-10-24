package tv.cleanyour.core.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

// The spike's S8 vectors (app-spike, same author): signatures made outside any Kotlin code (OpenSSL
// Ed25519, Python BLAKE2b, assembled in minisign's layout), so a pass proves format interop.
class MinisignTest {
    private fun res(name: String): ByteArray = javaClass.getResourceAsStream("/minisign/$name")!!.use { it.readBytes() }

    private val pk = Ed25519Minisign.parsePublicKey(String(res("spike.pub")))
    private val other = Ed25519Minisign.parsePublicKey(String(res("other.pub")))
    private val sample = res("sample.txt")

    @Test fun parsesKeyAndSignature() {
        assertEquals("EFCDAB8967452301", Ed25519Minisign.keyIdHex(pk.id))
        val sig = Ed25519Minisign.parseSignature(res("sample.txt.minisig"))
        assertTrue(sig.prehashed)
        assertTrue(!Ed25519Minisign.parseSignature(res("sample.txt.legacy.minisig")).prehashed)
        assertEquals("timestamp:1760000000\tfile:sample.txt\tprehashed", sig.trustedComment)
        assertTrue(Ed25519Minisign.keyIdFromHex("EFCDAB8967452301").contentEquals(pk.id))
    }

    @Test fun prehashedSignatureVerifies() {
        val r = Ed25519Minisign.verify(sample, res("sample.txt.minisig"), listOf(pk))
        assertEquals(VerifyOutcome.Ok("timestamp:1760000000\tfile:sample.txt\tprehashed"), r)
    }

    @Test fun legacySignatureVerifies() {
        assertTrue(Ed25519Minisign.verify(sample, res("sample.txt.legacy.minisig"), listOf(pk)) is VerifyOutcome.Ok)
    }

    @Test fun keyIsPickedById() {
        assertTrue(Ed25519Minisign.verify(sample, res("sample.txt.minisig"), listOf(other, pk)) is VerifyOutcome.Ok)
    }

    @Test fun tamperedFileFails() {
        val r = Ed25519Minisign.verify(res("tampered.txt"), res("sample.txt.minisig"), listOf(pk))
        assertTrue((r as VerifyOutcome.Fail).reason.contains("signature does not verify"))
    }

    @Test fun wrongKeyFailsOnKeyId() {
        val r = Ed25519Minisign.verify(sample, res("sample.txt.minisig"), listOf(other))
        assertTrue((r as VerifyOutcome.Fail).reason.contains("key id"))
    }

    @Test fun keyWithTheRightIdButOtherBytesFails() {
        val impostor = MinisignKey(pk.id, other.publicKey)
        assertTrue(Ed25519Minisign.verify(sample, res("sample.txt.minisig"), listOf(impostor)) is VerifyOutcome.Fail)
    }

    @Test fun editedTrustedCommentFailsGlobalSignature() {
        val edited = String(res("sample.txt.minisig")).replace("file:sample.txt", "file:evil.txt").toByteArray()
        val r = Ed25519Minisign.verify(sample, edited, listOf(pk))
        assertTrue((r as VerifyOutcome.Fail).reason.contains("global"))
    }

    @Test fun swappedAlgorithmFails() {
        val lines = String(res("sample.txt.minisig")).lines()
        val raw = Base64.getDecoder().decode(lines[1]).also { it[1] = 'd'.code.toByte() }
        val swapped = (listOf(lines[0], Base64.getEncoder().encodeToString(raw)) + lines.drop(2)).joinToString("\n").toByteArray()
        assertTrue(Ed25519Minisign.verify(sample, swapped, listOf(pk)) is VerifyOutcome.Fail)
    }

    @Test fun malformedInputsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { Ed25519Minisign.parsePublicKey("untrusted comment: x\nnot base64!!\n") }
        assertThrows(IllegalArgumentException::class.java) { Ed25519Minisign.parsePublicKey("untrusted comment: x\nQUJD\n") }
        assertThrows(IllegalArgumentException::class.java) { Ed25519Minisign.parsePublicKey("x".repeat(5000)) }
        for (bad in listOf("just one line", "untrusted comment: a\nQUJD\ntrusted comment: b\nQUJD\n", "", "\u0000\u0001", "a\nb\nc\nd\ne\n")) {
            assertTrue(bad, Ed25519Minisign.verify(sample, bad.toByteArray(), listOf(pk)) is VerifyOutcome.Fail)
        }
        assertTrue(Ed25519Minisign.verify(sample, ByteArray(Ed25519Minisign.MAX_SIGNATURE_BYTES + 1) { 'A'.code.toByte() }, listOf(pk)) is VerifyOutcome.Fail)
        assertTrue(Ed25519Minisign.verify(sample, res("sample.txt.minisig"), emptyList()) is VerifyOutcome.Fail)
    }

    @Test fun generatedSignaturesRoundTrip() {
        val k = TestKey.generate()
        val data = "hello".toByteArray()
        assertTrue(Ed25519Minisign.verify(data, k.sign(data), listOf(k.key)) is VerifyOutcome.Ok)
        assertTrue(Ed25519Minisign.verify(data, k.sign(data, prehashed = false), listOf(k.key)) is VerifyOutcome.Ok)
        assertTrue(Ed25519Minisign.verify("hellO".toByteArray(), k.sign(data), listOf(k.key)) is VerifyOutcome.Fail)
        assertEquals(k.idHex, Ed25519Minisign.keyIdHex(Ed25519Minisign.parsePublicKey(k.publicLine).id))
    }

    @Test fun blake2bMatchesRfc7693Vector() {
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            Ed25519Minisign.blake2b512("abc".toByteArray()).joinToString("") { "%02x".format(it) },
        )
    }
}
