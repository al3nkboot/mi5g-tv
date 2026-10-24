package tv.cleanyour.core.spec

import kotlin.random.Random

// Generates launcher config files the way people and broken tools write them: strict JSON and
// the lenient forms Android's org.json also reads, every key with right and wrong types, values
// in and out of range, padded strings, bad colours, BOMs, comments, duplicates, truncation,
// trailing garbage, invalid UTF-8 and sizes around the 64 KB cap. Deterministic per seed.
class ConfigFuzz(seed: Long) {
    private val r = Random(seed)

    private val listKeys = listOf("dock_apps", "hidden_apps")
    private val stringKeys = listOf("live_tv_package", "live_tv_action", "source_panel_service", "source_panel_action", "vpn_app", "input_action", "greeting_name")
    private val intKeys = listOf("columns", "rows_below_dock")
    private val boolKeys = listOf(
        "shamsi_date", "show_home_settings_shortcut", "module_cards", "wallpaper",
        // launcher v2.4.0 (RFC-W4-2)
        "fill_dock", "animations", "advanced_effects", "reduced_motion", "dock_title", "dock_row", "reorder_button",
        "show_stats", "app_library", "settings_key_panel",
        // launcher v2.5.0 (RFC-W4-3)
        "cinematic_home",
    )
    private val textListKeys = listOf("launch_actions", "app_labels", "app_banners", "app_keys")
    private val festivalKeys = listOf("festival", "festival_until", "greeting", "accent_color")
    private val knownKeys = listKeys + stringKeys + intKeys + boolKeys + festivalKeys +
        listOf("background_color", "sky_intensity", "settings_key_keycodes") + textListKeys +
        listOf("input_key_keycodes", "wallpaper_rotation_minutes", "wallpaper_dim", "race_calendar")
    private val packages = listOf(
        "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar",
        "com.mediatek.wwtv.tvcenter", "com.android.tv", "a.b", "com.x_y.z9", "android",
    )

    private fun pick(vararg xs: String) = xs[r.nextInt(xs.size)]
    private fun <T> pickL(xs: List<T>) = xs[r.nextInt(xs.size)]

    fun next(): ByteArray {
        val roll = r.nextInt(100)
        return when {
            roll < 52 -> doc(lenient = r.nextInt(4) == 0).encodeToByteArray()
            roll < 62 -> doc(lenient = true).encodeToByteArray()
            roll < 66 -> pick("[1, 2]", "\"just a string\"", "42", "true", "null", "", "   \n", "{", "}", "{}", "{} {}", "nope", "[{\"columns\": 6}]")
                .encodeToByteArray()
            roll < 72 -> doc(false).let { it.substring(0, r.nextInt(it.length + 1)) }.encodeToByteArray()
            roll < 76 -> withBadBytes(doc(false).encodeToByteArray())
            roll < 82 -> ("﻿".repeat(1 + r.nextInt(3)) + doc(r.nextBoolean())).encodeToByteArray()
            roll < 87 -> nearCap()
            roll < 92 -> (doc(false) + pick(" trailing", "}", ",{\"columns\": 2}", "\u0000", " // done", "]]]")).encodeToByteArray()
            roll < 96 -> duplicates().encodeToByteArray()
            else -> nested().encodeToByteArray()
        }
    }

