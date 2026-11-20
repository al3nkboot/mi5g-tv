package tv.cleanyour.core.flows

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 2.4 M3 (backlog 15): the link behind the launcher's "Connect a phone" QR.
class ConnectLinkTest {
    @Test fun readsAHomeNetworkTv() {
        assertEquals(ConnectTarget("192.168.1.50", 5555, "Living room"), ConnectLink.parse("mi5gtv://connect?host=192.168.1.50&port=5555&name=Living%20room"))
        assertEquals(ConnectTarget("10.0.0.7"), ConnectLink.parse("mi5gtv://connect/?host=10.0.0.7"))
        assertEquals("تلفاز", ConnectLink.parse("mi5gtv://connect?host=10.0.0.7&name=%D8%AA%D9%84%D9%81%D8%A7%D8%B2")?.name)
    }

    @Test fun refusesAnythingElse() {
        assertNull(ConnectLink.parse(null))
        assertNull(ConnectLink.parse("https://connect?host=192.168.1.50"))
        assertNull(ConnectLink.parse("mi5gtv://other?host=192.168.1.50"))
        assertNull(ConnectLink.parse("mi5gtv://connectx?host=192.168.1.50"))
        assertNull("not a home address", ConnectLink.parse("mi5gtv://connect?host=8.8.8.8"))
        assertNull(ConnectLink.parse("mi5gtv://connect?host=tv.example.com"))
        assertNull(ConnectLink.parse("mi5gtv://connect?host=192.168.1.50&port=70000"))
        assertNull(ConnectLink.parse("mi5gtv://connect"))
    }

    @Test fun theNameIsTrimmedAndCleaned() {
        val t = ConnectLink.parse("mi5gtv://connect?host=192.168.1.50&name=%E2%80%AEevil%0A" + "x".repeat(60))!!
        assertEquals(40, t.name.length)
        assertEquals('e', t.name[0])
    }

    @Test fun miniStoreHasOnlyValidDistinctPackages() {
        val re = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        MINI_STORE.forEach { assert(re.matches(it.pkg)) { it.pkg } }
        assertEquals(MINI_STORE.size, MINI_STORE.map { it.pkg }.toSet().size)
    }
}
