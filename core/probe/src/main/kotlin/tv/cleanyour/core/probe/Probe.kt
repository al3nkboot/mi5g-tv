// FROZEN INTERFACE (PRODUCT-SPEC section 5.5, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W4.

package tv.cleanyour.core.probe

import tv.cleanyour.core.model.*
import tv.cleanyour.core.session.TvSession

enum class ProbeStage { FACTS, PACKAGES, HOME, INPUTS, SETTINGS, LAUNCHER, DONE }
interface Prober {
    suspend fun facts(session: TvSession): DeviceFacts
    suspend fun snapshot(session: TvSession, reason: SnapshotReason, onStage: (ProbeStage) -> Unit = {}): Snapshot
    suspend fun homeNow(session: TvSession): HomeResolution
    suspend fun homeFocus(session: TvSession): Component?        // resumed activity, for the live check
    suspend fun screenshot(session: TvSession): ByteArray?       // PNG, 2 MiB cap, null on failure
    suspend fun fileSha256(session: TvSession, path: RemotePath): Sha256?
    suspend fun installedSha256(session: TvSession, pkg: PackageName): Sha256?
    suspend fun freeDataMb(session: TvSession): Int
    suspend fun installRefusal(session: TvSession, noopApk: BlobRef): Boolean
    suspend fun watchdogAlive(session: TvSession, token: RunToken): Boolean
    suspend fun bootId(session: TvSession): String }
