package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HarmonieEngineTest {

    private fun HarmonieEngine.symbol() = snapshot().current?.best?.symbol

    @Test
    fun chordIsValidatedAfterTheStabilityDelay() {
        val e = HarmonieEngine(AnalysisSettings(stabilityMs = 60))
        e.noteOn(0, 60, 90, 0)
        e.noteOn(0, 64, 90, 5)
        e.noteOn(0, 67, 90, 10)
        assertFalse(e.tick(40))
        assertNull(e.symbol())
        assertTrue(e.tick(71))
        assertEquals("C", e.symbol())
    }

    @Test
    fun releasingNotesDoesNotChangeTheChord() {
        val e = HarmonieEngine()
        e.noteOn(0, 60, 90, 0); e.noteOn(0, 64, 90, 0); e.noteOn(0, 67, 90, 0)
        e.tick(100)
        e.noteOff(0, 67, 200)
        e.tick(300)
        e.noteOff(0, 64, 310)
        e.noteOff(0, 60, 320)
        e.tick(500)
        assertEquals("C", e.symbol())
        assertEquals(1, e.snapshot().history.size)
    }

    @Test
    fun addingANoteMakesANewChord() {
        val e = HarmonieEngine()
        e.noteOn(0, 60, 90, 0); e.noteOn(0, 64, 90, 0); e.noteOn(0, 67, 90, 0)
        e.tick(100)
        e.noteOn(0, 70, 90, 200)
        e.tick(300)
        assertEquals("C7", e.symbol())
        assertEquals(listOf("C", "C7"), e.snapshot().history.map { it.chord.best?.symbol })
    }

    @Test
    fun aVeryShortChordIsStillRecognized() {
        val e = HarmonieEngine(AnalysisSettings(stabilityMs = 80))
        e.noteOn(0, 57, 90, 0); e.noteOn(0, 60, 90, 2); e.noteOn(0, 64, 90, 4)
        e.noteOff(0, 57, 40); e.noteOff(0, 60, 41); e.noteOff(0, 64, 42)
        e.tick(200)
        assertEquals("Am", e.symbol())
    }

    @Test
    fun sustainPedalKeepsNotesInTheChord() {
        val e = HarmonieEngine(AnalysisSettings(includeSustain = true))
        e.controlChange(0, 64, 127, 0)
        e.noteOn(0, 48, 90, 0)
        e.noteOff(0, 48, 50)
        e.noteOn(0, 52, 90, 100); e.noteOn(0, 55, 90, 100)
        e.tick(300)
        assertEquals("C", e.symbol())
        assertTrue(e.snapshot().notes.single { it.note == 48 }.sustained)
        e.controlChange(0, 64, 0, 400)
        assertEquals(2, e.snapshot().notes.size)
    }

    @Test
    fun sustainedNotesCanBeExcluded() {
        val e = HarmonieEngine(AnalysisSettings(includeSustain = false))
        e.controlChange(0, 64, 127, 0)
        e.noteOn(0, 45, 90, 0)
        e.noteOff(0, 45, 50)
        e.noteOn(0, 48, 90, 100); e.noteOn(0, 52, 90, 100); e.noteOn(0, 55, 90, 100)
        e.tick(300)
        assertEquals("C", e.symbol())
        assertFalse(e.snapshot().notes.single { it.note == 45 }.analysed)
    }

    @Test
    fun melodyChannelIsIgnoredInChordsAndMelodyMode() {
        val e = HarmonieEngine(AnalysisSettings(mode = AnalysisMode.CHORDS_AND_MELODY, chordChannel = 2))
        e.noteOn(2, 50, 90, 0); e.noteOn(2, 53, 90, 0); e.noteOn(2, 57, 90, 0)
        e.noteOn(0, 72, 90, 0) // mélodie
        e.tick(100)
        assertEquals("Dm", e.symbol())
        val melody = e.snapshot().notes.single { it.note == 72 }
        assertTrue(melody.melody)
        assertFalse(melody.analysed)
        // Une note de mélodie seule ne déclenche pas de nouvel accord
        e.noteOn(0, 74, 90, 200)
        e.tick(300)
        assertEquals(1, e.snapshot().history.size)
    }

    @Test
    fun closeInversionsOfTheSameChordAreOneHistoryEntry() {
        val e = HarmonieEngine(AnalysisSettings(slashMinGap = 7))
        val voicings = listOf(listOf(60, 63, 67), listOf(67, 72, 75), listOf(63, 67, 72))
        voicings.forEachIndexed { i, v ->
            val t = i * 1000L
            v.forEach { e.noteOn(0, it, 90, t) }
            e.tick(t + 100)
            v.forEach { e.noteOff(0, it, t + 200) }
            e.tick(t + 300)
        }
        assertEquals(listOf("Cm"), e.snapshot().history.map { it.chord.best?.symbol })
    }

    @Test
    fun addingALowRootUnderAHeldChordKeepsTheChord() {
        for ((top, expected) in listOf(listOf(65, 69, 72, 76) to "FM7", listOf(65, 69, 72, 74) to "F6")) {
            val e = HarmonieEngine()
            top.forEach { e.noteOn(0, it, 90, 0) }
            e.tick(100)
            assertEquals(expected, e.symbol())
            e.noteOn(0, 53, 90, 200) // F3
            e.tick(300)
            assertEquals(expected, e.symbol())
        }
    }

    private fun HarmonieEngine.playChord(notes: List<Int>, from: Long, holdMs: Long = 400): Long {
        notes.forEach { noteOn(0, it, 90, from) }
        var t = from
        while (t < from + holdMs) { t += 10; tick(t) }
        notes.forEach { noteOff(0, it, t) }
        t += 10; tick(t)
        return t
    }

    @Test
    fun twoFiveOneIsAnnotated() {
        val e = HarmonieEngine()
        var t = e.playChord(listOf(50, 53, 57, 60), 0)   // Dm7
        t = e.playChord(listOf(43, 47, 50, 53), t)       // G7
        e.playChord(listOf(48, 52, 55, 59), t)           // CM7
        val h = e.snapshot().history
        assertEquals(listOf("Dm7", "G7", "CM7"), h.map { it.chord.best?.symbol })
        assertTrue(h[1].closesTwoFive, "crochet II-V attendu sous Dm7 – G7")
        assertNull(h[1].resolution)
        assertEquals(Resolution.FIFTH, h[2].resolution)
        assertFalse(h[2].closesTwoFive)
    }

    @Test
    fun tritoneSubstituteResolutionIsDashed() {
        val e = HarmonieEngine()
        var t = e.playChord(listOf(50, 53, 57, 60), 0)   // Dm7
        t = e.playChord(listOf(49, 53, 56, 59), t)       // D♭7
        e.playChord(listOf(48, 52, 55, 59), t)           // CM7
        val h = e.snapshot().history
        assertFalse(h[1].closesTwoFive)
        assertEquals(Resolution.HALF_STEP, h[2].resolution)
    }

    @Test
    fun arpeggioIsRecognizedWhenNoChordIsHeld() {
        val e = HarmonieEngine()
        var t = 0L
        for (n in listOf(48, 52, 55, 60)) { // C E G C, notes séparées
            e.noteOn(0, n, 90, t)
            repeat(25) { t += 10; e.tick(t) }
            e.noteOff(0, n, t)
            repeat(5) { t += 10; e.tick(t) }
        }
        val cur = e.snapshot().current
        assertEquals("C", cur?.best?.symbol)
        assertTrue(cur!!.arpeggiated)
        assertTrue(e.snapshot().history.last().chord.arpeggiated)
    }

    @Test
    fun arpeggioKeepsTheLongestChord() {
        val e = HarmonieEngine()
        var t = 0L
        for (n in listOf(48, 52, 55, 58)) { // C E G B♭ : C7 (et non Edim)
            e.noteOn(0, n, 90, t)
            repeat(25) { t += 10; e.tick(t) }
            e.noteOff(0, n, t)
        }
        repeat(10) { t += 10; e.tick(t) }
        assertEquals("C7", e.snapshot().current?.best?.symbol)
    }

    @Test
    fun noArpeggioWhileAChordIsHeld() {
        val e = HarmonieEngine()
        listOf(53, 57, 60).forEach { e.noteOn(0, it, 90, 0) } // F tenu
        var t = 0L
        repeat(10) { t += 10; e.tick(t) }
        assertEquals("F", e.symbol())
        assertFalse(e.snapshot().current!!.arpeggiated)
    }

    @Test
    fun arpeggioDetectionCanBeDisabled() {
        val e = HarmonieEngine(AnalysisSettings(arpeggioDetection = false))
        var t = 0L
        for (n in listOf(48, 52, 55)) {
            e.noteOn(0, n, 90, t)
            repeat(25) { t += 10; e.tick(t) }
            e.noteOff(0, n, t)
        }
        assertTrue(e.snapshot().history.isEmpty())
    }

    @Test
    fun sameChordTwiceIsNotDuplicatedInHistory() {
        val e = HarmonieEngine()
        repeat(2) { i ->
            val t = i * 1000L
            e.noteOn(0, 60, 90, t); e.noteOn(0, 64, 90, t); e.noteOn(0, 67, 90, t)
            e.tick(t + 100)
            e.noteOff(0, 60, t + 200); e.noteOff(0, 64, t + 200); e.noteOff(0, 67, t + 200)
            e.tick(t + 300)
        }
        assertEquals(1, e.snapshot().history.size)
    }
}
