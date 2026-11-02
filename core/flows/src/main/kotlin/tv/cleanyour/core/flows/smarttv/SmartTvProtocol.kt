package tv.cleanyour.core.flows.smarttv

import java.util.Base64

enum class SmartTvBrand { SAMSUNG, LG }

enum class SmartTvKey(val samsung: String, val lg: String) {
    UP("KEY_UP", "UP"), DOWN("KEY_DOWN", "DOWN"), LEFT("KEY_LEFT", "LEFT"), RIGHT("KEY_RIGHT", "RIGHT"),
    OK("KEY_ENTER", "ENTER"), BACK("KEY_RETURN", "BACK"), HOME("KEY_HOME", "HOME"),
    VOL_UP("KEY_VOLUP", "VOLUMEUP"), VOL_DOWN("KEY_VOLDOWN", "VOLUMEDOWN"), MUTE("KEY_MUTE", "MUTE"),
    CH_UP("KEY_CHUP", "CHANNELUP"), CH_DOWN("KEY_CHDOWN", "CHANNELDOWN"), POWER("KEY_POWER", "POWER"),
}

// Samsung (Tizen, 2016+): wss://TV:8002/api/v2/channels/samsung.remote.control, the TV asks once to
// allow the phone and answers with a token we keep. LG (webOS): SSAP over wss://TV:3001 (ws://TV:3000 on
// older firmware): register (the TV shows a prompt, then returns a client key), then the pointer socket
// takes "type:button" messages. Written from the public protocol descriptions; no third-party code.
object SmartTvProtocol {
    const val APP_NAME = "Mi5G TV"

    fun isPrivateLan(host: String?): Boolean {
        val p = host?.split('.')?.map { it.toIntOrNull() ?: return false } ?: return false
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    fun samsungUrl(ip: String, token: String?): String {
        require(isPrivateLan(ip)) { "not a local TV address" }
        val name = Base64.getEncoder().encodeToString(APP_NAME.toByteArray(Charsets.UTF_8))
        val t = token?.takeIf { it.matches(Regex("[0-9A-Za-z]{1,64}")) }?.let { "&token=$it" }.orEmpty()
        return "wss://$ip:8002/api/v2/channels/samsung.remote.control?name=$name$t"
    }

    fun samsungKey(key: SmartTvKey): String =
        """{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"${key.samsung}","Option":"false","TypeOfRemote":"SendRemoteKey"}}"""

    sealed interface SamsungReply { data class Connected(val token: String?) : SamsungReply; data object Denied : SamsungReply; data object Other : SamsungReply }

    fun samsungReply(msg: String): SamsungReply = when {
        "\"ms.channel.connect\"" in msg -> SamsungReply.Connected(Regex("\"token\"\\s*:\\s*\"?([0-9A-Za-z]+)\"?").find(msg)?.groupValues?.get(1))
        "\"ms.channel.unauthorized\"" in msg || "\"ms.channel.timeOut\"" in msg -> SamsungReply.Denied
        else -> SamsungReply.Other
    }

    fun lgUrls(ip: String): List<String> {
        require(isPrivateLan(ip)) { "not a local TV address" }
        return listOf("wss://$ip:3001/", "ws://$ip:3000/")
    }

    private val LG_PERMISSIONS = listOf("CONTROL_INPUT_TV", "CONTROL_POWER", "CONTROL_AUDIO", "CONTROL_INPUT_JOYSTICK",
        "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS", "LAUNCH", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST")

    fun lgRegister(clientKey: String?): String {
        val key = clientKey?.takeIf { it.matches(Regex("[0-9A-Za-z]{1,64}")) }?.let { ",\"client-key\":\"$it\"" }.orEmpty()
        val perms = LG_PERMISSIONS.joinToString(",") { "\"$it\"" }
        return """{"type":"register","id":"register_0","payload":{"forcePairing":false,"pairingType":"PROMPT"$key,""" +
            """"manifest":{"manifestVersion":1,"appVersion":"2.0","appId":"com.midroid.mi5gtv","vendorId":"mi5g","localizedAppNames":{"":"$APP_NAME"},""" +
            """"permissions":[$perms]}}}"""
    }

    fun lgClientKey(msg: String): String? =
        if ("\"registered\"" in msg) Regex("\"client-key\"\\s*:\\s*\"([0-9A-Za-z]+)\"").find(msg)?.groupValues?.get(1) else null

    fun lgDenied(msg: String): Boolean = "\"type\":\"error\"" in msg.replace(" ", "") && "register_0" in msg

    const val LG_POINTER_REQUEST = """{"type":"request","id":"pointer_1","uri":"ssap://com.webos.service.networkinput/getPointerInputSocket"}"""
    const val LG_POWER_OFF = """{"type":"request","id":"power_1","uri":"ssap://system/turnOff"}"""

    fun lgPointerPath(msg: String, ip: String): String? {
        val path = Regex("\"socketPath\"\\s*:\\s*\"([^\"]+)\"").find(msg)?.groupValues?.get(1)?.replace("\\/", "/") ?: return null
        val host = Regex("^wss?://([^:/]+)").find(path)?.groupValues?.get(1)
        return if (host == ip) path else null      // never follow the TV to another host
    }

    fun lgButton(key: SmartTvKey): String = "type:button\nname:${key.lg}\n\n"
}
