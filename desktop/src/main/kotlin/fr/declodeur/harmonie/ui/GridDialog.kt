package fr.declodeur.harmonie.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.declodeur.harmonie.engine.ChordGrid
import fr.declodeur.harmonie.engine.GridCell
import fr.declodeur.harmonie.engine.GridLine
import fr.declodeur.harmonie.engine.GridSection
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob

private val CELL_W = 120.dp
private val MARK_W = 34.dp

/**
 * Grille du morceau joué (étape 5c) : quatre cases par ligne, sections par tonalité, reprises
 * regroupées ; copie en texte et impression (sous Windows, « Microsoft Print to PDF » donne un PDF).
 */
@Composable
fun GridDialog(build: (byDuration: Boolean, groupRepeats: Boolean) -> List<GridSection>, onClose: () -> Unit) {
    var numerals by remember { mutableStateOf(true) }
    var repeats by remember { mutableStateOf(true) }
    var byDuration by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val sections = build(byDuration, repeats)
    val text = ChordGrid.toText(sections, numerals)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Palette.surface,
            border = BorderStroke(1.dp, Palette.border),
            modifier = Modifier.widthIn(max = MARK_W * 2 + CELL_W * ChordGrid.PER_LINE + 120.dp).fillMaxWidth(0.96f),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grille du morceau", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    HelpBubble(Help.GRID)
                    Spacer(Modifier.weight(1f))
                    val count = sections.sumOf { s -> s.lines.sumOf { l -> l.cells.count { !it.continuation } * maxOf(1, l.repeatTimes) } }
                    Text("$count accords", fontSize = 13.sp, color = Palette.muted)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f)) { SwitchSetting("Chiffrage", Help.GRID_NUMERALS, numerals) { numerals = it } }
                    Box(Modifier.weight(1f)) { SwitchSetting("Regrouper les reprises", Help.GRID_REPEATS, repeats) { repeats = it } }
                    Box(Modifier.weight(1.2f)) { SwitchSetting("Une case par durée (≈ mesure)", Help.GRID_DURATION, byDuration) { byDuration = it } }
                }
                Spacer(Modifier.height(16.dp))
                // La grille défile (verticalement et, si la fenêtre est étroite, horizontalement) ; les boutons restent visibles
                Column(
                    Modifier.weight(1f, fill = false).heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
                ) {
                    if (sections.isEmpty()) {
                        Text("Jouez quelques accords : la grille du morceau s'affichera ici.", fontSize = 14.sp, color = Palette.muted)
                    }
                    for (s in sections) {
                        Text(
                            (s.key?.name ?: "Tonalité indéterminée") + (s.pivot?.let { " · pivot : $it" } ?: ""),
                            fontSize = 13.sp,
                            color = Palette.muted,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                        )
                        for (l in s.lines) GridRow(l, numerals)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                        copied = true
                    }) { Text(if (copied) "Texte copié" else "Copier le texte") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { printText(text) }) { Text("Imprimer / PDF", maxLines = 1, softWrap = false) }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onClose) { Text("Fermer", maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}

@Composable
private fun GridRow(line: GridLine, numerals: Boolean) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(MARK_W), contentAlignment = Alignment.CenterEnd) {
            if (line.repeatOpen) Text("‖:", fontSize = 20.sp, color = Palette.muted)
        }
        Spacer(Modifier.width(4.dp))
        Row(Modifier.border(1.dp, Palette.border, RoundedCornerShape(2.dp))) {
            for (i in 0 until ChordGrid.PER_LINE) {
                val cell = line.cells.getOrNull(i)
                GridCellView(cell, numerals, last = i == ChordGrid.PER_LINE - 1)
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.width(MARK_W + 30.dp)) {
            if (line.repeatTimes > 0) Text(":‖ ×${line.repeatTimes}", fontSize = 16.sp, color = Palette.muted)
        }
    }
}

@Composable
private fun GridCellView(cell: GridCell?, numerals: Boolean, last: Boolean) {
    Row {
        Column(Modifier.width(CELL_W).height(if (numerals) 52.dp else 38.dp).padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (cell != null) {
                Text(
                    cell.symbol,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    color = if (cell.continuation) Palette.muted else Palette.text,
                    maxLines = 1,
                )
                val numeral = cell.numeral
                if (numerals && numeral != null) {
                    Text(
                        numeral,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = cell.function?.let { functionColor(it) } ?: Palette.muted,
                        maxLines = 1,
                    )
                }
            }
        }
        if (!last) Box(Modifier.width(1.dp).height(if (numerals) 52.dp else 38.dp).border(1.dp, Palette.border))
    }
}

/** Impression du texte de la grille (police à chasse fixe, plusieurs pages si besoin). */
private fun printText(text: String) {
    val lines = text.lines()
    val job = PrinterJob.getPrinterJob()
    job.jobName = "Harmonie — grille"
    job.setPrintable(object : Printable {
        override fun print(g: java.awt.Graphics, pf: PageFormat, page: Int): Int {
            val font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            g.font = font
            val lh = g.fontMetrics.height
            val perPage = maxOf(1, (pf.imageableHeight / lh).toInt() - 1)
            if (page * perPage >= lines.size) return Printable.NO_SUCH_PAGE
            val x = pf.imageableX.toInt()
            var y = pf.imageableY.toInt() + lh
            for (l in lines.drop(page * perPage).take(perPage)) {
                g.drawString(l, x, y)
                y += lh
            }
            return Printable.PAGE_EXISTS
        }
    })
    if (job.printDialog()) runCatching { job.print() }
}
