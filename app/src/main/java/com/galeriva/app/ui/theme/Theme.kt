package com.galeriva.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Indigo = Color(0xFF5B6CFF)
private val IndigoSoft = Color(0xFF8B9BFF)
private val Amber = Color(0xFFFFD479)

private val DarkColors = darkColorScheme(
    primary = IndigoSoft,
    onPrimary = Color(0xFF10123A),
    secondary = Amber,
    background = Color(0xFF0E0F1A),
    surface = Color(0xFF15172A),
    surfaceVariant = Color(0xFF1E2138),
    onBackground = Color(0xFFECEDF5),
    onSurface = Color(0xFFECEDF5),
    onSurfaceVariant = Color(0xFFA9ADCC)
)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Color(0xFFB8860B),
    background = Color(0xFFF7F7FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFECEDF7),
    onBackground = Color(0xFF191A2C),
    onSurface = Color(0xFF191A2C),
    onSurfaceVariant = Color(0xFF5A5D7A)
)

private val GalerivaTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp)
)

@Composable
fun GalerivaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GalerivaTypography,
        content = content
    )
}
