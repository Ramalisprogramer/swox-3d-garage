package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val SwoxDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = OledCanvasBlack,
    primaryContainer = Color(0xFF072B33),
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF280B36),
    onSecondaryContainer = NeonPurple,
    tertiary = StatusActiveGreen,
    onTertiary = OledCanvasBlack,
    background = OledCanvasBlack,
    onBackground = TextPrimary,
    surface = Color(0xFF12131A),
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1A1C26),
    onSurfaceVariant = TextSecondary,
    outline = CyanStroke35,
    outlineVariant = PurpleStroke35
)

val SwoxShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SwoxGarageTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val swoxTypography = remember(context) {
        createSwoxTypography(context)
    }

    MaterialTheme(
        colorScheme = SwoxDarkColorScheme,
        typography = swoxTypography,
        shapes = SwoxShapes
    ) {
        ProvideTextStyle(value = swoxTypography.bodyMedium) {
            content()
        }
    }
}
