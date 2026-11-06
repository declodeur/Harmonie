package fr.declodeur.harmonie.engine

/**
 * Enregistrement des réglages : une seule table décrit chaque réglage (clé, lecture, écriture, bornes),
 * utilisée dans les deux sens. Un réglage ajouté à la table est donc toujours sauvegardé et relu ;
 * le test « écrire puis relire » le vérifie. Indépendant de la plateforme : Windows (Preferences
 * Java) et plus tard Android (SharedPreferences) stockent les mêmes clés, en texte.
 *
 * Les clés sont celles des versions précédentes : les réglages déjà enregistrés sont conservés.
 */
object SettingsCodec {
    private class Field(
        val key: String,
        val write: (AnalysisSettings) -> String,
        /** Applique la valeur lue ; null si elle est illisible (le réglage garde alors sa valeur). */
        val read: (AnalysisSettings, String) -> AnalysisSettings?,
    )

    private fun bool(key: String, get: (AnalysisSettings) -> Boolean, set: AnalysisSettings.(Boolean) -> AnalysisSettings) =
        Field(key, { get(it).toString() }, { s, v -> v.toBooleanStrictOrNull()?.let { s.set(it) } })

    private fun int(key: String, range: IntRange, get: (AnalysisSettings) -> Int, set: AnalysisSettings.(Int) -> AnalysisSettings) =
        Field(key, { get(it).toString() }, { s, v -> v.toIntOrNull()?.let { s.set(it.coerceIn(range)) } })

    private inline fun <reified E : Enum<E>> enum(
        key: String,
        crossinline get: (AnalysisSettings) -> E,
        crossinline set: AnalysisSettings.(E) -> AnalysisSettings,
    ) = Field(key, { get(it).name }, { s, v -> enumValues<E>().firstOrNull { it.name == v }?.let { s.set(it) } })

    private fun AnalysisSettings.bass(f: BassReadingOptions.() -> BassReadingOptions) = copy(bassOptions = bassOptions.f())
    private fun AnalysisSettings.interp(f: InterpretationSettings.() -> InterpretationSettings) = copy(interpretation = interpretation.f())
    private fun AnalysisSettings.pred(f: PredictionSettings.() -> PredictionSettings) = copy(prediction = prediction.f())
    private fun AnalysisSettings.voice(f: VoicingSettings.() -> VoicingSettings) = copy(voicing = voicing.f())
    private fun AnalysisSettings.keyOf(style: MusicStyle, f: KeyParams.() -> KeyParams) =
        copy(keyParams = keyParams + (style to (keyParams[style] ?: KeyParams.POP).f()))

    private fun keyFields(style: MusicStyle): List<Field> {
        val p = "keyParams3.${style.name}."
        fun kp(s: AnalysisSettings) = s.keyParams[style] ?: KeyParams.POP
        return listOf(
            int(p + "memoryChords", 0..24, { kp(it).memoryChords }) { v -> keyOf(style) { copy(memoryChords = v) } },
            int(p + "tonic", 0..100, { kp(it).tonicPercent }) { v -> keyOf(style) { copy(tonicPercent = v) } },
            int(p + "dominant", 0..100, { kp(it).dominantPercent }) { v -> keyOf(style) { copy(dominantPercent = v) } },
            int(p + "inertia", 0..10, { kp(it).inertiaPercent }) { v -> keyOf(style) { copy(inertiaPercent = v) } },
            int(p + "hold", 0..60, { kp(it).holdPercent }) { v -> keyOf(style) { copy(holdPercent = v) } },
            int(p + "arrival", 0..100, { kp(it).arrivalPercent }) { v -> keyOf(style) { copy(arrivalPercent = v) } },
            int(p + "primacy", 0..100, { kp(it).primacyPercent }) { v -> keyOf(style) { copy(primacyPercent = v) } },
        )
    }

