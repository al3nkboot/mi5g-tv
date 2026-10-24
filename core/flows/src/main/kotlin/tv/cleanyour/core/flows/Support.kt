package tv.cleanyour.core.flows

import kotlin.coroutines.cancellation.CancellationException
import tv.cleanyour.core.discovery.FoundTv
import tv.cleanyour.core.engine.Journal
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.model.ConnectionState
import tv.cleanyour.core.model.DebugState
import tv.cleanyour.core.model.PlanId
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tv
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.Plan

// runCatching that lets cancellation through, for calls into W2 to W6 that may throw anything.
suspend inline fun <T> attempt(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

// The TV status chip (DESIGN-SYSTEM 8.3, D42): a dot plus the word, never colour alone.
enum class ChipState { CONNECTED, WAITING, WORKING, DEBUGGING_OFF, OFFLINE }

fun chipFor(connection: ConnectionState?, run: RunState?, debugging: DebugState): ChipState = when {
    run is RunState.Running || run is RunState.RollingBack || run is RunState.InDoubt -> ChipState.WORKING
    connection is ConnectionState.Connected -> ChipState.CONNECTED
    connection is ConnectionState.AwaitingAllow || connection is ConnectionState.Connecting ||
        connection is ConnectionState.WaitingForBoot -> ChipState.WAITING
    connection is ConnectionState.Reconnecting -> ChipState.WAITING
    debugging == DebugState.OFF -> ChipState.DEBUGGING_OFF
    else -> ChipState.OFFLINE
}

// Where the app opens, decided from disk before the first frame (ARCHITECTURE 2.2): a run with no
// End record gives the resume prompt (S4e) for that run; a TV with a WizardDraft continues the
// wizard; any TV gives Manage; nothing gives Welcome.
sealed interface StartDestination {
    data object Welcome : StartDestination
    data class ResumeRun(val tvId: TvId, val planId: PlanId) : StartDestination
    data class ContinueSetup(val tvId: TvId) : StartDestination
    data class Manage(val tvId: TvId) : StartDestination
}

suspend fun resolveStart(tvs: List<Tv>, journal: Journal): StartDestination {
    val byRecent = tvs.sortedByDescending { it.lastSeenAt }
    for (tv in byRecent) journal.openRuns(tv.id).firstOrNull()?.let { return StartDestination.ResumeRun(tv.id, it) }
    byRecent.firstOrNull { it.wizard != null }?.let { return StartDestination.ContinueSetup(it.id) }
    return byRecent.firstOrNull()?.let { StartDestination.Manage(it.id) } ?: StartDestination.Welcome
}

// What the foreground service's notification says (D18): "Setting up Mi TV 4A, 58%" while a plan
// runs, "Look at the TV. Tap to answer." at the check screen, nothing when no run is active.
data class OperationNotice(val tvId: TvId, val text: String, val percent: Int?, val look: Boolean)

object OperationNotices {
    fun of(tvId: TvId, tvName: String, state: RunState, copy: FlowCopy): OperationNotice? = when (state) {
        is RunState.Running -> OperationNotice(tvId, copy.text(CopyKey.NOTIFY_PROGRESS, mapOf("tv" to tvName, "percent" to state.percent)), state.percent, look = false)
        is RunState.AwaitingConfirm -> OperationNotice(tvId, copy.text(CopyKey.NOTIFY_LOOK), null, look = true)
        is RunState.RollingBack, is RunState.InDoubt -> OperationNotice(tvId, copy.text(CopyKey.NOTIFY_PROGRESS, mapOf("tv" to tvName, "percent" to 0)), null, look = false)
        else -> null
    }

    // D18: the service is for a run, install, push or backup longer than 5 s. A plan with a transfer,
    // an install or more than 5 shell steps is treated as long; a single "Put back" is not.
    fun needsForeground(plan: Plan): Boolean =
        plan.steps.size > 5 || plan.steps.any { it.rendered.transfer != null } || plan.sections.isNotEmpty()
}

// The D1 key: the first 12 hex characters of the signer digest in groups of 4 ("6966 ed15 1f52").
fun keyDigest12(sha: Sha256): String = sha.hex.take(12).chunked(4).joinToString(" ")

// The Allow screen's fingerprint tail: the last two bytes of the MD5 colon hex ("7F:2A").
fun fingerprintTail(fingerprint: String): String =
    fingerprint.split(':').takeLast(2).joinToString(":") { it.uppercase() }

// S1c: RFC 1918 and link-local IPv4 only ("That isn't an address on your home network").
fun isHomeAddress(text: String): Boolean {
    val parts = text.trim().split('.')
    if (parts.size != 4) return false
    val o = parts.map { p -> if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit)) return false else p.toInt() }
    if (o.any { it > 255 }) return false
    return o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168) || (o[0] == 169 && o[1] == 254)
}

// The step-card family of UX 2 from what discovery knows before connecting; the person can change
// it with "Not a Mi TV? Change model". atv: Android TV 9 to 11; gtv: Google TV 12 to 14.
object Families {
    const val ATV = "atv"
    const val GTV = "gtv"
    const val FIRE = "fire"
    const val TCL = "tcl"
    const val UNKNOWN = "unknown"
    val all = listOf(ATV, GTV, FIRE, TCL, UNKNOWN)

    fun of(tv: FoundTv): String {
        val text = listOfNotNull(tv.model, tv.friendlyName).joinToString(" ").lowercase()
        return when {
            text.isBlank() -> UNKNOWN
            text.startsWith("aft") || "fire tv" in text || "firetv" in text -> FIRE
            "chromecast" in text || "google tv" in text || "philips" in text || "benq" in text -> GTV
            "mi tv" in text || "mitv" in text || "xiaomi" in text || "mibox" in text || "mi box" in text ||
                "hisense" in text || "bravia" in text || "sony" in text || "shield" in text -> ATV
            "tcl" in text -> UNKNOWN
            else -> UNKNOWN
        }
    }

    // Brand chips on "Change model" (UX 2, unknown brand row) to the family whose steps apply.
    fun ofBrand(brand: String): String = when (brand.lowercase()) {
        "xiaomi", "hisense", "sony" -> ATV
        "google", "philips" -> GTV
        "amazon" -> FIRE
        "tcl" -> TCL
        else -> UNKNOWN
    }
}
