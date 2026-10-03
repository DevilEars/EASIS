package app.quenya.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Exact anchor colors from the user's own mood-board samples — not guessed. Every other
 * color Material3 needs (containers, surface variants, outline) is derived from these five
 * per theme by blending, in Theme.kt — never a new invented hue.
 */

// Laurelin (light, "day"): cream ground, warm ink text, honey/leaf-gold/young-green accents.
object Laurelin {
    val cream = Color(0xFFF4EBD4)
    val honey = Color(0xFFE2B657)
    val leafGold = Color(0xFFC6A15B)
    val youngGreen = Color(0xFF7D9A62)
    val warmInk = Color(0xFF3E3428)
}

// Telperion (dark, "night"): night-blue ground, moon/silver text, blue-leaf/star-gold accents.
object Telperion {
    val night = Color(0xFF0E1620)
    val silver = Color(0xFFC9D2DC)
    val moon = Color(0xFFE7EEF4)
    val blueLeaf = Color(0xFF6E8F86)
    val starGold = Color(0xFFD6C7A1)
}
