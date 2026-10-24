package tv.cleanyour.ui.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// The app's icon set (ARCHITECTURE 1.2: about 25 vectors, no icons-extended). Outline strokes on
// a 24 grid, 1.5 dp, round caps and joins, matching Outfit Light and the mockups. Icons are tinted
// by Icon(); the black stroke here is only a placeholder colour. Use CytIcon for the right sizes.
object CytIcons {
    private const val CIRCLE9 = "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z"
    private fun dot(x: Float, y: Float) = "M${x - 0.6f} ${y}a0.6 0.6 0 1 1 1.2 0a0.6 0.6 0 1 1-1.2 0z"

    val Back: ImageVector by lazy { icon("Back", "M15 5l-7 7 7 7", mirror = true) }
    val Close: ImageVector by lazy { icon("Close", "M6 6l12 12M18 6L6 18") }
    val Help: ImageVector by lazy { icon("Help", CIRCLE9, "M9.6 9.3a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .8-1 1.5v.4", dot(12f, 16.8f)) }
    val Check: ImageVector by lazy { icon("Check", "M5 12.5l4.5 4.5L19 7.5", width = 2f) }
    val ChevronForward: ImageVector by lazy { icon("ChevronForward", "M9 6l6 6-6 6", mirror = true) }
    val ChevronDown: ImageVector by lazy { icon("ChevronDown", "M6 9l6 6 6-6") }
    val ChevronUp: ImageVector by lazy { icon("ChevronUp", "M6 15l6-6 6 6") }
    val Info: ImageVector by lazy { icon("Info", CIRCLE9, "M12 11v5", dot(12f, 7.8f)) }
    val Warning: ImageVector by lazy { icon("Warning", "M12 3.5L2.5 20h19z", "M12 10v4.5", dot(12f, 17.2f)) }
    val Stopped: ImageVector by lazy { icon("Stopped", CIRCLE9, "M12 8v5", dot(12f, 16.3f)) }
    val Shield: ImageVector by lazy { icon("Shield", "M12 3L5 6v5c0 4.5 3 8.3 7 10 4-1.7 7-5.5 7-10V6z", "M9 12l2 2 4-4") }
    val Wifi: ImageVector by lazy { icon("Wifi", "M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.7 16a5 5 0 0 1 6.6 0", dot(12f, 19f)) }
    val WifiOff: ImageVector by lazy { icon("WifiOff", "M3 3l18 18M8.5 16.5a5 5 0 0 1 7 0M5 12.5a10 10 0 0 1 4-2.3M14.5 10.3A10 10 0 0 1 19 12.5", dot(12f, 19.5f)) }
    val Tv: ImageVector by lazy { icon("Tv", "M5 4.5h14a2.5 2.5 0 0 1 2.5 2.5v7.5a2.5 2.5 0 0 1-2.5 2.5H5a2.5 2.5 0 0 1-2.5-2.5V7A2.5 2.5 0 0 1 5 4.5z", "M9 20.5h6") }
    val Minus: ImageVector by lazy { icon("Minus", "M6 12h12", width = 2.2f) }
    val Plus: ImageVector by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Refresh: ImageVector by lazy { icon("Refresh", "M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.5 4.5v4h-4") }
    val Copy: ImageVector by lazy { icon("Copy", "M11 9h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M5 15h-.5A1.5 1.5 0 0 1 3 13.5v-9A1.5 1.5 0 0 1 4.5 3h9A1.5 1.5 0 0 1 15 4.5V5") }
    val Settings: ImageVector by lazy { icon("Settings", "M4 7h10M18 7h2M4 17h4M12 17h8", "M16 5a2 2 0 1 1 0 4a2 2 0 1 1 0-4z", "M10 15a2 2 0 1 1 0 4a2 2 0 1 1 0-4z") }
    val ArrowUp: ImageVector by lazy { icon("ArrowUp", "M12 19V5M6 11l6-6 6 6") }
    val ArrowDown: ImageVector by lazy { icon("ArrowDown", "M12 5v14M6 13l6 6 6-6") }
    val Search: ImageVector by lazy { icon("Search", "M11 4.5a6.5 6.5 0 1 1 0 13a6.5 6.5 0 1 1 0-13z", "M16 16l4.5 4.5") }
    val Cards: ImageVector by lazy { icon("Cards", "M5 8.5h14a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 18v-8A1.5 1.5 0 0 1 5 8.5z", "M6 5.5h12") }
    val Home: ImageVector by lazy { icon("Home", "M4 11l8-6.5 8 6.5", "M6 9.5v10h12v-10") }
    val Undo: ImageVector by lazy { icon("Undo", "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11", mirror = true) }
    val Restart: ImageVector by lazy { icon("Restart", "M12 3v8", "M7.5 5.8a7.5 7.5 0 1 0 9 0") }
    val Key: ImageVector by lazy { icon("Key", "M8 11a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", "M11 12l9-9M17 6l2.5 2.5M15 8l2 2") }
    val Download: ImageVector by lazy { icon("Download", "M12 4v11M7 10l5 5 5-5M5 20h14") }
    val Phone: ImageVector by lazy { icon("Phone", "M8.5 2.5h7a2 2 0 0 1 2 2v15a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-15a2 2 0 0 1 2-2z", "M11 18.5h2") }
    val Delete: ImageVector by lazy { icon("Delete", "M4.5 7h15M10 4h4M6.5 7l1 13h9l1-13M10 11v5M14 11v5") }
    val Share: ImageVector by lazy { icon("Share", "M12 15V4M8 8l4-4 4 4", "M5 13v6.5h14V13") }
    val Edit: ImageVector by lazy { icon("Edit", "M4 20h4L19 9l-4-4L4 16z") }
    val Lock: ImageVector by lazy { icon("Lock", "M7 10.5h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 10.5V7.5a4 4 0 0 1 8 0v3") }
    val External: ImageVector by lazy { icon("External", "M14 4h6v6M20 4l-9 9", "M18 14v5.5H4.5V6H10") }
    val Reorder: ImageVector by lazy { icon("Reorder", "M5 9h14M5 15h14") }
    // The drag handle: six dots in two columns (the dock sheet's grip).
    val Grip: ImageVector by lazy { icon("Grip", dot(9f, 6.5f), dot(15f, 6.5f), dot(9f, 12f), dot(15f, 12f), dot(9f, 17.5f), dot(15f, 17.5f), width = 2f) }
    val Circle: ImageVector by lazy { icon("Circle", "M12 6a6 6 0 1 1 0 12a6 6 0 1 1 0-12z") }
    val Dash: ImageVector by lazy { icon("Dash", "M8 12h8", width = 2f) }

    val all: List<ImageVector>
        get() = listOf(
            Back, Close, Help, Check, ChevronForward, ChevronDown, ChevronUp, Info, Warning, Stopped, Shield,
            Wifi, WifiOff, Tv, Minus, Plus, Refresh, Copy, Settings, ArrowUp, ArrowDown, Search, Cards, Home,
            Undo, Restart, Key, Download, Phone, Delete, Share, Edit, Lock, External, Reorder, Grip, Circle, Dash,
        )

    private fun icon(name: String, vararg paths: String, width: Float = 1.5f, mirror: Boolean = false): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = mirror)
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }
}
