package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PredictorTest {
    private val c = Key(0, false)
    private val a = Key(9, true)

    private fun deg(iv: Int, q: Quality) = Degree(iv, q)
    private val I = deg(0, Quality.MAJ)
    private val ii = deg(2, Quality.MIN)
    private val IV = deg(5, Quality.MAJ)
    private val V = deg(7, Quality.MAJ)
    private val V7 = deg(7, Quality.DOM)
    private val vi = deg(9, Quality.MIN)

    private fun top(history: List<Degree>, key: Key = c, style: MusicStyle = MusicStyle.POP_ROCK, n: Int = 2) =
        Predictor.next(history, key, style, n)

    @Test
    fun probabilitiesAreNormalizedAndSorted() {
        val p = top(listOf(I))
        assertEquals(1.0, p.sumOf { it.second }, 1e-9)
        assertTrue(p.zipWithNext().all { (x, y) -> x.second >= y.second })
        assertTrue(p.none { it.first == I }, "un accord n'est pas suivi de lui-même")
    }

    @Test
    fun popAfterTonicGoesToIVorV() {
        val firsts = top(listOf(I)).take(3).map { it.first }
        assertTrue(IV in firsts && V in firsts && vi in firsts, "obtenu $firsts")
    }

    @Test
    fun twoFiveResolvesToOne() {
        val p = top(listOf(ii, V7), style = MusicStyle.JAZZ)
        assertEquals(I, p.first().first)
        assertTrue(p.first().second > 0.6, "I à ${p.first().second}")
    }

    @Test
    fun secondaryDominantGoesToItsTarget() {
        val a7 = deg(9, Quality.DOM)
        assertEquals(ii, top(listOf(I, a7)).first().first) // hors table pop : règle de la dominante
        assertEquals(ii, top(listOf(I, a7), style = MusicStyle.JAZZ).first().first)
    }

    @Test
    fun tritoneSubstituteGoesHalfStepDown() {
        val db7 = deg(1, Quality.DOM)
        assertEquals(I, top(listOf(ii, db7), style = MusicStyle.JAZZ).first().first)
    }

    @Test
    fun songLoopIsRemembered() {
        // I V vi IV joué deux fois, puis I V vi : IV attendu, même si la table seule dirait autre chose
        val h = listOf(I, V, vi, IV, I, V, vi, IV, I, V, vi)
        val p = top(h, n = 3)
        assertEquals(IV, p.first().first)
        assertTrue(p.first().second > 0.7, "IV à ${p.first().second}")
    }

    @Test
    fun longerContextGivesMoreConfidence() {
        val h = listOf(I, V, vi, IV, I, V, vi, IV, I, V, vi)
        val p1 = top(h, n = 1).first { it.first == IV }.second
        val p3 = top(h, n = 3).first { it.first == IV }.second
        assertTrue(p3 > p1, "contexte 3 : $p3, contexte 1 : $p1")
    }

    @Test
    fun bluesTurnsAround() {
        val i7 = deg(0, Quality.DOM)
        assertEquals(deg(5, Quality.DOM), top(listOf(i7), style = MusicStyle.BLUES).first().first)
    }

    @Test
    fun minorKeyFavoursMinorDegrees() {
        val e7 = deg(7, Quality.DOM)
        assertEquals(deg(0, Quality.MIN), top(listOf(deg(2, Quality.HDIM), e7), key = a, style = MusicStyle.JAZZ).first().first)
    }

    @Test
    fun treeFollowsItsSettings() {
        val t = Predictor.tree(
            listOf(I), c, MusicStyle.POP_ROCK,
            PredictionSettings(level1 = 4, level2 = 2, level3 = 1, expandThresholdPercent = 0), listOf(60, 64, 67),
        )
        assertEquals(4, t.size)
        assertTrue(t.all { it.children.size == 2 })
        assertTrue(t.all { n -> n.children.all { it.children.size == 1 } })
        assertTrue(t.all { it.voicing.isNotEmpty() })
        val t2 = Predictor.tree(listOf(I), c, MusicStyle.POP_ROCK, PredictionSettings(level1 = 3, level2 = 0, level3 = 2), listOf(60, 64, 67))
        assertEquals(3, t2.size)
        assertTrue(t2.all { it.children.isEmpty() })
    }

    @Test
    fun weakBranchesAreNotExpanded() {
        val t = Predictor.tree(listOf(I), c, MusicStyle.POP_ROCK, PredictionSettings(expandThresholdPercent = 20), emptyList())
        assertTrue(t.any { it.probability < 0.2 }, "il faut une branche faible pour ce test")
        for (n in t) {
            assertEquals(n.pathProbability >= 0.2, n.children.isNotEmpty(), "branche ${n.numeral} à ${n.pathProbability}")
            for (ch in n.children) assertEquals(ch.pathProbability >= 0.2, ch.children.isNotEmpty())
        }
    }

    @Test
    fun styleChoosesChordQuality() {
        val pop = Predictor.tree(listOf(I), c, MusicStyle.POP_ROCK, PredictionSettings(level1 = 6, level2 = 0), emptyList())
        assertTrue(pop.any { it.chord.symbol == "F" })
        val jazz = Predictor.tree(listOf(ii), c, MusicStyle.JAZZ, PredictionSettings(level1 = 1, level2 = 0), emptyList())
        assertEquals("G7", jazz.first().chord.symbol)
        assertEquals("V7", jazz.first().numeral)
    }

    @Test
    fun voicingMovesAsLittleAsPossible() {
        val dm7 = listOf(62, 65, 69, 72) // D F A C
        val g7 = ChordCandidate(7, ChordType.DOM7, 7, false, emptySet(), 0)
        val v = Voicing.lead(dm7, g7)
        assertEquals(setOf(7, 11, 2, 5), v.map(Notes::pc).toSet())
        assertTrue(Voicing.movement(dm7, v) <= 6.0, "voicing $v trop éloigné")
    }

    @Test
    fun engineProducesPredictionsOnceTheKeyIsKnown() {
        val e = HarmonieEngine()
        var t = 0L
        for (chord in listOf(listOf(48, 52, 55), listOf(53, 57, 60), listOf(43, 47, 50, 53), listOf(48, 52, 55))) {
            chord.forEach { e.noteOn(0, it, 90, t) }
            repeat(60) { t += 10; e.tick(t) }
            chord.forEach { e.noteOff(0, it, t) }
        }
        val p = e.snapshot().predictions
        assertEquals(4, p.size)
        assertTrue(p.none { it.chord.symbol == "C" }, "C ne se suit pas lui-même")
    }
}
