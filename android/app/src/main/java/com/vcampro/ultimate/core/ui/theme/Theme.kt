package com.vcampro.ultimate.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── NEURAL VISION INTERFACE — bảng màu ────────────────────────────────────────
val NeonCyan = Color(0xFF3DE0FF)
val NeonViolet = Color(0xFF7C5CFF)
val NeonMagenta = Color(0xFFFF4FD8)
val NeonLime = Color(0xFF6BFFB8)
val NeonAmber = Color(0xFFFFC65C)

val DeepBackground = Color(0xFF04060C)
val DeepSurface = Color(0xFF0A0F1E)
val DeepSurfaceHigh = Color(0xFF111A2E)
val DeepOutline = Color(0xFF1E2B45)

val LightBackground = Color(0xFFF4F7FC)
val LightSurface = Color(0xFFFFFFFF)

private val DarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00151C),
    primaryContainer = Color(0xFF0C3B48),
    onPrimaryContainer = NeonCyan,
    secondary = NeonViolet,
    onSecondary = Color(0xFF0B0325),
    secondaryContainer = Color(0xFF241B4D),
    onSecondaryContainer = Color(0xFFD8CEFF),
    tertiary = NeonMagenta,
    onTertiary = Color(0xFF200014),
    background = DeepBackground,
    onBackground = Color(0xFFE8F1FF),
    surface = DeepSurface,
    onSurface = Color(0xFFE8F1FF),
    surfaceVariant = DeepSurfaceHigh,
    onSurfaceVariant = Color(0xFF9FB3D1),
    outline = DeepOutline,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00788F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8ECF8),
    onPrimaryContainer = Color(0xFF00151C),
    secondary = Color(0xFF5B43C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5DEFF),
    onSecondaryContainer = Color(0xFF1B0E4D),
    tertiary = Color(0xFFB81F94),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF0B1220),
    surface = LightSurface,
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE6EDF7),
    onSurfaceVariant = Color(0xFF44526B),
    outline = Color(0xFFC3D0E2),
    error = Color(0xFFC02626),
    onError = Color.White
)

private val VcamTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.2).sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp
    )
)

private val VcamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VcamTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = VcamTypography,
        shapes = VcamShapes,
        content = content
    )
}
