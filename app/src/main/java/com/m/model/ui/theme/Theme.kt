package com.m.model.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val MmodelShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
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
        letterSpacing = 0.3.sp,
    ),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5145E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E0FF),
    onPrimaryContainer = Color(0xFF171052),
    secondary = Color(0xFF0E7490),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4F3FB),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF16151F),
    surface = Color(0xFFFCFBFF),
    onSurface = Color(0xFF16151F),
    surfaceVariant = Color(0xFFECE9F6),
    onSurfaceVariant = Color(0xFF5D5A70),
    outline = Color(0xFF8B87A0),
    outlineVariant = Color(0xFFDEDBEA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF2F2D38),
    inverseOnSurface = Color(0xFFF3EFF7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B4FF),
    onPrimary = Color(0xFF241B7A),
    primaryContainer = Color(0xFF3A2FA8),
    onPrimaryContainer = Color(0xFFE4E0FF),
    secondary = Color(0xFF67D7F0),
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFF63E2CF),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF0F4F47),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0B0F1D),
    onBackground = Color(0xFFE8E5F2),
    surface = Color(0xFF111627),
    onSurface = Color(0xFFE8E5F2),
    surfaceVariant = Color(0xFF20253A),
    onSurfaceVariant = Color(0xFFB9B4CC),
    outline = Color(0xFF8B87A0),
    outlineVariant = Color(0xFF2E3348),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE8E5F2),
    inverseOnSurface = Color(0xFF2F2D38),
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
