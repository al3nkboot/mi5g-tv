package tv.cleanyour.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.cleanyour.core.model.Sha256

// The digest rules PackageManagerApkInspector applies to what PackageManager returns.
class SignerDigestsTest {
    private val certA = "certificate A".toByteArray()
    private val certB = "certificate B".toByteArray()

    @Test fun digestIsSha256OfTheDerCertificate() {
        // sha256("certificate A")
        assertEquals(Sha256("ce5cab5a5cd3591001b5f484cb5531d81445565ca3ed21ca16010a76fa8cc5c2"), SignerDigests.digest(certA))
        assertEquals(SignerDigests.digest(certA), SignerDigests.fromSigningInfo(false, listOf(certA)))
        assertEquals(SignerDigests.digest(certA), SignerDigests.fromLegacy(listOf(certA)))
    }

    @Test fun moreThanOneSignerOrNoneIsRefused() {
        assertNull(SignerDigests.fromSigningInfo(true, listOf(certA, certB)))
        assertNull(SignerDigests.fromSigningInfo(true, listOf(certA)))
        assertNull(SignerDigests.fromSigningInfo(false, emptyList()))
        assertNull(SignerDigests.fromLegacy(listOf(certA, certB)))
        assertNull(SignerDigests.fromLegacy(emptyList()))
    }

    @Test fun methodNamesSayWhichApiAnswered() {
        assertEquals("GET_SIGNING_CERTIFICATES", SignerDigests.METHOD_SIGNING_CERTIFICATES)
        assertEquals("GET_SIGNATURES (signingInfo was null)", SignerDigests.METHOD_SIGNATURES_FALLBACK)
    }
}
