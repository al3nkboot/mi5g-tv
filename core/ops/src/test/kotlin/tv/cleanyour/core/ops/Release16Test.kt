package tv.cleanyour.core.ops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.model.AdbError

class Release16Test {
    @Test fun eachKnownFailureHasAnArabicReasonAndANextStep() {
        Release16.LinkFailure.values().forEach { failure ->
            val text = Release16.linkMessage(failure, arabic = true)
            assertTrue(text.length > 20)
            assertFalse("pairing code leaked", Regex("\\b\\d{6}\\b").containsMatchIn(text))
        }
    }

    @Test fun unreachableWithoutWirelessDebuggingIsTheUsbPath() {
        val failure = Release16.classifyLink(AdbError.Transport.Unreachable, sdk = 28, onWifi = true, wirelessPresent = false)
        assertEquals(Release16.LinkFailure.WIRELESS_MISSING, failure)
    }

    @Test fun oldAndroidIsNamedAndNotAGenericFailure() {
        val failure = Release16.classifyLink(AdbError.Transport.NoShellV2, sdk = 19, onWifi = true, wirelessPresent = true)
        assertEquals(Release16.LinkFailure.TOO_OLD, failure)
        assertTrue(Release16.tooOldMessage(19, true).contains("أقدم من الحد الأدنى"))
    }

    @Test fun diagnosticDropsPairingCodes() {
        val text = Release16.diagnostic("TCL", "BeyondTV", "9", 28, "pair", "code 123456 refused", arabic = true)
        assertFalse(text.contains("123456"))
        assertTrue(text.contains("BeyondTV"))
        assertTrue(text.contains("[redacted]"))
    }

    @Test fun installsAreOptInAndShahidIsNotRequired() {
        val ids = Release16.optionalInstalls().map { it.id }
        assertFalse("shahid" in ids)
        assertTrue("smarttube" in ids)
        assertEquals(Release16.noneSelected(true).contains("شاهد"), true)
    }

    @Test fun http404NamesTheItem() {
        val fail = Release16.httpFailure("SmartTube", 404, arabic = true)
        assertTrue(fail.message.contains("SmartTube"))
        assertTrue(fail.message.contains("404"))
    }

    @Test fun dnsFallsBackLocallyAndSmartTubeDoesNot() {
        assertEquals("adguard-dns" to "com.midroid.mi5gtv.dns", Release16.bundledFallback("DNS"))
        assertNull(Release16.bundledFallback("SmartTube"))
    }

    @Test fun mapperFailureIsExplicit() {
        val text = Release16.mapMessage(Release16.MapFail.DIGEST_MISMATCH, arabic = true)
        assertTrue(text.contains("غير مفعّل"))
        assertFalse(text.contains("تم بنجاح"))
    }
}
