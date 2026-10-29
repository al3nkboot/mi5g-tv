package tv.cleanyour.ui.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// DESIGN-SYSTEM 2 and 11. Dark is the default and the brand; light mirrors the roles one to one.
@Immutable
data class CytColors(
    val bg: Color, val surface1: Color, val surface2: Color, val surface3: Color,
    val rim: Color, val outline: Color, val outlineStrong: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val primary: Color, val onPrimary: Color, val primaryPressed: Color, val accent: Color,
    val glowBrand: Color, val glowTv: Color,
    val success: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val warningContainer: Color, val onWarningContainer: Color,
    val danger: Color, val dangerFill: Color, val onDanger: Color,
    val dangerContainer: Color, val onDangerContainer: Color,
    val scrim: Color,
    // Glass recipe (DESIGN-SYSTEM 5.1): fill top and bottom, specular top, rim.
    val glassFillTop: Color, val glassFillBottom: Color, val glassSpecular: Color,
    // The springy highlight: brighter fill, an 80% rim and three outer rings.
    val highlightFillTop: Color, val highlightFillBottom: Color, val highlightSpecular: Color,
    val highlightRim: Color, val highlightRing: Color,
    // Ambient glow core alpha (24% dark, 14% light) and its mid stop.
    val glowCoreAlpha: Float, val glowMidAlpha: Float,
    val isDark: Boolean,
)

// Mi5G TV 1.6: dark palette from web-preview/src/styles.css (bg, surface, fg, muted, primary, accent gold).
val DarkCytColors = CytColors(
    bg = Color(0xFF0C1416), surface1 = Color(0xFF152022), surface2 = Color(0xFF1C2B2E),
    surface3 = Color(0xFF243336), rim = Color(0xFF2A3C40), outline = Color(0xFF2A3C40),
    outlineStrong = Color(0x5CFFFFFF), textPrimary = Color(0xFFE7F3F0),
    textSecondary = Color(0xFF8AA39D), textTertiary = Color(0xFF8AA39D),
    primary = Color(0xFF14998E), onPrimary = Color(0xFF04221F), primaryPressed = Color(0xFF10847A),
    accent = Color(0xFF4FD8C4),
    glowBrand = Color(0xFF14998E), glowTv = Color(0xFFB9C2CC),
    success = Color(0xFF30D158), successContainer = Color(0xFF0D2A16),
    onSuccessContainer = Color(0xFF5CE07E), warning = Color(0xFFE4C07A),
    warningContainer = Color(0xFF33240A), onWarningContainer = Color(0xFFE4C07A),
    danger = Color(0xFFFF6961), dangerFill = Color(0xFFFF453A), onDanger = Color(0xFF000000),
    dangerContainer = Color(0xFF3A1512), onDangerContainer = Color(0xFFFF8A80),
    scrim = Color(0xA3000000),
    glassFillTop = Color.White.copy(alpha = 0.09f), glassFillBottom = Color.White.copy(alpha = 0.04f),
    glassSpecular = Color.White.copy(alpha = 0.08f),
    highlightFillTop = Color.White.copy(alpha = 0.16f), highlightFillBottom = Color.White.copy(alpha = 0.06f),
    highlightSpecular = Color.White.copy(alpha = 0.14f), highlightRim = Color.White.copy(alpha = 0.80f),
    highlightRing = Color.White,
    glowCoreAlpha = 0.24f, glowMidAlpha = 0.10f,
    isDark = true,
)

val LightCytColors = CytColors(
    bg = Color(0xFFF2F2F4), surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFF7F7F9),
    surface3 = Color(0xFFE6E6EA), rim = Color(0x1A000000), outline = Color(0x14000000),
    outlineStrong = Color(0x52000000), textPrimary = Color(0xFF0B0B0C),
    textSecondary = Color(0xFF4A4A50), textTertiary = Color(0xFF5A5A61),
    primary = Color(0xFF0B0B0C), onPrimary = Color(0xFFFFFFFF), primaryPressed = Color(0xFF2A2A2E),
    accent = Color(0xFF006B5E),
    glowBrand = Color(0xFF14998E), glowTv = Color(0xFFB9C2CC),
    success = Color(0xFF157030), successContainer = Color(0xFFE3F4E8),
    onSuccessContainer = Color(0xFF0F5A26), warning = Color(0xFF8F5300),
    warningContainer = Color(0xFFFFF1DB), onWarningContainer = Color(0xFF7A4600),
    danger = Color(0xFFC01B2C), dangerFill = Color(0xFFC01B2C), onDanger = Color(0xFFFFFFFF),
    dangerContainer = Color(0xFFFDE7E9), onDangerContainer = Color(0xFFA3121F),
    scrim = Color(0x66000000),
    glassFillTop = Color.White.copy(alpha = 0.72f), glassFillBottom = Color.White.copy(alpha = 0.56f),
    glassSpecular = Color.White.copy(alpha = 0.50f),
    highlightFillTop = Color.White.copy(alpha = 0.95f), highlightFillBottom = Color.White.copy(alpha = 0.80f),
    highlightSpecular = Color.White.copy(alpha = 0.60f), highlightRim = Color.Black.copy(alpha = 0.55f),
    highlightRing = Color.Black,
    glowCoreAlpha = 0.14f, glowMidAlpha = 0.06f,
    isDark = false,
)

// Stock Material 3 components inherit the brand without wrappers (DESIGN-SYSTEM 11).
fun CytColors.toM3(): ColorScheme = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = primary, onPrimary = onPrimary, background = bg, onBackground = textPrimary,
    surface = bg, onSurface = textPrimary, onSurfaceVariant = textSecondary,
    surfaceContainerLowest = bg, surfaceContainerLow = surface1, surfaceContainer = surface2,
    surfaceContainerHigh = surface3, surfaceContainerHighest = surface3,
    surfaceVariant = surface3, inverseSurface = surface3, inverseOnSurface = textPrimary,
    outline = outlineStrong, outlineVariant = outline, error = danger,
    errorContainer = dangerContainer, onErrorContainer = onDangerContainer,
    secondary = accent, onSecondary = onPrimary, tertiary = accent, scrim = scrim,
    primaryContainer = surface3, onPrimaryContainer = textPrimary,
    secondaryContainer = surface3, onSecondaryContainer = textPrimary,
)
