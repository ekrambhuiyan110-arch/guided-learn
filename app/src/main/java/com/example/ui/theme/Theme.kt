package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// MicroWrite Pure Light Theme — clean, formal, zero darkness
private val LightColorScheme = lightColorScheme(
    primary = AcademicNavy,
    onPrimary = Color.White,
    primaryContainer = SoftBlueContainer,
    onPrimaryContainer = AcademicNavy,
    secondary = AcademicBlue,
    onSecondary = Color.White,
    secondaryContainer = SoftBlueContainer,
    onSecondaryContainer = AcademicNavy,
    background = SurfaceBg,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceBg,
    onSurfaceVariant = TextSecondary,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder
)

@Composable
fun MicroWriteTheme(
    darkTheme: Boolean = false, // Forced light theme — remove all darkness
    dynamicColor: Boolean = false, // Preserve crisp formal brand aesthetic
    content: @Composable () -> Unit
) {
    // Pure light scheme is permanently enforced across all Android system modes
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
