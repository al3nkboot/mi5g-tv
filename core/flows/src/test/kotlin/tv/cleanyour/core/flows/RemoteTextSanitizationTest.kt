package tv.cleanyour.core.flows

import org.junit.Assert.*
import org.junit.Test

class RemoteTextSanitizationTest {
    @Test fun escapingDropsUnsupportedCharactersAndFlagsUnicode() {
        assertEquals("hello%sworld", sanitizeForTv("hello world").latinToken)
        assertEquals("helloworld", sanitizeForTv("hello'world").latinToken)
        val mixed = sanitizeForTv("Hi مرحبا")
        assertEquals("Hi%s", mixed.latinToken)
        assertTrue(mixed.droppedNonAscii)
        val arabic = sanitizeForTv("مرحبا")
        assertNull(arabic.latinToken)
        assertTrue(arabic.droppedNonAscii)
    }

    @Test fun textBeyondRendererLimitIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { sanitizeForTv("x".repeat(501)) }
    }
}
