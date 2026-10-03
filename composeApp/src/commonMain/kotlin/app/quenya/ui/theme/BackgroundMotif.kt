package app.quenya.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A faint, procedural branch-and-leaf motif in one corner, inspired by the Laurelin/Telperion
 * mood boards' tree compositions — drawn as vector curves, not an image asset, so it stays tiny
 * and regenerates cleanly with the theme. Opacity is kept low (<=14%) specifically so it never
 * competes with foreground text; it sits purely behind content.
 *
 * @param corner true = top-left (Laurelin's golden branches), false = bottom-left (Telperion's
 *   tree by the water).
 */
@Composable
fun BackgroundMotif(lineColor: Color, leafColor: Color, topLeft: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val originY = if (topLeft) 0f else h
        val sign = if (topLeft) 1f else -1f

        val branches = listOf(
            Triple(0.55f, 0.08f, 0.55f),
            Triple(0.40f, 0.14f, 0.40f),
            Triple(0.68f, 0.05f, 0.70f),
            Triple(0.28f, 0.10f, 0.26f),
        )
        branches.forEach { (lengthFrac, widthFrac, curveFrac) ->
            val length = w * lengthFrac
            val spread = h * widthFrac
            // y grows AWAY from the origin edge (down the screen for top-left, up for bottom-left)
            val path = Path().apply {
                moveTo(0f, originY)
                cubicTo(
                    length * 0.3f, originY + sign * spread * 0.2f,
                    length * 0.6f, originY + sign * spread * curveFrac,
                    length, originY + sign * spread,
                )
            }
            drawPath(path, color = lineColor, style = Stroke(width = 3f))

            // a few leaf/blossom dots trailing the branch tip
            val leafCount = 5
            for (i in 0 until leafCount) {
                val t = 0.45f + i * (0.55f / leafCount)
                val bx = length * t
                val by = originY + sign * spread * (curveFrac * t * t + 0.2f * t * (1 - t))
                val angleRad = i * 47f * (PI.toFloat() / 180f)
                val r = 7f + (i % 3) * 3f
                val lx = bx + cos(angleRad) * r
                val ly = by + sin(angleRad) * r
                drawCircle(color = leafColor, radius = 6f, center = Offset(lx, ly))
            }
        }
    }
}
