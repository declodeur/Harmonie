package fr.declodeur.harmonie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.declodeur.harmonie.engine.MusicStyle
import fr.declodeur.harmonie.engine.Notes
import fr.declodeur.harmonie.engine.PredictionNode
import fr.declodeur.harmonie.engine.Spelling
import fr.declodeur.harmonie.engine.ToneRole
import kotlin.math.roundToInt

private val COL1_W = 100.dp
private val COL2_W = 64.dp
private val COL3_W = 64.dp
private val LEFT_MARGIN = 18.dp
private val GAP_12 = 26.dp
private val GAP_23 = 20.dp
private val CHIP1_H = 46.dp
private val KEYBOARD_H = 24.dp

/** Position calculée d'un nœud de l'arbre. */
private data class Placed(val node: PredictionNode, val level: Int, val x: Dp, val y: Dp, val w: Dp, val h: Dp, val parent: Int = -1)

/**
 * Arbre des suites probables, en éventail vers la droite : premier niveau avec pourcentage et
 * mini clavier (comment jouer l'accord), niveaux suivants plus petits. L'intensité de chaque
 * case suit la probabilité du chemin ; le chiffrage est coloré selon la fonction.
 */
/** Cadre « Suites probables » : l'arbre part du bord gauche, c'est-à-dire de l'accord courant. */
@Composable
fun PredictionPanel(
    nodes: List<PredictionNode>,
    style: MusicStyle,
    spelling: Spelling,
    showMiniKeyboards: Boolean,
    modifier: Modifier = Modifier,
) {
    Panel(
        "Suites probables",
        modifier,
        help = Help.PREDICTIONS,
        action = { Text("estimation · ${style.label}", fontSize = 12.sp, color = Palette.muted) },
    ) {
        if (nodes.isEmpty()) {
            Text("Jouez quelques accords…", fontSize = 15.sp, color = Palette.muted)
        } else {
            TreeBody(nodes, spelling, showMiniKeyboards)
        }
    }
}

@Composable
private fun TreeBody(nodes: List<PredictionNode>, spelling: Spelling, showMiniKeyboards: Boolean) {
    run {
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 4.dp).clipToBounds()) {
            val placed = layoutTree(nodes, maxHeight, maxWidth, showMiniKeyboards)
            val top1 = nodes.maxOf { it.pathProbability }.coerceAtLeast(1e-9)

            // Liens entre les cases
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(1.dp.toPx())
                fun link(x1: Dp, y1: Dp, x2: Dp, y2: Dp) {
                    val a = Offset(x1.toPx(), y1.toPx())
                    val b = Offset(x2.toPx(), y2.toPx())
                    val mid = (a.x + b.x) / 2
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                    }
                    drawPath(path, Palette.border, style = stroke)
                }
                val anchorY = placed.filter { it.level == 1 }.let { l1 ->
                    if (l1.isEmpty()) 0.dp else (l1.first().y + l1.last().y + CHIP1_H) / 2
                }
                for (p in placed) {
                    val cy = p.y + (if (p.level == 1) CHIP1_H else p.h) / 2
                    if (p.level == 1) link(0.dp, anchorY, p.x, cy)
                    val parent = placed.getOrNull(p.parent)
                    if (parent != null) {
                        val py = parent.y + (if (parent.level == 1) CHIP1_H else parent.h) / 2
                        link(parent.x + parent.w, py, p.x, cy)
                    }
                }
            }

            for (p in placed) {
                val alpha = (0.06f + 0.6f * (p.node.pathProbability / top1).toFloat()).coerceIn(0.04f, 0.7f)
                Box(Modifier.offset(p.x, p.y)) {
                    if (p.level == 1) {
                        Column {
                            FirstLevelChip(p.node, alpha, spelling, Modifier.size(p.w, CHIP1_H))
                            if (showMiniKeyboards && p.h >= CHIP1_H + KEYBOARD_H + 4.dp && p.node.voicing.isNotEmpty()) {
                                Spacer(Modifier.size(4.dp))
                                MiniKeyboard(p.node, Modifier.size(p.w, KEYBOARD_H))
                            }
                        }
                    } else {
                        SmallChip(p.node, alpha, spelling, Modifier.size(p.w, p.h))
                    }
                }
            }
        }
    }
}

