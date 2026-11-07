package fr.declodeur.harmonie.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsCodecTest {
    /** Tous les réglages changés par rapport aux valeurs par défaut. */
    private val changed = AnalysisSettings(
        style = MusicStyle.JAZZ, mode = AnalysisMode.CHORDS_AND_MELODY, chordChannel = 3, stabilityMs = 90,
        includeSustain = false, slashMinGap = 5,
        bassOptions = BassReadingOptions(keepRootPositionReading = false, keepFullReadingWhenUpperIncomplete = false, keepSeventhInBass = false),
        keyMemorySeconds = 9, cadenceKeyChange = true,
        keyParams = MusicStyle.entries.associateWith { KeyParams(7, 11, 13, 5, 17, 19, 23) },
        melodyWeightPercent = 40, fastKeyChange = false, arpeggioDetection = false, arpeggioWindowMs = 1500, arpeggioMaxNotes = 9,
        prediction = PredictionSettings(contextSize = 3, level1 = 5, level2 = 3, level3 = 1, showMiniKeyboards = true,
            expandThresholdPercent = 25, source = PredictionSource.THEORY),
        reanalysisDepth = 9,
        interpretation = InterpretationSettings(false, false, false, false, false, 700, true),
        scaleOnKeyboard = false,
        voicing = VoicingSettings(false, false, false, true),
        degreeNotation = DegreeNotation.JAZZ, contextualFunctions = false,
        showTension = false, showDurations = false, circlePathLength = 3,
        showCirclePath = false, showScaleLines = false, tensionModel = TensionModel.SIMPLE, recomputeOnRelease = false,
    )

    @Test
    fun writeThenReadGivesTheSameSettings() {
        assertEquals(changed, SettingsCodec.decode(SettingsCodec.encode(changed)::get))
        assertEquals(AnalysisSettings(), SettingsCodec.decode(SettingsCodec.encode(AnalysisSettings())::get))
    }

    @Test
    fun missingOrInvalidValuesKeepTheDefault() {
        val d = AnalysisSettings()
        assertEquals(d, SettingsCodec.decode({ null }))
        val s = SettingsCodec.decode(mapOf("stabilityMs" to "abc", "style" to "INCONNU", "slashMinGap" to "99")::get)
        assertEquals(d.stabilityMs, s.stabilityMs)
        assertEquals(d.style, s.style)
        assertEquals(24, s.slashMinGap) // borné
    }

    @Test
    fun keysOfPreviousVersionsAreKept() {
        val keys = SettingsCodec.keys
        for (k in listOf("keyMemorySeconds2", "interpSixthAsSeventh", "predictionCorpus", "keyParams3.CLASSICAL.tonic", "degreeNotation")) {
            kotlin.test.assertTrue(k in keys, k)
        }
        assertEquals(keys.size, keys.toSet().size, "clé en double")
    }
}
