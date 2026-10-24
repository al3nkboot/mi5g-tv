package tv.cleanyour.adb.session

import org.junit.Rule
import org.junit.rules.Timeout
import kotlinx.coroutines.async
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import tv.cleanyour.core.model.TvId
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityAndQueueTest {
    // Every test is bounded (a hang once blocked the shared forge build lock).
    @get:Rule val timeout: Timeout = Timeout.seconds(60)

    // D37, pinned byte for byte with W6's restore.sh, which runs
    //   printf '%s%s' "$(getprop ro.serialno)" "$(getprop ro.product.device)" | sha256 | cut -c1-16
    // on the values exactly as getprop prints them: no separator, nothing trimmed beyond the line
    // break. The expected values below came from that pipeline (shasum -a 256), not from this code.
    @Test fun tvIdFormulaIsPinned() {
        assertEquals(TvId("22261e454f5254fb"), Identity.tvId("ABC123XYZ0001", "kiwi"))
        // Spaces inside a value are part of it; neither side trims them.
        assertEquals(TvId("c6d41e09e790e606"), Identity.tvId(" 0123 ", "mitv"))
        val props = Identity.parseProps("[ro.serialno]: [ 0123 ]\n[ro.product.device]: [mitv]\n[ro.build.version.sdk]: [28]\n")
        val identity = Identity.from(props, setOf("shell_v2"), 4096)
        assertEquals(TvId("c6d41e09e790e606"), identity.tvId)
        assertEquals("123 ", identity.serialLast4)
    }

    // Single flight (ARCHITECTURE 3.3): two calls on one session never overlap on the TV.
    @Test fun callsRunOneAtATime() = runTest {
        val rig = Rig(this)
        val s = rig.open()
        val start = currentTime
        val a = async { s.exec(line("sleep 2")) }
        val b = async { s.exec(line("sleep 3")) }
        assertEquals(0, a.await().exit)
        assertEquals(0, b.await().exit)
        // Serialised: 2 s then 3 s, not 3 s for both side by side.
        assertEquals(5_000, currentTime - start)
        val shells = rig.adbd.connections.flatMap { it.openedServices }.filter { it.startsWith("shell,v2,raw:sleep") }
        assertEquals(listOf("shell,v2,raw:sleep 2", "shell,v2,raw:sleep 3"), shells)
    }
}
