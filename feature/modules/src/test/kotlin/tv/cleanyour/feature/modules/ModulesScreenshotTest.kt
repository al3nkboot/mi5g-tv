package tv.cleanyour.feature.modules

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.core.flows.CardAnswer
import tv.cleanyour.core.flows.IndexView
import tv.cleanyour.core.flows.InstallStep
import tv.cleanyour.core.flows.ModuleDetail
import tv.cleanyour.core.flows.ModuleRow
import tv.cleanyour.core.flows.ModulesState
import tv.cleanyour.core.flows.UpdateConsent
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.Module
import tv.cleanyour.core.model.ModuleVersion
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PermissionStatement
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.model.Tier
import tv.cleanyour.core.registry.FetchFailure
import tv.cleanyour.core.trust.IndexFailure

// Every section 4 state of :feature:modules (PRODUCT-SPEC 4: M3, M3a, M3b, the install narration,
// M3c and its outcomes, the remove, tiers and update re-consent sheets), each in dark, light, 200%
// font and right to left, with ATF on. Sample data: Retro v0.1.0 from the signed index (D6), key
// 6966 ed15 1f52 (D1), accent #E8A33D (DESIGN-SYSTEM 2.1).

private const val RETRO_KEY = "6966 ed15 1f52"
private val signer = Sha256("6966ed151f52" + "0".repeat(52))
private val zeroHash = Sha256("0".repeat(64))

private val retroVersion = ModuleVersion(
    versionCode = 1, versionName = "0.1.0", releasedAt = "3 Oct 2026",
    artifact = ArtifactRef("https://github.com/example/retro/releases/download/v0.1.0/retro.apk", 4_000_000, zeroHash, signer),
    permissions = listOf(
        PermissionStatement("android.permission.INTERNET", "Uses the internet to fetch game files"),
        PermissionStatement("android.permission.READ_EXTERNAL_STORAGE", "Reads USB storage, for your own games"),
    ),
    networkStatement = "", minSdk = 28, abis = emptyList(), postInstall = emptyList(),
)

private val retro = Module(
    id = "retro", name = "Retro", oneLine = "NES games",
    description = "NES games on the TV. Adds a Retro card that opens a library of free, legally shared games. Works with the remote.",
    pkg = PackageName("io.github.udhaykumarbala.retro"), type = "card", tier = Tier.COMMUNITY, publisher = "udhaykumarbala",
    sourceUrl = "https://github.com/udhaykumarbala/retro", licence = "MIT", accent = "#E8A33D", artwork = null, versions = listOf(retroVersion),
)
private val airplay = retro.copy(
    id = "airplay", name = "AirPlay receiver", oneLine = "Phones send video to the TV", type = "service", accent = "#3A86E8",
    description = "Lets phones and tablets send video to this TV.", pkg = PackageName("io.example.airplay"),
    versions = listOf(retroVersion.copy(permissions = listOf(PermissionStatement("android.permission.INTERNET", "Uses the local network to find senders")))),
)
private val pausedModule = retro.copy(tier = Tier.QUARANTINED, description = "The author asked us to pause it while a bug is fixed.")

private fun row(id: String, name: String, oneLine: String, size: Long?, accent: String?, tier: Tier = Tier.COMMUNITY, installed: Boolean = false,
    card: CardAnswer? = null, update: String? = null) =
    ModuleRow(id, name, oneLine, size, licence = "MIT", tier = tier, accent = accent, installed = installed, card = card, updateTo = update)

private val retroRow = row("retro", "Retro", "NES games on the TV.", 4_000_000, "#E8A33D")
private val stremioRow = row("stremio", "Stremio shelf", "Your Stremio library as a row.", 6_500_000, "#7B5BFF")
private val airplayRow = row("airplay", "AirPlay receiver", "Runs in the background.", 3_000_000, null)
private val allRows = listOf(retroRow, stremioRow, airplayRow)

private fun state(
    index: IndexView = IndexView.Ready(stale = false), installed: List<ModuleRow> = emptyList(), available: List<ModuleRow> = allRows,
    query: String = "", detail: ModuleDetail? = null, install: InstallStep? = null,
) = ModulesState(tvName = "Mi TV 4A", index = index, installed = installed, available = available, query = query, detail = detail, install = install)

private fun detail(module: Module = retro, installed: Boolean = false) = ModuleDetail(module, module.versions.first(), RETRO_KEY, installed)

private val installedRows = listOf(
    retroRow.copy(installed = true, card = CardAnswer.SHOWN),
    stremioRow.copy(installed = true, card = CardAnswer.HIDDEN, updateTo = "0.2.0"),
    airplayRow.copy(tier = Tier.QUARANTINED, installed = true),
)

private val consent = UpdateConsent("stremio", "Stremio shelf", "0.2.0",
    listOf(PermissionStatement("android.permission.RECORD_AUDIO", "Listen with the remote's microphone")))

private val tall = 1100.dp

