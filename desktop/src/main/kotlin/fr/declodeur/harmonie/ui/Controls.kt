package fr.declodeur.harmonie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Cursor
import kotlin.math.roundToInt

/** Curseur compact (20 dp de haut) : clic ou glisser pour changer la valeur, par pas de [step]. */
@Composable
fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val change by rememberUpdatedState(onValueChange)
    val start = valueRange.start
    val end = valueRange.endInclusive

    fun valueAt(x: Float, width: Float, inset: Float): Float {
        val f = ((x - inset) / (width - 2 * inset)).coerceIn(0f, 1f)
        val raw = start + f * (end - start)
        return (start + ((raw - start) / step).roundToInt() * step).coerceIn(start, end)
    }

    Canvas(
        modifier.fillMaxWidth().height(20.dp)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { change(valueAt(it.x, size.width.toFloat(), 7.dp.toPx())) }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures { c, _ ->
                        c.consume()
                        change(valueAt(c.position.x, size.width.toFloat(), 7.dp.toPx()))
                    }
                }
            },
    ) {
        val r = 7.dp.toPx()
        val y = size.height / 2
        val left = r
        val right = size.width - r
        val f = ((value - start) / (end - start)).coerceIn(0f, 1f)
        val x = left + f * (right - left)
        val active = if (enabled) Palette.accent else Palette.muted
        drawLine(Palette.border, Offset(left, y), Offset(right, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(active, Offset(left, y), Offset(x, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(active, r, Offset(x, y))
    }
}

/** Interrupteur compact (34 × 18 dp). */
@Composable
fun CompactSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Canvas(
        Modifier.size(34.dp, 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable { onCheckedChange(!checked) }
            .pointerHoverIcon(PointerIcon.Hand),
    ) {
        drawRoundRect(if (checked) Palette.accent else Palette.border, cornerRadius = CornerRadius(size.height / 2))
        val r = size.height / 2 - 2.dp.toPx()
        val cx = if (checked) size.width - size.height / 2 else size.height / 2
        drawCircle(Color.White, r, Offset(cx, size.height / 2))
    }
}

/** Titre de section dans les réglages. */
@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.accent)
}

/** Réglage à curseur : libellé (avec aide) puis curseur compact. */
@Composable
fun SliderSetting(
    label: String,
    help: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column {
        Label(label, help)
        CompactSlider(value, onChange, valueRange, step, enabled = enabled)
    }
}

/** Réglage à interrupteur : libellé (avec aide) à gauche, interrupteur à droite. */
@Composable
fun SwitchSetting(label: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Label(label, help) }
        Spacer(Modifier.width(10.dp))
        CompactSwitch(checked, onChange)
    }
}

/**
 * Frontière verticale déplaçable entre deux colonnes : invisible, curseur ↔ au survol.
 * [onDrag] reçoit le déplacement horizontal en dp.
 */
@Composable
fun VerticalSplitter(onDrag: (Dp) -> Unit, onDragEnd: () -> Unit, modifier: Modifier = Modifier) {
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    Box(
        modifier.width(12.dp).fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { end() }, onDragCancel = { end() }) { c, dx ->
                    c.consume()
                    drag(dx.toDp())
                }
            },
    )
}

/**
 * Frontière horizontale déplaçable entre deux rangées : invisible, curseur ↕ au survol.
 * [onDrag] reçoit le déplacement vertical en dp.
 */
@Composable
fun HorizontalSplitter(onDrag: (Dp) -> Unit, onDragEnd: () -> Unit, modifier: Modifier = Modifier) {
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    Box(
        modifier.height(12.dp).fillMaxWidth()
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragEnd = { end() }, onDragCancel = { end() }) { c, dy ->
                    c.consume()
                    drag(dy.toDp())
                }
            },
    )
}

/** Espace vide de [height] (utilitaire). */
@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height).padding(0.dp))
