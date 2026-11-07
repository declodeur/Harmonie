package fr.declodeur.harmonie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import fr.declodeur.harmonie.engine.ChordCandidate
import fr.declodeur.harmonie.engine.ChordScaleResult
import fr.declodeur.harmonie.engine.Notes
import fr.declodeur.harmonie.engine.SoundingNote
import fr.declodeur.harmonie.engine.ToneRole

/** Géométrie d'un clavier de 88 touches (A0 = 21 à C8 = 108). */
object KeyboardLayout {
    const val LOWEST = 21
    const val HIGHEST = 108
    private val whiteIndex = IntArray(128) { -1 }
    val whiteCount: Int

    init {
        var w = 0
        for (n in LOWEST..HIGHEST) if (!Notes.isBlack(n)) whiteIndex[n] = w++
        whiteCount = w
    }

    fun keyRect(note: Int, width: Float, height: Float): Rect {
        val ww = width / whiteCount
        return if (!Notes.isBlack(note)) {
            val i = whiteIndex[note]
            Rect(i * ww, 0f, (i + 1) * ww, height)
        } else {
            // Touche noire : environ 58 % de la largeur d'une blanche sur un vrai piano (13,7 mm pour 23,5 mm)
            val bw = ww * 0.58f
            val x = (whiteIndex[note - 1] + 1) * ww - bw / 2
            Rect(x, 0f, x + bw, height * 0.62f)
        }
    }

    fun keyAt(pos: Offset, width: Float, height: Float): Int? {
        for (n in LOWEST..HIGHEST) if (Notes.isBlack(n) && keyRect(n, width, height).contains(pos)) return n
        for (n in LOWEST..HIGHEST) if (!Notes.isBlack(n) && keyRect(n, width, height).contains(pos)) return n
        return null
    }
}

fun roleColor(role: ToneRole): Color = when (role) {
    ToneRole.ROOT -> Palette.root
    ToneRole.CHORD_TONE -> Palette.chordTone
    ToneRole.TENSION -> Palette.tension
    ToneRole.EXTRA -> Palette.extra
    ToneRole.BASS -> Palette.bass
    ToneRole.NONE -> Palette.pending
}

/** Couleur d'une touche enfoncée, ou null si elle ne sonne pas. */
private fun keyColor(notes: List<SoundingNote>?, chord: ChordCandidate?): Color? {
    if (notes.isNullOrEmpty()) return null
    val n = notes.firstOrNull { !it.melody } ?: return Palette.melody
    val base = when {
        !n.analysed -> Palette.pending
        chord == null -> Palette.pending
        else -> roleColor(chord.roleOf(n.note))
    }
    return if (n.sustained) base.copy(alpha = 0.5f) else base
}

/**
 * Clavier 88 touches. Les notes qui sonnent sont colorées selon leur rôle dans l'accord.
 * Si [scale] est donnée, un point repère chaque note de la gamme de l'accord (plus gros sur
 * sa tonique) et un cercle rouge chaque note à éviter.
 * Un clic enfonce ou relâche une touche (pour tester sans instrument).
 */
@Composable
fun PianoKeyboard(
    notes: List<SoundingNote>,
    chord: ChordCandidate?,
    onKeyClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scale: ChordScaleResult? = null,
) {
    val scalePcs = scale?.primary?.pitchClasses?.toSet() ?: emptySet()
    val avoid = scale?.avoid ?: emptySet()
    val tonic = scale?.primary?.root
    val measurer = rememberTextMeasurer()
    val click by rememberUpdatedState(onKeyClick)
    val byNote = notes.groupBy { it.note }

    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos ->
                KeyboardLayout.keyAt(pos, size.width.toFloat(), size.height.toFloat())?.let { click(it) }
            }
        },
    ) {
        val w = size.width
        val h = size.height
        val labelStyle = TextStyle(fontSize = 10.sp, color = Color(0xFF6B717C))

        for (n in KeyboardLayout.LOWEST..KeyboardLayout.HIGHEST) {
            if (Notes.isBlack(n)) continue
            val r = KeyboardLayout.keyRect(n, w, h)
            val c = keyColor(byNote[n], chord)
            drawKey(r, Palette.whiteKey)
            if (c != null) drawKey(r, c)
            drawRect(Palette.keyBorder, r.topLeft, r.size, style = Stroke(1f))
            drawScaleMark(n, r, r.bottom - 24.dp.toPx(), scalePcs, avoid, tonic, pressed = c != null)
            if (Notes.pc(n) == 0) {
                // Étiquette mesurée puis centrée en bas de la touche (quelle que soit l'échelle Windows)
                val layout = measurer.measure(Notes.nameWithOctave(n), labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        r.left + (r.width - layout.size.width) / 2f,
                        r.bottom - layout.size.height - 4.dp.toPx(),
                    ),
                )
            }
        }
        for (n in KeyboardLayout.LOWEST..KeyboardLayout.HIGHEST) {
            if (!Notes.isBlack(n)) continue
            val r = KeyboardLayout.keyRect(n, w, h)
            val c = keyColor(byNote[n], chord)
            drawKey(r, Palette.blackKey)
            if (c != null) drawKey(r, c)
            drawRect(Palette.keyBorder, r.topLeft, r.size, style = Stroke(1f))
            drawScaleMark(n, r, r.bottom - 9.dp.toPx(), scalePcs, avoid, tonic, pressed = c != null)
        }
    }
}

/** Repère de gamme : point (note de la gamme), point plus gros (tonique de la gamme), cercle rouge (à éviter). */
private fun DrawScope.drawScaleMark(note: Int, r: Rect, cy: Float, scale: Set<Int>, avoid: Set<Int>, tonic: Int?, pressed: Boolean) {
    val pc = Notes.pc(note)
    if (pc !in scale) return
    val center = Offset(r.center.x, cy)
    // Taille limitée par la largeur de la touche (clavier étroit)
    val maxR = r.width * 0.3f
    if (pc in avoid) {
        drawCircle(Palette.danger, radius = minOf(4.dp.toPx(), maxR), center = center, style = Stroke(1.8.dp.toPx()))
    } else {
        val radius = if (pc == tonic) 4.5.dp.toPx() else 3.dp.toPx()
        val color = if (Notes.isBlack(note) && !pressed) Palette.scaleMarkOnBlack else Palette.scaleMarkOnWhite
        drawCircle(color, radius = minOf(radius, maxR), center = center)
    }
}

/** Légende des repères de gamme (mêmes dessins que sur le clavier). */
@Composable
fun ScaleLegend(text: String, avoid: Boolean = false, tonic: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            if (avoid) drawCircle(Palette.danger, radius = 4.dp.toPx(), center = c, style = Stroke(1.8.dp.toPx()))
            else drawCircle(Palette.scaleMarkOnBlack, radius = (if (tonic) 4.5.dp else 3.dp).toPx(), center = c)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Palette.muted, maxLines = 1, softWrap = false)
    }
}

private fun DrawScope.drawKey(r: Rect, color: Color) {
    drawRect(color, r.topLeft, Size(r.width, r.height))
}
