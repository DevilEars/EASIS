package app.quenya.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One world, two times of day — the same forest-green/gold/wine hue family in both modes,
 * with which role is "ground" and which is "ink" swapped. Not a generic light/dark inversion.
 * Forest green leads (primary) — the Elvish identity, not a minor accent.
 */

// Telperion (dark, "night"): deep aubergine-black ground, warm ivory ink.
object Telperion {
    val background = Color(0xFF120B16)
    val onBackground = Color(0xFFEDE3D0)
    val surface = Color(0xFF1B1220)
    val onSurface = Color(0xFFEDE3D0)
    val surfaceVariant = Color(0xFF2A1E33)
    val onSurfaceVariant = Color(0xFFCBB9A8)
    val outline = Color(0xFF6B5C4A)

    val primary = Color(0xFF3D9970)           // forest green, brightened to lead on near-black
    val onPrimary = Color(0xFF07160F)
    val primaryContainer = Color(0xFF1C4A35)
    val onPrimaryContainer = Color(0xFFA8E8C8)

    val secondary = Color(0xFFC9A869)         // antique gold
    val onSecondary = Color(0xFF2A1C08)
    val secondaryContainer = Color(0xFF4A3A1A)
    val onSecondaryContainer = Color(0xFFF0DBA8)

    val tertiary = Color(0xFF8C2F4E)          // deep wine
    val onTertiary = Color(0xFFFBE8EE)
    val tertiaryContainer = Color(0xFF4A1524)
    val onTertiaryContainer = Color(0xFFF3C9D6)
}

// Laurelin (light, "day"): warm parchment ground, deep ink-brown text — an illuminated
// manuscript, not an inverted dark mode.
object Laurelin {
    val background = Color(0xFFF3E9D2)
    val onBackground = Color(0xFF2A1810)
    val surface = Color(0xFFEFE1C4)
    val onSurface = Color(0xFF2A1810)
    val surfaceVariant = Color(0xFFE3D3AD)
    val onSurfaceVariant = Color(0xFF4A3A26)
    val outline = Color(0xFFA08E6E)

    val primary = Color(0xFF1F4D34)           // forest-green ink
    val onPrimary = Color(0xFFE3F2E8)
    val primaryContainer = Color(0xFFBFE0CC)
    val onPrimaryContainer = Color(0xFF0E2B1C)

    val secondary = Color(0xFF8A5A1E)         // bronze/gold ink
    val onSecondary = Color(0xFFFFF8E8)
    val secondaryContainer = Color(0xFFE8CB8C)
    val onSecondaryContainer = Color(0xFF4A3005)

    val tertiary = Color(0xFF7A1F38)          // wine ink
    val onTertiary = Color(0xFFFBE8EE)
    val tertiaryContainer = Color(0xFFEFC3D0)
    val onTertiaryContainer = Color(0xFF4A0E1E)
}
