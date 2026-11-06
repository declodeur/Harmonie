package fr.declodeur.harmonie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import fr.declodeur.harmonie.engine.HarmonicRhythm
import fr.declodeur.harmonie.engine.HistoryEntry
import fr.declodeur.harmonie.engine.RhythmInfo
import fr.declodeur.harmonie.engine.Tension
import fr.declodeur.harmonie.engine.TensionModel
import fr.declodeur.harmonie.engine.MusicStyle
import fr.declodeur.harmonie.engine.PredictionNode
import fr.declodeur.harmonie.engine.Key
import fr.declodeur.harmonie.engine.Resolution
import fr.declodeur.harmonie.engine.Spelling

private val CHIP_HEIGHT = 56.dp
private val CONNECTOR_WIDTH = 26.dp
private val BRACKET_HEIGHT = 10.dp
private val GROUP_FOOTER = 22.dp

/** Bande de la courbe de tension au-dessus des accords, et ligne des durées en dessous. */
internal val TENSION_H = 40.dp
internal val DURATION_H = 20.dp
private val AXIS_WIDTH = 38.dp

/**
 * Historique annoté façon analyse jazz :
 * - flèche pleine entre deux accords quand une dominante résout une quinte plus bas ;
 * - flèche pointillée quand une dominante résout un demi-ton plus bas (substitution tritonique) ;
 * - crochet sous les deux accords d'un II-V ;
 * - bordure pointillée pour un accord arpégé (interprété) ;
 * - regroupement par tonalité, séparateur à chaque changement, espace libre à droite.
 */
/** Options d'affichage de l'historique (étape 5c). */
data class HistoryDisplay(
    val showTension: Boolean = true,
    val showDurations: Boolean = true,
    val nowMs: Long = 0L,
    val rhythm: RhythmInfo? = null,
    val tensionModel: TensionModel = TensionModel.TONAL_SPACE,
)

@Composable
fun HistoryPanel(
    history: List<HistoryEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    display: HistoryDisplay = HistoryDisplay(),
    onGrid: () -> Unit = {},
    chips: (@Composable () -> Unit)? = null,
) {
    Panel(
        "Historique",
        modifier,
        help = Help.HISTORY,
        chips = chips,
        action = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem(Palette.tonic, "Tonique")
                LegendItem(Palette.subdominant, "Sous-dominante")
                LegendItem(Palette.dominant, "Dominante")
                LegendItem(Palette.muted, "Chromatique")
                LegendGlyph(Glyph.ARROW, "Résolution")
                LegendGlyph(Glyph.DASHED_ARROW, "Subst. tritonique")
                LegendGlyph(Glyph.BRACKET, "II-V")
                LegendGlyph(Glyph.DASHED_BOX, "Arpégé")
                if (display.showTension) LegendGlyph(Glyph.TENSION, "Tension")
                if (display.showDurations) LegendGlyph(Glyph.DURATION, "Durée")
                Text("vi = ii : pivot", fontSize = 12.sp, color = Palette.muted)
                Text("VI? : fonction provisoire", fontSize = 12.sp, color = Palette.muted)
                TextButton(onClick = onGrid) { Text("Grille", fontSize = 13.sp) }
                TextButton(onClick = onClear) { Text("Effacer", fontSize = 13.sp) }
            }
        },
    ) {
        if (history.isEmpty()) {
            Text("Les accords reconnus s'afficheront ici, avec leur chiffrage.", fontSize = 14.sp, color = Palette.muted)
        } else {
            Row {
                if (display.showTension) TensionAxis()
                PastChords(history, display)
            }
            if (display.showDurations) display.rhythm?.let { RhythmLine(it) }
        }
    }
}

/** Échelle de la courbe de tension, fixe à gauche de l'historique qui défile. */
@Composable
private fun TensionAxis() {
    Column(Modifier.width(AXIS_WIDTH)) {
        Box(Modifier.fillMaxWidth().height(TENSION_H)) {
            Text("max", fontSize = 11.sp, lineHeight = 12.sp, color = Palette.muted, modifier = Modifier.align(Alignment.TopStart))
            Text("repos", fontSize = 11.sp, lineHeight = 12.sp, color = Palette.muted, modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

/** Synthèse du rythme harmonique, sous l'historique. */
@Composable
private fun RhythmLine(r: RhythmInfo) {
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        val text = buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.muted)) { append("Rythme harmonique : ") }
            append("1 accord toutes les ${seconds((r.meanSeconds * 1000).toLong())} (${r.perMinute.roundToInt()} par minute)")
            if (r.acceleratesBeforeCadence) {
                withStyle(SpanStyle(color = Palette.muted)) { append("  ·  ") }
                append("s'accélère avant la cadence")
            }
        }
        Text(text, fontSize = 12.sp, lineHeight = 16.sp)
        Spacer(Modifier.width(6.dp))
        HelpBubble(Help.RHYTHM)
    }
}

