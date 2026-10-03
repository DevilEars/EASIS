package app.quenya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val telperionScheme = darkColorScheme(
    background = Telperion.background, onBackground = Telperion.onBackground,
    surface = Telperion.surface, onSurface = Telperion.onSurface,
    surfaceVariant = Telperion.surfaceVariant, onSurfaceVariant = Telperion.onSurfaceVariant,
    outline = Telperion.outline,
    primary = Telperion.primary, onPrimary = Telperion.onPrimary,
    primaryContainer = Telperion.primaryContainer, onPrimaryContainer = Telperion.onPrimaryContainer,
    secondary = Telperion.secondary, onSecondary = Telperion.onSecondary,
    secondaryContainer = Telperion.secondaryContainer, onSecondaryContainer = Telperion.onSecondaryContainer,
    tertiary = Telperion.tertiary, onTertiary = Telperion.onTertiary,
    tertiaryContainer = Telperion.tertiaryContainer, onTertiaryContainer = Telperion.onTertiaryContainer,
)

private val laurelinScheme = lightColorScheme(
    background = Laurelin.background, onBackground = Laurelin.onBackground,
    surface = Laurelin.surface, onSurface = Laurelin.onSurface,
    surfaceVariant = Laurelin.surfaceVariant, onSurfaceVariant = Laurelin.onSurfaceVariant,
    outline = Laurelin.outline,
    primary = Laurelin.primary, onPrimary = Laurelin.onPrimary,
    primaryContainer = Laurelin.primaryContainer, onPrimaryContainer = Laurelin.onPrimaryContainer,
    secondary = Laurelin.secondary, onSecondary = Laurelin.onSecondary,
    secondaryContainer = Laurelin.secondaryContainer, onSecondaryContainer = Laurelin.onSecondaryContainer,
    tertiary = Laurelin.tertiary, onTertiary = Laurelin.onTertiary,
    tertiaryContainer = Laurelin.tertiaryContainer, onTertiaryContainer = Laurelin.onTertiaryContainer,
)

/** @param darkOverride null = follow the system setting; otherwise Telperion(true)/Laurelin(false). */
@Composable
fun EasisTheme(darkOverride: Boolean?, content: @Composable () -> Unit) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) telperionScheme else laurelinScheme,
        typography = easisTypography(),
        shapes = EasisShapes,
        content = content,
    )
}
