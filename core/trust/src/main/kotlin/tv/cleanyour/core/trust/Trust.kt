// FROZEN INTERFACE (PRODUCT-SPEC section 5.11, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W5.

package tv.cleanyour.core.trust

import tv.cleanyour.core.model.*

data class MinisignKey(val id: ByteArray, val publicKey: ByteArray)
sealed interface VerifyOutcome { data class Ok(val trustedComment: String) : VerifyOutcome; data class Fail(val reason: String) : VerifyOutcome }
interface Minisign { fun verify(data: ByteArray, signature: ByteArray, keys: List<MinisignKey>): VerifyOutcome }
data class KeySet(val root: MinisignKey, val active: List<MinisignKey>, val revoked: List<ByteArray>)
data class VerifiedIndex(val sequence: Long, val expiresAtMs: Long, val modules: List<Module>, val revocations: List<Sha256>)
enum class IndexFailure { BAD_SIGNATURE, UNKNOWN_KEY, REVOKED_KEY, BAD_KEYS_FILE, EXPIRED, ROLLBACK, MALFORMED }   // R0: 13 one per F9 copy line
sealed interface IndexVerdict { data class Ok(val index: VerifiedIndex) : IndexVerdict; data class Bad(val failure: IndexFailure, val detail: String) : IndexVerdict }   // R0: 13
interface IndexVerifier { fun verify(indexBytes: ByteArray, sigBytes: ByteArray, keysBytes: ByteArray, keysSig: ByteArray, lastSequence: Long?, nowMs: Long): IndexVerdict }
interface LauncherPin { fun bundled(): LauncherArtifact }
interface CurtainPin { fun bundled(): LauncherArtifact }   // RFC-CURTAIN-1: the TV curtain, pinned like the launcher
interface ApkInspector { fun inspect(blob: BlobRef): ApkInfo? }   // R0: 13 null when the file is not a readable APK
