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
        // Notation classique (par défaut) : degrés de la gamme mineure
        assertEquals("VII", analyze(aMinor, 55, 59, 62).numeral)
        assertEquals("IIIM7", analyze(aMinor, 48, 52, 55, 59).numeral)
        assertEquals("VII", analyze(aMinor, 56, 59, 62).numeral.take(3)) // G♯° : sensible
        // Notation jazz : degrés comptés dans la gamme majeure de la tonique
        assertEquals("♭VII", HarmonicAnalyzer.analyze(chord(55, 59, 62), aMinor, notation = DegreeNotation.JAZZ).numeral)
        assertEquals("♭IIIM7", HarmonicAnalyzer.analyze(chord(48, 52, 55, 59), aMinor, notation = DegreeNotation.JAZZ).numeral)
        assertEquals("V7/VI", analyze(aMinor, 48, 52, 55, 58).numeral) // C7 vers F
        assertEquals("V7/♭VI", HarmonicAnalyzer.analyze(chord(48, 52, 55, 58), aMinor, notation = DegreeNotation.JAZZ).numeral)
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

    /** Accords puis mélodie (notes seules) sur le même canal, en mode Complète. */
    private fun playWithMelody(chords: List<List<Int>>, melody: List<Int>, settings: AnalysisSettings): HarmonieEngine {
        val e = HarmonieEngine(settings)
        var t = 0L
        for (c in chords) {
            c.forEach { e.noteOn(0, it, 90, t) }
            val end = t + 1500
            while (t < end) { t += 10; e.tick(t) }
            c.forEach { e.noteOff(0, it, t) }
        }
        for (n in melody) {
            e.noteOn(0, n, 90, t)
            val end = t + 400
            while (t < end) { t += 10; e.tick(t) }
            e.noteOff(0, n, t)
        }
        repeat(30) { t += 10; e.tick(t) }
        return e
    }

    private val am = listOf(45, 48, 52)
    private val popLoop = listOf(am, listOf(48, 52, 55), listOf(53, 57, 60), listOf(43, 47, 50)).let { it + it }

    @Test
    fun blackKeysStayAmbiguousBetweenTheSixCompatibleKeys() {
        val fs = listOf(54, 58, 61); val dsm = listOf(51, 54, 58)
        val e = play(listOf(fs, dsm, listOf(49, 54, 56), listOf(56, 61, 63), fs, listOf(51, 56, 58), dsm), holdMs = 1500)
        val key = assertNotNull(e.snapshot().key)
        val expected = setOf(Key(6, false), Key(1, false), Key(11, false), Key(3, true), Key(10, true), Key(8, true))
        assertEquals(expected, key.compatible)
        // 0,65 avant le terme tonique (v0.10), 0,75 ensuite ; v0.11 : le premier accord (F♯) compte pour la
        // tonique, comme le veut l'usage (un morceau commence le plus souvent sur sa tonique)
        assertTrue(key.ranking.first().probability < 0.9, "trop sûr de lui : ${key.ranking.first()}")
        assertTrue(key.ranking.take(3).any { it.key == Key(3, true) }, "D♯ mineur attendu parmi les 3 premières")
    }

    @Test
    fun melodyHelpsToChooseBetweenRelativeKeys() {
        val melody = listOf(76, 74, 72, 71, 68, 69, 71, 68, 69, 72, 71, 69) // insiste sur G♯
        val with = playWithMelody(popLoop, melody, AnalysisSettings()).snapshot().key!!
        val without = playWithMelody(popLoop, melody, AnalysisSettings(melodyWeightPercent = 0)).snapshot().key!!
        assertEquals(Key(9, true), with.ranking.first().key)
        // v0.11 : le premier accord (Am) compte déjà pour A mineur ; la mélodie renforce encore
        assertTrue(
            with.probabilityOf(Key(9, true)) > without.probabilityOf(Key(9, true)) + 0.05,
            "avec mélodie ${with.probabilityOf(Key(9, true))}, sans ${without.probabilityOf(Key(9, true))}",
        )
    }

    @Test
    fun popStyleAcceptsNaturalMinor() {
        val pop = play(popLoop, holdMs = 1500).snapshot().key!!
        val e = HarmonieEngine(AnalysisSettings(style = MusicStyle.CLASSICAL))
        var t = 0L
        for (c in popLoop) {
            c.forEach { e.noteOn(0, it, 90, t) }
            val end = t + 1500
            while (t < end) { t += 10; e.tick(t) }
            c.forEach { e.noteOff(0, it, t) }
        }
        val classical = e.snapshot().key!!
        assertTrue(pop.probabilityOf(Key(9, true)) > classical.probabilityOf(Key(9, true)) + 0.15)
    }

    private val cProgression = listOf(
        listOf(48, 52, 55), listOf(53, 57, 60), listOf(43, 47, 50, 53), listOf(48, 52, 55),
        listOf(45, 48, 52), listOf(50, 53, 57), listOf(43, 47, 50, 53), listOf(48, 52, 55),
    )

    @Test
    fun aChordThatClearlyLeavesTheKeyChangesItAtOnce() {
        val fSharp = listOf(54, 58, 61)
        val e = play(cProgression + listOf(fSharp))
        val key = e.snapshot().key!!
        assertTrue(key.current != cMajor, "C majeur ne doit plus être retenu après F♯")
        assertTrue(key.probabilityOf(cMajor) < 0.02, "C majeur à ${key.probabilityOf(cMajor)}")
        assertTrue(key.current in key.compatible, "la nouvelle tonalité doit contenir F♯")
        val last = e.snapshot().history.last()
        assertTrue(last.analysis?.key != cMajor, "F♯ doit être rangé dans la nouvelle tonalité")
    }

    @Test
    fun functionalChromaticChordsDoNotChangeTheKey() {
        val a7 = listOf(45, 49, 52, 55); val bb7 = listOf(46, 50, 53, 56); val db7 = listOf(49, 53, 56, 59)
        val fm = listOf(53, 56, 60)
        for (chord in listOf(a7, bb7, db7, fm)) {
            val e = play(cProgression + listOf(chord))
            assertEquals(cMajor, e.snapshot().key?.current, "tonalité perdue après ${chord}")
        }
    }

    @Test
    fun fastKeyChangeCanBeDisabled() {
        val e = HarmonieEngine(AnalysisSettings(fastKeyChange = false))
        var t = 0L
        for (c in cProgression + listOf(listOf(54, 58, 61))) {
            c.forEach { e.noteOn(0, it, 90, t) }
            val end = t + 900
            while (t < end) { t += 10; e.tick(t) }
            c.forEach { e.noteOff(0, it, t) }
        }
        assertEquals(cMajor, e.snapshot().key?.current)
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

class ContextFunctionTest {
    private fun chord(vararg n: Int) = ChordRecognizer.recognize(n.toList()).best!!
    private val cMinor = Key(0, true)
    private val cm = chord(48, 55, 60, 63)
    private val ab = chord(44, 56, 60, 63)
    private val eb = chord(51, 55, 58, 63)
    private val bb = chord(46, 58, 62, 65)
    private val g = chord(43, 55, 59, 62)

    private fun f(c: ChordCandidate, prev: ChordCandidate?, next: ChordCandidate?) =
        HarmonicAnalyzer.inContext(HarmonicAnalyzer.analyze(c, cMinor), c, prev, next).function

    @Test
    fun degreeNames() {
        assertEquals("sus-dominante", HarmonicAnalyzer.analyze(ab, cMinor).degreeName)
        assertEquals("médiante", HarmonicAnalyzer.analyze(eb, cMinor).degreeName)
        assertEquals("sous-tonique", HarmonicAnalyzer.analyze(bb, cMinor).degreeName)
        assertEquals("sensible", HarmonicAnalyzer.analyze(chord(47, 50, 53, 56), cMinor).degreeName)
    }

    @Test
    fun sixthAndSeventhDegreesDependOnWhatFollows() {
        assertEquals(HarmonicFunction.TONIC, f(ab, cm, eb)) // VI → III : tonique substitutive
        assertEquals(HarmonicFunction.SUBDOMINANT, f(ab, cm, g)) // VI → V : prépare la dominante
        assertEquals(HarmonicFunction.TONIC, f(ab, g, null)) // V → VI : cadence rompue
        assertEquals(HarmonicFunction.DOMINANT, f(bb, eb, cm)) // VII → I : cadence éolienne
        assertEquals(HarmonicFunction.DOMINANT, f(bb, cm, eb)) // VII → III : dominante du relatif
        assertEquals(HarmonicFunction.SUBDOMINANT, f(bb, cm, null)) // suite inconnue : table
    }

    @Test
    fun aeolianLoopInTheEngine() {
        val e = HarmonieEngine(AnalysisSettings(style = MusicStyle.POP_ROCK))
        var t = 0L
        repeat(3) {
            for (c in listOf(listOf(48, 60, 63, 67), listOf(44, 60, 63, 68), listOf(51, 58, 63, 67), listOf(46, 58, 62, 65))) {
                c.forEach { e.noteOn(0, it, 90, t) }
                val end = t + 1500
                while (t < end) { t += 10; e.tick(t) }
                c.forEach { e.noteOff(0, it, t) }
                t += 30; e.tick(t)
            }
        }
        val h = e.snapshot().history
        assertEquals(listOf("Im", "VI", "III", "VII"), h.take(4).map { it.analysis?.numeral })
        val T = HarmonicFunction.TONIC; val D = HarmonicFunction.DOMINANT
        assertEquals(listOf(T, T, T, D, T, T, T, D), h.take(8).map { it.analysis?.function })
        // Option désactivée : table fixe (VI et VII sous-dominante)
        assertEquals(HarmonicFunction.SUBDOMINANT, HarmonicAnalyzer.analyze(bb, cMinor).function)
    }
}

class SpellingTest {
    private fun ch(vararg n: Int) = ChordRecognizer.recognize(n.toList()).best!!
    private fun names(key: Key, vararg n: Int): String {
        val c = ch(*n)
        val sp = Spelling.forChord(c, HarmonicAnalyzer.analyze(c, key), key.spelling)
        return c.symbolIn(sp) + ":" + n.joinToString(" ") { Notes.name(it, sp) }
    }

    @Test
    fun leadingToneIsRaisedInMinor() {
        assertEquals("G♯", Notes.name(8, Key(9, true).spelling)) // la mineur
        assertEquals("C♯", Notes.name(1, Key(2, true).spelling)) // ré mineur
        assertEquals("F♯", Notes.name(6, Key(7, true).spelling)) // sol mineur
        assertEquals("B", Notes.name(11, Key(0, true).spelling)) // do mineur
        assertEquals("F♯♯", Notes.name(7, Key(8, true).spelling)) // sol♯ mineur : fa double dièse
        assertEquals("E", Notes.name(4, Key(9, true).spelling))
        assertEquals("D♯", Notes.name(3, Key(4, false).spelling)) // mi majeur
        assertEquals("B♭", Notes.name(10, Key(0, false).spelling)) // ♭7 en do majeur
        assertEquals("F♯", Notes.name(6, Key(0, false).spelling)) // ♯4 en do majeur
    }

    @Test
    fun chordsAreSpelledInThirds() {
        val c = Key(0, false); val f = Key(5, false); val cm = Key(0, true)
        assertEquals("E7:E G♯ B D", names(c, 52, 56, 59, 62))
        assertEquals("D7:D F♯ A C", names(f, 50, 54, 57, 60))
        assertEquals("E:E G♯ B", names(Key(9, true), 52, 56, 59))
        assertEquals("F♯dim7:F♯ A C E♭", names(cm, 54, 57, 60, 63))
        assertEquals("D♭7:D♭ F A♭ C♭", names(c, 49, 53, 56, 59)) // substitution tritonique
        assertEquals("G7:G B D F", names(cm, 55, 59, 62, 65))
        assertEquals("A♭:A♭ C E♭", names(cm, 56, 60, 63))
        assertEquals("C♭5", Notes.nameWithOctave(71, Spelling.forChord(ch(61, 65, 68, 71), null, Spelling.FLATS)))
    }

    @Test
    fun sevenNoteScalesUseSevenLetters() {
        val s = ScaleSuggestion(Scale.entries.first { it.intervals.size == 7 }, 6, "")
        val sp = s.spelling(Spelling.DEFAULT)
        assertEquals(7, s.pitchClasses.map { Notes.name(it, sp)[0] }.toSet().size)
    }
}

class ContextCasesTest {
    private fun ch(vararg n: Int) = ChordRecognizer.recognize(n.toList()).best!!
    private val cm = Key(0, true)
    private fun ctx(c: ChordCandidate, prev: ChordCandidate?, next: ChordCandidate?) =
        HarmonicAnalyzer.inContext(HarmonicAnalyzer.analyze(c, cm), c, prev, next)

    @Test
    fun picardyThirdAndSecondaryDominants() {
        val cMaj = ch(48, 52, 55); val g = ch(43, 47, 50); val fm = ch(41, 44, 48)
        assertEquals("I", ctx(cMaj, g, null).numeral) // fin de morceau : tierce picarde
        assertEquals("V/IV", ctx(cMaj, g, fm).numeral) // vers Fm : dominante de IV
        val bb7 = ch(46, 50, 53, 56); val eb = ch(51, 55, 58)
        val v = ctx(bb7, null, eb)
        assertEquals("V7/III", v.numeral)
        assertEquals(HarmonicFunction.DOMINANT, v.function)
        assertEquals("VII7", ctx(bb7, null, ch(48, 51, 55)).numeral) // B♭7 → Cm : reste VII7 (dominante substitutive)
        // Même fondamentale : la suite reste inconnue
        val ab = ch(44, 48, 51)
        assertEquals(HarmonicFunction.SUBDOMINANT, ctx(ab, null, ch(44, 48, 51, 55)).function)
    }

    @Test
    fun historyIsReanalysedWhenTheNotationChanges() {
        val e = HarmonieEngine(AnalysisSettings(style = MusicStyle.POP_ROCK))
        var t = 0L
        repeat(2) {
            for (c in listOf(listOf(48, 60, 63, 67), listOf(44, 60, 63, 68), listOf(51, 58, 63, 67), listOf(46, 58, 62, 65))) {
                c.forEach { e.noteOn(0, it, 90, t) }
                val end = t + 1500
                while (t < end) { t += 10; e.tick(t) }
                c.forEach { e.noteOff(0, it, t) }
                t += 30; e.tick(t)
            }
        }
        assertEquals("VI", e.snapshot().history[1].analysis?.numeral)
        e.updateSettings(e.settings.copy(degreeNotation = DegreeNotation.JAZZ), t)
        assertEquals("♭VI", e.snapshot().history[1].analysis?.numeral)
        assertEquals(HarmonicFunction.TONIC, e.snapshot().history[1].analysis?.function)
    }
}
