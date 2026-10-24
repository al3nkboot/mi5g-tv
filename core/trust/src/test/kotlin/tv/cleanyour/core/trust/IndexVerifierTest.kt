package tv.cleanyour.core.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tier

// The W5 fixture matrix (PRODUCT-SPEC 6 W5, SECURITY 6.1 "Registry"): good, bad signature, wrong
// key, revoked key, expired, rollback, tampered keys, plus the edges around each.
class IndexVerifierTest {
    private val root = TestKey.generate()
    private val ci = TestKey.generate()
    private val oldCi = TestKey.generate()
    private val stranger = TestKey.generate()

    private val keys = Docs.keys(sequence = 3, active = listOf(ci), revoked = listOf(oldCi))
    private val keysSig = root.sign(keys)
    private val index = Docs.index(sequence = 7)
    private val indexSig = ci.sign(index)
    private val verifier = MinisignIndexVerifier(root.key, keysFloor = 2)

    private fun verify(
        idx: ByteArray = index, sig: ByteArray = indexSig, k: ByteArray = keys, kSig: ByteArray = keysSig,
        last: Long? = 6, now: Long = Docs.NOW, v: MinisignIndexVerifier = verifier,
    ) = v.verify(idx, sig, k, kSig, last, now)

    private fun failure(verdict: IndexVerdict): IndexFailure = (verdict as IndexVerdict.Bad).failure

    @Test fun good() {
        val ok = verify() as IndexVerdict.Ok
        assertEquals(7, ok.index.sequence)
        assertEquals(1_793_577_600_000L, ok.index.expiresAtMs)
        val retro = ok.index.modules.single()
        assertEquals("retro", retro.id)
        assertEquals(Tier.COMMUNITY, retro.tier)
        assertEquals(Sha256(Docs.RETRO_SHA), retro.versions.single().artifact.sha256)
        assertEquals(Sha256(Docs.RETRO_SIGNER), retro.versions.single().artifact.signer)
        assertTrue(retro.versions.single().postInstall.isEmpty())
    }

    @Test fun goodWithoutHistoryAndWithEqualSequence() {
        assertTrue(verify(last = null) is IndexVerdict.Ok)
        assertTrue(verify(last = 7) is IndexVerdict.Ok)
    }

    @Test fun badSignatureOnAFlippedByte() {
        val flipped = index.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        assertEquals(IndexFailure.BAD_SIGNATURE, failure(verify(idx = flipped)))
    }

    @Test fun badSignatureOnAnEditedTrustedComment() {
        val edited = String(indexSig).replace("file:test", "file:other").toByteArray()
        assertEquals(IndexFailure.BAD_SIGNATURE, failure(verify(sig = edited)))
    }

    @Test fun badSignatureWhenUnreadable() {
        assertEquals(IndexFailure.BAD_SIGNATURE, failure(verify(sig = "garbage".toByteArray())))
        assertEquals(IndexFailure.BAD_SIGNATURE, failure(verify(sig = ByteArray(0))))
    }

    @Test fun wrongKeyIsUnknown() {
        assertEquals(IndexFailure.UNKNOWN_KEY, failure(verify(sig = stranger.sign(index))))
    }

    @Test fun theRootMayNotSignTheIndex() {
        assertEquals(IndexFailure.UNKNOWN_KEY, failure(verify(sig = root.sign(index))))
    }

    @Test fun revokedKey() {
        assertEquals(IndexFailure.REVOKED_KEY, failure(verify(sig = oldCi.sign(index))))
    }

    @Test fun rotationAcceptsTheNewKeyAndRefusesTheOld() {
        val next = TestKey.generate()
        val rotated = Docs.keys(sequence = 4, active = listOf(next), revoked = listOf(ci, oldCi))
        val rotatedSig = root.sign(rotated)
        assertTrue(verify(sig = next.sign(index), k = rotated, kSig = rotatedSig) is IndexVerdict.Ok)
        assertEquals(IndexFailure.REVOKED_KEY, failure(verify(sig = ci.sign(index), k = rotated, kSig = rotatedSig)))
    }

    @Test fun expired() {
        assertEquals(IndexFailure.EXPIRED, failure(verify(now = 1_793_577_600_000L)))
        assertEquals(IndexFailure.EXPIRED, failure(verify(now = 1_793_577_600_000L + 1)))
        assertTrue(verify(now = 1_793_577_600_000L - 1) is IndexVerdict.Ok)
        assertTrue(verify(now = MinisignIndexVerifier.VERIFY_IGNORING_EXPIRY) is IndexVerdict.Ok)
    }

    @Test fun rollback() {
        assertEquals(IndexFailure.ROLLBACK, failure(verify(last = 8)))
    }

    @Test fun replayedOldIndexIsRollback() {
        val old = Docs.index(sequence = 3)
        assertEquals(IndexFailure.ROLLBACK, failure(verify(idx = old, sig = ci.sign(old), last = 7)))
    }

