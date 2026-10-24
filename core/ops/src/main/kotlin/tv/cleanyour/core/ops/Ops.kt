// FROZEN INTERFACE (PRODUCT-SPEC section 5.2, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W3.

package tv.cleanyour.core.ops   // R0: 1 owns rendering only; the vocabulary above is in :core:model

import tv.cleanyour.core.model.*
import tv.cleanyour.core.session.TvSession

enum class Privilege { READ, PROFILE_STEP, COMMUNITY, TAKEOVER, SESSION_END }
enum class FailurePolicy { STOP_AND_ROLLBACK, CONTINUE, SKIP_IF_REFUSED }
sealed interface Transfer { data class Push(val src: BlobRef, val dest: RemotePath, val mode: Int = 0x1A4) : Transfer
    data class Pull(val src: RemotePath, val maxBytes: Long) : Transfer   // R0: 3
    data class StreamInstall(val src: BlobRef, val size: Long) : Transfer }
data class Rendered(val op: Op, val lines: List<ShellLine>, val transfer: Transfer?, val privilege: Privilege,
    val verify: Op.Read?, val timeoutMs: Long, val stdoutCap: Int)
sealed interface VerifyResult { data object Ok : VerifyResult; data class Mismatch(val expected: String, val actual: String) : VerifyResult }

interface OpRenderer {
    fun render(op: Op): Rendered                       // throws RenderRefused for any invalid argument
    fun inverse(op: Op, prior: Prior): Op?             // null when not undoable (Read, VerifyHome, PressHome)
    fun check(op: Op, verifyOutput: String, prior: Prior?): VerifyResult
    fun planHash(rendered: List<Rendered>): Sha256 }   // what the UI confirms and the executor compares (R0: 5)
class RenderRefused(val op: Op, val reason: String) : Exception(reason)
// R0: 1 moved from :core:planner: the executor (same layer as the planner) must call the same check with live facts
object Protected { fun isProtected(pkg: PackageName, state: PackageState?, profile: Profile?): String? {   // reason or null
    // Body by W3: the data is in Denylist; the order is static names, the wizard rule, live
    // facts of the package, then the profile's own never_disable list.
    Denylist.staticReason(pkg.value)?.let { return it }
    if (pkg.value in Denylist.setupWizards && !Denylist.isWizardAllowed(pkg, profile)) return Denylist.REASON_SETUP_WIZARD
    if (state?.persistent == true) return Denylist.REASON_PERSISTENT
    if (state?.tvInput == true) return Denylist.REASON_TV_INPUT
    if (profile != null && pkg in profile.neverDisable) return Denylist.REASON_PROFILE
    return null
} }

enum class ConfigTransportKind { FILE_OVERLAY, PROVIDER }
interface ConfigTransport {   // R0: 1 moved from :core:engine: the planner emits its steps; implementations stay in :core:engine (W3)
    val kind: ConfigTransportKind
    suspend fun readDigest(session: TvSession): Sha256?
    fun writePlanSteps(config: BlobRef, wallpaper: BlobRef?): List<Op>   // config = LauncherConfigCodec.encode(doc) stored in the BlobStore; mkdir, push, verify read, restart home
    fun resetPlanSteps(): List<Op> }
