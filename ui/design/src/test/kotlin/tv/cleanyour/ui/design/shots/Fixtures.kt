package tv.cleanyour.ui.design.shots

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import tv.cleanyour.ui.design.component.ChoiceOption
import tv.cleanyour.ui.design.component.DiffGroup
import tv.cleanyour.ui.design.component.DiffItem
import tv.cleanyour.ui.design.component.NarrationLine
import tv.cleanyour.ui.design.component.NarrationState
import tv.cleanyour.ui.design.component.SummaryGroup
import tv.cleanyour.ui.design.component.ThemeOption

// Copy and data from PRODUCT-SPEC 4 and the mockups, for the screenshots only.
object Fx {
    val summary = listOf(
        SummaryGroup("ads", "Adverts and sponsored rows", 6),
        SummaryGroup("tracking", "Tracking and analytics", 7),
        SummaryGroup("bloat", "Apps nobody asked for", 8),
        SummaryGroup("extras", "Xiaomi extras", 2),
    )

    val adverts = DiffGroup(
        "ads", "Adverts and sponsored rows",
        listOf(
            DiffItem("com.mitv.tvhome.atv", "PatchWall", "Xiaomi's home screen with adverts", "com.mitv.tvhome.atv", off = true),
            DiffItem("com.mitv.tvhome.mitvplus", "Mi TV+ promos", "Promoted content rows", "com.mitv.tvhome.mitvplus", off = false),
            DiffItem("com.mitv.ad", "Ad service", "Shows adverts in other apps", "com.miui.systemAdSolution", off = true),
            DiffItem("com.mitv.screensaver.ads", "Screensaver adverts", "Sponsored pictures when idle", "com.mitv.screensaver", off = true),
            DiffItem("com.mitv.videoplayer.ads", "Player adverts", "Clips before videos", "com.mitv.videoplayer", off = true),
            DiffItem("com.mitv.store.promos", "Store promos", "Featured apps nobody asked for", "com.xiaomi.mitv.appstore", off = true),
        ),
    )

    fun collapsed() = listOf(
        DiffGroup("tracking", "Tracking and analytics", List(7) { DiffItem("t$it", "t$it", "", null, true) }),
        DiffGroup("bloat", "Apps nobody asked for", List(8) { DiffItem("b$it", "b$it", "", null, true) }),
        DiffGroup("extras", "Xiaomi extras", List(2) { DiffItem("x$it", "x$it", "", null, true) }),
    )

    val protectedItems = listOf(
        DiffItem("com.google.android.tvlauncher", "Remote control service", "Keeps the remote working", "com.android.tv.remote.service", off = false),
        DiffItem("com.android.vending", "Software updates", "Kept on so the TV keeps getting security fixes", "com.android.vending", off = false),
    )

    val narration = listOf(
        NarrationLine("restore", "Restore point saved", NarrationState.DONE),
        NarrationLine("ads", "Adverts switched off (6)", NarrationState.DONE),
        NarrationLine("tracking", "Tracking: 4 of 7", NarrationState.DOING),
        NarrationLine("bloat", "Apps nobody asked for", NarrationState.UPCOMING),
        NarrationLine("install", "Installing the new home screen", NarrationState.UPCOMING),
        NarrationLine("home", "Switching Home over", NarrationState.UPCOMING),
    )

    val narrationLong = listOf(
        NarrationLine("restore", "Restore point saved", NarrationState.DONE),
        NarrationLine("ads", "Adverts switched off (6)", NarrationState.DONE),
        NarrationLine("tracking", "Tracking switched off (7)", NarrationState.DONE),
        NarrationLine("skip", "Software updates kept", NarrationState.SKIPPED),
        NarrationLine("bloat", "Apps nobody asked for (8)", NarrationState.DONE),
        NarrationLine("replace", "Replacing the home screen app", NarrationState.DONE),
        NarrationLine("install", "Installing the new home screen", NarrationState.DONE),
        NarrationLine("home", "Switching Home over", NarrationState.DOING),
        NarrationLine("check", "Checking the TV", NarrationState.UPCOMING),
    )

    val tvs = listOf(
        ChoiceOption("mitv", "Mi TV 4A", "Living room · Android TV 9"),
        ChoiceOption("cc", "Chromecast", "Bedroom · Google TV 12"),
    )

    val themes = listOf(
        ThemeOption("default", "Default", "Black with a soft glow, like the TV today", listOf(Color.Black, Color(0xFF5B6CFF), Color(0xFF2A2A2E), Color(0xFF3A86E8))),
        ThemeOption("aurora", "Aurora", "Deep blues and greens that drift behind the dock", listOf(Color(0xFF041018), Color(0xFF1FA597), Color(0xFF0B5E57), Color(0xFF1D4FBF))),
        ThemeOption("six", "Six columns", "Smaller tiles, six to a row", listOf(Color.Black, Color(0xFFB9C2CC), Color(0xFF3B3B40), Color(0xFF76767D))),
    )

    const val FINGERPRINT = "3A:91:0C:55:E8:12:9B:44:5D:0E:21:C7:AF:63:7F:2A"
    val retro = Color(0xFFE8A33D)

    // A stand-in for a TV screenshot: the clean home screen with a dock of four tiles.
    val homeShot: ImageBitmap by lazy {
        val bmp = ImageBitmap(640, 360)
        val canvas = Canvas(bmp)
        val p = Paint()
        p.color = Color.Black
        canvas.drawRect(Rect(0f, 0f, 640f, 360f), p)
        p.shader = RadialGradientShader(Offset(190f, 140f), 420f, listOf(Color(0x4DB9C2CC), Color(0x00000000)))
        canvas.drawRect(Rect(0f, 0f, 640f, 360f), p)
        p.shader = null
        p.color = Color(0xFF111113)
        canvas.drawRoundRect(20f, 250f, 620f, 345f, 22f, 22f, p)
        val tiles = listOf(Color(0xFF2A2A2E), Color(0xFF1D4FBF), Color(0xFF3B3B40), Color(0xFF0B5E57))
        tiles.forEachIndexed { i, c ->
            val x = 32f + i * 148f
            p.shader = LinearGradientShader(Offset(x, 262f), Offset(x + 136f, 333f), listOf(c, c.copy(alpha = 0.7f)))
            canvas.drawRoundRect(x, 262f, x + 136f, 333f, 14f, 14f, p)
        }
        p.shader = null
        p.color = Color.White
        canvas.drawRoundRect(32f, 262f, 168f, 333f, 14f, 14f, Paint().apply { color = Color.White; style = androidx.compose.ui.graphics.PaintingStyle.Stroke; strokeWidth = 3f })
        p.color = Color(0xFFF5F5F7)
        canvas.drawRect(Rect(30f, 40f, 140f, 80f), p.apply { color = Color(0x33FFFFFF) })
        bmp
    }
}