    private fun withBadBytes(b: ByteArray): ByteArray {
        if (b.isEmpty()) return byteArrayOf(0xC3.toByte())
        val out = b.toMutableList()
        repeat(1 + r.nextInt(3)) {
            val bad = pickL(listOf(byteArrayOf(0xFF.toByte()), byteArrayOf(0xC3.toByte()), byteArrayOf(0xE2.toByte(), 0x82.toByte()), byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte())))
            out.addAll(r.nextInt(out.size + 1), bad.toList())
        }
        return out.toByteArray()
    }

    private fun nearCap(): ByteArray {
        val base = doc(false)
        val target = 64 * 1024 + r.nextInt(-8, 9) + if (r.nextInt(5) == 0) r.nextInt(-3000, 6000) else 0
        val pad = (target - base.encodeToByteArray().size).coerceAtLeast(0)
        val text = if (r.nextBoolean()) base.dropLast(1) + ", \"pad\": \"" + "x".repeat((pad - 11).coerceAtLeast(0)) + "\"}"
        else " ".repeat(pad) + base
        return text.encodeToByteArray()
    }

    private fun duplicates(): String {
        val k = pickL(knownKeys)
        return "{" + (0 until 2 + r.nextInt(3)).joinToString(", ") { "\"$k\": ${value(k, false)}" } + "}"
    }

    private fun nested(): String {
        val depth = 1 + r.nextInt(40)
        val open = (0 until depth).joinToString("") { if (r.nextBoolean()) "[" else "{\"n\": " }
        // Close in reverse; objects close with }, arrays with ].
        val closes = open.split(Regex("(?<=[\\[ ])")).filter { it.isNotEmpty() }.reversed().joinToString("") { if (it == "[") "]" else "}" }
        return "{\"columns\": 5, \"deep\": $open 1 $closes}"
    }

    fun doc(lenient: Boolean): String {
        val n = r.nextInt(0, 12)
        val entries = (0 until n).map {
            val key = when (r.nextInt(10)) {
                in 0..6 -> pickL(knownKeys)
                7 -> pick("Columns", "dock_apps ", " columns", "columns\u0000", "backgroundColor", "dock-apps")
                else -> "x_" + r.nextInt(1000)
            }
            key to value(key.trim(), lenient)
        }
        val sep = { if (lenient && r.nextInt(4) == 0) ";" else "," }
        val colon = { if (lenient && r.nextInt(4) == 0) pick("=", "=>", " = ") else ":" }
        val ws = { if (lenient && r.nextInt(5) == 0) pick(" /* c */ ", "\n// line\n", "\n# hash\n", "\t", "\r\n") else pick("", " ", "\n  ") }
        val body = entries.joinToString(sep()) { (k, v) -> ws() + name(k, lenient) + ws() + colon() + ws() + v + ws() }
        return "{" + body + (if (r.nextInt(20) == 0) "," else "") + "}"
    }

    private fun name(k: String, lenient: Boolean): String = when {
        lenient && r.nextInt(3) == 0 && k.matches(Regex("[a-z_]+")) -> k
        lenient && r.nextInt(3) == 0 -> "'" + k.replace("'", "\\'") + "'"
        else -> str(k)
    }

    private fun str(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private fun lenientStr(s: String, lenient: Boolean): String = when {
        lenient && r.nextInt(3) == 0 && s.matches(Regex("[A-Za-z][A-Za-z0-9_.]*")) -> s
        lenient && r.nextInt(3) == 0 -> "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
        else -> str(s)
    }

    private fun anyValue(lenient: Boolean): String = when (r.nextInt(12)) {
        0 -> str(pickL(packages))
        1 -> number()
        2 -> pick("true", "false", "TRUE", "False", "null", "NULL")
        3 -> "[" + (0 until r.nextInt(4)).joinToString(", ") { str(pickL(packages)) } + "]"
        4 -> "{\"a\": 1}"
        5 -> "[]"
        6 -> "{}"
        7 -> str("")
        8 -> if (lenient) pick("bare", "0x1G", "-0x5", "4px") else str("bare")
        9 -> str(" " + pickL(packages) + "\t")
        10 -> pick("[1, 2, 3]", "[null]", "[[\"a.b\"]]")
        else -> number()
    }

    private fun number(): String = pick(
        "0", "1", "4", "6", "8", "9", "-1", "3", "12", "4.0", "4.9", "-0.0", "1e1", "8E0", "0x4", "0X6", "04", "010", "08",
        "4294967300", "-4294967292", "9223372036854775807", "9223372036854775808", "1e400", "-1e400", "1e-400",
        "NaN", "Infinity", "-Infinity", "2147483648", "0.5", ".5", "5.", "1f", "2d", "0x1p2", "+3", "00", "0x",
    )

    // Launcher v2.5.0's race_calendar: races and sessions with right and wrong shapes, and the
    // values optString and optInt coerce (numbers, null, booleans, lists and objects as names).
    private fun raceCalendar(): String {
        if (r.nextInt(8) == 0) return pick("{}", "\"x\"", "null", "3")
        fun text(fallback: String) = pick(str(fallback), str(" $fallback "), str(""), "5", "5.0", "-0.0", "1e400", "null", "true", "[1, \"a/b\", null, 2.0]", "{\"k\": \"v\\n\"}", "[NaN]", str("tab\there"))
        fun session(): String = when (r.nextInt(8)) {
            0 -> pick("7", "null", "\"FP1\"", "[]")
            else -> {
                val fields = mutableListOf<String>()
                if (r.nextInt(6) != 0) fields += "\"name\": " + text(pick("FP1", "fp2", "Sprint Quali", "RACE", "straße"))
                if (r.nextInt(6) != 0) fields += "\"start\": " + pick(
                    str("2026-10-23T17:30:00Z"), str("2026-10-25T19:00:00Z"), str("2026-10-24T21:00:00.5Z"), str("2026-10-23T17:30Z"),
                    str("2026-10-23T18:30:00+01:00"), str(" 2026-10-23T17:30:00Z"), str("2026-02-30T00:00:00Z"), str("tomorrow"), str(""), "1761240600", "null")
                "{" + fields.shuffled(r).joinToString(", ") + "}"
            }
        }
        fun race(): String = when (r.nextInt(10)) {
            0 -> pick("1", "null", "\"race\"", "[]")
            else -> {
                val fields = mutableListOf<String>()
                if (r.nextInt(4) != 0) fields += "\"round\": " + pick("18", "\"18\"", "\"18.9\"", "\" 7 \"", "18.9", "4294967302", "null", "true", "\"x\"", "-1")
                if (r.nextInt(4) != 0) fields += "\"name\": " + text("United States Grand Prix")
                if (r.nextInt(4) != 0) fields += "\"place\": " + text("Austin")
                if (r.nextInt(8) != 0) fields += "\"sessions\": " + if (r.nextInt(10) == 0) pick("{}", "null", "\"FP1\"") else
                    "[" + (0 until r.nextInt(0, 5)).joinToString(", ") { session() } + "]"
                "{" + fields.shuffled(r).joinToString(", ") + "}"
            }
        }
        return "[" + (0 until r.nextInt(0, 4)).joinToString(", ") { race() } + "]"
    }

    private fun value(key: String, lenient: Boolean): String {
        if (r.nextInt(4) == 0) return anyValue(lenient)
        return when (key) {
            in listKeys -> {
                val items = (0 until r.nextInt(0, 6)).map {
                    when (r.nextInt(10)) {
                        in 0..4 -> lenientStr(pickL(packages), lenient)
                        5 -> str(pick("  com.netflix.ninja  ", "\tin.startv.hotstar\n", "", "   ", "foo bar", "com.", ".x", "Com.Example.App", "ñ.ü", "a".repeat(70) + ".b"))
                        6 -> pick("1", "null", "true", "{}", "[\"a.b\"]")
                        7 -> if (lenient) "" else str("a.b")   // empty slot: [,"a.b"] holds a Java null
                        else -> str(pickL(packages))
                    }
                }
                "[" + items.joinToString(if (lenient && r.nextBoolean()) ";" else ", ") + (if (lenient && r.nextInt(5) == 0) "," else "") + "]"
            }
            in stringKeys -> lenientStr(pick("Mr. Trịnh", "com.android.tv", "  padded.value  ", "", " ", "\u0001ctl", "a\\b", "quote\"in", "line\nbreak", "ünïcode", "com.xgimi.action.hdmiPlayer", " nbsp "), lenient)
            in intKeys -> number()
            in boolKeys -> pick("true", "false", "true", "false", "TRUE", "\"true\"", "1", "0", "null", "\"false\"")
            "sky_intensity" -> pick("0", "1", "60", "100", "101", "-1", "99.9", "100.5", "1e2", "4294967356", "NaN", "\"60\"", "true")
            in textListKeys -> {
                val items = (0 until r.nextInt(0, 5)).map {
                    when (r.nextInt(10)) {
                        in 0..3 -> str(pick("com.telekom.onetv.tv.pl=TV", "com.android.tv=banner_tv", "a.b=banner_none", " a.b = x ", "a.b=x=y", "=x", "a.b=", "no equals", "😀=x"))
                        in 4..6 -> str(pick("190:com.google.android.youtube.tv", "KEYCODE_BUTTON_3:a.b", " 191 : a.b ", "0:a.b", "000000:a.b", "1234567:a.b", "23:a.b", "KEYCODE_DPAD_CENTER:a.b", "KEYCODE_back:a.b", "x:a.b", "190:", ":a.b", "190"))
                        7 -> pick("1", "null", "true", "{}", "[\"a=b\"]")
                        8 -> if (lenient) "" else str("a.b=c")
                        else -> str(pick("", "   "))
                    }
                }
                "[" + items.joinToString(if (lenient && r.nextBoolean()) ";" else ", ") + "]"
            }
            "wallpaper_rotation_minutes" -> pick("0", "1", "30", "1440", "1441", "-1", "30.9", "\"30\"", "4294967326", "true")
            "wallpaper_dim" -> pick("0", "40", "90", "91", "-1", "89.9", "\"40\"", "null")
            "race_calendar" -> raceCalendar()
            "input_key_keycodes", "settings_key_keycodes" -> {
                val items = (0 until r.nextInt(0, 5)).map {
                    when (r.nextInt(12)) {
                        in 0..4 -> pick("83", "384", "176", "1", "2147483647")
                        5 -> pick("0", "-1", "-83")
                        6 -> pick("4", "19", "20", "21", "22", "23", "66", "82")
                        7 -> pick("4.9", "0.5", "1e400", "-1e400", "NaN", "4294967379", "9223372036854775807")
                        8 -> pick("\"83\"", "null", "true", "[83]", "{}")
                        9 -> if (lenient) "" else "83"
                        else -> pick("83", "384")
                    }
                }
                "[" + items.joinToString(if (lenient && r.nextBoolean()) ";" else ", ") + (if (lenient && r.nextInt(5) == 0) "," else "") + "]"
            }
            "festival" -> lenientStr(pick("dussehra", "Diwali", " DIWALI ", "", " ", "x".repeat(40), "x".repeat(41), "İ".repeat(20), "İ".repeat(21), "holi\u0000"), lenient)
            "festival_until" -> lenientStr(pick("2026-10-21", "2026-02-30", "2026-1-1", "+12026-01-01", "", " 2026-10-21 ", "tomorrow", "2026-10-21T00:00", "9999-12-31"), lenient)
            "greeting" -> lenientStr(pick("Happy Dussehra", "  spaced  out  ", "‮bidi", "g".repeat(39) + "😀", "g".repeat(60), "", "​", "tab\there"), lenient)
            "accent_color" -> lenientStr(pick("#FFB23C", "#ffb23c", "#FFF", "FFB23C", "#FFB23CFF", " #123456 ", "", "#GGGGGG", "navy"), lenient)
            "background_color" -> lenientStr(
                pick(
                    "#070A14", "#000000", "#ffffff", "#80FF0000", "#FFF", "#", "#12345", "#1234567", "#-12345", "#+12345", "#0x1234",
                    "#-0000001", "#GGGGGG", "navy", "NAVY", "Teal", "darkgrey", "rebeccapurple", "", " ", "  #00ff00  ", "\t", "rgb(1,2,3)",
                    "#ffffffffff", "#7fffffff", "#ffffffff",
                ),
                lenient,
            ).let { if (r.nextInt(8) == 0) pick("0", "null", "true", "[\"#000000\"]") else it }
            else -> anyValue(lenient)
        }
    }
}
