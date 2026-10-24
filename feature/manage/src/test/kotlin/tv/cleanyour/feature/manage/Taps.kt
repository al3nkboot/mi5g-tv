package tv.cleanyour.feature.manage

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import tv.cleanyour.ui.design.theme.CytTheme

// Taps through an overlay (SECURITY 4.3, R5-W8 O1): real MotionEvents with the window flags, sent
// through the activity's decor view, the path a touch takes on a device (as :ui:design's
// ObscuredTouchTest does).
typealias TapRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

const val OBSCURED = MotionEvent.FLAG_WINDOW_IS_OBSCURED

fun TapRule.showColumn(content: @Composable ColumnScope.() -> Unit) {
    setContent { CytTheme(reducedMotion = true) { Column { content() } } }
    waitForIdle()
}

fun TapRule.tap(node: SemanticsNodeInteraction, flags: Int) {
    val b = node.fetchSemanticsNode().boundsInWindow
    val x = (b.left + b.right) / 2
    val y = (b.top + b.bottom) / 2
    val decor = activity.window.decorView
    val t = SystemClock.uptimeMillis()
    runOnUiThread {
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { i, action ->
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
            val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
            val e = MotionEvent.obtain(t, t + i * 50L, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags)
            decor.dispatchTouchEvent(e)
            e.recycle()
        }
    }
    waitForIdle()
}

fun TapRule.tap(text: String, flags: Int) = tap(onNodeWithText(text), flags)
