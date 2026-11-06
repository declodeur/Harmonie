package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChordInterpreterTest {
    private val cMajor = Key(0, false)
    private val all = InterpretationSettings()

    private fun read(vararg notes: Int) = ChordRecognizer.recognize(notes.toList(), 7)

    private fun interpret(key: Key?, style: MusicStyle, vararg notes: Int) =
        assertNotNull(ChordInterpreter.interpret(read(*notes), key, style, all).best)

    @Test
    fun sixthIsReadAsInvertedSeventhInClassicalStyle() {
        assertEquals("Am7", interpret(cMajor, MusicStyle.CLASSICAL, 48, 52, 55, 57).symbol) // C E G A
        assertEquals("Am7♭5", interpret(cMajor, MusicStyle.CLASSICAL, 48, 51, 55, 57).symbol) // C E♭ G A
        assertEquals("C6", interpret(cMajor, MusicStyle.POP_ROCK, 48, 52, 55, 57).symbol)
    }

    @Test
    fun suspensionsAreReadWithTheirThird() {
        val g = interpret(cMajor, MusicStyle.CLASSICAL, 43, 48, 50) // G C D
        assertEquals("G", g.symbol)
        assertEquals(setOf(0), g.extraNotes)
        assertNotNull(g.interpretation)
        assertEquals("Dm", interpret(cMajor, MusicStyle.CLASSICAL, 50, 55, 57).symbol) // D G A : tierce fa
        assertEquals("G7", interpret(cMajor, MusicStyle.CLASSICAL, 43, 48, 50, 53).symbol) // G C D F
        assertEquals("Gsus4", interpret(cMajor, MusicStyle.POP_ROCK, 43, 48, 50).symbol)
    }

    @Test
    fun diminishedSeventhIsNamedAfterTheLeadingTone() {
        // D F A♭ B en C majeur : sensible B
        assertEquals(11, interpret(cMajor, MusicStyle.CLASSICAL, 50, 53, 56, 59).root)
        // Même accord en la mineur : G♯ = A♭ est la sensible
        assertEquals(8, interpret(Key(9, true), MusicStyle.CLASSICAL, 50, 53, 56, 59).root)
    }

    @Test
    fun dyadsAreCompleted() {
        fun dyad(key: Key?, style: MusicStyle, vararg n: Int) = ChordInterpreter.dyad(read(*n), key, style).best?.symbol
        assertEquals("A", dyad(null, MusicStyle.POP_ROCK, 57, 61)) // A C♯
        assertEquals("Em", dyad(null, MusicStyle.POP_ROCK, 52, 55)) // E G
        assertEquals("A", dyad(null, MusicStyle.POP_ROCK, 49, 57)) // C♯ A (sixte)
        assertEquals("G", dyad(cMajor, MusicStyle.CLASSICAL, 43, 50)) // G D, tierce si
        assertEquals("C1+5", dyad(cMajor, MusicStyle.POP_ROCK, 48, 55)) // quinte à vide en variété
    }

    @Test
    fun aNoteOfTheCurrentChordDoesNotReplaceIt() {
        val e = HarmonieEngine(AnalysisSettings(style = MusicStyle.CLASSICAL))
        var t = 0L
        listOf(48, 52, 55).forEach { e.noteOn(0, it, 90, t) }
        repeat(20) { t += 10; e.tick(t) }
        listOf(48, 52, 55).forEach { e.noteOff(0, it, t) }
        e.noteOn(0, 64, 90, t) // E seul
        repeat(20) { t += 10; e.tick(t) }
        assertEquals("C", e.snapshot().current?.best?.symbol)
        // Option désactivée : le mi seul n'est pas un accord, l'affichage change
        val off = HarmonieEngine(
            AnalysisSettings(arpeggioDetection = false, interpretation = InterpretationSettings(keepChordOnSubset = false, completeDyads = false)),
        )
        t = 0L
        listOf(48, 52, 55).forEach { off.noteOn(0, it, 90, t) }
        repeat(20) { t += 10; off.tick(t) }
        listOf(48, 52, 55).forEach { off.noteOff(0, it, t) }
        off.noteOn(0, 64, 90, t); off.noteOn(0, 67, 90, t) // E G
        repeat(20) { t += 10; off.tick(t) }
        assertTrue(off.snapshot().current?.best?.symbol != "C")
    }
}
