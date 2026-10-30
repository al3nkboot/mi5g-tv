package tv.cleanyour.core.flows

import org.junit.Assert.*
import org.junit.Test
import tv.cleanyour.core.model.TvKeyEvent

class RemoteMappingTest {
    @Test fun directionsRemainPhysicalAndRepeat() {
        val pairs = listOf(
            RemoteButton.UP to TvKeyEvent.DPAD_UP, RemoteButton.DOWN to TvKeyEvent.DPAD_DOWN,
            RemoteButton.LEFT to TvKeyEvent.DPAD_LEFT, RemoteButton.RIGHT to TvKeyEvent.DPAD_RIGHT,
            RemoteButton.OK to TvKeyEvent.DPAD_CENTER, RemoteButton.BACK to TvKeyEvent.BACK, RemoteButton.HOME to TvKeyEvent.HOME,
            RemoteButton.MUTE to TvKeyEvent.VOLUME_MUTE, RemoteButton.PLAY_PAUSE to TvKeyEvent.PLAY_PAUSE,
            RemoteButton.VOL_UP to TvKeyEvent.VOLUME_UP, RemoteButton.VOL_DOWN to TvKeyEvent.VOLUME_DOWN,
            RemoteButton.CH_UP to TvKeyEvent.CHANNEL_UP, RemoteButton.CH_DOWN to TvKeyEvent.CHANNEL_DOWN,
            RemoteButton.MENU to TvKeyEvent.MENU, RemoteButton.SETTINGS to TvKeyEvent.SETTINGS,
            RemoteButton.POWER to TvKeyEvent.POWER, RemoteButton.SLEEP to TvKeyEvent.SLEEP, RemoteButton.INPUT to TvKeyEvent.TV_INPUT,
            RemoteButton.REWIND to TvKeyEvent.MEDIA_REWIND, RemoteButton.FAST_FORWARD to TvKeyEvent.MEDIA_FAST_FORWARD,
            RemoteButton.BACKSPACE to TvKeyEvent.DEL,
            RemoteButton.DIGIT_0 to TvKeyEvent.DIGIT_0, RemoteButton.DIGIT_1 to TvKeyEvent.DIGIT_1,
            RemoteButton.DIGIT_2 to TvKeyEvent.DIGIT_2, RemoteButton.DIGIT_3 to TvKeyEvent.DIGIT_3,
            RemoteButton.DIGIT_4 to TvKeyEvent.DIGIT_4, RemoteButton.DIGIT_5 to TvKeyEvent.DIGIT_5,
            RemoteButton.DIGIT_6 to TvKeyEvent.DIGIT_6, RemoteButton.DIGIT_7 to TvKeyEvent.DIGIT_7,
            RemoteButton.DIGIT_8 to TvKeyEvent.DIGIT_8, RemoteButton.DIGIT_9 to TvKeyEvent.DIGIT_9,
        )
        pairs.forEach { (button, key) -> assertEquals(button.name, key, keyFor(button)) }
        assertEquals(TvKeyEvent.DPAD_LEFT, keyFor(RemoteButton.LEFT))
        assertEquals(TvKeyEvent.DPAD_RIGHT, keyFor(RemoteButton.RIGHT))
        listOf(RemoteButton.UP, RemoteButton.DOWN, RemoteButton.LEFT, RemoteButton.RIGHT, RemoteButton.VOL_UP, RemoteButton.VOL_DOWN, RemoteButton.CH_UP, RemoteButton.CH_DOWN).forEach { assertTrue(repeats(it)) }
        assertFalse(repeats(RemoteButton.OK))
    }
    @Test fun mapperUsesDominantPhysicalAxis() {
        assertEquals(RemoteButton.RIGHT, TrackpadMapper.swipe(20f, 2f, 10f))
        assertEquals(RemoteButton.LEFT, TrackpadMapper.swipe(-20f, 2f, 10f))
        assertEquals(RemoteButton.DOWN, TrackpadMapper.swipe(2f, 20f, 10f))
        assertEquals(RemoteButton.UP, TrackpadMapper.swipe(2f, -20f, 10f))
        assertNull(TrackpadMapper.swipe(8f, 8f, 10f))
        assertEquals(RemoteButton.OK, TrackpadMapper.tap())
    }
    @Test fun textSanitizesSupportedCharactersAndFlagsUnicode() {
        assertEquals("hello%sworld", sanitizeForTv("hello world").latinToken)
        assertEquals("helloworld", sanitizeForTv("hello'world").latinToken)
        val mixed = sanitizeForTv("Hi مرحبا")
        assertEquals("Hi%s", mixed.latinToken)
        assertTrue(mixed.droppedNonAscii)
        assertNull(sanitizeForTv("مرحبا").latinToken)
        assertThrows(IllegalArgumentException::class.java) { sanitizeForTv("x".repeat(501)) }
    }
}
