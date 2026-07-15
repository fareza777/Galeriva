package com.galeriva.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ----- Brand palette: "ink & gold" -----

object Brand {
    val Gold = Color(0xFFEEC170)
    val GoldDeep = Color(0xFFC99A45)
    val Periwinkle = Color(0xFF93A5FF)
    val InkBackground = Color(0xFF0A0B12)
    val InkSurface = Color(0xFF12141F)
    val InkSurfaceHigh = Color(0xFF1A1D2C)
    val InkOutline = Color(0xFF2A2E44)
    val Rose = Color(0xFFFF6B81)

    /** Signature gradient used for the wordmark and accents. */
    val Sheen = Brush.linearGradient(listOf(Gold, Periwinkle))
}

private val DarkColors = darkColorScheme(
    primary = Brand.Gold,
    onPrimary = Color(0xFF241A05),
    primaryContainer = Color(0xFF3A2E12),
    onPrimaryContainer = Brand.Gold,
    secondary = Brand.Periwinkle,
    onSecondary = Color(0xFF10123A),
    secondaryContainer = Color(0xFF232848),
    onSecondaryContainer = Brand.Periwinkle,
    background = Brand.InkBackground,
    surface = Brand.InkSurface,
    surfaceVariant = Brand.InkSurfaceHigh,
    surfaceContainer = Brand.InkSurface,
    surfaceContainerHigh = Brand.InkSurfaceHigh,
    onBackground = Color(0xFFF2F1EC),
    onSurface = Color(0xFFF2F1EC),
    onSurfaceVariant = Color(0xFF9DA1BC),
    outline = Brand.InkOutline,
    outlineVariant = Color(0xFF20233A),
    error = Brand.Rose
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C6D1F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4E3BC),
    onPrimaryContainer = Color(0xFF4A3910),
    secondary = Color(0xFF4F5DDB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E3FF),
    onSecondaryContainer = Color(0xFF20276B),
    background = Color(0xFFFBF8F2),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1EDE3),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF6F2E9),
    onBackground = Color(0xFF1B1A16),
    onSurface = Color(0xFF1B1A16),
    onSurfaceVariant = Color(0xFF6E6A5E),
    outline = Color(0xFFDCD6C6),
    outlineVariant = Color(0xFFEAE5D8),
    error = Color(0xFFC2334D)
)

// ----- Typography: Plus Jakarta Sans (variable, bundled in assets) -----

@Composable
fun galerivaFontFamily(): FontFamily {
    val assets = LocalContext.current.assets
    return remember {
        try {
            FontFamily(
                Font("fonts/plus_jakarta.ttf", assets, FontWeight.Normal),
                Font("fonts/plus_jakarta.ttf", assets, FontWeight.Medium),
                Font("fonts/plus_jakarta.ttf", assets, FontWeight.SemiBold),
                Font("fonts/plus_jakarta.ttf", assets, FontWeight.Bold),
                Font("fonts/plus_jakarta.ttf", assets, FontWeight.ExtraBold)
            )
        } catch (_: Exception) {
            FontFamily.SansSerif
        }
    }
}

private fun buildTypography(family: FontFamily) = Typography(
    displaySmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    ),
    bodyLarge = TextStyle(fontFamily = family, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.3.sp
    )
)

private val GalerivaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun GalerivaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = buildTypography(galerivaFontFamily()),
        shapes = GalerivaShapes,
        content = content
    )
}
