package ir.uciranx.ucg.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object UcgColors {
    val Accent = Color(0xFF34D1BF)
    val Connected = Color(0xFF34D1BF)
    val Connecting = Color(0xFFFFC857)
    val Paused = Color(0xFF7AA7FF)
    val Failed = Color(0xFFFF6B6B)
    val Idle = Color(0xFF6B7A90)
}

private val Scheme = darkColorScheme(
    primary = UcgColors.Accent,
    onPrimary = Color(0xFF00201C),
    secondary = Color(0xFF7AA7FF),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2438),
    onSurfaceVariant = Color(0xFF9AA8BD),
    outline = Color(0xFF2A3650),
    error = UcgColors.Failed,
)

@Composable
fun UcgTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
