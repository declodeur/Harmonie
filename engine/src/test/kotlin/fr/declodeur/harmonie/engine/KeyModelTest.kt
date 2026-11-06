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

class KeyStabilityTest {
    /** Cm – A♭ – E♭ – B♭, trois fois (i – VI – III – VII en C mineur, la boucle de « Zombie »). */
    @Test
    fun aeolianLoopStaysInCMinor() {
        val loop = listOf(listOf(48, 60, 63, 67), listOf(44, 60, 63, 68), listOf(51, 58, 63, 67), listOf(46, 58, 62, 65))
        val e = HarmonieEngine(AnalysisSettings(style = MusicStyle.POP_ROCK))
        var t = 0L
        val keys = mutableListOf<Key?>()
        repeat(3) {
            for (c in loop) {
                c.forEach { e.noteOn(0, it, 90, t) }
                val end = t + 1500
                while (t < end) { t += 10; e.tick(t) }
                keys += e.snapshot().key?.current
                c.forEach { e.noteOff(0, it, t) }
                t += 30; e.tick(t)
            }
        }
        val cMinor = Key(0, true)
        assertEquals(List(keys.size) { cMinor }, keys, "la tonalité doit rester C mineur d'un bout à l'autre")
        val estimate = e.snapshot().key!!
        assertEquals(cMinor, estimate.ranking.first().key)
        // Probabilité honnête : C mineur nettement devant, sans prétendre à la certitude
        val p = estimate.probabilityOf(cMinor)
        kotlin.test.assertTrue(p in 0.6..0.95, "probabilité de C mineur : $p")
        // Aucun changement de tonalité dans l'historique
        kotlin.test.assertTrue(e.snapshot().history.all { it.analysis?.key == cMinor })
    }
}
