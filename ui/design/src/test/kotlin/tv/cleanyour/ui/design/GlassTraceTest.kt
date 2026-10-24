package tv.cleanyour.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.cleanyour.ui.design.theme.DarkCytColors
import tv.cleanyour.ui.design.theme.LightCytColors
import tv.cleanyour.ui.design.theme.buildGlass
import tv.cleanyour.ui.design.theme.drawGlass
import tv.cleanyour.ui.design.theme.glassStyle
import tv.cleanyour.ui.design.theme.highlightStyle

// DESIGN-SYSTEM 5.1 and the W7 definition of done: the glass recipe draws without offscreen
// layers. The draw is run against a recording canvas and every call is traced: no saveLayer, no
// clip, exactly fill + specular + rim (plus the three rings on the highlight).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassTraceTest {

    private class RecordingCanvas(private val real: Canvas) : Canvas by real {
        val trace = mutableListOf<String>()
        override fun saveLayer(bounds: Rect, paint: Paint) { trace += "saveLayer"; real.saveLayer(bounds, paint) }
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float, clipOp: ClipOp) { trace += "clipRect"; real.clipRect(left, top, right, bottom, clipOp) }
        override fun clipPath(path: Path, clipOp: ClipOp) { trace += "clipPath"; real.clipPath(path, clipOp) }
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) { trace += "drawRect ${paint.describe()}"; real.drawRect(left, top, right, bottom, paint) }
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, radiusX: Float, radiusY: Float, paint: Paint) {
            trace += "drawRoundRect ${paint.describe()}"; real.drawRoundRect(left, top, right, bottom, radiusX, radiusY, paint)
        }
        override fun drawPath(path: Path, paint: Paint) { trace += "drawPath ${paint.describe()}"; real.drawPath(path, paint) }
        override fun translate(dx: Float, dy: Float) { trace += "translate"; real.translate(dx, dy) }
        override fun save() { trace += "save"; real.save() }
        override fun restore() { trace += "restore"; real.restore() }
        private fun Paint.describe() = buildString {
            append(style.toString().lowercase())
            if (shader != null) append(" gradient") else append(" ").append(color.toString())
            if (alpha < 1f) append(" paintAlpha=").append(alpha)
        }
    }

    private fun trace(name: String, style: tv.cleanyour.ui.design.theme.GlassStyle, shape: androidx.compose.ui.graphics.Shape): List<String> {
        val size = Size(400f, 112f)
        val density = Density(2f)
        val rec = RecordingCanvas(Canvas(ImageBitmap(440, 150)))
        val g = buildGlass(size, LayoutDirection.Ltr, density, shape, style)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, rec, size) { drawGlass(g) }
        val out = File("build/outputs/glass-trace").apply { mkdirs() }
        File(out, "$name.txt").writeText(rec.trace.joinToString("\n") + "\n")
        println("glass trace $name:\n  " + rec.trace.joinToString("\n  "))
        return rec.trace
    }

    @Test
    fun glassPillDrawsThreeOpsAndNoLayer() {
        for ((name, colors) in listOf("dark" to DarkCytColors, "light" to LightCytColors)) {
            val t = trace("glass-pill-$name", colors.glassStyle(), RoundedCornerShape(percent = 50))
            assertEquals(0, t.count { it == "saveLayer" })
            assertEquals(0, t.count { it.startsWith("clip") })
            assertEquals(3, t.count { it.startsWith("draw") })
            assertEquals(2, t.count { it.startsWith("draw") && it.contains("gradient") })
            assertEquals(1, t.count { it.startsWith("draw") && it.contains("stroke") })
        }
    }

    @Test
    fun highlightDrawsRingsWithoutLayer() {
        val t = trace("highlight-row", DarkCytColors.highlightStyle(), RoundedCornerShape(16f))
        assertEquals(0, t.count { it == "saveLayer" })
        assertEquals(0, t.count { it.startsWith("clip") })
        assertEquals(6, t.count { it.startsWith("draw") })
        assertEquals(4, t.count { it.startsWith("draw") && it.contains("stroke") })
    }
}