    private val FIELDS: List<Field> = listOf(
        enum("style", { it.style }) { copy(style = it) },
        enum("mode", { it.mode }) { copy(mode = it) },
        int("chordChannel", 0..15, { it.chordChannel }) { copy(chordChannel = it) },
        int("stabilityMs", 20..200, { it.stabilityMs }) { copy(stabilityMs = it) },
        bool("includeSustain", { it.includeSustain }) { copy(includeSustain = it) },
        int("slashMinGap", 0..24, { it.slashMinGap }) { copy(slashMinGap = it) },
        bool("keepRootPositionReading", { it.bassOptions.keepRootPositionReading }) { v -> bass { copy(keepRootPositionReading = v) } },
        bool("keepFullReadingWhenUpperIncomplete", { it.bassOptions.keepFullReadingWhenUpperIncomplete }) { v ->
            bass { copy(keepFullReadingWhenUpperIncomplete = v) }
        },
        bool("keepSeventhInBass", { it.bassOptions.keepSeventhInBass }) { v -> bass { copy(keepSeventhInBass = v) } },
        // Clé changée en v0.9 : la valeur par défaut est passée de 12 à 4 s d'après le banc d'essai
        int("keyMemorySeconds2", 2..60, { it.keyMemorySeconds }) { copy(keyMemorySeconds = it) },
        bool("cadenceKeyChange", { it.cadenceKeyChange }) { copy(cadenceKeyChange = it) },
        int("melodyWeightPercent", 0..100, { it.melodyWeightPercent }) { copy(melodyWeightPercent = it) },
        bool("fastKeyChange", { it.fastKeyChange }) { copy(fastKeyChange = it) },
        bool("arpeggioDetection", { it.arpeggioDetection }) { copy(arpeggioDetection = it) },
        int("arpeggioWindowMs", 500..5000, { it.arpeggioWindowMs }) { copy(arpeggioWindowMs = it) },
        int("arpeggioMaxNotes", 3..12, { it.arpeggioMaxNotes }) { copy(arpeggioMaxNotes = it) },
        int("reanalysisDepth", 0..16, { it.reanalysisDepth }) { copy(reanalysisDepth = it) },
        bool("interpSixthAsSeventh", { it.interpretation.sixthAsSeventh }) { v -> interp { copy(sixthAsSeventh = v) } },
        bool("interpDiminishedByKey", { it.interpretation.diminishedByKey }) { v -> interp { copy(diminishedByKey = v) } },
        bool("interpSuspensions", { it.interpretation.suspensions }) { v -> interp { copy(suspensions = v) } },
        bool("interpKeepChordOnSubset", { it.interpretation.keepChordOnSubset }) { v -> interp { copy(keepChordOnSubset = v) } },
        bool("interpCompleteDyads", { it.interpretation.completeDyads }) { v -> interp { copy(completeDyads = v) } },
        int("interpHarmonicWindowMs", 0..1500, { it.interpretation.harmonicWindowMs }) { v -> interp { copy(harmonicWindowMs = v) } },
        bool("interpPassingTones", { it.interpretation.passingTones }) { v -> interp { copy(passingTones = v) } },
        bool("scaleOnKeyboard", { it.scaleOnKeyboard }) { copy(scaleOnKeyboard = it) },
        enum("degreeNotation", { it.degreeNotation }) { copy(degreeNotation = it) },
        bool("contextualFunctions", { it.contextualFunctions }) { copy(contextualFunctions = it) },
        bool("showTension", { it.showTension }) { copy(showTension = it) },
        bool("showDurations", { it.showDurations }) { copy(showDurations = it) },
        int("circlePathLength", 0..12, { it.circlePathLength }) { copy(circlePathLength = it) },
        bool("voicingShow", { it.voicing.showVoicing }) { v -> voice { copy(showVoicing = v) } },
        bool("voicingLeading", { it.voicing.showVoiceLeading }) { v -> voice { copy(showVoiceLeading = v) } },
        bool("voicingParallels", { it.voicing.flagParallels }) { v -> voice { copy(flagParallels = v) } },
        bool("voicingParallelsAllStyles", { it.voicing.parallelsInAllStyles }) { v -> voice { copy(parallelsInAllStyles = v) } },
        int("predictionContext", 1..4, { it.prediction.contextSize }) { v -> pred { copy(contextSize = v) } },
        int("predictionLevel1", 1..6, { it.prediction.level1 }) { v -> pred { copy(level1 = v) } },
        int("predictionLevel2", 0..4, { it.prediction.level2 }) { v -> pred { copy(level2 = v) } },
        int("predictionLevel3", 0..3, { it.prediction.level3 }) { v -> pred { copy(level3 = v) } },
        bool("predictionMiniKeyboards", { it.prediction.showMiniKeyboards }) { v -> pred { copy(showMiniKeyboards = v) } },
        int("predictionExpandThreshold", 0..50, { it.prediction.expandThresholdPercent }) { v -> pred { copy(expandThresholdPercent = v) } },
        bool("predictionCorpus", { it.prediction.source == PredictionSource.CORPUS }) { v ->
            pred { copy(source = if (v) PredictionSource.CORPUS else PredictionSource.THEORY) }
        },
    ) + MusicStyle.entries.flatMap { keyFields(it) }

    /** Toutes les clés gérées (pour les tests et le nettoyage). */
    val keys: List<String> get() = FIELDS.map { it.key }

    fun encode(settings: AnalysisSettings): Map<String, String> = FIELDS.associate { it.key to it.write(settings) }

    /** Relit les réglages ; une clé absente ou illisible garde la valeur par défaut. */
    fun decode(read: (String) -> String?, defaults: AnalysisSettings = AnalysisSettings()): AnalysisSettings =
        FIELDS.fold(defaults) { s, f -> read(f.key)?.let { f.read(s, it) } ?: s }
}
