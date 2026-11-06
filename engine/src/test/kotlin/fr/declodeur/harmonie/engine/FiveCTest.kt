package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FiveCTest {
    private fun ch(vararg n: Int) = ChordRecognizer.recognize(n.toList()).best!!
    private val c = Key(0, false)
    private fun t(vararg n: Int): Double { val x = ch(*n); return Tension.of(x, HarmonicAnalyzer.analyze(x, c)) }

    @Test
    fun tensionFollowsFunctionDissonanceAndDistance() {
        val tonic = t(48, 52, 55)
        val sub = t(53, 57, 60)
        val dom = t(55, 59, 62)
        val dom7 = t(55, 59, 62, 65)
        val dom7b9 = t(55, 59, 62, 65, 68)
        assertTrue(tonic < sub && sub < dom && dom < dom7 && dom7 < dom7b9, "$tonic $sub $dom $dom7 $dom7b9")
        assertEquals(0.0, tonic)
        assertTrue(t(52, 56, 59, 62) > dom7, "E7 (V7/VI) plus tendu que G7") // éloignement
        assertTrue(dom7b9 <= 1.0)
    }

    /** Joue des accords (notes, durée en ms) et renvoie le moteur. */
    private fun play(chords: List<Pair<List<Int>, Long>>): HarmonieEngine {
        val e = HarmonieEngine(AnalysisSettings(style = MusicStyle.POP_ROCK), historyLimit = 500)
        var t = 0L
        for ((notes, ms) in chords) {
            notes.forEach { e.noteOn(0, it, 90, t) }
            val end = t + ms
            while (t < end) { t += 10; e.tick(t) }
            notes.forEach { e.noteOff(0, it, t) }
            t += 10; e.tick(t)
        }
        return e
    }

    private val cm = listOf(48, 60, 63, 67); private val ab = listOf(44, 60, 63, 68)
    private val eb = listOf(51, 58, 63, 67); private val bb = listOf(46, 58, 62, 65)
    private val fm = listOf(41, 60, 65, 68); private val g7 = listOf(43, 59, 62, 65)

    @Test
    fun rhythmAcceleratesBeforeTheCadence() {
        val loop = listOf(cm to 2000L, ab to 2000L, eb to 2000L, bb to 2000L)
        val e = play(loop + loop + listOf(fm to 800L, g7 to 800L, cm to 2000L, ab to 1000L))
        val r = assertNotNull(e.snapshot().rhythm)
        assertTrue(r.meanSeconds in 1.2..2.2, "moyenne ${r.meanSeconds}")
        assertTrue(r.acceleratesBeforeCadence)
    }

    @Test
    fun gridGroupsLoopsIntoRepeats() {
        val loop = listOf(cm to 1500L, ab to 1500L, eb to 1500L, bb to 1500L)
        val e = play(loop + loop + loop + listOf(fm to 1500L, g7 to 1500L, cm to 1500L))
        val grid = e.grid(byDuration = false, groupRepeats = true)
        assertEquals(1, grid.size)
        val lines = grid[0].lines
        assertEquals(listOf("Cm", "A♭", "E♭", "B♭"), lines[0].cells.map { it.symbol })
        assertTrue(lines[0].repeatOpen)
        assertEquals(3, lines[0].repeatTimes)
        assertEquals(listOf("Fm", "G7", "Cm"), lines[1].cells.map { it.symbol })
        val text = ChordGrid.toText(grid)
        assertTrue(text.startsWith("C mineur"), text)
        assertTrue("‖:" in text && ":‖ ×3" in text, text)
        // Sans regroupement : 4 lignes de 4 et une de 3
        assertEquals(4, e.grid(byDuration = false, groupRepeats = false)[0].lines.size)
    }

    @Test
    fun gridCellsFollowDurations() {
        val e = play(listOf(cm to 1000L, ab to 1000L, eb to 2000L, bb to 1000L, cm to 1000L))
        val cells = e.grid(byDuration = true, groupRepeats = false)[0].lines.flatMap { it.cells }.map { it.symbol }
        assertEquals(listOf("Cm", "A♭", "E♭", "%", "B♭", "Cm"), cells)
    }
}
