package tv.cleanyour.core.flows.smarttv

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartTvProtocolTest {
    @Test fun onlyPrivateLanAddresses() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.3.4", "172.31.255.1").forEach { assertTrue(SmartTvProtocol.isPrivateLan(it), it) }
        listOf("8.8.8.8", "172.32.0.1", "127.0.0.1", "tv.example.com", "192.168.1", "192.168.1.300", null).forEach { assertFalse(SmartTvProtocol.isPrivateLan(it), "$it") }
        assertFailsWith<IllegalArgumentException> { SmartTvProtocol.samsungUrl("1.2.3.4", null) }
    }

    @Test fun samsungUrlAndKeys() {
        val u = SmartTvProtocol.samsungUrl("192.168.1.20", "12345678")
        assertEquals("wss://192.168.1.20:8002/api/v2/channels/samsung.remote.control?name=TWk1RyBUVg==&token=12345678", u)
        assertTrue("token" !in SmartTvProtocol.samsungUrl("192.168.1.20", "bad&x=1"))
        assertTrue("\"DataOfCmd\":\"KEY_VOLUP\"" in SmartTvProtocol.samsungKey(SmartTvKey.VOL_UP))
    }

    @Test fun samsungReplies() {
        assertEquals(SmartTvProtocol.SamsungReply.Connected("9876"),
            SmartTvProtocol.samsungReply("""{"data":{"clients":[],"id":"x","token":"9876"},"event":"ms.channel.connect"}"""))
        assertEquals(SmartTvProtocol.SamsungReply.Denied, SmartTvProtocol.samsungReply("""{"event":"ms.channel.unauthorized"}"""))
        assertEquals(SmartTvProtocol.SamsungReply.Other, SmartTvProtocol.samsungReply("""{"event":"ms.channel.ready"}"""))
    }

    @Test fun lgRegisterAndPointer() {
        val r = SmartTvProtocol.lgRegister("abc123")
        assertTrue("\"client-key\":\"abc123\"" in r && "\"pairingType\":\"PROMPT\"" in r)
        assertTrue("client-key" !in SmartTvProtocol.lgRegister("\"inject"))
        assertEquals("k1", SmartTvProtocol.lgClientKey("""{"type":"registered","id":"register_0","payload":{"client-key":"k1"}}"""))
        assertNull(SmartTvProtocol.lgClientKey("""{"type":"response","payload":{"pairingType":"PROMPT"}}"""))
        val p = """{"type":"response","id":"pointer_1","payload":{"socketPath":"ws:\/\/192.168.1.30:3000\/resources\/x\/netinput.pointer.sock"}}"""
        assertEquals("ws://192.168.1.30:3000/resources/x/netinput.pointer.sock", SmartTvProtocol.lgPointerPath(p, "192.168.1.30"))
        assertNull(SmartTvProtocol.lgPointerPath(p, "192.168.1.31"))
        assertEquals("type:button\nname:ENTER\n\n", SmartTvProtocol.lgButton(SmartTvKey.OK))
    }

    @Test fun framesRoundTripMaskedAndLong() {
        for (size in listOf(0, 5, 125, 126, 300, 70000)) {
            val payload = ByteArray(size) { (it % 251).toByte() }
            val f = Frames.read(ByteArrayInputStream(Frames.encode(0x1, payload, byteArrayOf(1, 2, 3, 4))))!!
            assertEquals(1, f.opcode)
            assertTrue(payload.contentEquals(f.payload), "size $size")
        }
        assertNull(Frames.read(ByteArrayInputStream(ByteArray(0))))
    }

    @Test fun fragmentedMessageIsJoined() {
        val a = Frames.encode(0x1, "مرح".toByteArray(), null).also { it[0] = 0x01 }   // FIN clear
        val b = Frames.encode(0x0, "با".toByteArray(), null)
        val f = Frames.read(ByteArrayInputStream(a + b))!!
        assertEquals("مرحبا", f.payload.toString(Charsets.UTF_8))
    }
}
