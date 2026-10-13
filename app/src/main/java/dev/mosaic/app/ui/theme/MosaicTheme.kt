package dev.mosaic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.mosaic.app.data.preferences.ThemeMode

@Immutable
data class MosaicColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val accent: Color,
    val link: Color,
    val error: Color,
    val mediaBackdrop: Color,
)

private val LightColors = MosaicColors(
    background = Color(0xFFDAE0E6),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1A1A1B),
    textSecondary = Color(0xFF576F76),
    divider = Color(0xFFE5E7E8),
    accent = Color(0xFFFF4500),
    link = Color(0xFF0079D3),
    error = Color(0xFFC62828),
    mediaBackdrop = Color(0xFFF3F5F6),
)
private val DarkColors = MosaicColors(
    background = Color(0xFF0E1113),
    surface = Color(0xFF181C1F),
    surfaceRaised = Color(0xFF24292D),
    textPrimary = Color(0xFFF2F4F5),
    textSecondary = Color(0xFFA6ADB2),
    divider = Color(0xFF343A3F),
    accent = Color(0xFFFF4500),
    link = Color(0xFF4FBCFF),
    error = Color(0xFFFF6B6B),
    mediaBackdrop = Color(0xFF050606),
)

private val OnAccent = Color(0xFF1A1A1B)

val LocalMosaicColors = staticCompositionLocalOf { LightColors }

@Composable
fun MosaicTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = if (mode == ThemeMode.Dark) DarkColors else LightColors
    val scheme = if (mode == ThemeMode.Dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = OnAccent,
            secondary = colors.link,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.divider,
            error = colors.error,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            // Dark text on the orange accent maintains readable contrast.
            onPrimary = OnAccent,
            secondary = colors.link,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.divider,
            error = colors.error,
        )
    }
    CompositionLocalProvider(LocalMosaicColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
