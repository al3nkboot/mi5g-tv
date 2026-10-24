// FROZEN INTERFACE (PRODUCT-SPEC section 5.7, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W4.

package tv.cleanyour.core.spec

import tv.cleanyour.core.model.*
import kotlinx.serialization.json.JsonElement

data class LauncherConfigDoc(val dockApps: List<PackageName>? = null, val hiddenApps: List<PackageName>? = null,
    val liveTvPackage: String? = null, val liveTvAction: String? = null, val sourcePanelService: String? = null,
    val sourcePanelAction: String? = null, val columns: Int? = null, val rowsBelowDock: Int? = null, val shamsiDate: Boolean? = null,
    val vpnApp: String? = null, val showHomeSettingsShortcut: Boolean? = null, val backgroundColor: String? = null,
    val wallpaper: Boolean? = null, val unknown: Map<String, JsonElement> = emptyMap(),
    val inputAction: String? = null, val moduleCards: Boolean? = null,                                 // RFC-W4-1
    val fillDock: Boolean? = null, val animations: Boolean? = null, val advancedEffects: Boolean? = null,  // RFC-W4-2
    val greetingName: String? = null, val reducedMotion: Boolean? = null, val dockTitle: Boolean? = null,
    val dockRow: Boolean? = null, val reorderButton: Boolean? = null, val showStats: Boolean? = null,
    val appLibrary: Boolean? = null, val skyIntensity: Int? = null, val settingsKeyKeycodes: List<Int>? = null,
    val settingsKeyPanel: Boolean? = null, val festival: String? = null, val festivalUntil: String? = null,
    val greeting: String? = null, val accentColor: String? = null,
    val launchActions: List<String>? = null, val appLabels: List<String>? = null, val appBanners: List<String>? = null,  // RFC-W4-3
    val inputKeyKeycodes: List<Int>? = null, val appKeys: List<String>? = null, val cinematicHome: Boolean? = null,
    val wallpaperRotationMinutes: Int? = null, val wallpaperDim: Int? = null, val raceCalendar: List<LauncherRace>? = null)
data class EffectiveConfig(val dockApps: List<String>, val hiddenApps: List<String>, val liveTvPackage: String, val liveTvAction: String,
    val sourcePanelService: String, val sourcePanelAction: String, val columns: Int, val rowsBelowDock: Int, val shamsiDate: Boolean,
    val vpnApp: String, val showHomeSettingsShortcut: Boolean, val backgroundColorArgb: Int, val wallpaper: Boolean,
    val inputAction: String, val moduleCards: Boolean,                                                  // RFC-W4-1
    val fillDock: Boolean = true, val animations: Boolean = true, val advancedEffects: Boolean = false,    // RFC-W4-2
    val greetingName: String = "", val reducedMotion: Boolean = false, val dockTitle: Boolean = true,
    val dockRow: Boolean = true, val reorderButton: Boolean = false, val showStats: Boolean = false,
    val appLibrary: Boolean = false, val skyIntensity: Int = 0, val settingsKeyKeycodes: List<Int> = emptyList(),
    val settingsKeyPanel: Boolean = false, val festival: String = "", val festivalUntil: String = "",
    val greeting: String = "", val accentColorArgb: Int = 0,
    val launchActions: Map<String, String> = emptyMap(), val appLabels: Map<String, String> = emptyMap(),  // RFC-W4-3
    val appBanners: Map<String, String> = emptyMap(), val inputKeyKeycodes: List<Int> = emptyList(),
    val appKeys: List<String> = emptyList(), val cinematicHome: Boolean = false, val wallpaperRotationMinutes: Int = 0,
    val wallpaperDim: Int = 0, val raceCalendar: List<LauncherRace> = emptyList())
data class LauncherRace(val round: Int, val name: String, val place: String, val sessions: List<LauncherRaceSession>)  // RFC-W4-3
data class LauncherRaceSession(val name: String, val start: String)
sealed interface DecodeResult { data class Ok(val doc: LauncherConfigDoc, val warnings: List<String>) : DecodeResult; data class Rejected(val reason: String) : DecodeResult }
object LauncherConfigCodec {
    fun decode(bytes: ByteArray): DecodeResult = ConfigCodecImpl.decode(bytes)                       // Config.java semantics: 64 KB cap, BOM, per-key type and range
    fun encode(doc: LauncherConfigDoc): ByteArray = ConfigCodecImpl.encode(doc)                     // canonical key order, UTF-8, no BOM, no unknown keys unless carried
    fun effective(doc: LauncherConfigDoc?, defaults: EffectiveConfig): EffectiveConfig = ConfigCodecImpl.effective(doc, defaults)
    val builtInDefaults: EffectiveConfig get() = ConfigCodecImpl.BUILT_IN }
data class Theme(val id: String, val name: String, val description: String, val doc: LauncherConfigDoc, val wallpaper: BlobRef?)
interface ThemeSource { fun bundled(): List<Theme> }