/** Calcule la position de chaque case : chaque branche du premier niveau occupe une bande verticale. */
private fun layoutTree(nodes: List<PredictionNode>, height: Dp, width: Dp, showKeyboards: Boolean): List<Placed> {
    val rowsPerBranch = nodes.map { n -> maxOf(1, n.children.size) }
    val totalRows = rowsPerBranch.sum()
    val withKeyboard = CHIP1_H + KEYBOARD_H + 6.dp
    // Hauteur d'une ligne de petites cases, et place pour le mini clavier si possible
    var rowH = (height / totalRows).coerceIn(24.dp, 36.dp)
    var blockMin = if (showKeyboards) withKeyboard else CHIP1_H + 4.dp
    fun total() = rowsPerBranch.fold(0.dp) { acc, r -> acc + maxOf(rowH * r, blockMin) }
    if (total() > height) blockMin = CHIP1_H + 4.dp
    if (total() > height) rowH = (height / totalRows).coerceAtLeast(20.dp)

    // Colonnes élargies quand la place le permet (jusqu'à +60 %)
    val natural = LEFT_MARGIN + COL1_W + GAP_12 + COL2_W + GAP_23 + COL3_W
    val k = (width / natural).coerceIn(1f, 1.6f)
    val w1 = COL1_W * k
    val w2 = COL2_W * k
    val w3 = COL3_W * k
    val x1 = LEFT_MARGIN * k
    val x2 = x1 + w1 + GAP_12 * k
    val x3 = x2 + w2 + GAP_23 * k
    val result = mutableListOf<Placed>()
    var y = 0.dp
    nodes.forEachIndexed { i, n ->
        val bandH = maxOf(rowH * rowsPerBranch[i], blockMin)
        result += Placed(n, 1, x1, y, w1, bandH)
        val i1 = result.lastIndex
        val childH = (rowH - 4.dp).coerceAtLeast(18.dp)
        val childrenTop = y + (bandH - rowH * n.children.size) / 2
        n.children.forEachIndexed { j, c ->
            val cy = childrenTop + rowH * j + 2.dp
            result += Placed(c, 2, x2, cy, w2, childH, parent = i1)
            val i2 = result.lastIndex
            c.children.firstOrNull()?.let { g -> result += Placed(g, 3, x3, cy, w3, childH, parent = i2) }
        }
        y += bandH
    }
    return result
}

@Composable
private fun FirstLevelChip(node: PredictionNode, alpha: Float, spelling: Spelling, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier.background(Palette.accent.copy(alpha = alpha), shape).border(1.dp, Palette.border, shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(node.chord.symbolIn(spelling), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                node.numeral,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = node.function?.let { functionColor(it) } ?: Palette.muted,
                maxLines = 1,
            )
        }
        Text("${(node.probability * 100).roundToInt()} %", fontSize = 12.sp, color = Palette.text)
    }
}

@Composable
private fun SmallChip(node: PredictionNode, alpha: Float, spelling: Spelling, modifier: Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier.background(Palette.accent.copy(alpha = alpha), shape).border(1.dp, Palette.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            node.chord.symbolIn(spelling),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = node.function?.let { functionColor(it) }?.let { lerpToText(it) } ?: Palette.text,
            maxLines = 1,
        )
    }
}

/** Couleur de fonction adoucie vers le texte, pour rester lisible sur fond bleu. */
private fun lerpToText(c: Color): Color = Color(
    red = (c.red + Palette.text.red) / 2,
    green = (c.green + Palette.text.green) / 2,
    blue = (c.blue + Palette.text.blue) / 2,
)

/** Mini clavier : la position à jouer, choisie pour bouger le moins possible depuis l'accord courant. */
@Composable
private fun MiniKeyboard(node: PredictionNode, modifier: Modifier) {
    val notes = node.voicing
    val low = notes.min()
    val high = notes.max()
    val start = low - Notes.pc(low)                 // le C sous la note la plus grave
    var end = start + 23                             // deux octaves au moins
    while (end < high) end += 12
    val whites = (start..end).filter { !Notes.isBlack(it) }
    Canvas(modifier.border(1.dp, Palette.border, RoundedCornerShape(2.dp))) {
        val ww = size.width / whites.size
        val bw = ww * 0.62f
        val bh = size.height * 0.6f
        fun colorOf(n: Int): Color? = if (n !in notes) null else when (node.chord.roleOf(n)) {
            ToneRole.ROOT -> Palette.root
            ToneRole.TENSION -> Palette.tension
            else -> Palette.chordTone
        }
        whites.forEachIndexed { i, n ->
            val r = Rect(Offset(i * ww, 0f), Size(ww, size.height))
            drawRect(colorOf(n) ?: Palette.whiteKey, r.topLeft, r.size)
            drawRect(Palette.keyBorder, r.topLeft, r.size, style = Stroke(0.5f))
        }
        for (n in start..end) {
            if (!Notes.isBlack(n)) continue
            val wi = whites.indexOf(n - 1)
            if (wi < 0) continue
            val x = (wi + 1) * ww - bw / 2
            drawRect(colorOf(n) ?: Palette.blackKey, Offset(x, 0f), Size(bw, bh))
        }
    }
}
