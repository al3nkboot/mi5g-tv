package tv.cleanyour.core.spec

import android.content.MemoryPreferences
import com.gathin.tvhome.pinned.PinnedConfigBridge
import com.gathin.tvhome.previous.PreviousConfigBridge
import tv.cleanyour.core.spec.upstream.UpstreamConfig
import tv.cleanyour.testing.fixtures.Fixtures
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// Runs the real upstream Config.java against a folder that plays the launcher's external files
// dir, with resource values read from the matching config.xml. `pinned` is the launcher the app
// bundles (v2.5.0: Config.java and Festival.java copied from the submodule at build time,
// WallpaperRotation.java and RaceWeekend.java vendored in the test sources and checked against
// the submodule); the
// other is launcher 2.0 (main 1bbf0c9, vendored in the test sources), the model of a TV that has
// not been updated yet.
class UpstreamLauncher(val pinned: Boolean, private val dir: File) {
    val values: Map<String, Any> = parseConfigXml(
        if (pinned) File(Fixtures.upstreamDir, "launcher/app/src/main/res/values/config.xml")
        else File(javaClass.getResource("/upstream/config-1bbf0c9.xml")!!.toURI()),
    )

    // The launcher's private launcher_settings (v2.4.0 and later): the choices made on the TV. It lives as
    // long as this launcher, as it does on a TV, so a test can make a choice and push after it.
    val choices = MemoryPreferences()

    // Every push gets a newer modification time, as AdbTvSession's sync sets the phone's clock.
    private var pushes = 0L

    sealed interface Outcome {
        data class Loaded(val config: UpstreamConfig) : Outcome
        data class Crashed(val error: Throwable) : Outcome
    }

    // Pushes configJson (null removes config.json) and the wallpaper, then starts the launcher.
    fun load(configJson: ByteArray?, wallpaper: Boolean): Outcome {
        val cfg = File(dir, "config.json")
        val wp = File(dir, "wallpaper.jpg")
        if (configJson == null) cfg.delete() else {
            cfg.writeBytes(configJson)
            cfg.setLastModified(1_760_000_000_000L + 1_000L * ++pushes)
        }
        if (wallpaper) wp.writeBytes(byteArrayOf(1, 2, 3)) else wp.delete()
        return restart()
    }

    // Starts the launcher again on the files as they are (Home restarted on the TV, no push).
    fun restart(): Outcome = try {
        Outcome.Loaded(if (pinned) PinnedConfigBridge.load(dir, values, choices) else PreviousConfigBridge.load(dir, values))
    } catch (e: RuntimeException) {
        Outcome.Crashed(e)
    }

    // The EffectiveConfig the launcher starts from with no overlay (Config.load's resource reads).
    // Launcher 2.0 does not read the keys v2.4.0 and v2.5.0 added; it gets the built-in values for them.
    fun defaults(): EffectiveConfig {
        @Suppress("UNCHECKED_CAST")
        fun list(k: String) = (values.getValue(k) as Array<String>).toList()
        fun s(k: String) = (values.getValue(k) as String).javaTrim()
        fun b(k: String) = values.getValue(k) as Boolean
        fun map(k: String) = list(k).mapNotNull { ConfigCodecImpl.packageMapEntry(it.javaTrim()) }.toMap()
        val base = EffectiveConfig(
            dockApps = list("dock_apps"), hiddenApps = list("hidden_apps"), liveTvPackage = s("live_tv_package"),
            liveTvAction = s("live_tv_action"), sourcePanelService = s("source_panel_service"), sourcePanelAction = s("source_panel_action"),
            columns = values.getValue("columns") as Int, rowsBelowDock = values.getValue("rows_below_dock") as Int,
            shamsiDate = b("shamsi_date"), vpnApp = s("vpn_app"),
            showHomeSettingsShortcut = b("show_home_settings_shortcut"),
            backgroundColorArgb = 0xFF000000.toInt(), wallpaper = true,
            inputAction = s("input_action"), moduleCards = b("module_cards"),
        )
        if (!pinned) return base
        return base.copy(
            fillDock = b("fill_dock"), animations = b("animations"), advancedEffects = b("advanced_effects"),
            greetingName = s("greeting_name"), reducedMotion = b("reduced_motion"), dockTitle = b("dock_title"),
            dockRow = b("dock_row"), reorderButton = b("reorder_button"), showStats = b("show_stats"),
            appLibrary = b("app_library"), skyIntensity = values.getValue("sky_intensity") as Int,
            settingsKeyKeycodes = (values.getValue("settings_key_keycodes") as IntArray).toList(),
            settingsKeyPanel = b("settings_key_panel"),
            launchActions = map("launch_actions"), appLabels = map("app_labels"), appBanners = map("app_banners"),
            inputKeyKeycodes = (values.getValue("input_key_keycodes") as IntArray).toList(),
            appKeys = list("app_keys").map { it.javaTrim() }.filter { it.isNotEmpty() }, cinematicHome = b("cinematic_home"),
        )
    }

    companion object {
        // aapt keeps string resources trimmed; these values are plain words, so text content is enough.
        fun parseConfigXml(f: File): Map<String, Any> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val out = LinkedHashMap<String, Any>()
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                val name = e.getAttribute("name")
                val items = e.getElementsByTagName("item")
                out[name] = when (e.tagName) {
                    "string-array" -> Array(items.length) { items.item(it).textContent.trim() }
                    "integer-array" -> IntArray(items.length) { items.item(it).textContent.trim().toInt() }
                    "string" -> e.textContent.trim()
                    "integer" -> e.textContent.trim().toInt()
                    "bool" -> e.textContent.trim().toBooleanStrict()
                    else -> continue
                }
            }
            return out
        }
    }
}
