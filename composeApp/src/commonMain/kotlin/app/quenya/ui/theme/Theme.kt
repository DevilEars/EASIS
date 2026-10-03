package app.quenya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Material3 needs more roles (containers, surface variants) than the 5 anchor colors per theme
 * supply. Every one of those extra roles is `lerp`-derived from the anchors below — never a new
 * invented hue. `error`/`onError`/etc. are left at Material3's built-in default: no anchor in
 * either mood board covers an error color, so defaulting beats guessing one.
 */

private val laurelinScheme = lightColorScheme(
    background = Laurelin.cream, surface = Laurelin.cream,
    onBackground = Laurelin.warmInk, onSurface = Laurelin.warmInk,
    surfaceVariant = lerp(Laurelin.cream, Laurelin.warmInk, 0.08f),
    onSurfaceVariant = lerp(Laurelin.warmInk, Laurelin.cream, 0.25f),
    outline = Laurelin.leafGold,

    primary = Laurelin.youngGreen, onPrimary = Laurelin.warmInk,
    primaryContainer = lerp(Laurelin.youngGreen, Laurelin.cream, 0.75f),
    onPrimaryContainer = lerp(Laurelin.youngGreen, Laurelin.warmInk, 0.6f),

    secondary = Laurelin.honey, onSecondary = Laurelin.warmInk,
    secondaryContainer = lerp(Laurelin.honey, Laurelin.cream, 0.75f),
    onSecondaryContainer = lerp(Laurelin.honey, Laurelin.warmInk, 0.6f),

    tertiary = Laurelin.leafGold, onTertiary = Laurelin.warmInk,
    tertiaryContainer = lerp(Laurelin.leafGold, Laurelin.cream, 0.75f),
    onTertiaryContainer = lerp(Laurelin.leafGold, Laurelin.warmInk, 0.6f),
)

private val telperionScheme = darkColorScheme(
    background = Telperion.night, surface = Telperion.night,
    onBackground = Telperion.moon, onSurface = Telperion.moon,
    surfaceVariant = lerp(Telperion.night, Telperion.moon, 0.12f),
    onSurfaceVariant = Telperion.silver,
    outline = Telperion.starGold,

    primary = Telperion.blueLeaf, onPrimary = Telperion.night,
    primaryContainer = lerp(Telperion.blueLeaf, Telperion.night, 0.6f),
    onPrimaryContainer = lerp(Telperion.blueLeaf, Telperion.moon, 0.7f),

    secondary = Telperion.starGold, onSecondary = Telperion.night,
    secondaryContainer = lerp(Telperion.starGold, Telperion.night, 0.6f),
    onSecondaryContainer = lerp(Telperion.starGold, Telperion.moon, 0.7f),

    tertiary = Telperion.silver, onTertiary = Telperion.night,
    tertiaryContainer = lerp(Telperion.silver, Telperion.night, 0.6f),
    onTertiaryContainer = lerp(Telperion.silver, Telperion.moon, 0.3f),
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
