package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FiveCTest {
    private fun ch(vararg n: Int) = ChordRecognizer.recognize(n.toList()).best!!
    private val c = Key(0, false)
    private fun t(vararg n: Int): Double = Tension.tonalSpace(ch(*n), c, null, n.toList())
    private fun simple(vararg n: Int): Double { val x = ch(*n); return Tension.simple(x, HarmonicAnalyzer.analyze(x, c)) }

    @Test
    fun tonalSpaceDistancesMatchLerdahl() {
        val tonic = t(48, 52, 55)
        val v = t(43, 47, 50)
        val iv = t(41, 45, 48)
        val vi = t(45, 48, 52)
        val ii = t(50, 53, 57)
        assertEquals(0.0, tonic)
        assertEquals(v, iv) // δ(I → V) = δ(I → IV) = 5
        assertTrue(v < vi && vi < ii, "$v $vi $ii") // 5 < 7 < 8
    }

    @Test
    fun diminishedAndAugmentedChordsRaiseTheTension() {
        val v7 = t(43, 47, 50, 53)
        val dim = t(48, 51, 54)
        val aug = t(48, 52, 56)
        val m7aug = t(48, 52, 56, 59)
        for ((name, x) in listOf("Cdim" to dim, "Caug" to aug, "CM7aug" to m7aug)) {
            assertTrue(x >= 0.55 && x > v7 + 0.1, "$name : $x (G7 : $v7)")
        }
        assertTrue(t(43, 47, 50) < v7, "la septième ajoute de la tension")
        assertTrue(t(55, 60, 64) > t(43, 47, 50), "quarte et sixte de cadence (C/G) plus tendue que V")
    }

    @Test
    fun simpleModelIsKept() {
        assertTrue(simple(48, 52, 55) < simple(55, 59, 62, 65))
        assertEquals(0.0, simple(48, 52, 55))
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