/** Nombre avec une virgule décimale (« 1,7 »), quel que soit le Java installé. */
internal fun frenchDecimal(value: Double, decimals: Int): String =
    String.format(java.util.Locale.ROOT, "%.${decimals}f", value).replace('.', ',')

/** Durée lisible : « 2 s », « 1,5 s », « 12 s ». */
internal fun seconds(ms: Long): String {
    val s = ms / 1000.0
    // Virgule décimale écrite à la main : le Java fourni avec l'application n'a pas les formats régionaux
    return if (s >= 10) "${s.roundToInt()} s" else frenchDecimal(s, 1).removeSuffix(",0") + " s"
}

/** Tension d'un accord et des points de liaison avec ses voisins (milieux), pour tracer la courbe. */
private data class TensionPoint(val left: Double, val value: Double, val right: Double)

/** Durée d'un accord : part de la plus longue durée affichée, et libellé. */
private data class DurationInfo(val fraction: Float, val label: String, val current: Boolean)

/** Les accords joués, regroupés par tonalité, le plus récent toujours visible à droite. */
@Composable
private fun PastChords(history: List<HistoryEntry>, display: HistoryDisplay) {
    val scroll = rememberScrollState()
    val groups = groupByKey(history)
    val tensions = if (display.showTension) Tension.series(history, display.tensionModel) else null
    fun point(i: Int): TensionPoint? = tensions?.let { t ->
        val v = t[i]
        TensionPoint(t.getOrNull(i - 1)?.let { (it + v) / 2 } ?: v, v, t.getOrNull(i + 1)?.let { (it + v) / 2 } ?: v)
    }
    val durations = if (display.showDurations) HarmonicRhythm.durations(history, display.nowMs) else null
    val longest = durations?.let { d -> (d.dropLast(1).maxOrNull() ?: d.last()).coerceAtLeast(500) }
    fun duration(i: Int): DurationInfo? = durations?.let { d ->
        val current = i == d.lastIndex
        DurationInfo((d[i].toFloat() / longest!!).coerceIn(0.05f, 1f), if (current) "en cours" else seconds(d[i]), current)
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll, reverseScrolling = true),
        verticalAlignment = Alignment.Top,
    ) {
        groups.forEachIndexed { g, group ->
            val firstIndex = group.entries.first().index
            if (g > 0) Connector(group.entries.first().value, keyChange = true, tension = point(firstIndex)?.left, display = display)
            Column(Modifier.width(IntrinsicSize.Max)) {
                Row {
                    group.entries.forEachIndexed { j, (i, entry) ->
                        if (j > 0) Connector(entry, keyChange = false, tension = point(i)?.left, display = display)
                        AnnotatedChip(
                            entry = entry,
                            opensTwoFive = history.getOrNull(i + 1)?.closesTwoFive == true,
                            last = i == history.lastIndex,
                            tension = point(i),
                            duration = duration(i),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.fillMaxWidth().height(2.dp)
                        .background(Palette.accent.copy(alpha = 0.45f), RoundedCornerShape(1.dp)),
                )
                Text(
                    group.key?.name ?: "Tonalité indéterminée",
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 12.sp,
                    color = Palette.muted,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
    }
}

/** Ordonnée d'une tension (0 = repos en bas, 1 = maximum en haut) dans la bande de la courbe. */
private fun DrawScope.tensionY(t: Double): Float {
    val pad = 5.dp.toPx()
    return pad + (1f - t.toFloat()) * (size.height - 2 * pad)
}

/** Repères de la bande : maximum (pointillé) et repos (trait fin). */
private fun DrawScope.tensionGuides() {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    drawLine(Palette.muted.copy(alpha = 0.6f), Offset(0f, tensionY(1.0)), Offset(size.width, tensionY(1.0)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
    drawLine(Palette.border, Offset(0f, tensionY(0.0)), Offset(size.width, tensionY(0.0)), strokeWidth = 1.dp.toPx())
}

private data class KeyGroup(val key: Key?, val entries: List<IndexedValue<HistoryEntry>>)

/** Regroupe les accords consécutifs joués dans la même tonalité. */
private fun groupByKey(history: List<HistoryEntry>): List<KeyGroup> {
    val groups = mutableListOf<KeyGroup>()
    history.withIndex().forEach { iv ->
        val key = iv.value.analysis?.key
        val lastGroup = groups.lastOrNull()
        if (lastGroup != null && lastGroup.key == key) {
            groups[groups.lastIndex] = lastGroup.copy(entries = lastGroup.entries + iv)
        } else {
            groups += KeyGroup(key, listOf(iv))
        }
    }
    return groups
}

/**
 * Espace entre deux accords : flèche de résolution vers [entry], prolongement du crochet II-V,
 * et, en cas de changement de tonalité, trait vertical de séparation.
 */
@Composable
private fun Connector(entry: HistoryEntry, keyChange: Boolean, tension: Double?, display: HistoryDisplay) {
    val extra = (if (keyChange) GROUP_FOOTER + 4.dp else 0.dp) + (if (display.showDurations) DURATION_H else 0.dp)
    val height = CHIP_HEIGHT + BRACKET_HEIGHT + extra
    Column {
        if (display.showTension) {
            Canvas(Modifier.width(CONNECTOR_WIDTH).height(TENSION_H)) {
                if (tension != null) {
                    drawRect(Palette.tensionFill, Offset(0f, tensionY(tension)), androidx.compose.ui.geometry.Size(size.width, tensionY(0.0) - tensionY(tension)))
                }
                tensionGuides()
                if (tension != null) {
                    val y = tensionY(tension)
                    drawLine(Palette.tension, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Canvas(Modifier.width(CONNECTOR_WIDTH).height(height)) {
            val w = size.width
            if (keyChange) {
                drawLine(Palette.border, Offset(w / 2, 0f), Offset(w / 2, size.height), strokeWidth = 1.dp.toPx())
            }
            entry.resolution?.let { r ->
                drawArrow(
                    from = Offset(3.dp.toPx(), CHIP_HEIGHT.toPx() / 2),
                    to = Offset(w - 3.dp.toPx(), CHIP_HEIGHT.toPx() / 2),
                    color = Palette.dominant,
                    dashed = r == Resolution.HALF_STEP,
                )
            }
            if (entry.closesTwoFive) {
                val y = CHIP_HEIGHT.toPx() + BRACKET_HEIGHT.toPx() - 2.dp.toPx()
                drawLine(Palette.muted, Offset(0f, y), Offset(w, y), strokeWidth = 1.5.dp.toPx())
            }
        }
    }
}

/** Accord de l'historique, avec sa part de crochet II-V en dessous. */
@Composable
private fun AnnotatedChip(entry: HistoryEntry, opensTwoFive: Boolean, last: Boolean, tension: TensionPoint?, duration: DurationInfo?) {
    Column(Modifier.width(IntrinsicSize.Max)) {
        if (tension != null) {
            Canvas(Modifier.fillMaxWidth().height(TENSION_H)) {
                val cx = size.width / 2
                // Aire sous la courbe, jusqu'à la ligne de repos
                val area = Path().apply {
                    moveTo(0f, tensionY(tension.left))
                    lineTo(cx, tensionY(tension.value))
                    lineTo(size.width, tensionY(tension.right))
                    lineTo(size.width, tensionY(0.0))
                    lineTo(0f, tensionY(0.0))
                    close()
                }
                drawPath(area, Palette.tensionFill)
                tensionGuides()
                val path = Path().apply {
                    moveTo(0f, tensionY(tension.left))
                    lineTo(cx, tensionY(tension.value))
                    lineTo(size.width, tensionY(tension.right))
                }
                drawPath(path, Palette.tension, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(Palette.tension, radius = 3.dp.toPx(), center = Offset(cx, tensionY(tension.value)))
            }
        }
        HistoryChip(entry, last)
        Canvas(Modifier.fillMaxWidth().height(BRACKET_HEIGHT)) {
            val y = size.height - 2.dp.toPx()
            val cx = size.width / 2
            val stroke = 1.5.dp.toPx()
            if (opensTwoFive) {
                drawLine(Palette.muted, Offset(cx, 0f), Offset(cx, y), strokeWidth = stroke)
                drawLine(Palette.muted, Offset(cx, y), Offset(size.width, y), strokeWidth = stroke)
            }
            if (entry.closesTwoFive) {
                drawLine(Palette.muted, Offset(cx, 0f), Offset(cx, y), strokeWidth = stroke)
                drawLine(Palette.muted, Offset(0f, y), Offset(cx, y), strokeWidth = stroke)
            }
        }
        if (duration != null) {
            Column(Modifier.fillMaxWidth().height(DURATION_H), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val w = size.width * duration.fraction
                    val color = if (duration.current) Palette.accent else Palette.muted.copy(alpha = 0.7f)
                    drawRoundRect(color, topLeft = Offset((size.width - w) / 2, 0f), size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                }
                Text(duration.label, fontSize = 11.sp, lineHeight = 14.sp, color = Palette.muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun HistoryChip(entry: HistoryEntry, last: Boolean) {
    val sp = entry.spelling
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (last) Palette.accent else Palette.border
    val borderWidth = if (last) 2.dp else 1.dp
    val base = Modifier.height(CHIP_HEIGHT).background(Palette.surfaceHigh, shape)
    val framed = if (entry.chord.arpeggiated) {
        base.drawBehind {
            val sw = borderWidth.toPx()
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
            )
        }
    } else {
        base.border(borderWidth, borderColor, shape)
    }
    Column(
        framed.padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            entry.chord.best?.symbolIn(sp) ?: "?",
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        val numeral = entry.analysis?.numeral ?: "·"
        val color = entry.analysis?.function?.let { functionColor(it) } ?: Palette.muted
        val pivot = entry.pivotFrom
        if (pivot != null) {
            // Accord pivot, notation d'analyse classique : chiffrage dans l'ancienne tonalité = dans la nouvelle
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Palette.muted)) { append(pivot.numeral) }
                    withStyle(SpanStyle(color = Palette.muted)) { append(" = ") }
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(numeral) }
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
            )
        } else {
            // Fonction provisoire (VI, VII…) : elle dépend de l'accord suivant, pas encore joué
            val provisional = entry.analysis?.contextPredicted == true
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = color)) { append(numeral) }
                    if (provisional) withStyle(SpanStyle(color = Palette.muted)) { append("?") }
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
            )
        }
    }
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, dashed: Boolean) {
    val stroke = 1.8.dp.toPx()
    val head = 5.dp.toPx()
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())) else null
    drawLine(color, from, Offset(to.x - head * 0.6f, to.y), strokeWidth = stroke, pathEffect = effect, cap = StrokeCap.Round)
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(to.x - head, to.y - head * 0.7f)
        lineTo(to.x - head, to.y + head * 0.7f)
        close()
    }
    drawPath(path, color)
}

enum class Glyph { ARROW, DASHED_ARROW, BRACKET, DASHED_BOX, TENSION, DURATION }

/** Petit dessin suivi d'un texte, pour les légendes des annotations. */
@Composable
fun LegendGlyph(glyph: Glyph, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.5.dp.toPx()
            when (glyph) {
                Glyph.ARROW -> drawArrow(Offset(0f, h / 2), Offset(w, h / 2), Palette.dominant, dashed = false)
                Glyph.DASHED_ARROW -> drawArrow(Offset(0f, h / 2), Offset(w, h / 2), Palette.dominant, dashed = true)
                Glyph.BRACKET -> {
                    drawLine(Palette.muted, Offset(2f, 0f), Offset(2f, h - stroke), strokeWidth = stroke)
                    drawLine(Palette.muted, Offset(2f, h - stroke), Offset(w - 2f, h - stroke), strokeWidth = stroke)
                    drawLine(Palette.muted, Offset(w - 2f, 0f), Offset(w - 2f, h - stroke), strokeWidth = stroke)
                }
                Glyph.TENSION -> {
                    val path = Path().apply { moveTo(0f, h * 0.8f); lineTo(w * 0.5f, h * 0.2f); lineTo(w, h * 0.6f) }
                    drawPath(path, Palette.tension, style = Stroke(width = stroke))
                }
                Glyph.DURATION -> drawRoundRect(
                    color = Palette.muted,
                    topLeft = Offset(0f, h / 2 - stroke),
                    size = androidx.compose.ui.geometry.Size(w * 0.7f, 2 * stroke),
                    cornerRadius = CornerRadius(stroke),
                )
                Glyph.DASHED_BOX -> drawRoundRect(
                    color = Palette.muted,
                    topLeft = Offset(stroke, stroke),
                    size = androidx.compose.ui.geometry.Size(w - 2 * stroke, h - 2 * stroke),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Palette.muted)
    }
}
