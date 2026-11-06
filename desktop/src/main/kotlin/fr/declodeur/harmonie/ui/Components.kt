package fr.declodeur.harmonie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Petite bulle « ? » qui affiche une aide au survol. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HelpBubble(text: String) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Palette.surfaceHigh,
                border = BorderStroke(1.dp, Palette.border),
                shadowElevation = 6.dp,
            ) {
                Text(
                    text,
                    modifier = Modifier.padding(12.dp).widthIn(max = 380.dp),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Palette.text,
                )
            }
        },
        delayMillis = 250,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(8.dp, 16.dp)),
    ) {
        HelpIcon(Modifier.size(18.dp))
    }
}

/**
 * Icône « ? » dessinée (cercle + point d'interrogation tracé), pour être parfaitement
 * centrée quelle que soit la police du système.
 */
@Composable
private fun HelpIcon(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2, size.height / 2)
        val stroke = s * 0.075f
        drawCircle(Palette.helpIcon, radius = s / 2 - stroke / 2, center = c, style = Stroke(stroke))
        val r = s * 0.15f
        val arcCenter = Offset(c.x, c.y - s * 0.11f)
        val hook = Path().apply {
            addArc(Rect(arcCenter, r), 180f, 270f)
            lineTo(c.x, arcCenter.y + r + s * 0.09f)
        }
        drawPath(hook, Palette.helpIcon, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(Palette.helpIcon, radius = stroke * 0.7f, center = Offset(c.x, c.y + s * 0.27f))
    }
}

/** Libellé de section, éventuellement avec sa bulle d'aide. */
@Composable
fun Label(text: String, help: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 12.sp, color = Palette.muted)
        if (help != null) {
            Spacer(Modifier.width(6.dp))
            HelpBubble(help)
        }
    }
}

/** Carte de l'écran principal. */
@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    help: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Palette.surface,
        border = BorderStroke(1.dp, Palette.border),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Titre de cadre : plus grand et en demi-gras, en couleur de texte (les libellés de
                // réglages restent petits et discrets)
                Text(
                    title,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    color = Palette.text,
                )
                if (help != null) {
                    Spacer(Modifier.width(8.dp))
                    HelpBubble(help)
                }
                Spacer(Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Liste déroulante simple : libellé au-dessus, bouton avec la valeur choisie. */
@Composable
fun <T> Selector(
    label: String,
    options: List<T>,
    selected: T?,
    display: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null,
    enabled: Boolean = true,
    placeholder: String = "—",
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label, help)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty()) {
                Text(selected?.let(display) ?: placeholder, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text("▾", fontSize = 12.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(display(option)) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** Petit carré de couleur suivi d'un texte, pour les légendes. */
@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).border(1.dp, Palette.keyBorder, RoundedCornerShape(2.dp)).padding(1.dp)) {
            Surface(color = color, shape = RoundedCornerShape(2.dp), modifier = Modifier.size(10.dp)) {}
        }
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Palette.muted)
    }
}

/** Carré vide au contour coloré suivi d'un texte, pour les légendes de contours. */
@Composable
fun LegendOutline(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).border(2.dp, color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Palette.muted)
    }
}
