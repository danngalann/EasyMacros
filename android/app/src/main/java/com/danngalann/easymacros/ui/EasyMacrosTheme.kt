package com.danngalann.easymacros.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8F397),
    onPrimary = Color(0xFF153707),
    primaryContainer = Color(0xFF294E19),
    onPrimaryContainer = Color(0xFFD4FFBD),
    secondary = Color(0xFFFFC56B),
    onSecondary = Color(0xFF442B00),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE3E7DF),
    surface = Color(0xFF171B17),
    onSurface = Color(0xFFE3E7DF),
    surfaceVariant = Color(0xFF252A24),
    onSurfaceVariant = Color(0xFFBFC8BA),
    outline = Color(0xFF899385),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF356A20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F397),
    onPrimaryContainer = Color(0xFF0C2003),
    secondary = Color(0xFF765A00),
    background = Color(0xFFF9FBF5),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFF9FBF5),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE0E4DB),
    onSurfaceVariant = Color(0xFF44483F),
)

private val EasyMacrosTypography
    @Composable get() = MaterialTheme.typography.copy(
        displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    )

@Composable
fun EasyMacrosTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EasyMacrosTypography,
        content = content,
    )
}