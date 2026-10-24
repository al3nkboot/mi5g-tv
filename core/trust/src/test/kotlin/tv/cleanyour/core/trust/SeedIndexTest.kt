package tv.cleanyour.core.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tier

// The files the app ships (android/platform/src/main/assets/bundled), signed by the real minisign
// 0.12 tool with the release index keys (K2R): interop with the reference implementation, and release
// checklist item 4 ("the seed index verifies against the shipped keys.json").
class SeedIndexTest {
    private val index = Repo.bundled("index.json")
    private val indexSig = Repo.bundled("index.json.minisig")
    private val keys = Repo.bundled("keys.json")
    private val keysSig = Repo.bundled("keys.json.minisig")

    @Test fun seedVerifiesAgainstTheCompiledRoot() {
        val verdict = TrustRoots.indexVerifier().verify(index, indexSig, keys, keysSig, null, MinisignIndexVerifier.VERIFY_IGNORING_EXPIRY)
        val ok = verdict as? IndexVerdict.Ok ?: error("seed does not verify: $verdict")
        assertEquals(2, ok.index.sequence)
        val retro = ok.index.modules.single()
        assertEquals("retro", retro.id)
        assertEquals(PackageName("io.github.udhaykumarbala.retro"), retro.pkg)
        assertEquals(Tier.COMMUNITY, retro.tier)
        val v = retro.versions.single()
        assertEquals("0.1.0", v.versionName)
        assertEquals(1L, v.versionCode)
        assertEquals("https://github.com/udhaykumarbala/tv-vibehack-retro/releases/download/v0.1.0/retro-release.apk", v.artifact.url)
        assertEquals(3_663_466L, v.artifact.size)
        assertEquals(Sha256("a3bcf445e1062d9e905b1d21b347236a6831e9ace6bfa1e76f9d154acf2d9db8"), v.artifact.sha256)
        assertEquals(Sha256("0202281bb726dbc12b82622dee50a278656128a82204db3cf055f8f5c006e405"), v.artifact.signer)
        assertEquals(setOf("android.permission.INTERNET", "android.permission.READ_EXTERNAL_STORAGE",
            "io.github.udhaykumarbala.retro.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"), v.permissions.map { it.permission }.toSet())
        assertEquals(26, v.minSdk)
    }

    @Test fun shippedKeysMatchTheCompiledFloorAndListOneActiveKey() {
        val k = (TrustRoots.indexVerifier().verifyKeys(keys, keysSig) as MinisignIndexVerifier.KeysVerdict.Ok).keys
        assertEquals(TrustRoots.BUNDLED_KEYS_SEQUENCE, k.sequence)
        assertEquals(listOf("0190B42669E69FB0"), k.keys.active.map { Ed25519Minisign.keyIdHex(it.id) })
        assertEquals("692E33AEFDE19BC1", Ed25519Minisign.keyIdHex(TrustRoots.indexRoot.id))
        assertEquals(TrustRoots.BUNDLED_KEYS_SEQUENCE, KeysFile.peekSequence(keys))
    }

    @Test fun seedSignaturesAreBoundToTheirFiles() {
        val swapped = TrustRoots.indexVerifier().verify(keys, keysSig, keys, keysSig, null, MinisignIndexVerifier.VERIFY_IGNORING_EXPIRY)
        assertEquals(IndexFailure.UNKNOWN_KEY, (swapped as IndexVerdict.Bad).failure)
        val text = String(indexSig)
        assertTrue(text.contains("trusted comment: ") && text.contains("file:index.json"))
    }

    @Test fun seedExpiresNinetyDaysAfterItWasSigned() {
        val doc = IndexDocument.parse(index)
        assertEquals(90L * 24 * 3600 * 1000, doc.expiresAtMs - doc.generatedAtMs)
    }
}
