package tv.cleanyour.core.flows

import kotlinx.coroutines.test.runTest
import tv.cleanyour.testing.fixtures.MemoryDocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals

class CardSizePrefsTest {
    @Test fun cardSizeRoundTripsAndDefaultsToMedium() = runTest {
        val docs = MemoryDocumentStore()
        val prefs = AppPrefs(docs)
        assertEquals(CardSize.MEDIUM, prefs.load().cardSize)
        prefs.save(AppPrefsValue(cardSize = CardSize.LARGE))
        assertEquals(CardSize.LARGE, prefs.load().cardSize)
        docs.write("settings.txt", "haptics=true\ncardSize=HUGE\n".encodeToByteArray())
        assertEquals(CardSize.MEDIUM, prefs.load().cardSize)
    }
}
