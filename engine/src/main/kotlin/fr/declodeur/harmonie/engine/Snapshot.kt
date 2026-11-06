package fr.declodeur.harmonie.engine

/** Une note qui sonne, telle que l'interface doit l'afficher. */
data class SoundingNote(
    val note: Int,
    val channel: Int,
    val velocity: Int,
    /** Tenue par la pédale (touche déjà relâchée). */
    val sustained: Boolean,
    /** Considérée comme mélodie (mode accords + mélodie, autre canal). */
    val melody: Boolean,
    /** Prise en compte dans la reconnaissance d'accord. */
    val analysed: Boolean,
    /** Jouée seule (mode Complète) : traitée comme une note de mélodie pour la tonalité et la gamme. */
    val solo: Boolean = false,
    /** Rôle de la note de mélodie sur l'accord courant (notes de mélodie et notes seules uniquement). */
    val melodyRole: MelodyRole? = null,
)

/** Type de résolution entre un accord et le précédent (notation d'analyse jazz). */
enum class Resolution {
    /** La fondamentale descend d'une quinte depuis une dominante (V → I, V7/II → IIm) : flèche pleine. */
    FIFTH,

    /** Une dominante descend d'un demi-ton (SubV → I) : flèche pointillée. */
    HALF_STEP,
}

/** Un accord de l'historique, avec son analyse dans la tonalité du moment. */
data class HistoryEntry(
    val chord: ChordResult,
    val analysis: HarmonicAnalysis?,
    /** Résolution depuis l'accord précédent, s'il y en a une. */
    val resolution: Resolution? = null,
    /** Cet accord est le V d'un II-V dont le II est l'accord précédent (crochet). */
    val closesTwoFive: Boolean = false,
    /** Instant de l'accord (ms, horloge du moteur). */
    val timeMs: Long = 0L,
    /** Notes MIDI de l'accord (pour la conduite des voix). */
    val notes: List<Int> = emptyList(),
    /** Rechiffré après coup dans la tonalité qui a suivi (réanalyse a posteriori). */
    val reanalyzed: Boolean = false,
    /** Accord pivot de la modulation : son chiffrage dans l'ancienne tonalité ([pivotFrom]). */
    val pivotFrom: HarmonicAnalysis? = null,
) {
    /** Orthographe de l'accord dans sa tonalité. */
    val spelling: Spelling get() = Spelling.forChord(chord.best, analysis, analysis?.key?.spelling ?: Spelling.DEFAULT)
}

/** Relations harmoniques entre deux accords consécutifs. */
object ChordRelations {
    fun resolution(previous: ChordCandidate, previousFunction: HarmonicFunction?, chord: ChordCandidate): Resolution? {
        val fall = Notes.pc(previous.root - chord.root)
        val dominantLike = previous.type.isDominant ||
            (previous.type.isMajorTriadBased && previousFunction == HarmonicFunction.DOMINANT)
        return when {
            fall == 7 && dominantLike -> Resolution.FIFTH
            fall == 1 && previous.type.isDominant -> Resolution.HALF_STEP
            else -> null
        }
    }

    /** II-V : accord mineur (ou demi-diminué) suivi d'une dominante une quarte plus haut. */
    fun isTwoFive(previous: ChordCandidate, chord: ChordCandidate): Boolean =
        (previous.type.isMinorTriadBased || previous.type == ChordType.MINOR7_FLAT5) &&
            chord.type.isDominant && Notes.pc(chord.root - previous.root) == 5
}

data class EngineSnapshot(
    val version: Long,
    val notes: List<SoundingNote>,
    /** Dernier accord validé (reste affiché quand on relâche les notes). */
    val current: ChordResult?,
    /** Analyse de l'accord courant dans la tonalité retenue, si elle est connue. */
    val currentAnalysis: HarmonicAnalysis?,
    /** Tonalité probable, null tant qu'il n'y a pas assez de notes. */
    val key: KeyEstimate?,
    /** Accords successifs, du plus ancien au plus récent. */
    val history: List<HistoryEntry>,
    /** Arbre des suites probables (vide tant que la tonalité est inconnue). */
    val predictions: List<PredictionNode> = emptyList(),
    /** Gamme de l'accord courant (et variantes), avec notes à éviter et tensions. */
    val currentScale: ChordScaleResult? = null,
    /** Disposition de l'accord courant (serrée, ouverte, drop 2…). */
    val voicing: VoicingInfo? = null,
    /** Conduite des voix depuis l'accord précédent. */
    val voiceLeading: VoiceLeadingInfo? = null,
    /** Orthographe de l'accord courant (tonalité, notes de l'accord écrites par tierces). */
    val spelling: Spelling = Spelling.DEFAULT,
    /** Instant du dernier tick (ms, horloge du moteur) : durée de l'accord en cours. */
    val nowMs: Long = 0L,
    /** Rythme harmonique des derniers accords (null tant qu'il y en a trop peu). */
    val rhythm: RhythmInfo? = null,
)
