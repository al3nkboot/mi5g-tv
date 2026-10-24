// FROZEN INTERFACE (PRODUCT-SPEC section 5.11, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W1 (data; W5 produces it).

package tv.cleanyour.core.model   // R0: 1 index data moved here from :core:trust: the planner (AddModule, Setup) names these types

enum class Tier { VERIFIED, COMMUNITY, QUARANTINED, REVOKED }
data class ArtifactRef(val url: String, val size: Long, val sha256: Sha256, val signer: Sha256)
data class PermissionStatement(val permission: String, val plainWords: String)
data class ModuleVersion(val versionCode: Long, val versionName: String, val releasedAt: String, val artifact: ArtifactRef,
    val permissions: List<PermissionStatement>, val networkStatement: String, val minSdk: Int, val abis: List<String>, val postInstall: List<Op>)
data class Module(val id: String, val name: String, val oneLine: String, val description: String, val pkg: PackageName, val type: String,
    val tier: Tier, val publisher: String, val sourceUrl: String, val licence: String, val accent: String?, val artwork: String?, val versions: List<ModuleVersion>)
data class LauncherArtifact(val pkg: PackageName, val versionCode: Long, val versionName: String, val sha256: Sha256, val signer: Sha256, val blob: BlobRef)
data class ApkInfo(val pkg: PackageName, val versionCode: Long, val versionName: String, val signer: Sha256, val sha256: Sha256, val size: Long, val method: String)
