package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChordScalesTest {

    private val cMajor = Key(0, false)
    private val aMinor = Key(9, true)

    private fun chord(vararg notes: Int): ChordCandidate =
        assertNotNull(ChordRecognizer.recognize(notes.toList()).best)

    private fun scales(key: Key?, vararg notes: Int, style: MusicStyle = MusicStyle.POP_ROCK): ChordScaleResult {
        val c = chord(*notes)
        val a = key?.let { HarmonicAnalyzer.analyze(c, it, style) }
        return ChordScales.suggest(c, a, key, style)
    }

    private fun names(pcs: Set<Int>) = pcs.map { Notes.name(it) }.toSet()

    @Test
    fun diatonicModesInMajor() {
        val dm7 = scales(cMajor, 50, 53, 57, 60)
        assertEquals(Scale.DORIAN, dm7.primary.scale)
        assertEquals(emptySet(), dm7.avoid)
        assertEquals(listOf("9", "11", "13"), dm7.tensions)

        val cmaj7 = scales(cMajor, 48, 52, 55, 59)
        assertEquals(Scale.IONIAN, cmaj7.primary.scale)
        assertEquals(setOf("F"), names(cmaj7.avoid))

        val em7 = scales(cMajor, 52, 55, 59, 62)
        assertEquals(Scale.PHRYGIAN, em7.primary.scale)
        assertEquals(setOf("F", "C"), names(em7.avoid))

        val fmaj7 = scales(cMajor, 53, 57, 60, 64)
        assertEquals(Scale.LYDIAN, fmaj7.primary.scale)
        assertEquals(emptySet(), fmaj7.avoid)
        assertTrue("♯11" in fmaj7.tensions)

        val am7 = scales(cMajor, 57, 60, 64, 67)
        assertEquals(Scale.AEOLIAN, am7.primary.scale)
        assertEquals(setOf("F"), names(am7.avoid))
    }

    @Test
    fun dominants() {
        val g7 = scales(cMajor, 43, 47, 50, 53)
        assertEquals(Scale.MIXOLYDIAN, g7.primary.scale)
        assertEquals(setOf("C"), names(g7.avoid))

        // A7 = V7/II, résout vers Dm : mixolydien ♭9 ♭13 ; ♭9 et ♭13 ne sont pas à éviter
        val a7 = scales(cMajor, 45, 49, 52, 55)
        assertEquals(Scale.MIXO_B9_B13, a7.primary.scale)
        assertTrue("♭9" in a7.tensions && "♭13" in a7.tensions)
        assertEquals(setOf("D"), names(a7.avoid))

        // E7 en la mineur : V7 vers un accord mineur
        val e7 = scales(aMinor, 52, 56, 59, 62)
        assertEquals(Scale.MIXO_B9_B13, e7.primary.scale)

        // D♭7 = SubV7 de C : lydien ♭7
        val db7 = scales(cMajor, 49, 53, 56, 59)
        assertEquals(Scale.LYDIAN_DOMINANT, db7.primary.scale)
        assertEquals(emptySet(), db7.avoid)

        // G7(♯9) : altérée
        val alt = scales(cMajor, 43, 47, 53, 58)
        assertEquals(ChordType.DOM7_SHARP9, alt.chord.type)
        assertEquals(Scale.ALTERED, alt.primary.scale)
    }

    @Test
    fun otherQualities() {
        val dim7 = scales(cMajor, 49, 52, 55, 58)
        assertEquals(Scale.WHOLE_HALF, dim7.primary.scale)
        assertEquals(emptySet(), dim7.avoid)

        val bm7b5 = scales(cMajor, 47, 50, 53, 57)
        assertEquals(Scale.LOCRIAN, bm7b5.primary.scale)
        assertEquals(setOf("C"), names(bm7b5.avoid))

        val bm7b5InAm = scales(aMinor, 47, 50, 53, 57)
        assertEquals(Scale.LOCRIAN_NAT2, bm7b5InAm.primary.scale)
        assertEquals(emptySet(), bm7b5InAm.avoid)

        val aug = scales(null, 48, 52, 56)
        assertEquals(Scale.WHOLE_TONE, aug.primary.scale)

        // Sans tonalité : mineur → dorien, majeur → ionien
        assertEquals(Scale.DORIAN, scales(null, 50, 53, 57).primary.scale)
        assertEquals(Scale.IONIAN, scales(null, 48, 52, 55).primary.scale)
    }

    @Test
    fun pentatonicsInPopRock() {
        val c = scales(cMajor, 48, 52, 55)
        assertEquals(Scale.IONIAN, c.primary.scale)
        assertTrue(c.alternatives.any { it.scale == Scale.MAJOR_PENTATONIC })
        val jazz = scales(cMajor, 48, 52, 55, style = MusicStyle.JAZZ)
        assertTrue(jazz.alternatives.none { it.scale == Scale.MAJOR_PENTATONIC })
    }

    @Test
    fun melodyClassification() {
        val cmaj7 = scales(cMajor, 48, 52, 55, 59)
        assertEquals(MelodyRole.CHORD_TONE, ChordScales.classify(76, cmaj7)) // E
        assertEquals(MelodyRole.TENSION, ChordScales.classify(74, cmaj7)) // D = 9
        assertEquals(MelodyRole.AVOID, ChordScales.classify(77, cmaj7)) // F
        assertEquals(MelodyRole.CHROMATIC, ChordScales.classify(75, cmaj7)) // E♭
    }

    @Test
    fun engineExposesScaleAndMelodyRoles() {
        val e = HarmonieEngine(AnalysisSettings(mode = AnalysisMode.CHORDS_AND_MELODY, chordChannel = 0))
        var t = 0L
        listOf(48, 52, 55, 59).forEach { e.noteOn(0, it, 90, t) }
        while (t < 300) { t += 10; e.tick(t) }
        e.noteOn(1, 77, 90, t)
        t += 10; e.tick(t)
        val snap = e.snapshot()
        assertEquals(Scale.IONIAN, assertNotNull(snap.currentScale).primary.scale)
        val melody = snap.notes.single { it.channel == 1 }
        assertEquals(MelodyRole.AVOID, melody.melodyRole)
        assertTrue(snap.notes.filter { it.channel == 0 }.all { it.melodyRole == null })
    }
}
