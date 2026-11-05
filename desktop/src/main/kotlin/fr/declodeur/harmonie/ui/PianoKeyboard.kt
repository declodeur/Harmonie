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
import fr.declodeur.harmonie.engine.ChordCandidate
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
            val bw = ww * 0.62f
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
 * Un clic enfonce ou relâche une touche (pour tester sans instrument).
 */
@Composable
fun PianoKeyboard(
    notes: List<SoundingNote>,
    chord: ChordCandidate?,
    onKeyClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        }
    }
}

private fun DrawScope.drawKey(r: Rect, color: Color) {
    drawRect(color, r.topLeft, Size(r.width, r.height))
}
