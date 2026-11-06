package fr.declodeur.harmonie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import fr.declodeur.harmonie.engine.ChordCandidate
import fr.declodeur.harmonie.engine.Key
import fr.declodeur.harmonie.engine.Notes
import fr.declodeur.harmonie.engine.KeyEstimate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cercle des quintes des 24 tonalités : majeures sur l'anneau extérieur, relatives mineures
 * sur l'anneau intérieur. Remplissage = probabilité, contour orange = compatible (la gamme
 * contient les notes jouées), trait épais = tonalité retenue.
 */
@Composable
fun CircleOfFifths(estimate: KeyEstimate, modifier: Modifier = Modifier, path: List<ChordCandidate> = emptyList()) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = radius * 0.98f
        val middle = radius * 0.64f
        val inner = radius * 0.32f
        val maxP = estimate.ranking.firstOrNull()?.probability?.takeIf { it > 0 } ?: 1.0

        for (i in 0 until 12) {
            val major = Key((i * 7) % 12, false)
            val minor = Key((i * 7 + 9) % 12, true)
            drawSector(i, major, middle, outer, center, estimate, maxP, measurer, radius * 0.115f, false)
            drawSector(i, minor, inner, middle, center, estimate, maxP, measurer, radius * 0.085f, true)
        }
        if (path.size >= 2) drawChordPath(path, center, inner, middle, outer)
    }
}

/**
 * Parcours des derniers accords : accords majeurs (et de dominante) sur l'anneau extérieur,
 * mineurs et diminués sur l'anneau intérieur ; flèches de plus en plus foncées vers l'accord
 * actuel, entouré.
 */
private fun DrawScope.drawChordPath(path: List<ChordCandidate>, center: Offset, inner: Float, middle: Float, outer: Float) {
    fun position(c: ChordCandidate): Offset {
        val minorRing = c.type.isMinorTriadBased || c.type.isDiminished
        val index = if (minorRing) Notes.pc((c.root - 9) * 7) else Notes.pc(c.root * 7)
        val r = if (minorRing) (inner + middle) / 2f else (middle + outer) / 2f
        val a = Math.toRadians(-90.0 + index * 30.0)
        return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
    }
    val points = path.map(::position)
    val stroke = 2.dpPx(this)
    val gap = 11.dpPx(this)
    val n = points.size - 1
    for (k in 0 until n) {
        val a = points[k]
        val b = points[k + 1]
        val d = b - a
        val len = d.getDistance()
        if (len < 2 * gap) continue
        val u = d / len
        val from = a + u * gap
        val to = b - u * gap
        val alpha = 0.3f + 0.7f * (k + 1) / n
        val color = Palette.path.copy(alpha = alpha)
        drawLine(color, from, to, strokeWidth = stroke)
        // Pointe de flèche
        val head = 7.dpPx(this)
        val nx = -u.y
        val ny = u.x
        val arrow = Path().apply {
            moveTo(to.x, to.y)
            lineTo(to.x - u.x * head + nx * head * 0.5f, to.y - u.y * head + ny * head * 0.5f)
            lineTo(to.x - u.x * head - nx * head * 0.5f, to.y - u.y * head - ny * head * 0.5f)
            close()
        }
        drawPath(arrow, color)
    }
    drawCircle(Palette.path, radius = gap, center = points.last(), style = Stroke(stroke))
}

private fun DrawScope.drawSector(
    index: Int,
    key: Key,
    r1: Float,
    r2: Float,
    center: Offset,
    estimate: KeyEstimate,
    maxP: Double,
    measurer: TextMeasurer,
    fontPx: Float,
    minor: Boolean,
) {
    // Angle Compose : 0° = 3 h, sens horaire. La tonalité d'indice 0 (C / Am) est en haut.
    val start = -90f + index * 30f - 15f
    val path = Path().apply {
        arcTo(Rect(center, r2), start, 30f, forceMoveTo = true)
        arcTo(Rect(center, r1), start + 30f, -30f, forceMoveTo = false)
        close()
    }
    val p = estimate.probabilityOf(key)
    drawPath(path, Palette.surfaceHigh)
    if (p > 0.005) {
        val alpha = (0.10 + 0.85 * (p / maxP)).toFloat().coerceIn(0f, 0.95f)
        drawPath(path, Palette.accent.copy(alpha = alpha))
    }
    drawPath(path, Palette.border, style = Stroke(1f))
    if (key in estimate.compatible) {
        drawPath(path, Palette.compatible, style = Stroke(2.dpPx(this)))
    }
    if (key == estimate.current) {
        drawPath(path, Palette.text, style = Stroke(3.dpPx(this)))
    }

    // Nom de la tonalité (et pourcentage s'il y a la place), centré dans le secteur
    val angle = Math.toRadians((-90.0 + index * 30.0))
    val rm = (r1 + r2) / 2f
    val cx = center.x + rm * cos(angle).toFloat()
    val cy = center.y + rm * sin(angle).toFloat()
    val strong = p >= 0.10 || key == estimate.current
    val label = key.tonicName + if (minor) "m" else ""
    val style = TextStyle(
        fontSize = (fontPx / density).sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        color = if (p > 0.5) Color.White else Palette.text,
    )
    val layout = measurer.measure(label, style)
    val showPercent = p >= 0.05 && fontPx >= 11f * density
    val pctLayout = if (showPercent) {
        measurer.measure("${(p * 100).toInt()} %", TextStyle(fontSize = (fontPx * 0.72f / density).sp, color = Palette.text))
    } else null
    val totalH = layout.size.height + (pctLayout?.size?.height ?: 0)
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - totalH / 2f))
    if (pctLayout != null) {
        drawText(pctLayout, topLeft = Offset(cx - pctLayout.size.width / 2f, cy - totalH / 2f + layout.size.height))
    }
}

private fun Int.dpPx(scope: DrawScope): Float = this * scope.density
