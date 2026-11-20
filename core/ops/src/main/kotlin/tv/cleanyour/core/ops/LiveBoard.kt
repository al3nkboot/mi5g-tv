package tv.cleanyour.core.ops

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 2.4 M3 (backlog 32, UrTV idea, own code): a phone drawing as one shell-safe token. Each point is x then y on a
 * 0..999 grid, three digits each; strokes are joined with "-". Points closer than [MIN_STEP] to the last kept one
 * are dropped, and the token stops before [MAX_CHARS] (one adb shell token holds 4096). The launcher's
 * LiveBoardActivity reads the same format back.
 */
object LiveBoard {
    const val MAX_CHARS = 3600
    const val MAX_TEXT = 120
    const val MIN_STEP = 6
    private val TOKEN_RE = Regex("^\\d{6}(\\d{6})*(-\\d{6}(\\d{6})*)*$")

    fun valid(token: String): Boolean = token.length <= MAX_CHARS && TOKEN_RE.matches(token)

    /** Strokes of (x, y) points in 0..1 (outside values are clamped). Empty when nothing is drawn. */
    fun encode(strokes: List<List<Pair<Float, Float>>>): String {
        val out = StringBuilder()
        for (stroke in strokes) {
            val pts = ArrayList<Pair<Int, Int>>()
            for ((x, y) in stroke) {
                val p = grid(x) to grid(y)
                val last = pts.lastOrNull()
                if (last == null || abs(last.first - p.first) + abs(last.second - p.second) >= MIN_STEP) pts += p
            }
            if (pts.isEmpty()) continue
            val cost = (if (out.isEmpty()) 0 else 1) + pts.size * 6
            if (out.length + cost > MAX_CHARS) {
                // Room left for part of this stroke only: keep what fits, then stop.
                val room = (MAX_CHARS - out.length - (if (out.isEmpty()) 0 else 1)) / 6
                if (room >= 1) { if (out.isNotEmpty()) out.append('-'); pts.take(room).forEach { out.append(fmt(it)) } }
                break
            }
            if (out.isNotEmpty()) out.append('-')
            pts.forEach { out.append(fmt(it)) }
        }
        return out.toString()
    }

    /** The token back as strokes on the 0..999 grid. */
    fun decode(token: String): List<List<Pair<Int, Int>>> {
        if (token.isEmpty() || !valid(token)) return emptyList()
        return token.split('-').map { s -> (0 until s.length / 6).map { i -> s.substring(i * 6, i * 6 + 3).toInt() to s.substring(i * 6 + 3, i * 6 + 6).toInt() } }
    }

    private fun grid(v: Float): Int = (v.coerceIn(0f, 1f) * 999f).roundToInt()
    private fun fmt(p: Pair<Int, Int>): String = "%03d%03d".format(p.first, p.second)
}
