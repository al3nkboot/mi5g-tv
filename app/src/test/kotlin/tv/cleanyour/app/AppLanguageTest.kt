package tv.cleanyour.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun arabicIsTheDefaultAndUnknownFallsBack() {
        assertEquals(AppLanguage.ARABIC, AppLanguage.normalize(null))
        assertEquals(AppLanguage.ARABIC, AppLanguage.normalize("fr"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.normalize("en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalize("system"))
    }
}