object ModulesShots {
    val all: List<Shot> = listOf(
        // M3 Cards and modules
        Shot("modules_installed_list") { InstalledScreen(state(installed = installedRows), {}, {}, {}) },
        // RFC-CARDS-1: cards off on the TV (a module installed before any card was asked for).
        Shot("modules_cards_off") { InstalledScreen(state(installed = listOf(retroRow.copy(installed = true))).copy(cardsOff = true), {}, {}, {}) },
        Shot("modules_removing") { InstalledScreen(state(installed = installedRows).copy(removing = "retro"), {}, {}, {}) },
        Shot("modules_empty") { InstalledScreen(state(installed = emptyList()), {}, {}, {}) },
        Shot("modules_remove_sheet") { SheetOver({ InstalledScreen(state(installed = installedRows), {}, {}, {}) }) { RemoveContent("Retro", "Mi TV 4A", {}, {}) } },
        Shot("modules_update_consent") { SheetOver({ InstalledScreen(state(installed = installedRows), {}, {}, {}) }) { ConsentContent(consent) {} } },

        // M3a Add to Mi TV 4A
        Shot("addmodule_list") { AddScreen(state(), {}, {}) },
        Shot("addmodule_stale_index") { AddScreen(state(index = IndexView.Ready(stale = true)), {}, {}) },
        Shot("addmodule_loading") { AddScreen(state(index = IndexView.Loading, available = emptyList()), {}, {}) },
        Shot("addmodule_no_match") { AddScreen(state(query = "zzz"), {}, {}) },
        Shot("addmodule_failed_bad_signature") { AddScreen(state(index = IndexView.Failed(IndexFailure.BAD_SIGNATURE, null, hasLast = false), available = emptyList()), {}, {}) },
        Shot("addmodule_failed_expired") { AddScreen(state(index = IndexView.Failed(IndexFailure.EXPIRED, null, hasLast = true)), {}, {}) },
        Shot("addmodule_failed_offline") { AddScreen(state(index = IndexView.Failed(null, FetchFailure.OFFLINE, hasLast = true)), {}, {}) },
        Shot("addmodule_failed_unknown_key") { AddScreen(state(index = IndexView.Failed(IndexFailure.UNKNOWN_KEY, null, hasLast = false), available = emptyList()), {}, {}) },
        Shot("addmodule_tiers_sheet") { SheetOver({ AddScreen(state(), {}, {}) }) { TiersContent() } },

        // M3b Module: Retro (mockup 07)
        Shot("module_detail_retro", tall) { DetailScreen(detail(), "Mi TV 4A", {}, {}) },
        Shot("module_detail_retro_cards_off", tall) { DetailScreen(detail(), "Mi TV 4A", {}, {}, cardsOff = true) },   // RFC-CARDS-1
        Shot("module_detail_retro_installed", tall) { DetailScreen(detail(installed = true), "Mi TV 4A", {}, {}) },
        Shot("module_detail_service", tall) { DetailScreen(detail(airplay), "Mi TV 4A", {}, {}) },
        Shot("module_detail_paused_installed", tall) { DetailScreen(detail(pausedModule, installed = true), "Mi TV 4A", {}, {}) },

        // The install narration, M3c Look at the TV and the outcomes
        Shot("install_downloading") { InstallScreen(InstallStep.Downloading(1_500_000, 4_000_000), state(detail = detail()), {}) },
        Shot("install_checking") { InstallScreen(InstallStep.Checking, state(detail = detail()), {}) },
        Shot("install_installing") { InstallScreen(InstallStep.Installing(60), state(detail = detail()), {}) },
        Shot("cardprompt_asking", tall) { InstallScreen(InstallStep.Asking("Retro", "io.github.udhaykumarbala.retro", RETRO_KEY), state(detail = detail()), {}) },
        Shot("cardprompt_shown") { InstallScreen(InstallStep.Shown("Retro"), state(detail = detail()), {}) },
        Shot("cardprompt_hidden") { InstallScreen(InstallStep.Hidden("Retro"), state(detail = detail()), {}) },
        Shot("install_failed_signer_mismatch") { InstallScreen(InstallStep.Failed("Retro", FetchFailure.SIGNER_MISMATCH), state(detail = detail()), {}) },
        Shot("install_failed_hash_mismatch") { InstallScreen(InstallStep.Failed("Retro", FetchFailure.HASH_MISMATCH), state(detail = detail()), {}) },
        Shot("install_failed_offline") { InstallScreen(InstallStep.Failed("Retro", FetchFailure.OFFLINE), state(detail = detail()), {}) },
    )
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h2400dp-xhdpi")
class ModulesScreenshotTest(private val shot: Shot, private val variant: Variant) {
    @get:Rule val compose = createComposeRule()

    @Before fun fixedTimeZone() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }

    @Test fun shot() = compose.shoot(shot, variant)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun params(): List<Array<Any>> = ModulesShots.all.flatMap { s -> Variant.entries.map { v -> arrayOf<Any>(s, v) } }
    }
}
