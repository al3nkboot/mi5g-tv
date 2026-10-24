package tv.cleanyour.core.spec

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

// The festive skin of launcher v2.4.0 (Festival.parse and Cards.cleanLabel, ported line by line):
// a theme's festival, festival_until, greeting and accent_color. Anything malformed means no
// festival at all, so a skin can never stay on the screen forever. ConfigDifferentialTest holds
// this to the real Festival.java.
internal object LauncherFestival {
    const val MAX_NAME = 40

    // Festival with its values as the launcher keeps them: the lower-case name, the last day
    // (inclusive, null for no end date), the cleaned greeting and the accent (0 for the diya gold).
    data class Parsed(val name: String, val until: LocalDate?, val greeting: String, val accent: Int)

    fun name(raw: String): String = raw.javaTrim().lowercase(Locale.ROOT)

    // Festival.parse: null is Festival.NONE.
    fun parse(name: String, until: String, greeting: String, accent: String): Parsed? {
        val n = name(name)
        if (n.isEmpty() || n.length > MAX_NAME) return null
        var end: LocalDate? = null
        if (until.javaTrim().isNotEmpty()) {
            end = try { LocalDate.parse(until.javaTrim()) } catch (_: DateTimeParseException) { return null }
        }
        return Parsed(n, end, cleanLabel(greeting, ""), parseColor(accent))
    }

    // Festival.parseColor: #RRGGBB to an opaque colour, anything else to 0.
    fun parseColor(s: String): Int {
        val t = s.javaTrim()
        if (!HEX6.matches(t)) return 0
        return 0xFF000000.toInt() or t.substring(1).toInt(16)
    }

    private val HEX6 = Regex("#[0-9A-Fa-f]{6}")

    // Cards.cleanLabel: blanks, controls, format and separator chars collapse to one space, at
    // most 40 UTF-16 units (a surrogate pair is never split), trimmed; empty gives the fallback.
    fun cleanLabel(s: String, fallback: String): String {
        val b = StringBuilder(minOf(s.length, 48))
        var space = true
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val type = Character.getType(c).toByte()
            val blank = c == ' ' || Character.isISOControl(c) || Character.isWhitespace(c) || type == Character.FORMAT ||
                type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR || type == Character.SPACE_SEPARATOR
            if (blank) {
                if (!space) b.append(' ')
                space = true
                i++
                continue
            }
            val pair = Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])
            if (b.length + (if (pair) 2 else 1) > 40) break
            b.append(c)
            if (pair) b.append(s[++i])
            space = false
            i++
        }
        val out = b.toString().javaTrim()
        return out.ifEmpty { fallback }
    }
}
