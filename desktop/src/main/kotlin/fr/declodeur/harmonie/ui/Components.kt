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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.layout.BoxWithConstraints
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
        // Peut passer à la ligne (libellés des réglages), mais jamais au milieu d'un mot coupé à une lettre
        Text(text, fontSize = 12.sp, color = Palette.muted, modifier = Modifier.weight(1f, fill = false))
        if (help != null) {
            Spacer(Modifier.width(6.dp))
            HelpBubble(help)
        }
    }
}

/** Carte de l'écran principal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    help: String? = null,
    action: (@Composable () -> Unit)? = null,
    /** Interrupteurs d'affichage, juste après le titre (« Tension », « Durées »…). */
    chips: (@Composable () -> Unit)? = null,
    /** Filigrane du cadre vide (« Présent », « Futur »…), null quand le cadre affiche quelque chose. */
    watermark: String? = null,
    /** Une couleur par lettre du filigrane (« Couleur » en arc-en-ciel) ; sinon une seule teinte. */
    watermarkColors: List<Color>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Palette.surface,
        border = BorderStroke(1.dp, Palette.border),
    ) {
      Box {
        // Derrière le contenu, sous la barre de titre ; n'occupe aucune place dans la mise en page
        if (watermark != null) {
            Watermark(watermark, watermarkColors, Modifier.matchParentSize().padding(top = 44.dp, bottom = 8.dp, start = 16.dp, end = 16.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val titleBlock: @Composable () -> Unit = {
                // Titre de cadre : plus grand et en demi-gras, en couleur de texte (les libellés de
                // réglages restent petits et discrets)
                Text(
                    title,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    color = Palette.text,
                    maxLines = 1,
                    softWrap = false,
                )
                if (help != null) HelpBubble(help)
                if (chips != null) {
                    Spacer(Modifier.width(2.dp))
                    chips()
                }
            }
            if (action == null) {
                // Sans action à droite : les interrupteurs passent sous le titre si le cadre est étroit
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) { titleBlock() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    titleBlock()
                    // La place restante revient à l'action (légendes, boutons) : elle s'enroule au lieu de déborder
                    Box(Modifier.weight(1f).padding(start = 6.dp), contentAlignment = Alignment.CenterEnd) { action() }
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
      }
    }
}

/** Teinte du filigrane : à peine plus claire que le fond du cadre. */
private fun watermarkTint(c: Color, amount: Float) = lerp(Palette.surface, c, amount)

/** Arc-en-ciel discret, une couleur par lettre de « Couleur » (rouge, orange, jaune, vert, bleu, indigo, violet). */
val RAINBOW_WATERMARK: List<Color> = listOf(
    Color(0xFFFF4D4D), Color(0xFFFF9F43), Color(0xFFFFE066), Color(0xFF5BD67A),
    Color(0xFF4DA3FF), Color(0xFF6C6CFF), Color(0xFFB36BFF),
).map { watermarkTint(it, 0.16f) }

/** Mot en filigrane, aussi grand que le cadre le permet, centré. */
@Composable
private fun Watermark(text: String, colors: List<Color>?, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Largeur moyenne d'une lettre ≈ 0,6 × la taille de la police
        val byWidth = maxWidth.value / (text.length * 0.6f)
        val byHeight = maxHeight.value * 0.6f
        val size = minOf(byWidth, byHeight, 240f).coerceAtLeast(12f)
        val plain = watermarkTint(Palette.text, 0.07f)
        Text(
            buildAnnotatedString {
                text.forEachIndexed { i, ch ->
                    withStyle(SpanStyle(color = colors?.getOrNull(i % colors.size) ?: plain)) { append(ch) }
                }
            },
            fontSize = with(LocalDensity.current) { size.dp.toSp() },
            lineHeight = with(LocalDensity.current) { (size * 1.1f).dp.toSp() },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
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
    /** Couleur du texte choisi (vert vif : entrée MIDI connectée), et du texte quand rien n'est choisi. */
    selectedColor: Color? = null,
    placeholderColor: Color? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label, help)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled && options.isNotEmpty(),
            ) {
                val text = selected?.let(display)
                if (text != null) {
                    Text(
                        text, fontSize = 14.sp, color = selectedColor ?: Color.Unspecified,
                        fontWeight = if (selectedColor != null) FontWeight.SemiBold else null,
                        maxLines = 1, softWrap = false,
                    )
                } else {
                    Text(
                        placeholder, fontSize = 14.sp, color = placeholderColor ?: Color.Unspecified,
                        fontWeight = if (placeholderColor != null) FontWeight.SemiBold else null,
                        maxLines = 1, softWrap = false,
                    )
                }
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

/** Interrupteur d'affichage compact, dans la barre de titre d'un cadre : bleu = affiché, gris = masqué. */
@Composable
fun ToggleChip(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (on) Palette.accent.copy(alpha = 0.18f) else Color.Transparent,
        border = BorderStroke(1.dp, if (on) Palette.accent else Palette.border),
        modifier = Modifier.clickable { onToggle(!on) },
    ) {
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = if (on) Palette.accent else Palette.muted,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}

/** Légendes d'un cadre : sur une ligne si la place le permet, sinon sur deux ou plus, alignées à droite. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegendFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Petit carré de couleur suivi d'un texte, pour les légendes. */
@Composable
fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).border(1.dp, Palette.keyBorder, RoundedCornerShape(2.dp)).padding(1.dp)) {
            Surface(color = color, shape = RoundedCornerShape(2.dp), modifier = Modifier.size(10.dp)) {}
        }
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Palette.muted, maxLines = 1, softWrap = false)
    }
}

/** Carré vide au contour coloré suivi d'un texte, pour les légendes de contours. */
@Composable
fun LegendOutline(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).border(2.dp, color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = Palette.muted, maxLines = 1, softWrap = false)
    }
}
