package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyModelTest {
    /** Joue une suite d'accords en boucle (chaque accord tenu [holdMs]) et renvoie la tonalité retenue. */
    private fun keyAfter(settings: AnalysisSettings, chords: List<List<Int>>, loops: Int, holdMs: Long = 2000): Key? {
        val e = HarmonieEngine(settings)
        var t = 0L
        repeat(loops) {
            for (c in chords) {
                c.forEach { e.noteOn(0, it, 90, t) }
                val end = t + holdMs
                while (t < end) { t += 10; e.tick(t) }
                c.forEach { e.noteOff(0, it, t) }
                t += 10; e.tick(t)
            }
        }
        return e.snapshot().key?.current
    }

    private val f = listOf(41, 57, 60, 65) // F
    private val eb = listOf(39, 55, 58, 63) // E♭
    private val bb = listOf(46, 58, 62, 65) // B♭

    @Test
    fun rockVampIsInTheKeyOfItsTonic() {
        // I – ♭VII – IV – I : toutes les notes tiennent dans B♭ majeur, mais la tonique est F
        val loop = listOf(f, eb, bb, f)
        val pop = AnalysisSettings(style = MusicStyle.POP_ROCK)
        assertEquals(Key(5, false), keyAfter(pop, loop, loops = 4))
        // Ancien modèle (notes seules) : B♭
        val legacy = pop.withAllKeyParams { KeyParams.LEGACY }
        assertEquals(Key(10, false), keyAfter(legacy, loop, loops = 4))
    }

    @Test
    fun keyParamsDependOnStyle() {
        assertEquals(KeyParams.CLASSICAL, AnalysisSettings(style = MusicStyle.CLASSICAL).currentKeyParams)
        assertEquals(KeyParams.POP, AnalysisSettings(style = MusicStyle.POP_ROCK).currentKeyParams)
        val s = AnalysisSettings(style = MusicStyle.POP_ROCK).withKeyParams { it.copy(tonicPercent = 0) }
        assertEquals(0, s.currentKeyParams.tonicPercent)
        assertEquals(KeyParams.CLASSICAL, s.copy(style = MusicStyle.CLASSICAL).currentKeyParams)
    }
}
