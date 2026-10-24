package tv.cleanyour.core.ops

// The quoting rules of SECURITY 3.4 and a strict POSIX tokenizer for the lines they produce.
//
// Every argv token, including the verb's own constant words, is wrapped in single quotes. No type
// admits an apostrophe, so a quoted token is always literal to sh. The line is the quoted tokens
// joined by single spaces. The tokenizer is the inverse: it accepts only lines of that shape and
// yields the argv back, so the renderer can assert tokenize(render(argv)) == argv on every line.
object ShellTokens {
    // Printable ASCII, no newline, no NUL, at most 131000 characters (MAX_ARG_STRLEN headroom).
    val LINE_RE = Regex("^[ -~]{1,131000}$")
    const val MAX_LINE = 131000

    // What a single token may contain between its quotes: printable ASCII minus the apostrophe.
    private val TOKEN_RE = Regex("^[ -&(-~]{1,4096}$")

    fun isSafeToken(raw: String): Boolean = TOKEN_RE.matches(raw)

    // 'raw' with the belt-and-braces check that the quote can never be closed from inside.
    fun quote(raw: String): String {
        require(isSafeToken(raw)) { "token is not in the safe alphabet: " + describe(raw) }
        return "'$raw'"
    }

    fun line(argv: List<String>): String {
        require(argv.isNotEmpty()) { "empty argv" }
        val line = argv.joinToString(" ") { quote(it) }
        assertLine(line)
        check(tokenize(line) == argv) { "rendered line does not tokenize back to its argv" }
        return line
    }

    // SECURITY 3.4 item 3: the whole line is printable ASCII and, outside the quotes, there is
    // nothing but single spaces between quoted tokens.
    fun assertLine(line: String) {
        check(LINE_RE.matches(line)) { "line is not printable ASCII of 1 to $MAX_LINE characters" }
        var inQuote = false
        var previousWasSpace = true   // a leading space is as wrong as a double one
        var count = 0
        for (c in line) {
            if (inQuote) {
                if (c == '\'') { inQuote = false; count++ }
                continue
            }
            when (c) {
                '\'' -> { check(previousWasSpace) { "token not separated by a space at ${count}" }; inQuote = true; previousWasSpace = false }
                ' ' -> { check(!previousWasSpace) { "double or leading space outside quotes" }; previousWasSpace = true }
                else -> error("character outside quotes: ${describe(c.toString())}")
            }
        }
        check(!inQuote) { "unterminated quote" }
        check(!previousWasSpace) { "trailing space" }
        check(count > 0) { "no tokens" }
    }

    // A strict POSIX tokenizer for lines of single-quoted tokens. Anything else (an unquoted word,
    // a metacharacter, a double quote, a backslash) is rejected rather than interpreted, so a test
    // that feeds it a line proves the line had only quoted tokens and spaces.
    fun tokenize(line: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val n = line.length
        require(n > 0) { "empty line" }
        while (i < n) {
            require(line[i] == '\'') { "unquoted character ${describe(line[i].toString())} at $i" }
            val end = line.indexOf('\'', i + 1)
            require(end > i) { "unterminated single quote at $i" }
            val token = line.substring(i + 1, end)
            require(token.isNotEmpty()) { "empty quoted token at $i" }
            out += token
            i = end + 1
            if (i == n) break
            require(line[i] == ' ' && i + 1 < n && line[i + 1] == '\'') { "tokens must be separated by exactly one space (at $i)" }
            i++
        }
        return out
    }

    private fun describe(s: String): String = s.map { c ->
        if (c in ' '..'~' && c != '\'') c.toString() else "\\u%04x".format(c.code)
    }.joinToString("")
}
