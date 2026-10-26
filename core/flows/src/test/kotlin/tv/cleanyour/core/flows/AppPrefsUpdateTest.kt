package tv.cleanyour.core.flows

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tv.cleanyour.testing.fixtures.MemoryDocumentStore
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPrefsUpdateTest {
    @Test fun olderSettingsEnableAutomaticChecksByDefaultAndTheChoicePersists() = runBlocking {
        val documents = MemoryDocumentStore()
        documents.write("settings.txt", "haptics=false\nlargeText=true\n".encodeToByteArray())
        val prefs = AppPrefs(documents)

        val oldSettings = prefs.load()
        assertFalse(oldSettings.haptics)
        assertTrue(oldSettings.automaticUpdateChecks)

        prefs.save(oldSettings.copy(automaticUpdateChecks = false))
        assertFalse(prefs.load().automaticUpdateChecks)
    }
}
