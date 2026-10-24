package tv.cleanyour.core.backup

// Quoting and cleaning for text that ends up in a file a person may run (restore.sh, restore.ps1,
// commands.txt) or read (restore.txt, the restore note). Free text (a TV nickname typed on the
// phone, the model string the TV reports, a profile note) is never trusted: in scripts and lists it
// is reduced to printable ASCII on one line, and it only ever appears inside a comment or a quoted
// literal, never as code.
internal object TextSafety {
    // Printable ASCII without the line break, the only alphabet a script or restore.txt line uses.
    private val PRINTABLE = Regex("^[ -~]*$")

    fun isPrintableAscii(s: String): Boolean = PRINTABLE.matches(s)

    // One line of printable ASCII: anything else becomes '?', runs of spaces collapse, capped.
    fun ascii(s: String, max: Int = 80): String {
        val sb = StringBuilder()
        for (ch in s) sb.append(if (ch in ' '..'~') ch else '?')
        val one = sb.toString().replace(Regex(" {2,}"), " ").trim()
        return if (one.length <= max) one else one.take(max - 3).trimEnd() + "..."
    }

    // One line for people: any character except control characters and the Unicode line and
    // paragraph separators, which become a space.
    fun oneLine(s: String, max: Int = 80): String {
        val sb = StringBuilder()
        for (ch in s) sb.append(if (ch.isISOControl() || ch == '\u2028' || ch == '\u2029') ' ' else ch)
        val one = sb.toString().replace(Regex("\\s{2,}"), " ").trim()
        return if (one.length <= max) one else one.take(max - 3).trimEnd() + "..."
    }

    // POSIX sh single quoting: everything literal, an apostrophe as '\''.
    fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    // A sh word for a TV command line or a description. Double quotes keep the TV's own single
    // quotes readable; they are used only when nothing in the text is special inside double quotes
    // ($, `, \, ", and ! for interactive shells), otherwise the text is single quoted.
    fun shWord(s: String): String {
        require(isPrintableAscii(s)) { "not printable ASCII" }
        return if (s.none { it == '$' || it == '`' || it == '\\' || it == '"' || it == '!' }) "\"$s\"" else shq(s)
    }

    // PowerShell single quoting: everything literal, an apostrophe doubled. ASCII only, so none of
    // the typographic quotes PowerShell also treats as apostrophes can appear.
    fun psq(s: String): String {
        require(isPrintableAscii(s)) { "not printable ASCII" }
        return "'" + s.replace("'", "''") + "'"
    }
}
