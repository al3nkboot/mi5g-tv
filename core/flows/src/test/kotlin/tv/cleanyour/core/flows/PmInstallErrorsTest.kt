package tv.cleanyour.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PmInstallErrorsTest {

    @Test
    fun extractsCodesCorrectly() {
        assertEquals("INSTALL_FAILED_INSUFFICIENT_STORAGE", PmInstallErrors.extractCode("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]"))
        assertEquals("INSTALL_FAILED_USER_RESTRICTED", PmInstallErrors.extractCode("Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]"))
        assertEquals("INSTALL_FAILED_VERIFICATION_FAILURE", PmInstallErrors.extractCode("Failure [INSTALL_FAILED_VERIFICATION_FAILURE]"))
        assertEquals("INSTALL_FAILED_UPDATE_INCOMPATIBLE", PmInstallErrors.extractCode("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures don't match]"))
        assertEquals("INSTALL_FAILED_WRONG_USER", PmInstallErrors.extractCode("installed apk sha256 abc, got no readable apk"))
        assertEquals("INSTALL_FAILED_WRONG_USER", PmInstallErrors.extractCode("not installed for user 0"))
        assertNull(PmInstallErrors.extractCode(null))
        assertNull(PmInstallErrors.extractCode(""))
        assertNull(PmInstallErrors.extractCode("random error text"))
    }

    @Test
    fun mapsKnownCodesToExplanations() {
        val testCases = listOf(
            "INSTALL_FAILED_INSUFFICIENT_STORAGE",
            "INSTALL_FAILED_USER_RESTRICTED",
            "INSTALL_FAILED_VERIFICATION_FAILURE",
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE",
            "INSTALL_FAILED_VERSION_DOWNGRADE",
            "INSTALL_FAILED_OLDER_SDK",
            "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE",
            "INSTALL_FAILED_INVALID_APK",
            "INSTALL_PARSE_FAILED_NOT_APK",
            "INSTALL_FAILED_TEST_ONLY",
            "INSTALL_FAILED_CONFLICTING_PROVIDER",
        )
        for (code in testCases) {
            val exp = PmInstallErrors.map("Failure [$code]")
            assertNotNull(exp, "Expected mapping for $code")
            assertEquals(code, exp.code)
            assertTrue(exp.arabic.isNotBlank(), "Arabic explanation should not be blank for $code")
            assertTrue(exp.english.isNotBlank(), "English explanation should not be blank for $code")
        }
    }

    @Test
    fun mapsUser0Errors() {
        val exp1 = PmInstallErrors.map("no readable apk")
        assertNotNull(exp1)
        assertEquals("INSTALL_FAILED_WRONG_USER", exp1.code)
        assertTrue(exp1.arabic.contains("للمستخدم الرئيسي"))

        val exp2 = PmInstallErrors.map("not installed for user 0")
        assertNotNull(exp2)
        assertEquals("INSTALL_FAILED_WRONG_USER", exp2.code)
        assertTrue(exp2.english.contains("main user"))
    }

    @Test
    fun formatIncludesExplanationAndRawCode() {
        val formattedAr = PmInstallErrors.format("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]", isArabic = true)
        assertNotNull(formattedAr)
        assertTrue(formattedAr.contains("ذاكرة التلفاز ممتلئة"))
        assertTrue(formattedAr.contains("[INSTALL_FAILED_INSUFFICIENT_STORAGE]"))

        val formattedEn = PmInstallErrors.format("Failure [INSTALL_FAILED_USER_RESTRICTED: blocked]", isArabic = false)
        assertNotNull(formattedEn)
        assertTrue(formattedEn.contains("user restrictions or Play Protect"))
        assertTrue(formattedEn.contains("[INSTALL_FAILED_USER_RESTRICTED]"))
    }

    @Test
    fun mapsUnreachableError() {
        val exp = PmInstallErrors.map("UNREACHABLE")
        assertNotNull(exp)
        assertEquals("UNREACHABLE", exp.code)
        assertTrue(exp.english.contains("The TV may be off or asleep"))
        assertTrue(exp.arabic.contains("قد يكون التلفاز مطفأً"))

        val formatted = PmInstallErrors.format("UNREACHABLE", isArabic = false)
        assertNotNull(formatted)
        assertTrue(formatted.contains("The TV may be off or asleep"))
    }
}
