package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HarmonyTest {

    private val cMajor = Key(0, false)
    private val aMinor = Key(9, true)

    private fun chord(vararg notes: Int): ChordCandidate =
        assertNotNull(ChordRecognizer.recognize(notes.toList()).best)

    private fun analyze(key: Key, vararg notes: Int, style: MusicStyle = MusicStyle.POP_ROCK) =
        HarmonicAnalyzer.analyze(chord(*notes), key, style)

    /** Joue une suite d'accords dans le moteur (chaque accord tenu [holdMs]) et renvoie le moteur. */
    private fun play(chords: List<List<Int>>, holdMs: Long = 900): HarmonieEngine {
        val e = HarmonieEngine()
        var t = 0L
        for (c in chords) {
            c.forEach { e.noteOn(0, it, 90, t) }
            val end = t + holdMs
            while (t < end) { t += 10; e.tick(t) }
            c.forEach { e.noteOff(0, it, t) }
            t += 10; e.tick(t)
        }
        return e
    }

    @Test
    fun diatonicChordsInMajor() {
        val ii = analyze(cMajor, 50, 53, 57, 60)
        assertEquals("IIm7", ii.numeral)
        assertEquals(HarmonicFunction.SUBDOMINANT, ii.function)
        assertEquals(HarmonicCategory.DIATONIC, ii.category)
        val v = analyze(cMajor, 43, 47, 50, 53)
        assertEquals("V7", v.numeral)
        assertEquals(HarmonicFunction.DOMINANT, v.function)
        assertEquals("IM7", analyze(cMajor, 48, 52, 55, 59).numeral)
        assertEquals(HarmonicFunction.TONIC, analyze(cMajor, 45, 48, 52).function)
        assertEquals("VIIm7♭5", analyze(cMajor, 47, 50, 53, 57).numeral)
    }

    @Test
    fun secondaryDominants() {
        val a7 = analyze(cMajor, 45, 49, 52, 55)
        assertEquals("V7/II", a7.numeral)
        assertEquals(HarmonicCategory.SECONDARY_DOMINANT, a7.category)
        assertTrue("Dm" in a7.description)
        assertEquals("V7/V", analyze(cMajor, 50, 54, 57, 60).numeral)
        assertEquals("V/VI", analyze(cMajor, 52, 56, 59).numeral) // E majeur en C
    }

    @Test
    fun alteredMainDominant() {
        val a = analyze(cMajor, 43, 47, 50, 53, 56) // G7(♭9)
        assertEquals("V7(♭9)", a.numeral)
        assertEquals(HarmonicCategory.ALTERED_DOMINANT, a.category)
    }

    @Test
    fun tritoneSubstitution() {
        val db7 = analyze(cMajor, 49, 53, 56, 59)
        assertEquals("SubV7", db7.numeral)
        assertEquals(HarmonicCategory.SUBSTITUTE_DOMINANT, db7.category)
        assertEquals("SubV7/II", analyze(cMajor, 51, 55, 58, 61).numeral) // E♭7 vers Dm
    }

    @Test
    fun modalBorrowing() {
        val fm = analyze(cMajor, 53, 56, 60)
        assertEquals("IVm", fm.numeral)
        assertEquals(HarmonicCategory.BORROWED, fm.category)
        assertEquals(HarmonicFunction.SUBDOMINANT, fm.function)
        assertEquals("♭VIM7", analyze(cMajor, 44, 48, 51, 55).numeral)
        val bb7 = analyze(cMajor, 46, 50, 53, 56)
        assertEquals("♭VII7", bb7.numeral)
        assertEquals(HarmonicCategory.BORROWED, bb7.category)
    }

    @Test
    fun passingDiminished() {
        val a = analyze(cMajor, 49, 52, 55, 58) // C♯dim7
        assertEquals("♯Idim7", a.numeral)
        assertEquals(HarmonicCategory.PASSING_DIMINISHED, a.category)
    }

    @Test
    fun neapolitan() {
        assertEquals("♭II", analyze(cMajor, 49, 53, 56).numeral)
    }

    @Test
    fun minorKey() {
        val e7 = analyze(aMinor, 52, 56, 59, 62)
        assertEquals("V7", e7.numeral)
        assertEquals(HarmonicCategory.DIATONIC, e7.category)
        assertEquals(HarmonicFunction.DOMINANT, e7.function)
        assertEquals("♭VII", analyze(aMinor, 55, 59, 62).numeral)
        assertEquals("♭IIIM7", analyze(aMinor, 48, 52, 55, 59).numeral)
        assertEquals("V7/V", analyze(aMinor, 47, 51, 54, 57).numeral)
        assertEquals("IIm7♭5", analyze(aMinor, 47, 50, 53, 57).numeral)
    }

    @Test
    fun bluesStyleTreatsDominantSeventhsAsColour() {
        val pop = analyze(cMajor, 48, 52, 55, 58)
        assertEquals("V7/IV", pop.numeral)
        val blues = analyze(cMajor, 48, 52, 55, 58, style = MusicStyle.BLUES)
        assertEquals("I7", blues.numeral)
        assertEquals(HarmonicCategory.BLUES, blues.category)
        assertEquals(HarmonicFunction.TONIC, blues.function)
    }

    @Test
    fun keyNamesAndSpelling() {
        assertEquals("E♭ majeur", Key(3, false).name)
        assertEquals("F♯ majeur", Key(6, false).name)
        assertEquals("E♭ mineur", Key(3, true).name)
        assertEquals("C♯ mineur", Key(1, true).name)
        assertEquals("G♯ mineur", Key(8, true).name)
        assertEquals("D♭ majeur", Key(1, false).name)
        assertEquals(Spelling.SHARPS, Key(4, false).spelling)
        assertEquals("D♯", Notes.name(3, Key(4, false).spelling))
        assertEquals(Key(9, true), cMajor.relative)
    }

    @Test
    fun keyIsUnknownWithoutEnoughNotes() {
        val e = HarmonieEngine()
        e.tick(0)
        assertNull(e.snapshot().key?.current)
    }

    @Test
    fun detectsCMajorFromAProgression() {
        val c = listOf(48, 52, 55); val f = listOf(53, 57, 60); val g7 = listOf(43, 47, 50, 53)
        val am = listOf(45, 48, 52); val dm = listOf(50, 53, 57)
        val e = play(listOf(c, f, g7, c, am, dm, g7, c))
        val key = e.snapshot().key
        assertEquals(cMajor, key?.current)
        assertTrue(key!!.ranking.first().probability > 0.4, "probabilité ${key.ranking.first().probability}")
        val numerals = e.snapshot().history.mapNotNull { it.analysis?.numeral }
        assertTrue(numerals.takeLast(3) == listOf("IIm", "V7", "I"), "chiffrage obtenu : $numerals")
    }

    @Test
    fun detectsAMinorFromAProgression() {
        val am = listOf(45, 48, 52); val dm = listOf(50, 53, 57); val e7 = listOf(52, 56, 59, 62)
        val f = listOf(53, 57, 60)
        val e = play(listOf(am, dm, e7, am, f, dm, e7, am))
        assertEquals(aMinor, e.snapshot().key?.current)
        assertEquals("Im", e.snapshot().currentAnalysis?.numeral)
    }

    @Test
    fun followsAModulation() {
        val c = listOf(48, 52, 55); val f = listOf(53, 57, 60); val g7 = listOf(43, 47, 50, 53)
        val d7 = listOf(50, 54, 57, 60); val g = listOf(43, 47, 50); val em = listOf(52, 55, 59)
        val bm = listOf(47, 50, 54); val a7 = listOf(45, 49, 52, 55); val dm = listOf(50, 54, 57)
        val e = play(listOf(c, f, g7, c) + List(4) { listOf(g, em, a7, d7, g, bm, d7, g) }.flatten())
        assertEquals(Key(7, false), e.snapshot().key?.current, "attendu G majeur après modulation")
        dm.size
    }

    @Test
    fun clearingHistoryResetsTheKey() {
        val c = listOf(48, 52, 55); val g7 = listOf(43, 47, 50, 53)
        val e = play(listOf(c, g7, c, g7, c))
        assertEquals(cMajor, e.snapshot().key?.current)
        e.clearHistory()
        assertNull(e.snapshot().key)
    }
}
