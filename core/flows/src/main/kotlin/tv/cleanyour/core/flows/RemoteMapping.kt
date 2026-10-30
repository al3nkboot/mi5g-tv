package tv.cleanyour.core.flows

import tv.cleanyour.core.model.TvKeyEvent
import tv.cleanyour.core.ops.ShellRenderer

enum class RemoteButton { UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MUTE, PLAY_PAUSE, VOL_UP, VOL_DOWN, CH_UP, CH_DOWN, MENU, SETTINGS, POWER, SLEEP, INPUT, REWIND, FAST_FORWARD, BACKSPACE, DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9 }

fun keyFor(button: RemoteButton): TvKeyEvent = when (button) {
    RemoteButton.UP -> TvKeyEvent.DPAD_UP; RemoteButton.DOWN -> TvKeyEvent.DPAD_DOWN
    RemoteButton.LEFT -> TvKeyEvent.DPAD_LEFT; RemoteButton.RIGHT -> TvKeyEvent.DPAD_RIGHT
    RemoteButton.OK -> TvKeyEvent.DPAD_CENTER; RemoteButton.BACK -> TvKeyEvent.BACK
    RemoteButton.HOME -> TvKeyEvent.HOME; RemoteButton.MUTE -> TvKeyEvent.VOLUME_MUTE
    RemoteButton.PLAY_PAUSE -> TvKeyEvent.PLAY_PAUSE; RemoteButton.VOL_UP -> TvKeyEvent.VOLUME_UP
    RemoteButton.VOL_DOWN -> TvKeyEvent.VOLUME_DOWN; RemoteButton.CH_UP -> TvKeyEvent.CHANNEL_UP
    RemoteButton.CH_DOWN -> TvKeyEvent.CHANNEL_DOWN; RemoteButton.MENU -> TvKeyEvent.MENU
    RemoteButton.SETTINGS -> TvKeyEvent.SETTINGS; RemoteButton.POWER -> TvKeyEvent.POWER
    RemoteButton.SLEEP -> TvKeyEvent.SLEEP; RemoteButton.INPUT -> TvKeyEvent.TV_INPUT
    RemoteButton.REWIND -> TvKeyEvent.MEDIA_REWIND; RemoteButton.FAST_FORWARD -> TvKeyEvent.MEDIA_FAST_FORWARD
    RemoteButton.BACKSPACE -> TvKeyEvent.DEL
    RemoteButton.DIGIT_0 -> TvKeyEvent.DIGIT_0; RemoteButton.DIGIT_1 -> TvKeyEvent.DIGIT_1
    RemoteButton.DIGIT_2 -> TvKeyEvent.DIGIT_2; RemoteButton.DIGIT_3 -> TvKeyEvent.DIGIT_3
    RemoteButton.DIGIT_4 -> TvKeyEvent.DIGIT_4; RemoteButton.DIGIT_5 -> TvKeyEvent.DIGIT_5
    RemoteButton.DIGIT_6 -> TvKeyEvent.DIGIT_6; RemoteButton.DIGIT_7 -> TvKeyEvent.DIGIT_7
    RemoteButton.DIGIT_8 -> TvKeyEvent.DIGIT_8; RemoteButton.DIGIT_9 -> TvKeyEvent.DIGIT_9
}

fun repeats(button: RemoteButton): Boolean = button in setOf(RemoteButton.UP, RemoteButton.DOWN, RemoteButton.LEFT, RemoteButton.RIGHT, RemoteButton.VOL_UP, RemoteButton.VOL_DOWN, RemoteButton.CH_UP, RemoteButton.CH_DOWN)

object TrackpadMapper {
    fun swipe(dx: Float, dy: Float, thresholdPx: Float): RemoteButton? {
        if (kotlin.math.abs(dx) < thresholdPx && kotlin.math.abs(dy) < thresholdPx) return null
        return if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) if (dx > 0) RemoteButton.RIGHT else RemoteButton.LEFT
        else if (dy > 0) RemoteButton.DOWN else RemoteButton.UP
    }
    fun tap(): RemoteButton = RemoteButton.OK
}

data class TextSendPlan(val latinToken: String?, val droppedNonAscii: Boolean)
fun sanitizeForTv(text: String): TextSendPlan {
    val trimmed = text.trim('\r', '\n')
    require(trimmed.length <= ShellRenderer.MAX_INPUT_TEXT_LEN) { "text too long" }
    val dropped = trimmed.any { it.code > 127 }
    val supported = trimmed.filter { it == ' ' || it == '\t' || it in '!'..'~' }
    val token = supported.takeIf { it.isNotBlank() }?.let(ShellRenderer::escapeInputText)
    return TextSendPlan(token, dropped)
}
