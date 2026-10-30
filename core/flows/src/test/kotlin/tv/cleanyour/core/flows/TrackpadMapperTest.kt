package tv.cleanyour.core.flows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackpadMapperTest {
    @Test fun swipesUsePhysicalDominantDirectionAndIgnoreSubthresholdMotion() {
        assertEquals(RemoteButton.RIGHT, TrackpadMapper.swipe(20f, 2f, 10f))
        assertEquals(RemoteButton.LEFT, TrackpadMapper.swipe(-20f, 2f, 10f))
        assertEquals(RemoteButton.DOWN, TrackpadMapper.swipe(2f, 20f, 10f))
        assertEquals(RemoteButton.UP, TrackpadMapper.swipe(2f, -20f, 10f))
        assertEquals(RemoteButton.RIGHT, TrackpadMapper.swipe(20f, 19f, 10f))
        assertNull(TrackpadMapper.swipe(8f, 8f, 10f))
    }

    @Test fun tapSelects() { assertEquals(RemoteButton.OK, TrackpadMapper.tap()) }
}
