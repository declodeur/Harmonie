package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VoicingsTest {
    private fun chord(vararg notes: Int) = ChordRecognizer.recognize(notes.toList()).best!!
    private fun layout(vararg notes: Int) = Voicings.describe(notes.toList(), chord(*notes), null)!!.layout

    @Test
    fun layouts() {
        assertEquals("position serrée", layout(60, 64, 67))
        assertEquals("position serrée", layout(60, 64, 67, 71))
        assertEquals("drop 2", layout(55, 60, 64, 71)) // CM7, sol descendu d'une octave
        assertEquals("drop 3", layout(52, 60, 67, 71)) // CM7, mi descendu d'une octave
        assertEquals("position large", layout(48, 55, 64))
        assertEquals("position serrée", layout(48, 52, 55, 60)) // C E G C : l'octave comprise
        assertEquals("position large", layout(48, 55, 64, 72)) // C3 G3 E4 C5 : voix supérieures écartées
        assertEquals("position serrée", layout(60, 67, 72, 75)) // Cm, basse C4 sous G4 C5 E♭5 serrés
        assertEquals("shell (fondamentale, tierce, septième)", layout(48, 52, 58))
        assertEquals("en quartes", layout(62, 67, 72, 77))
        assertEquals("position serrée sur basse séparée", layout(36, 60, 64, 67))
    }

    @Test
    fun doublingsAndLeadingTone() {
        val g = chord(43, 47, 50, 59)
        val v = Voicings.describe(listOf(43, 47, 50, 59), g, Key(0, false))!!
        assertEquals("sensible doublée", v.warning)
        assertTrue("tierce doublée" in v.doublings)
        val c = Voicings.describe(listOf(48, 55, 60, 64), chord(48, 55, 60, 64), Key(0, false))!!
        assertEquals(listOf("fondamentale doublée"), c.doublings)
        assertEquals("3", c.sopranoDegree)
        assertEquals("tierce", c.sopranoName)
    }

    @Test
    fun guideTonesFromG7ToC() {
        val a = listOf(43, 53, 59, 62)
        val b = listOf(48, 52, 55, 64)
        val vl = Voicings.lead(a, chord(*a.toIntArray()), b, chord(*b.toIntArray()), flagParallels = true)!!
        assertEquals(listOf("7e F → 3ce E"), vl.guideTones)
        assertTrue(vl.parallels.isEmpty())
        assertEquals(0, vl.commonTones)
        assertEquals(2, vl.steps)
        assertEquals(2, vl.leaps)
        assertEquals("direct", vl.outerMotion)
    }

    @Test
    fun parallelFifthsAndOctaves() {
        val vl = Voicings.lead(listOf(48, 55, 64), chord(48, 55, 64), listOf(50, 57, 66), chord(50, 57, 66), flagParallels = true)!!
        assertEquals(listOf("quintes consécutives : basse et voix du milieu"), vl.parallels)
        val oct = Voicings.lead(listOf(48, 55, 60, 64), chord(48, 55, 60, 64), listOf(50, 57, 62, 66), chord(50, 57, 62, 66), flagParallels = true)!!
        assertTrue(oct.parallels.any { it.startsWith("octaves consécutives : basse et alto") }, oct.parallels.toString())
        val contrary = Voicings.lead(listOf(48, 55, 64), chord(48, 55, 64), listOf(53, 57, 60), chord(53, 57, 60), flagParallels = true)!!
        assertEquals("contraire", contrary.outerMotion)
        assertTrue(contrary.parallels.isEmpty())
    }

    @Test
    fun engineReportsVoicingAndVoiceLeading() {
        val e = HarmonieEngine(AnalysisSettings(style = MusicStyle.CLASSICAL))
        var t = 0L
        for (ch in listOf(listOf(48, 55, 64, 72), listOf(53, 57, 65, 72))) {
            ch.forEach { e.noteOn(0, it, 90, t) }
            val end = t + 800
            while (t < end) { t += 10; e.tick(t) }
            if (ch.first() == 53) break
            ch.forEach { e.noteOff(0, it, t) }
            t += 20; e.tick(t)
        }
        val s = e.snapshot()
        assertNotNull(s.voicing)
        val vl = assertNotNull(s.voiceLeading)
        assertEquals("C", vl.from)
        assertEquals(1, vl.commonTones) // le do du soprano reste
    }
}
