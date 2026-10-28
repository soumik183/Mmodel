package com.m.model.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The same deep indigo → teal range used by the launcher icon.
 * Kept outside composition so cards do not allocate a new brush while scrolling.
 */
val BrandGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF17123B),
        Color(0xFF4C3FD1),
        Color(0xFF0F766E),
    ),
)

val AvatarPalettes = listOf(
    listOf(Color(0xFF4F46E5), Color(0xFF8B5CF6)),
    listOf(Color(0xFF0E7490), Color(0xFF22D3EE)),
    listOf(Color(0xFF0F766E), Color(0xFF2DD4BF)),
    listOf(Color(0xFFB45309), Color(0xFFF59E0B)),
    listOf(Color(0xFFBE123C), Color(0xFFFB7185)),
    listOf(Color(0xFF1D4ED8), Color(0xFF38BDF8)),
    listOf(Color(0xFF3F6212), Color(0xFF84CC16)),
    listOf(Color(0xFF334155), Color(0xFF64748B)),
)

val MmodelShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.25.sp,
    ),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5146D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E3FF),
    onPrimaryContainer = Color(0xFF1A114D),
    secondary = Color(0xFF0E6B63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F5EC),
    onSecondaryContainer = Color(0xFF033A35),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBC2),
    onTertiaryContainer = Color(0xFF2A1B00),
    background = Color(0xFFF7F4EF),
    onBackground = Color(0xFF191624),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF191624),
    surfaceVariant = Color(0xFFF0EBE4),
    onSurfaceVariant = Color(0xFF514C58),
    outline = Color(0xFF8F8994),
    outlineVariant = Color(0xFFDED7CE),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF302C3A),
    inverseOnSurface = Color(0xFFF5EFF7),
    surfaceDim = Color(0xFFE4DED6),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF7F1),
    surfaceContainer = Color(0xFFF4EFE8),
    surfaceContainerHigh = Color(0xFFEEE8E0),
    surfaceContainerHighest = Color(0xFFE8E1D8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC6BEFF),
    onPrimary = Color(0xFF241B7A),
    primaryContainer = Color(0xFF3A2E93),
    onPrimaryContainer = Color(0xFFEEE9FF),
    secondary = Color(0xFF74E5D8),
    onSecondary = Color(0xFF003733),
    secondaryContainer = Color(0xFF0F4B46),
    onSecondaryContainer = Color(0xFFC7FFF8),
    tertiary = Color(0xFFF3C56B),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFF4C370D),
    onTertiaryContainer = Color(0xFFFFE7B5),
    background = Color(0xFF0A0C12),
    onBackground = Color(0xFFEDEAF4),
    surface = Color(0xFF11141C),
    onSurface = Color(0xFFEDEAF4),
    surfaceVariant = Color(0xFF1C202A),
    onSurfaceVariant = Color(0xFFCAC4D4),
    outline = Color(0xFF8E8897),
    outlineVariant = Color(0xFF2D313C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFEDEAF4),
    inverseOnSurface = Color(0xFF302C3A),
    surfaceDim = Color(0xFF0A0C12),
    surfaceBright = Color(0xFF2A2E38),
    surfaceContainerLowest = Color(0xFF06070B),
    surfaceContainerLow = Color(0xFF11141C),
    surfaceContainer = Color(0xFF171A22),
    surfaceContainerHigh = Color(0xFF1E222C),
    surfaceContainerHighest = Color(0xFF262A34),
)

@Composable
fun MmodelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = MmodelShapes,
        content = content,
    )
}
