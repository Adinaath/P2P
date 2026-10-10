package org.airbornemesh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CrisisRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)
val SafeGreen = Color(0xFF10B981)
val InfoBlue = Color(0xFF3B82F6)

val BackgroundDark = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val SurfaceVariantDark = Color(0xFF334155)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = InfoBlue,
    onPrimary = Color.White,
    primaryContainer = SurfaceVariantDark,
    secondary = WarningAmber,
    onSecondary = Color.Black,
    error = CrisisRed,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun AirborneMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
