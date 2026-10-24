package tv.cleanyour.android.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Finding 1 of the emulator run: the phone's own adbd announced itself over mDNS and Start took
// the phone for the TV. Discovery now drops every address of the phone's own interfaces.
class OwnAddressesTest {
    @Test fun loopbackAndTheUnspecifiedAddressAreAlwaysThePhone() {
        assertTrue(OwnAddresses.isOwn("127.0.0.1", emptySet()))
        assertTrue(OwnAddresses.isOwn("127.4.5.6", emptySet()))
        assertTrue(OwnAddresses.isOwn("0.0.0.0", emptySet()))
    }

    @Test fun theEmulatorsOwnLinkIsThePhoneAndTheTvIsNot() {
        val own = setOf("10.0.2.16", "10.0.2.15")
        assertTrue(OwnAddresses.isOwn("10.0.2.16", own))
        assertTrue(OwnAddresses.isOwn("10.0.2.15", own))
        assertFalse(OwnAddresses.isOwn("172.30.77.10", own))
        assertFalse(OwnAddresses.isOwn("192.168.1.50", own))
    }

    @Test fun theInterfaceListIncludesLoopback() {
        // Every host this runs on has a loopback interface; its address must be in the set.
        assertTrue(OwnAddresses.current().any { it.startsWith("127.") })
    }
}
