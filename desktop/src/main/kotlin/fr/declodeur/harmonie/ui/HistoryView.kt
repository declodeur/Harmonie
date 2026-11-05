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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.declodeur.harmonie.engine.HistoryEntry
import fr.declodeur.harmonie.engine.MusicStyle
import fr.declodeur.harmonie.engine.PredictionNode
import fr.declodeur.harmonie.engine.Key
import fr.declodeur.harmonie.engine.Resolution
import fr.declodeur.harmonie.engine.Spelling

private val CHIP_HEIGHT = 48.dp
private val CONNECTOR_WIDTH = 26.dp
private val BRACKET_HEIGHT = 10.dp
private val GROUP_FOOTER = 22.dp

/**
 * Historique annoté façon analyse jazz :
 * - flèche pleine entre deux accords quand une dominante résout une quinte plus bas ;
 * - flèche pointillée quand une dominante résout un demi-ton plus bas (substitution tritonique) ;
 * - crochet sous les deux accords d'un II-V ;
 * - bordure pointillée pour un accord arpégé (interprété) ;
 * - regroupement par tonalité, séparateur à chaque changement, espace libre à droite.
 */
@Composable
fun HistoryPanel(history: List<HistoryEntry>, onClear: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    Panel(
        "Historique",
        modifier,
        help = Help.HISTORY,
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
                TextButton(onClick = onClear) { Text("Effacer", fontSize = 13.sp) }
            }
        },
    ) {
        if (history.isEmpty()) {
            Text("Les accords reconnus s'afficheront ici, avec leur chiffrage.", fontSize = 14.sp, color = Palette.muted)
        } else {
            PastChords(history)
        }
    }
}

/** Les accords joués, regroupés par tonalité, le plus récent toujours visible à droite. */
@Composable
private fun PastChords(history: List<HistoryEntry>) {
    val scroll = rememberScrollState()
    val groups = groupByKey(history)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll, reverseScrolling = true),
        verticalAlignment = Alignment.Top,
    ) {
        groups.forEachIndexed { g, group ->
            if (g > 0) Connector(group.entries.first().value, keyChange = true)
            Column(Modifier.width(IntrinsicSize.Max)) {
                Row {
                    group.entries.forEachIndexed { j, (i, entry) ->
                        if (j > 0) Connector(entry, keyChange = false)
                        AnnotatedChip(
                            entry = entry,
                            opensTwoFive = history.getOrNull(i + 1)?.closesTwoFive == true,
                            last = i == history.lastIndex,
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
private fun Connector(entry: HistoryEntry, keyChange: Boolean) {
    val extra = if (keyChange) GROUP_FOOTER + 4.dp else 0.dp
    val height = CHIP_HEIGHT + BRACKET_HEIGHT + extra
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

/** Accord de l'historique, avec sa part de crochet II-V en dessous. */
@Composable
private fun AnnotatedChip(entry: HistoryEntry, opensTwoFive: Boolean, last: Boolean) {
    Column(Modifier.width(IntrinsicSize.Max)) {
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
    }
}

@Composable
private fun HistoryChip(entry: HistoryEntry, last: Boolean) {
    val sp = entry.analysis?.key?.spelling ?: Spelling.DEFAULT
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
        framed.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            entry.chord.best?.symbolIn(sp) ?: "?",
            fontSize = 16.sp,
            fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        Text(
            entry.analysis?.numeral ?: "·",
            fontSize = 12.sp,
            color = entry.analysis?.function?.let { functionColor(it) } ?: Palette.muted,
            maxLines = 1,
        )
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

enum class Glyph { ARROW, DASHED_ARROW, BRACKET, DASHED_BOX }

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
