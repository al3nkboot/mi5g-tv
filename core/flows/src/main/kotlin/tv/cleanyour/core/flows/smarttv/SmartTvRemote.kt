package tv.cleanyour.core.flows.smarttv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SmartTvLink {
    /** [pairing] is the Samsung token or LG client key to keep so the TV does not ask again. */
    data class Connected(val pairing: String?) : SmartTvLink
    data object Denied : SmartTvLink
    data object Unreachable : SmartTvLink
}

/** One Wi-Fi remote session with a Samsung or LG TV on the local network. */
class SmartTvRemote(private val open: (String) -> MiniWebSocket = { MiniWebSocket.open(it) }) {
    private var main: MiniWebSocket? = null
    private var pointer: MiniWebSocket? = null
    private var brand: SmartTvBrand? = null

    suspend fun connect(brand: SmartTvBrand, ip: String, pairing: String?): SmartTvLink = withContext(Dispatchers.IO) {
        close()
        if (!SmartTvProtocol.isPrivateLan(ip)) return@withContext SmartTvLink.Unreachable
        this@SmartTvRemote.brand = brand
        runCatching { if (brand == SmartTvBrand.SAMSUNG) samsung(ip, pairing) else lg(ip, pairing) }
            .getOrElse { close(); SmartTvLink.Unreachable }
    }

    private fun samsung(ip: String, token: String?): SmartTvLink {
        val ws = open(SmartTvProtocol.samsungUrl(ip, token))
        repeat(5) {
            val msg = ws.readText() ?: return SmartTvLink.Unreachable.also { ws.close() }
            when (val r = SmartTvProtocol.samsungReply(msg)) {
                is SmartTvProtocol.SamsungReply.Connected -> { main = ws; return SmartTvLink.Connected(r.token ?: token) }
                SmartTvProtocol.SamsungReply.Denied -> { ws.close(); return SmartTvLink.Denied }
                SmartTvProtocol.SamsungReply.Other -> {}
            }
        }
        ws.close(); return SmartTvLink.Unreachable
    }

    private fun lg(ip: String, clientKey: String?): SmartTvLink {
        var ws: MiniWebSocket? = null
        for (url in SmartTvProtocol.lgUrls(ip)) { ws = runCatching { open(url) }.getOrNull(); if (ws != null) break }
        val s = ws ?: return SmartTvLink.Unreachable
        s.sendText(SmartTvProtocol.lgRegister(clientKey))
        var key: String? = null
        repeat(6) {
            val msg = s.readText() ?: return SmartTvLink.Unreachable.also { s.close() }
            if (SmartTvProtocol.lgDenied(msg)) { s.close(); return SmartTvLink.Denied }
            SmartTvProtocol.lgClientKey(msg)?.let { key = it }
            if (key != null) {
                s.sendText(SmartTvProtocol.LG_POINTER_REQUEST)
                repeat(4) {
                    val r = s.readText() ?: return SmartTvLink.Unreachable
                    SmartTvProtocol.lgPointerPath(r, ip)?.let { path -> pointer = open(path); main = s; return SmartTvLink.Connected(key) }
                }
                main = s; return SmartTvLink.Connected(key)   // buttons unavailable, power still works
            }
        }
        s.close(); return SmartTvLink.Unreachable
    }

    suspend fun press(key: SmartTvKey): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when (brand) {
                SmartTvBrand.SAMSUNG -> main!!.sendText(SmartTvProtocol.samsungKey(key))
                SmartTvBrand.LG -> if (key == SmartTvKey.POWER) main!!.sendText(SmartTvProtocol.LG_POWER_OFF) else pointer!!.sendText(SmartTvProtocol.lgButton(key))
                null -> error("not connected")
            }
        }.isSuccess
    }

    fun close() { pointer?.close(); main?.close(); pointer = null; main = null }
}
