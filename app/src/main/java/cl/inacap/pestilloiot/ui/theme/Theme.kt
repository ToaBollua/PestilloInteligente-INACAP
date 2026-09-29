package cl.inacap.pestilloiot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = TextDark,
    primaryContainer = CyberCard,
    onPrimaryContainer = NeonGreen,
    secondary = NeonCyan,
    onSecondary = TextDark,
    secondaryContainer = CyberPanel,
    onSecondaryContainer = NeonCyan,
    tertiary = NeonPink,
    onTertiary = TextPrimary,
    error = NeonPink,
    onError = TextPrimary,
    errorContainer = Color(0xFF2A0A14),
    onErrorContainer = NeonPink,
    background = CyberBg,
    onBackground = TextPrimary,
    surface = CyberPanel,
    onSurface = TextPrimary,
    surfaceVariant = CyberCard,
    onSurfaceVariant = TextPrimary,
    outline = CyberBorderBright,
    outlineVariant = CyberBorder
)

@Composable
fun PestilloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        content = content
    )
}
