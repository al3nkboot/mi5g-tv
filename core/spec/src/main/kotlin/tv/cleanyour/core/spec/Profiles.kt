// FROZEN INTERFACE (PRODUCT-SPEC section 5.6, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W4.

package tv.cleanyour.core.spec

import tv.cleanyour.core.model.*

sealed interface ProfileResolution { data class Exact(val profile: Profile) : ProfileResolution
    data class BrandSureSet(val brand: String, val sureSet: List<PackageGroup>, val stockLaunchers: List<Component>) : ProfileResolution
    data class None(val stockLaunchers: List<Component>) : ProfileResolution }
interface ProfileSource { suspend fun all(): List<Profile>; suspend fun resolve(facts: DeviceFacts): ProfileResolution }
data class PackageFacts(val friendlyName: String, val description: String, val category: String)
interface FactsTable { fun lookup(pkg: PackageName): PackageFacts? }
object DebloatTxtParser { fun parse(text: String, folder: String): Profile = DebloatTxt.parse(text, folder, ProfileMeta.bundled.profiles[folder]) }      // upstream examples format
object DockRanking { fun pick(profile: Profile?, installed: Set<PackageName>, hasInputs: Boolean, liveTv: PackageName?, locale: String): List<PackageName> = DockRankingImpl.pick(profile, installed, hasInputs, liveTv, locale) }
