package tv.cleanyour.feature.connect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualAddressTest {
    @Test fun validatesIpv4AndPortRange() {
        assertTrue(validManualAddress("192.168.1.20", "5555"))
        assertFalse(validManualAddress("999.168.1.20", "5555"))
        assertFalse(validManualAddress("192.168.1", "5555"))
        assertFalse(validManualAddress("192.168.1.20", "0"))
        assertFalse(validManualAddress("192.168.1.20", "65536"))
    }
}
