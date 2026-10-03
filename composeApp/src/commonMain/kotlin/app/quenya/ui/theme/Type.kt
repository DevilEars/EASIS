package app.quenya.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.quenya.ui.resources.Res
import app.quenya.ui.resources.cinzel_decorative_bold
import app.quenya.ui.resources.cinzel_decorative_regular
import org.jetbrains.compose.resources.Font

/**
 * The display font is chrome-only: section headers, screen titles, branding. It never touches
 * exercise prompts, Quenya word/gloss text, or anything being read or checked — those stay on
 * the system font at Material3's default sizes, unconditionally. See BACKLOG / theme decisions.
 */
@Composable
fun easisTypography(): Typography {
    val display = FontFamily(
        Font(Res.font.cinzel_decorative_regular, FontWeight.Normal),
        Font(Res.font.cinzel_decorative_bold, FontWeight.Bold),
    )
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = display),
        displayMedium = base.displayMedium.copy(fontFamily = display),
        headlineLarge = base.headlineLarge.copy(fontFamily = display),
        headlineMedium = base.headlineMedium.copy(fontFamily = display),
        headlineSmall = base.headlineSmall.copy(fontFamily = display),
        titleSmall = base.titleSmall.copy(fontFamily = display),
    )
}