    @Test fun tamperedKeysFile() {
        val tampered = Docs.keys(sequence = 3, active = listOf(ci, stranger), revoked = listOf(oldCi))
        assertEquals(IndexFailure.BAD_KEYS_FILE, failure(verify(sig = stranger.sign(index), k = tampered)))
        val flipped = keys.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertEquals(IndexFailure.BAD_KEYS_FILE, failure(verify(k = flipped)))
    }

    @Test fun keysFileSignedByAnotherKey() {
        assertEquals(IndexFailure.BAD_KEYS_FILE, failure(verify(kSig = ci.sign(keys))))
        assertEquals(IndexFailure.BAD_KEYS_FILE, failure(verify(kSig = "nope".toByteArray())))
    }

    @Test fun keysFileOlderThanTheBundledOne() {
        val old = Docs.keys(sequence = 1, active = listOf(oldCi))
        assertEquals(IndexFailure.BAD_KEYS_FILE, failure(verify(sig = oldCi.sign(index), k = old, kSig = root.sign(old))))
    }

    @Test fun keysFileShapes() {
        fun signedKeys(json: String) = json.toByteArray().let { it to root.sign(it) }
        val bad = listOf(
            """{"schema":2,"sequence":3,"generated":"2026-10-19T00:00:00Z","keys":[]}""",
            """{"schema":1,"sequence":3,"generated":"2026-10-19T00:00:00Z","keys":[]}""",
            """{"schema":1,"sequence":3,"generated":"2026-10-19T00:00:00Z","keys":[{"id":"0000000000000000","public_key":"${ci.publicLine}","status":"active"}]}""",
            """{"schema":1,"sequence":3,"generated":"2026-10-19T00:00:00Z","keys":[{"id":"${root.idHex}","public_key":"${root.publicLine}","status":"active"}]}""",
            """{"schema":1,"sequence":3,"generated":"2026-10-19T00:00:00Z","keys":[{"id":"${ci.idHex}","public_key":"${ci.publicLine}","status":"maybe"}]}""",
            """{"schema":1,"sequence":3,"generated":"2026-10-19T00:00:00Z","keys":[{"id":"${ci.idHex}","public_key":"${ci.publicLine}","status":"active"},{"id":"${ci.idHex}","public_key":"${ci.publicLine}","status":"revoked"}]}""",
            """[1,2,3]""",
        )
        for (json in bad) {
            val (k, s) = signedKeys(json)
            assertEquals(json, IndexFailure.BAD_KEYS_FILE, failure(verify(k = k, kSig = s)))
        }
    }

    @Test fun malformedIndexAfterAGoodSignature() {
        val broken = """{"schema":1,"sequence":"seven"}""".toByteArray()
        assertEquals(IndexFailure.MALFORMED, failure(verify(idx = broken, sig = ci.sign(broken))))
    }

    @Test fun failureOrderIsKeysThenSignatureThenShapeThenSequenceThenExpiry() {
        // An expired, rolled back index with a bad keys file reports the keys file.
        assertEquals(IndexFailure.BAD_KEYS_FILE, failure(verify(kSig = ci.sign(keys), last = 99, now = Long.MAX_VALUE)))
        // A rolled back and expired index reports ROLLBACK first.
        assertEquals(IndexFailure.ROLLBACK, failure(verify(last = 99, now = Long.MAX_VALUE)))
    }

    @Test fun revokedSignerMarksTheModuleRevoked() {
        val idx = Docs.index(revoked = listOf(Docs.RETRO_SIGNER))
        val ok = verify(idx = idx, sig = ci.sign(idx)) as IndexVerdict.Ok
        val retro = ok.index.modules.single()
        assertEquals(Tier.REVOKED, retro.tier)
        assertEquals(listOf(Sha256(Docs.RETRO_SIGNER)), ok.index.revocations)
        assertNull(ok.index.installable(retro))
    }

    @Test fun revokedApkRulesOutOnlyThatVersion() {
        val v2sha = "b".repeat(64)
        val idx = Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(), Docs.version(code = 2, sha = v2sha)))), revoked = listOf(v2sha))
        val ok = verify(idx = idx, sig = ci.sign(idx)) as IndexVerdict.Ok
        val retro = ok.index.modules.single()
        assertEquals(Tier.COMMUNITY, retro.tier)
        assertEquals(listOf(2L, 1L), retro.versions.map { it.versionCode })
        assertEquals(1L, ok.index.installable(retro)?.versionCode)
    }

    @Test fun quarantinedModulesAreNotInstallable() {
        val idx = Docs.index(modules = listOf(Docs.module(tier = "quarantined")))
        val ok = verify(idx = idx, sig = ci.sign(idx)) as IndexVerdict.Ok
        assertNull(ok.index.installable(ok.index.modules.single()))
    }
}
