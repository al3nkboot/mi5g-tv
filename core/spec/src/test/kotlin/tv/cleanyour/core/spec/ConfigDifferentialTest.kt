package tv.cleanyour.core.spec

import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.testing.fixtures.Fixtures
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

// F8 acceptance: upstream Config.java and LauncherConfigCodec agree on every theme fixture and on
// 10,000 generated files, so the phone's preview can never disagree with the TV. Upstream runs
// for real: its Config.java (and v2.4.0's Festival.java) compiled unchanged on the JVM against
// AOSP's own org.json and Color.parseColor (test sources) and stubs for Context, Resources,
// SharedPreferences, Log and Cards.cleanLabel. Two launchers: v2.5.0, the one the app bundles,
// and 2.0 (1bbf0c9), which a TV set up before the first update still runs; 2.0 ignores the keys
// v2.4.0 and v2.5.0 added, so only the keys both read are compared there. (A TV on our 2.4 reads
// v2.5.0's keys up to RFC-W4-2 the same way, since 2.5 changed none of them.)
class ConfigDifferentialTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun validPkg(s: String) = runCatching { PackageName(s) }.isSuccess

    private class Tally { var applied = 0; var ignored = 0; var crashed = 0; var withWarnings = 0 }

    // One file through both sides; returns which way the launcher went.
    private fun agree(up: UpstreamLauncher, bytes: ByteArray?, wallpaper: Boolean, label: String, t: Tally) {
        val upstream = up.load(bytes, wallpaper)
        val ours = bytes?.let { LauncherConfigCodec.decode(it) }
        fun ctx() = "$label: ${bytes?.let { String(it, Charsets.UTF_8).take(400) }}"
        when (upstream) {
            is UpstreamLauncher.Outcome.Crashed -> {
                t.crashed++
                assertTrue(upstream.error is StringIndexOutOfBoundsException, "unexpected upstream crash ${upstream.error} for ${ctx()}")
                assertTrue(ours is DecodeResult.Rejected && ours.reason.contains("crash"), "upstream crashes but we said $ours for ${ctx()}")
            }
            is UpstreamLauncher.Outcome.Loaded -> {
                val u = upstream.config
                if (bytes != null && !u.overlayApplied) {
                    t.ignored++
                    assertTrue(ours is DecodeResult.Rejected && !ours.reason.contains("crash"), "upstream ignores the file but we said $ours for ${ctx()}")
                }
                val doc = when (ours) {
                    null -> null
                    is DecodeResult.Ok -> { t.applied++; if (ours.warnings.isNotEmpty()) t.withWarnings++; ours.doc }
                    is DecodeResult.Rejected -> {
                        assertTrue(!u.overlayApplied, "we rejected (${ours.reason}) a file upstream applied: ${ctx()}")
                        null
                    }
                }
                val e = LauncherConfigCodec.effective(doc, up.defaults())
                val mine = listOf(
                    e.dockApps, e.hiddenApps, e.liveTvPackage, e.liveTvAction, e.sourcePanelService, e.sourcePanelAction,
                    e.columns, e.rowsBelowDock, e.shamsiDate, e.vpnApp, e.showHomeSettingsShortcut, e.backgroundColorArgb,
                    e.wallpaper && wallpaper, e.inputAction, e.moduleCards,
                ) + if (up.pinned) listOf(
                    e.fillDock, e.animations, e.advancedEffects, e.greetingName, e.reducedMotion, e.dockTitle, e.dockRow,
                    e.reorderButton, e.showStats, e.appLibrary, e.skyIntensity, e.settingsKeyKeycodes, e.settingsKeyPanel,
                    e.festival, e.festivalUntil, e.greeting, e.accentColorArgb,
                    e.launchActions, e.appLabels, e.appBanners, e.inputKeyKeycodes, e.appKeys, e.cinematicHome,
                    e.wallpaperRotationMinutes, e.wallpaperDim, e.raceCalendar.map(::raceText),
                ) else emptyList()
                // Entries that are not package names can never match an app; the codec drops them.
                val theirs = listOf(
                    u.dockApps.filter(::validPkg), u.hiddenApps.filter(::validPkg), u.liveTvPackage, u.liveTvAction,
                    u.sourcePanelService, u.sourcePanelAction, u.columns, u.rowsBelowDock, u.shamsiDate, u.vpnApp,
                    u.showHomeSettingsShortcut, u.backgroundColor, u.wallpaperShown, u.inputAction, u.moduleCards,
                ) + if (up.pinned) u.v24.let { v -> listOf(
                    v.fillDock, v.animations, v.advancedEffects, v.greetingName, v.reducedMotion, v.dockTitle, v.dockRow,
                    v.reorderButton, v.showStats, v.appLibrary, v.skyIntensity, v.settingsKeyKeycodes, v.settingsKeyPanel,
                    v.festival, v.festivalUntil, v.greeting, v.accent,
                ) } + u.v25.let { v -> listOf(
                    v.launchActions, v.appLabels, v.appBanners, v.inputKeyKeycodes, v.appKeys, v.cinematicHome,
                    v.wallpaperRotationMinutes, v.wallpaperDim, v.raceCalendar,
                ) } else emptyList()
                if (mine != theirs) fail("effective config differs for ${ctx()}\n ours:     $mine\n upstream: $theirs")
            }
        }
    }

    // A race as the bridge prints RaceWeekend's: "round|name|place|SESSION@start;...".
    private fun raceText(r: LauncherRace) = "${r.round}|${r.name}|${r.place}|" + r.sessions.joinToString(";") { "${it.name}@${it.start}" }

    private fun bothLaunchers() = listOf(
        UpstreamLauncher(pinned = true, dir = tmp.newFolder("bundled")),
        UpstreamLauncher(pinned = false, dir = tmp.newFolder("previous")),
    )

    @Test fun builtInDefaultsEqualTheLaunchersConfigXml() {
        for (up in bothLaunchers()) assertEquals(up.defaults(), LauncherConfigCodec.builtInDefaults)
        val previous = UpstreamLauncher.parseConfigXml(java.io.File(javaClass.getResource("/upstream/config-1bbf0c9.xml")!!.toURI()))
        val b = LauncherConfigCodec.builtInDefaults
        assertEquals(b.inputAction to b.moduleCards, previous.getValue("input_action") to previous.getValue("module_cards"))
    }

    @Test fun themeFixturesAndHandWrittenCasesAgree() {
        val cases = Fixtures.upstreamThemes().map { "theme ${it.name}" to it.configJson } + listOf(
            "no file" to null,
            "empty" to ByteArray(0),
            "bom" to "﻿{\"columns\": 6}".encodeToByteArray(),
            "two boms" to "﻿﻿{\"columns\": 6}".encodeToByteArray(),
            "three boms" to "﻿﻿﻿{\"columns\": 6}".encodeToByteArray(),
            "empty colour crashes Home" to "{\"background_color\": \"\"}".encodeToByteArray(),
            "blank colour crashes Home" to "{\"background_color\": \"  \"}".encodeToByteArray(),
            "long truncates" to "{\"columns\": 4294967302}".encodeToByteArray(),
            "octal" to "{\"columns\": 07, rows_below_dock = 03}".encodeToByteArray(),
            "nan forbidden" to "{\"columns\": NaN}".encodeToByteArray(),
            "nan in a list is fine" to "{\"dock_apps\": [NaN, \"a.b\"]}".encodeToByteArray(),
            "trailing garbage" to "{\"columns\": 3} and more".encodeToByteArray(),
            "64 KB exactly" to ("{\"columns\": 2}" + " ".repeat(65536 - 14)).encodeToByteArray(),
            "64 KB plus one" to ("{\"columns\": 2}" + " ".repeat(65537 - 14)).encodeToByteArray(),
            "extras" to "{\"input_action\": \" com.xgimi.action.hdmiPlayer \", \"module_cards\": false}".encodeToByteArray(),
            "extras wrong types" to "{\"input_action\": 3, \"module_cards\": \"false\"}".encodeToByteArray(),
            "wallpaper off" to "{\"wallpaper\": false}".encodeToByteArray(),
            "wallpaper not a bool" to "{\"wallpaper\": \"no\"}".encodeToByteArray(),
            "v2.4 keys" to ("{\"fill_dock\": false, \"animations\": false, \"advanced_effects\": true, \"greeting_name\": \" Mr. Trịnh \", " +
                "\"reduced_motion\": true, \"dock_title\": false, \"dock_row\": false, \"reorder_button\": true, \"show_stats\": true, " +
                "\"app_library\": true, \"sky_intensity\": 60, \"settings_key_keycodes\": [83, 384], \"settings_key_panel\": true}").encodeToByteArray(),
            "v2.4 keys wrong types" to "{\"fill_dock\": 0, \"greeting_name\": [], \"sky_intensity\": \"60\", \"settings_key_keycodes\": 83}".encodeToByteArray(),
            "sky out of range" to "{\"sky_intensity\": 101}".encodeToByteArray(),
            "sky truncates" to "{\"sky_intensity\": 99.9}".encodeToByteArray(),
            "keycodes all or nothing" to "{\"settings_key_keycodes\": [83, 0]}".encodeToByteArray(),
            "keycodes with a string" to "{\"settings_key_keycodes\": [83, \"84\"]}".encodeToByteArray(),
            "keycodes truncate and wrap" to "{\"settings_key_keycodes\": [4.9, 4294967379, 1e400]}".encodeToByteArray(),
            "keycodes empty" to "{\"settings_key_keycodes\": []}".encodeToByteArray(),
            "keycodes with an empty slot" to "{\"settings_key_keycodes\": [83,,84]}".encodeToByteArray(),
            "keycodes take the arrows" to "{\"settings_key_keycodes\": [19, 23, 66]}".encodeToByteArray(),
            "festival" to "{\"festival\": \" Diwali \", \"festival_until\": \"2026-11-09\", \"greeting\": \"  Happy\u2028 Diwali  \", \"accent_color\": \"#ffb23c\"}".encodeToByteArray(),
            "festival no end" to "{\"festival\": \"dussehra\"}".encodeToByteArray(),
            "festival bad date" to "{\"festival\": \"dussehra\", \"festival_until\": \"2026-02-30\"}".encodeToByteArray(),
            "festival bad colour" to "{\"festival\": \"dussehra\", \"accent_color\": \"#FFF\"}".encodeToByteArray(),
            "festival too long" to ("{\"festival\": \"" + "x".repeat(41) + "\"}").encodeToByteArray(),
            "festival grows when lower-cased" to ("{\"festival\": \"" + "\u0130".repeat(21) + "\"}").encodeToByteArray(),
            "festival long greeting" to ("{\"festival\": \"x\", \"greeting\": \"" + "g".repeat(39) + "\uD83D\uDE00 tail\"}").encodeToByteArray(),
            "greeting without festival" to "{\"greeting\": \"hi\", \"accent_color\": \"#123456\"}".encodeToByteArray(),
            "v2.5 keys" to ("{\"launch_actions\": [\" com.telekom.onetv.tv.pl = com.telekom.onetv.tv.pl.action.PROGRAM_GUIDE \", \"=x\", \"a.b=\", \"no equals\"], " +
                "\"app_labels\": [\"com.telekom.onetv.tv.pl=TV\", \"com.telekom.onetv.tv.pl=Later wins\", 5], \"app_banners\": [\"com.android.tv=banner_tv\", \"a.b=banner_none\"], " +
                "\"input_key_keycodes\": [192], \"app_keys\": [\"190:com.google.android.youtube.tv\", \"KEYCODE_BUTTON_3 : com.google.android.youtube.tv\", \"0:a.b\", \"x:a.b\", \"191:\", \"1234567:a.b\"], " +
                "\"cinematic_home\": true, \"wallpaper_rotation_minutes\": 30, \"wallpaper_dim\": 40}").encodeToByteArray(),
            "v2.5 keys wrong types" to "{\"launch_actions\": \"a=b\", \"input_key_keycodes\": [192, 0], \"cinematic_home\": 1, \"wallpaper_rotation_minutes\": 1441, \"wallpaper_dim\": 90.9}".encodeToByteArray(),
            "v2.5 keys take the arrows" to "{\"input_key_keycodes\": [23], \"app_keys\": [\"KEYCODE_DPAD_CENTER:a.b\", \"4:a.b\"]}".encodeToByteArray(),
            "race calendar" to ("{\"race_calendar\": [{\"round\": 18, \"name\": \" United States Grand Prix \", \"place\": \"Austin\", \"sessions\": [" +
                "{\"name\": \"race\", \"start\": \"2026-10-25T19:00:00Z\"}, {\"name\": \" fp1 \", \"start\": \"2026-10-23T17:30:00Z\"}, " +
                "{\"name\": \"\", \"start\": \"2026-10-23T17:30:00Z\"}, {\"name\": \"FP2\", \"start\": \"tomorrow\"}, 7]}, " +
                "{\"round\": \"19.7\", \"sessions\": [{\"name\": 5, \"start\": \"2026-11-01T18:00:00Z\"}]}, {\"round\": 20, \"sessions\": []}, " +
                "{\"name\": [1, \"a/b\", null, 2.0, -0.0], \"place\": {\"k\": \"v\"}, \"sessions\": [{\"name\": null, \"start\": \"2026-11-08T17:00:00Z\"}]}, \"not a race\"]}").encodeToByteArray(),
            "race calendar not a list" to "{\"race_calendar\": {\"round\": 1}}".encodeToByteArray(),
            "invalid utf8" to byteArrayOf('{'.code.toByte(), '"'.code.toByte(), 0xC3.toByte(), '"'.code.toByte(), ':'.code.toByte(), '1'.code.toByte(), '}'.code.toByte()),
        )
        for (up in bothLaunchers()) {
            val t = Tally()
            for ((label, bytes) in cases) for (wp in listOf(true, false)) agree(up, bytes, wp, label = label, t = t)
            assertTrue(t.crashed > 0 && t.ignored > 0 && t.applied > 0)
        }
    }

    // WallpaperRotation and RaceWeekend, which v2.5.0's Config.java calls, are vendored in the test
    // sources; they must be the submodule's own files, with only the package line changed.
    @Test fun vendoredLauncherClassesAreTheSubmodulesOwn() {
        for (name in listOf("WallpaperRotation.java", "RaceWeekend.java")) {
            val upstream = java.io.File(Fixtures.upstreamDir, "launcher/app/src/main/java/com/gathin/tvhome/$name").readText()
            val vendored = java.io.File(Fixtures.repoRoot, "core/spec/src/test/java/com/gathin/tvhome/pinned/$name").readText()
            assertEquals(upstream.replace("package com.gathin.tvhome;", "package com.gathin.tvhome.pinned;"), vendored, name)
        }
    }

    @Test fun tenThousandGeneratedFilesAgreeWithTheBundledLauncher() {
        val up = UpstreamLauncher(pinned = true, dir = tmp.newFolder("gen"))
        val fuzz = ConfigFuzz(seed = 0x5EED_0001)
        val t = Tally()
        repeat(10_000) { i -> agree(up, fuzz.next(), wallpaper = i % 3 != 0, label = "generated #$i", t = t) }
        println("launcher v2.5.0: ${t.applied} applied (${t.withWarnings} with warnings), ${t.ignored} ignored, ${t.crashed} would crash")
        assertTrue(t.applied > 4_000 && t.ignored > 1_000 && t.crashed > 10 && t.withWarnings > 1_000, "generator lost coverage: applied ${t.applied}, ignored ${t.ignored}, crashed ${t.crashed}")
    }

    @Test fun generatedFilesAgreeWithLauncher20() {
        val up = UpstreamLauncher(pinned = false, dir = tmp.newFolder("gen-previous"))
        val fuzz = ConfigFuzz(seed = 0x5EED_0002)
        val t = Tally()
        repeat(3_000) { i -> agree(up, fuzz.next(), wallpaper = i % 2 == 0, label = "launcher 2.0 #$i", t = t) }
    }

    // Launcher settings on the TV (v2.4.0) can choose 4 to 7 tiles per row and the motion; the
    // choices win over config.json until a push changes the file, which every push does (a new
    // modification time). A restart without a push keeps them.
    @Test fun aChoiceMadeOnTheTvLastsUntilTheNextPush() {
        val up = UpstreamLauncher(pinned = true, dir = tmp.newFolder("choices"))
        val pushed = LauncherConfigCodec.encode(LauncherConfigDoc(columns = 4, animations = true))
        fun loaded(o: UpstreamLauncher.Outcome) = assertIs<UpstreamLauncher.Outcome.Loaded>(o).config
        assertEquals(4, loaded(up.load(pushed, wallpaper = false)).columns)
        up.choices.values["columns"] = 6
        up.choices.values["animations"] = false
        val chosen = loaded(up.restart())
        assertEquals(6, chosen.columns)
        assertEquals(false, chosen.v24.animations)
        up.choices.values["columns"] = 9   // outside 4 to 7: the launcher ignores the choice
        assertEquals(4, loaded(up.restart()).columns)
        up.choices.values["columns"] = 6
        val again = loaded(up.load(pushed, wallpaper = false))
        assertEquals(4, again.columns, "the same bytes pushed again still clear the choice")
        assertEquals(true, again.v24.animations)
        assertEquals(null, up.choices.values["columns"])
        val e = LauncherConfigCodec.effective(assertIs<DecodeResult.Ok>(LauncherConfigCodec.decode(pushed)).doc, up.defaults())
        assertEquals(e.columns to e.animations, again.columns to again.v24.animations)
    }

    // Launcher 2.5 adds two choices on the TV: rows below the dock (0 to 3) and a wallpaper pick
    // ("none" or a picture's name). Like the columns they win until a push changes config.json.
    @Test fun launcher25ChoicesOnTheTvLastUntilTheNextPush() {
        val up = UpstreamLauncher(pinned = true, dir = tmp.newFolder("choices25"))
        val pushed = LauncherConfigCodec.encode(LauncherConfigDoc(rowsBelowDock = 1, wallpaper = true))
        fun loaded(o: UpstreamLauncher.Outcome) = assertIs<UpstreamLauncher.Outcome.Loaded>(o).config
        loaded(up.load(pushed, wallpaper = true)).let { assertEquals(1 to true, it.rowsBelowDock to it.wallpaperShown) }
        up.choices.values["rows_below_dock"] = 3
        up.choices.values["wallpaper_choice"] = "none"
        loaded(up.restart()).let { assertEquals(3 to false, it.rowsBelowDock to it.wallpaperShown) }
        up.choices.values["rows_below_dock"] = 4   // outside 0 to 3: ignored
        assertEquals(1, loaded(up.restart()).rowsBelowDock)
        up.choices.values["rows_below_dock"] = 2
        loaded(up.load(pushed, wallpaper = true)).let { assertEquals(1 to true, it.rowsBelowDock to it.wallpaperShown, "a push clears both") }
        assertEquals(null, up.choices.values["rows_below_dock"])
        assertEquals(null, up.choices.values["wallpaper_choice"])
    }

    // Our own encoder's output reads back to the same doc and the TV sees what effective() says.
    @Test fun encodedDocsReadBackIdenticallyOnBothSides() {
        val up = UpstreamLauncher(pinned = true, dir = tmp.newFolder("enc"))
        val r = Random(0x5EED_0003)
        val pkgs = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "in.startv.hotstar", "com.android.tv", "a.b")
        val strings = listOf("", "com.android.tv", "with \"quotes\" and \\ slash", "tab\tinside", "ünï", "\u0001", "emoji 😀", "lone \uD800 surrogate")
        val t = Tally()
        repeat(2_000) { i ->
            fun <T> maybe(v: () -> T): T? = if (r.nextBoolean()) v() else null
            var doc = LauncherConfigDoc(
                dockApps = maybe { List(r.nextInt(6)) { PackageName(pkgs.random(r)) } },
                hiddenApps = maybe { List(r.nextInt(3)) { PackageName(pkgs.random(r)) } },
                liveTvPackage = maybe { strings.random(r) }, liveTvAction = maybe { strings.random(r) },
                sourcePanelService = maybe { strings.random(r) }, sourcePanelAction = maybe { strings.random(r) },
                columns = maybe { r.nextInt(1, 9) }, rowsBelowDock = maybe { r.nextInt(0, 4) }, shamsiDate = maybe { r.nextBoolean() },
                vpnApp = maybe { strings.random(r) }, showHomeSettingsShortcut = maybe { r.nextBoolean() },
                backgroundColor = maybe { listOf("#070A14", "#80FF0000", "navy", "Teal", "#000000").random(r) }, wallpaper = maybe { r.nextBoolean() },
                unknown = if (r.nextInt(4) == 0) mapOf("x_carried" to JsonPrimitive(r.nextInt()), "a_list" to kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("v")))) else emptyMap(),
            ).let { d -> d.copy(liveTvPackage = d.liveTvPackage?.javaTrim()) }
            if (r.nextBoolean()) doc = doc.copy(inputAction = strings.random(r).javaTrim())
            if (r.nextBoolean()) doc = doc.copy(moduleCards = r.nextBoolean())
            // RFC-W4-2: the keys v2.4.0 added.
            doc = doc.copy(
                fillDock = maybe { r.nextBoolean() }, animations = maybe { r.nextBoolean() }, advancedEffects = maybe { r.nextBoolean() },
                greetingName = maybe { strings.random(r).javaTrim() }, reducedMotion = maybe { r.nextBoolean() },
                dockTitle = maybe { r.nextBoolean() }, dockRow = maybe { r.nextBoolean() }, reorderButton = maybe { r.nextBoolean() },
                showStats = maybe { r.nextBoolean() }, appLibrary = maybe { r.nextBoolean() }, skyIntensity = maybe { r.nextInt(0, 101) },
                settingsKeyKeycodes = maybe { List(r.nextInt(4)) { listOf(83, 384, 176, 1, Int.MAX_VALUE).random(r) } },
                settingsKeyPanel = maybe { r.nextBoolean() },
            )
            // RFC-W4-3: the keys v2.5.0 added.
            val entries = listOf("com.android.tv=banner_tv", "a.b=Name with spaces", "a.b=x=y", "no equals", "=x", "😀=x", "with \"quotes\"=\\")
            doc = doc.copy(
                launchActions = maybe { List(r.nextInt(3)) { entries.random(r) } }, appLabels = maybe { List(r.nextInt(3)) { entries.random(r) } },
                appBanners = maybe { List(r.nextInt(3)) { entries.random(r) } },
                inputKeyKeycodes = maybe { List(r.nextInt(3)) { listOf(192, 170, 1, Int.MAX_VALUE).random(r) } },
                appKeys = maybe { List(r.nextInt(3)) { listOf("190:com.google.android.youtube.tv", "KEYCODE_BUTTON_3:a.b", "x", "0:a.b").random(r) } },
                cinematicHome = maybe { r.nextBoolean() }, wallpaperRotationMinutes = maybe { r.nextInt(0, 1441) }, wallpaperDim = maybe { r.nextInt(0, 91) },
                raceCalendar = maybe { List(r.nextInt(3)) { k ->
                    LauncherRace(r.nextInt(-1, 25), strings.random(r).javaTrim(), strings.random(r).javaTrim(),
                        List(r.nextInt(1, 4)) { j -> LauncherRaceSession(listOf("FP1", "SPRINT QUALI", "RACE", "ÖSTERREICH").random(r), "2026-10-2${j}T1${k}:30:00Z") })
                } },
            )
            if (r.nextInt(3) == 0) doc = doc.copy(
                festival = listOf("dussehra", "Diwali", "", "x".repeat(41)).random(r),
                festivalUntil = maybe { listOf("2026-10-21", "2026-02-30", "").random(r) },
                greeting = maybe { strings.random(r).javaTrim() },
                accentColor = maybe { listOf("#FFB23C", "#fff", "").random(r) },
            )
            fun trimmed(s: String?) = s?.javaTrim()
            doc = doc.copy(liveTvAction = trimmed(doc.liveTvAction), sourcePanelService = trimmed(doc.sourcePanelService),
                sourcePanelAction = trimmed(doc.sourcePanelAction), vpnApp = trimmed(doc.vpnApp))
            val bytes = LauncherConfigCodec.encode(doc)
            val back = LauncherConfigCodec.decode(bytes)
            assertTrue(back is DecodeResult.Ok && back.doc == doc, "round trip changed the doc #$i\n$doc\n$back\n${bytes.decodeToString()}")
            agree(up, bytes, wallpaper = true, label = "encoded #$i", t = t)
            // And the AOSP parser reads our output as strict JSON with the same keys.
            assertEquals(JSONObject(bytes.decodeToString()).length(), (back as DecodeResult.Ok).doc.let { countKeys(it) })
        }
        assertEquals(2_000, t.applied)
    }

    // The dock sheet's rows reach the TV as the sheet shows them. Our doc goes through the real
    // Config.java; the grid is then built the way AppAdapter.reload (v2.4.0) builds and sorts it
    // and GridLayoutManager cuts it (`columns` per row, the first row the dock, rows_below_dock rows
    // in view under it). reload skips a non-exported activity, a second activity of one package
    // with the same label, and an app taken off Home on the TV, then sorts by the owner's TV-side
    // order (empty until the owner reorders on the TV), the place in dock_apps, cards (none here)
    // and the label. The phone's `shown` is the same notion: an app the TV shows as a tile. The
    // rows in view must start with exactly the sheet's rows, also when some dock apps are not shown
    // (they are skipped, the later ones move up). Not modelled: one package with two activities
    // under two labels, which shows two tiles on the TV and one on the phone.
    @Test fun dockRowsShowOnTheTvAsTheSheetShowsThem() {
        val up = UpstreamLauncher(pinned = true, dir = tmp.newFolder("dock"))
        val r = Random(0x5EED_0005)
        val pool = List(20) { PackageName("com.example.app$it") }
        data class Activity(val pkg: String, val name: String, val label: String, val exported: Boolean)
        val skips = mutableMapOf("missing" to 0, "not exported" to 0, "same label twice" to 0, "taken off Home" to 0)
        repeat(500) { i ->
            val columns = listOf(4, 4, 6, 3).random(r)
            val theme = LauncherConfigDoc(columns = columns.takeIf { it != 4 }, rowsBelowDock = listOf(null, 0, 1).random(r))
            val chosen = pool.shuffled(r).take(r.nextInt(1, 16))
            // What each app looks like on this TV.
            val activities = ArrayList<Activity>()
            val removedFromHome = HashSet<String>()
            for (p in pool.map { it.value } + "com.example.other") {
                when (r.nextInt(10)) {
                    0 -> skips.merge("missing", 1, Int::plus)
                    1 -> { activities += Activity(p, ".Main", p, exported = false); skips.merge("not exported", 1, Int::plus) }
                    2 -> { activities += Activity(p, ".Main", p, true); activities += Activity(p, ".Other", p, true); skips.merge("same label twice", 1, Int::plus) }
                    3 -> { activities += Activity(p, ".Main", p, true); removedFromHome += p; skips.merge("taken off Home", 1, Int::plus) }
                    else -> activities += Activity(p, ".Main", p, true)
                }
            }
            val shownOnTv = activities.filter { it.exported && it.pkg !in removedFromHome }.map { PackageName(it.pkg) }.toSet()
            val doc = DockLayers.applyTo(theme, chosen)
            val u = assertIs<UpstreamLauncher.Outcome.Loaded>(up.load(LauncherConfigCodec.encode(doc), wallpaper = false)).config
            val kept = DockLayers.trim(chosen, columns)
            assertEquals(kept.map { it.value }, u.dockApps, "#$i dock_apps")
            assertEquals(DockLayers.rowsBelowDock(kept.size, columns), u.rowsBelowDock, "#$i rows_below_dock")
            assertEquals(columns, u.columns)
            assertEquals(kept.size <= 2 * columns, u.v24.dockTitle, "#$i dock_title: off only for three rows (D1)")
            // AppAdapter.reload: the skips, then the comparator (labels: the package names).
            val seen = HashSet<String>()
            val tiles = activities.filter { a ->
                a.exported && LauncherVisible.visible(tvApp = true, hidden = a.pkg in u.hiddenApps, removed = a.pkg in removedFromHome) &&
                    seen.add(a.pkg + "\n" + a.label)
            }
            val grid = tiles.map { it.pkg }.sortedWith { a, b ->
                val fa = u.dockApps.indexOf(a)
                val fb = u.dockApps.indexOf(b)
                if (fa >= 0 || fb >= 0) { if (fa < 0) 1 else if (fb < 0) -1 else fa.compareTo(fb) } else a.compareTo(b, ignoreCase = true)
            }
            val inView = grid.chunked(u.columns).take(1 + u.rowsBelowDock)
            val sheet = DockLayers.rows(DockLayers.decode(kept, { it in shownOnTv }, columns), columns).map { row -> row.map { it.value } }
            assertTrue(sheet.size <= inView.size, "#$i the TV keeps every sheet row in view")
            // With every app shown the rows match exactly; with some not shown, the TV fills the
            // freed slots after the sheet's apps with the next apps by name.
            sheet.forEachIndexed { k, row -> assertEquals(row, inView[k].take(row.size), "#$i row ${k + 1}") }
            if (kept.all { it in shownOnTv }) assertEquals(sheet.dropLast(1), inView.take(sheet.size - 1), "#$i full rows")
        }
        assertTrue(skips.values.all { it > 100 }, "every skip is exercised: $skips")
    }

    // LibraryApps.visible (v2.4.0) for a tile with no library choice of its own: a TV app shows
    // unless the theme hides it or it was taken off Home on the TV.
    private object LauncherVisible {
        fun visible(tvApp: Boolean, hidden: Boolean, removed: Boolean) = !removed && (tvApp && !hidden)
    }

    private fun countKeys(d: LauncherConfigDoc) = listOfNotNull(d.dockApps, d.hiddenApps, d.liveTvPackage, d.liveTvAction, d.sourcePanelService,
        d.sourcePanelAction, d.columns, d.rowsBelowDock, d.shamsiDate, d.vpnApp, d.showHomeSettingsShortcut, d.backgroundColor, d.wallpaper,
        d.inputAction, d.moduleCards, d.fillDock, d.animations, d.advancedEffects, d.greetingName, d.reducedMotion, d.dockTitle,
        d.dockRow, d.reorderButton, d.showStats, d.appLibrary, d.skyIntensity, d.settingsKeyKeycodes, d.settingsKeyPanel,
        d.festival, d.festivalUntil, d.greeting, d.accentColor, d.launchActions, d.appLabels, d.appBanners, d.inputKeyKeycodes,
        d.appKeys, d.cinematicHome, d.wallpaperRotationMinutes, d.wallpaperDim, d.raceCalendar).size + d.unknown.size

    // The lenient reader is a port of JSONTokener; check it value for value, including the
    // Integer, Long and Double split, null slots, duplicate keys and what is refused.
    @Test fun androidJsonPortMatchesAospOrgJson() {
        val fuzz = ConfigFuzz(seed = 0x5EED_0004)
        var accepted = 0
        repeat(10_000) { i ->
            val text = String(fuzz.next(), Charsets.UTF_8)
            val theirs = runCatching { toAValue(JSONObject(text)) }
            val ours = runCatching { AndroidJsonReader(text).readObjectDocument() }
            assertEquals(theirs.isSuccess, ours.isSuccess, "accept differs for #$i ${text.take(300)}: ${theirs.exceptionOrNull()} vs ${ours.exceptionOrNull()}")
            if (ours.isSuccess) {
                accepted++
                assertEquals(theirs.getOrThrow().dump(), ours.getOrThrow().dump(), "value differs for #$i ${text.take(300)}")
            }
        }
        assertTrue(accepted > 5_000)
    }

    private fun toAValue(v: Any?): AValue = when (v) {
        null -> AValue.Absent
        JSONObject.NULL -> AValue.Null
        is String -> AValue.Str(v)
        is Int -> AValue.IntV(v)
        is Long -> AValue.LongV(v)
        is Double -> AValue.Dbl(v)
        is Boolean -> AValue.Bool(v)
        is JSONObject -> AValue.Obj(LinkedHashMap<String, AValue>().also { m -> v.keys().forEach { k -> m[k] = toAValue(v.opt(k)) } })
        is JSONArray -> AValue.Arr((0 until v.length()).map { toAValue(v.opt(it)) })
        else -> error("unexpected ${v::class}")
    }

    private fun AValue.dump(): String = when (this) {
        is AValue.Obj -> map.entries.joinToString(",", "{", "}") { "${it.key}=${it.value.dump()}" }
        is AValue.Arr -> items.joinToString(",", "[", "]") { it.dump() }
        is AValue.Dbl -> "D(${java.lang.Double.doubleToRawLongBits(v)})"
        else -> toString()
    }
}
