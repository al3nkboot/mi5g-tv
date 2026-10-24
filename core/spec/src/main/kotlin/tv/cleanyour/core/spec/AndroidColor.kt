package tv.cleanyour.core.spec

import java.util.Locale

// android.graphics.Color.parseColor, ported line by line (platform/frameworks/base, identical in
// pie-release and main, Apache-2.0). Config.java calls it on the trimmed background_color.
internal object AndroidColor {
    // Outcome of parseColor: a colour, the IllegalArgumentException Config.java catches (it then
    // keeps the default), or the StringIndexOutOfBoundsException an empty string raises, which
    // Config.java does not catch, so the launcher's onCreate would crash.
    sealed interface Parse {
        data class Ok(val argb: Int) : Parse
        data object Invalid : Parse
        data object Crash : Parse
    }

    private val names: Map<String, Int> = mapOf(
        "black" to 0xFF000000.toInt(), "darkgray" to 0xFF444444.toInt(), "gray" to 0xFF888888.toInt(),
        "lightgray" to 0xFFCCCCCC.toInt(), "white" to 0xFFFFFFFF.toInt(), "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF00FF00.toInt(), "blue" to 0xFF0000FF.toInt(), "yellow" to 0xFFFFFF00.toInt(),
        "cyan" to 0xFF00FFFF.toInt(), "magenta" to 0xFFFF00FF.toInt(), "aqua" to 0xFF00FFFF.toInt(),
        "fuchsia" to 0xFFFF00FF.toInt(), "darkgrey" to 0xFF444444.toInt(), "grey" to 0xFF888888.toInt(),
        "lightgrey" to 0xFFCCCCCC.toInt(), "lime" to 0xFF00FF00.toInt(), "maroon" to 0xFF800000.toInt(),
        "navy" to 0xFF000080.toInt(), "olive" to 0xFF808000.toInt(), "purple" to 0xFF800080.toInt(),
        "silver" to 0xFFC0C0C0.toInt(), "teal" to 0xFF008080.toInt(),
    )

    fun parseColor(colorString: String): Parse {
        if (colorString.isEmpty()) return Parse.Crash      // charAt(0) on ""
        if (colorString[0] == '#') {
            var color = try {
                java.lang.Long.parseLong(colorString.substring(1), 16)
            } catch (_: NumberFormatException) {
                return Parse.Invalid                           // NumberFormatException is an IllegalArgumentException
            }
            if (colorString.length == 7) color = color or 0x00000000ff000000L
            else if (colorString.length != 9) return Parse.Invalid
            return Parse.Ok(color.toInt())
        }
        return names[colorString.lowercase(Locale.ROOT)]?.let { Parse.Ok(it) } ?: Parse.Invalid
    }
}
