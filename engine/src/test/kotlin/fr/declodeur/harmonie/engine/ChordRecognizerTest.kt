package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordRecognizerTest {

    private fun best(vararg notes: Int): ChordCandidate =
        assertNotNull(ChordRecognizer.recognize(notes.toList()).best, "aucun accord pour ${notes.toList()}")

    @Test
    fun everyYamahaTypeIsRecognizedOnEveryRootInRootPosition() {
        for (root in 0 until 12) {
            for (type in ChordType.entries) {
                val notes = if (type == ChordType.ONE_PLUS_EIGHT) listOf(48 + root, 60 + root)
                else type.intervals.sorted().map { 48 + root + it }
                val result = ChordRecognizer.recognize(notes)
                val b = assertNotNull(result.best, "$type sur ${Notes.name(root)} non reconnu")
                assertEquals(type, b.type, "type pour ${Notes.name(root)}${type.suffix} : obtenu ${b.symbol}")
                assertEquals(root, b.root, "fondamentale pour ${Notes.name(root)}${type.suffix}")
                assertTrue(b.isExact)
                assertEquals("état fondamental", b.inversion)
            }
        }
    }

    @Test
    fun yamahaSymbols() {
        assertEquals("C", best(60, 64, 67).symbol)
        assertEquals("CM7", best(60, 64, 67, 71).symbol)
        assertEquals("Cm7♭5", best(60, 63, 66, 70).symbol)
        assertEquals("G7(♭9)", best(55, 59, 62, 65, 68).symbol)
        assertEquals("B♭", best(58, 62, 65).symbol)
        assertEquals("F♯m", best(54, 57, 61).symbol)
    }

    @Test
    fun firstInversionIsWrittenWithSlash() {
        val b = best(52, 55, 60) // E3 G3 C4
        assertEquals("C/E", b.symbol)
        assertEquals("1er renversement (tierce à la basse)", b.inversion)
    }

    @Test
    fun secondAndThirdInversions() {
        assertEquals("2e renversement (quinte à la basse)", best(55, 60, 64).inversion)
        assertEquals("3e renversement (septième à la basse)", best(58, 60, 64, 67).inversion)
    }

    @Test
    fun bassDecidesBetweenC6AndAm7() {
        val c6 = ChordRecognizer.recognize(listOf(48, 52, 55, 57))
        assertEquals("C6", c6.best?.symbol)
        assertTrue(c6.alternatives.any { it.symbol == "Am7/C" }, "Am7/C attendu en alternative")
        assertEquals("Am7", best(45, 48, 52, 55).symbol)
    }

    @Test
    fun symmetricChordsFollowTheBass() {
        assertEquals("E♭dim7", best(51, 54, 57, 60).symbol)
        assertEquals("Eaug", best(52, 56, 60).symbol)
    }

    @Test
    fun seventhWithoutFifthIsRecognized() {
        val b = best(48, 52, 58) // C E B♭
        assertEquals("C7", b.symbol)
        assertTrue(b.omittedFifth)
    }

    @Test
    fun oneForeignNoteIsTolerated() {
        val b = best(48, 52, 55, 66) // C E G + F♯ (pas un accord Yamaha)
        assertEquals(ChordType.MAJOR, b.type)
        assertEquals(setOf(6), b.extraNotes)
        assertEquals(ToneRole.EXTRA, b.roleOf(66))
    }

    @Test
    fun powerChordAndOctave() {
        assertEquals("C1+5", best(48, 55).symbol)
        assertEquals("C1+8", best(48, 60).symbol)
        assertNull(ChordRecognizer.recognize(listOf(60)).best)
    }

    @Test
    fun twoNotesThatAreNotAChordGiveTheInterval() {
        val r = ChordRecognizer.recognize(listOf(60, 64))
        assertNull(r.best)
        assertEquals("tierce majeure (C – E)", r.intervalDescription)
    }

    @Test
    fun rolesAndDegrees() {
        val b = best(43, 47, 50, 53, 57) // G7(9)
        assertEquals("G7(9)", b.symbol)
        assertEquals(ToneRole.ROOT, b.roleOf(43))
        assertEquals(ToneRole.CHORD_TONE, b.roleOf(53))
        assertEquals(ToneRole.TENSION, b.roleOf(57))
        assertEquals("♭7", b.degreeOf(53))
        assertEquals("9", b.degreeOf(57))
        assertEquals("♯9", best(48, 52, 55, 58, 63).degreeOf(63))
    }

    @Test
    fun suspendedChordsUseTheirOwnDegreeNames() {
        val b = best(48, 53, 55)
        assertEquals("Csus4", b.symbol)
        assertEquals("4", b.degreeOf(53))
        assertEquals("2", best(48, 50, 55).degreeOf(50))
    }
}
