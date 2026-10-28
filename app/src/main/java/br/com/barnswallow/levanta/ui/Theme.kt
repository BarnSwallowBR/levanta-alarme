package br.com.barnswallow.levanta.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LevantaColors = darkColorScheme(
    primary = Color(0xFFFF7A1A),
    onPrimary = Color(0xFF1F0D00),
    primaryContainer = Color(0xFF3D1E06),
    onPrimaryContainer = Color(0xFFFFD3B3),
    secondary = Color(0xFFFFC857),
    onSecondary = Color(0xFF261A00),
    background = Color(0xFF0F0F12),
    onBackground = Color(0xFFF1F0F4),
    surface = Color(0xFF0F0F12),
    onSurface = Color(0xFFF1F0F4),
    surfaceVariant = Color(0xFF26262D),
    onSurfaceVariant = Color(0xFFB9B8C3),
    surfaceContainerLowest = Color(0xFF0B0B0E),
    surfaceContainerLow = Color(0xFF15151A),
    surfaceContainer = Color(0xFF19191F),
    surfaceContainerHigh = Color(0xFF1F1F26),
    surfaceContainerHighest = Color(0xFF26262E),
    outline = Color(0xFF55545E),
    outlineVariant = Color(0xFF34343C),
    error = Color(0xFFFF5A5F),
    onError = Color(0xFF2B0003),
    errorContainer = Color(0xFF4A1013),
    onErrorContainer = Color(0xFFFFDAD9),
)

@Composable
fun LevantaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LevantaColors, content = content)
}
