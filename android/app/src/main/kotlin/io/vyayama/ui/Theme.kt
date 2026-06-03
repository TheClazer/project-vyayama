package io.vyayama.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Vyāyāma palette — dark ground, single teal accent (matches the bible PDF cover).
val Accent = Color(0xFF2FD9B6)
val AccentDeep = Color(0xFF0E8C73)
val Ground = Color(0xFF0E1014)
val Surface = Color(0xFF14161C)
val Ink = Color(0xFFEEF1F5)
val Muted = Color(0xFF9AA3B0)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF06140F),
    secondary = AccentDeep,
    background = Ground,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    onSurfaceVariant = Muted
)

@Composable
fun VyayamaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}
