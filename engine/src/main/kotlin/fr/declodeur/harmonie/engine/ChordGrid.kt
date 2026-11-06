package fr.declodeur.harmonie.engine

import kotlin.math.roundToInt

/** Une case de la grille : un accord, ou « % » (la case précédente continue). */
data class GridCell(
    val symbol: String,
    val numeral: String?,
    val function: HarmonicFunction?,
    /** « % » : même accord que la case précédente (accord plus long). */
    val continuation: Boolean = false,
)

/** Une ligne de la grille ; une reprise peut couvrir plusieurs lignes (‖: … :‖ ×N). */
data class GridLine(
    val cells: List<GridCell>,
    /** La ligne ouvre une reprise (‖:). */
    val repeatOpen: Boolean = false,
    /** La ligne ferme une reprise jouée N fois (:‖ ×N) ; 0 = pas de reprise. */
    val repeatTimes: Int = 0,
)

/** Les accords joués dans une même tonalité. */
data class GridSection(
    val key: Key?,
    /** Accord pivot de la modulation qui ouvre la section (« III = I »), s'il y en a un. */
    val pivot: String?,
    val lines: List<GridLine>,
)

/**
 * Grille du morceau : les accords de l'historique, par sections de tonalité, quatre cases par ligne,
 * les boucles répétées regroupées en reprises. Option : une case par durée (l'unité est la durée
 * médiane d'un accord ; un accord deux fois plus long occupe deux cases, « % » dans la seconde),
 * faute de connaître la mesure.
 */
object ChordGrid {
    const val PER_LINE = 4
    private const val MAX_BLOCK = 8

    fun build(
        history: List<HistoryEntry>,
        nowMs: Long,
        byDuration: Boolean = false,
        groupRepeats: Boolean = true,
        perLine: Int = PER_LINE,
    ): List<GridSection> {
        if (history.isEmpty()) return emptyList()
        val durations = HarmonicRhythm.durations(history, nowMs)
        val unit = durations.dropLast(1).sorted().let { d -> if (d.isEmpty()) 0L else d[d.size / 2] }
        fun cellsOf(i: Int): List<GridCell> {
            val e = history[i]
            val best = e.chord.best
            val cell = GridCell(best?.symbolIn(e.spelling) ?: "?", e.analysis?.numeral, e.analysis?.function)
            val n = if (byDuration && unit > 0 && i < history.lastIndex) (durations[i].toDouble() / unit).roundToInt().coerceIn(1, 4) else 1
            return listOf(cell) + List(n - 1) { GridCell("%", null, null, continuation = true) }
        }
        // Sections : accords consécutifs de même tonalité
        val sections = mutableListOf<IntRange>()
        var start = 0
        for (i in 1..history.size) {
            if (i == history.size || history[i].analysis?.key != history[start].analysis?.key) {
                sections += start until i
                start = i
            }
        }
        return sections.map { range ->
            val idx = range.toList()
            val lines = mutableListOf<GridLine>()
            val pending = mutableListOf<GridCell>()
            fun flush() {
                pending.chunked(perLine).forEach { lines += GridLine(it) }
                pending.clear()
            }
            var p = 0
            while (p < idx.size) {
                val block = if (groupRepeats) repeatAt(history, idx, p) else null
                if (block != null) {
                    flush()
                    val (len, times) = block
                    val cells = (p until p + len).flatMap { cellsOf(idx[it]) }
                    val chunks = cells.chunked(perLine)
                    chunks.forEachIndexed { c, chunk ->
                        lines += GridLine(chunk, repeatOpen = c == 0, repeatTimes = if (c == chunks.lastIndex) times else 0)
                    }
                    p += len * times
                } else {
                    pending += cellsOf(idx[p])
                    p++
                }
            }
            flush()
            val first = history[range.first]
            val pivot = first.pivotFrom?.let { "${it.numeral} = ${first.analysis?.numeral}" }
            GridSection(first.analysis?.key, pivot, lines)
        }
    }

    /** Bloc de 2 à 8 accords répété au moins deux fois de suite à partir de [p] : (longueur, nombre de fois). */
    private fun repeatAt(history: List<HistoryEntry>, idx: List<Int>, p: Int): Pair<Int, Int>? {
        fun sym(k: Int) = history[idx[k]].chord.best?.symbol
        for (len in minOf(MAX_BLOCK, (idx.size - p) / 2) downTo 2) {
            var times = 1
            while (p + (times + 1) * len <= idx.size && (0 until len).all { sym(p + it) == sym(p + times * len + it) }) times++
            if (times >= 2) return len to times
        }
        return null
    }

    /** Grille en texte (à copier), avec une ligne de chiffrage sous chaque ligne d'accords. */
    fun toText(sections: List<GridSection>, numerals: Boolean = true): String = buildString {
        val width = sections.flatMap { s -> s.lines.flatMap { it.cells } }
            .maxOfOrNull { maxOf(it.symbol.length, it.numeral?.length ?: 0) }?.plus(2) ?: 6
        for (s in sections) {
            append(s.key?.name ?: "Tonalité indéterminée")
            s.pivot?.let { append(" (pivot : $it)") }
            append('\n')
            for (l in s.lines) {
                val open = if (l.repeatOpen) "‖:" else "| "
                val close = if (l.repeatTimes > 0) ":‖ ×${l.repeatTimes}" else "|"
                append(open).append(l.cells.joinToString("|") { " " + it.symbol.padEnd(width) }).append(close).append('\n')
                if (numerals && l.cells.any { it.numeral != null }) {
                    append("  ").append(l.cells.joinToString(" ") { " " + (it.numeral ?: "").padEnd(width) }.trimEnd()).append('\n')
                }
            }
            append('\n')
        }
    }.trimEnd() + "\n"
}
