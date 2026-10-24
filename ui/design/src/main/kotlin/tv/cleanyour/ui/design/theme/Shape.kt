package tv.cleanyour.ui.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

// DESIGN-SYSTEM 4: pill (buttons, chips, the status chip, the snackbar), small 12 dp (row
// highlight, small thumbnails, text fields), medium 20 dp (cards, list groups, module card),
// large 28 dp (sheet top corners, hero cards).
@Immutable
data class CytShapes(
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
    val row: RoundedCornerShape = RoundedCornerShape(16.dp),
    val medium: RoundedCornerShape = RoundedCornerShape(20.dp),
    val large: RoundedCornerShape = RoundedCornerShape(28.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
)

fun CytShapes.toM3(): Shapes = Shapes(
    extraSmall = small, small = small, medium = medium, large = large, extraLarge = large,
)
