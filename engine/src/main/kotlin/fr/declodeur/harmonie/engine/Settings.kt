package fr.declodeur.harmonie.engine

/** Style musical choisi : il oriente l'analyse harmonique (et plus tard les prédictions). */
enum class MusicStyle(val label: String) {
    POP_ROCK("Pop/rock"),
    JAZZ("Jazz"),
    BLUES("Blues"),
    CLASSICAL("Classique"),
}

enum class AnalysisMode(val label: String) {
    /** Toutes les notes de tous les canaux servent à reconnaître l'accord. */
    FULL("Complète"),

    /** Seul le canal des accords est analysé ; les autres canaux sont la mélodie. */
    CHORDS_AND_MELODY("Accords + mélodie"),
}

/**
 * Tous les réglages du moteur. Un réglage ajouté ici doit l'être aussi dans [SettingsCodec] (sinon il
 * n'est pas sauvegardé) ; le test de [SettingsCodec] le rappelle.
 */
data class AnalysisSettings(
    val style: MusicStyle = MusicStyle.POP_ROCK,
    val mode: AnalysisMode = AnalysisMode.FULL,
    /** Canal des accords (0 à 15) en mode [AnalysisMode.CHORDS_AND_MELODY]. */
    val chordChannel: Int = 0,
    /** Durée de stabilité avant de valider un accord, en millisecondes. */
    val stabilityMs: Int = 60,
    /** Les notes tenues par la pédale de sustain font partie de l'accord. */
    val includeSustain: Boolean = true,
    /**
     * Écart minimal (demi-tons) entre la basse et la note suivante pour noter un accord
     * sur basse (« Cm/G »). En dessous, le renversement serré est noté « Cm ». 0 = toujours.
     */
    val slashMinGap: Int = 7,
    /** Exceptions à la règle de la basse détachée. */
    val bassOptions: BassReadingOptions = BassReadingOptions(),
    /** Mémoire de la tonalité : demi-vie, en secondes, du poids des notes passées. */
    val keyMemorySeconds: Int = 4,
    /** Une cadence parfaite (V7 → I) vers une tonalité plausible la fait retenir tout de suite. */
    val cadenceKeyChange: Boolean = false,
    /** Modèle de tonalité, réglé séparément pour chaque style (la notion de tonalité n'est pas la même). */
    val keyParams: Map<MusicStyle, KeyParams> = KeyParams.DEFAULTS,
    /** Poids de la mélodie par rapport à l'harmonie pour la tonalité, en % (0 à 100). */
    val melodyWeightPercent: Int = 70,
    /** Changement de tonalité immédiat quand un accord sort franchement de la tonalité. */
    val fastKeyChange: Boolean = true,
    /** Reconnaître les accords arpégés quand aucun accord n'est tenu. */
    val arpeggioDetection: Boolean = true,
    /** Arpèges : on remonte au plus ce nombre de millisecondes en arrière. */
    val arpeggioWindowMs: Int = 2000,
    /** Arpèges : on remonte au plus ce nombre de notes en arrière. */
    val arpeggioMaxNotes: Int = 6,
    /** Prédictions : forme de l'arbre et taille du contexte. */
    val prediction: PredictionSettings = PredictionSettings(),
    /**
     * Réanalyse a posteriori : à un changement de tonalité, nombre maximal d'accords de l'historique
     * rechiffrés dans la nouvelle tonalité (depuis l'accord pivot). 0 = désactivée.
     */
    val reanalysisDepth: Int = 4,
    /** Règles d'interprétation contextuelle (issues du banc d'essai). */
    val interpretation: InterpretationSettings = InterpretationSettings(),
    /** Affichage : repérer la gamme de l'accord courant sur le grand clavier. */
    val scaleOnKeyboard: Boolean = true,
    /** Affichage : voicing et conduite des voix. */
    val voicing: VoicingSettings = VoicingSettings(),
    /** Chiffrage : degrés en notation classique (VI, VII en mineur) ou jazz (♭VI, ♭VII). */
    val degreeNotation: DegreeNotation = DegreeNotation.CLASSICAL,
    /** Fonction de VI et VII d'après l'accord suivant (sinon : table fixe des degrés). */
    val contextualFunctions: Boolean = true,
)

/**
 * Réglages du modèle de tonalité pour un style (valeurs mises au point sur le banc d'essai).
 * [memoryChords] : demi-vie en nombre de changements d'accord (0 = en secondes, [AnalysisSettings.keyMemorySeconds]) ;
 * [tonicPercent] : poids du temps passé sur l'accord de tonique ; [dominantPercent] : part de ce poids
 * accordée à la dominante de la tonalité ; [inertiaPercent] : hystérésis de la tonalité retenue.
 */
data class KeyParams(
    val memoryChords: Int,
    val tonicPercent: Int,
    val dominantPercent: Int,
    val inertiaPercent: Int,
    /** Bonus de la tonalité retenue tant que tout ce qu'on joue reste dans sa gamme (0 = désactivé). */
    val holdPercent: Int = 0,
    /** Poids des arrivées sur un accord (V → I, ♭VII → I, IV → I) comme indice de tonique (0 = désactivé). */
    val arrivalPercent: Int = 0,
    /** Poids du premier accord du morceau comme indice de tonique (0 = désactivé). */
    val primacyPercent: Int = 0,
) {
    companion object {
        /** Classique : modulations fréquentes, mémoire courte. Pop : tonique stable, mémoire longue. */
        val CLASSICAL = KeyParams(
            memoryChords = 4, tonicPercent = 30, dominantPercent = 30, inertiaPercent = 3, holdPercent = 15,
        )
        val POP = KeyParams(
            memoryChords = 12, tonicPercent = 60, dominantPercent = 0, inertiaPercent = 3, holdPercent = 15,
            arrivalPercent = 20, primacyPercent = 20,
        )

        /** Jazz et blues : pas encore de corpus dans le banc d'essai, réglages de la pop. */
        val DEFAULTS: Map<MusicStyle, KeyParams> = mapOf(
            MusicStyle.CLASSICAL to CLASSICAL,
            MusicStyle.POP_ROCK to POP,
            MusicStyle.JAZZ to POP,
            MusicStyle.BLUES to POP,
        )

        /** Modèle d'avant le banc d'essai (mémoire en secondes, sans terme tonique). */
        val LEGACY = KeyParams(memoryChords = 0, tonicPercent = 0, dominantPercent = 0, inertiaPercent = 3)
    }
}

/** Réglages de tonalité du style choisi. */
val AnalysisSettings.currentKeyParams: KeyParams get() = keyParams[style] ?: KeyParams.POP

/** Modifie les réglages de tonalité du style choisi. */
fun AnalysisSettings.withKeyParams(f: (KeyParams) -> KeyParams): AnalysisSettings =
    copy(keyParams = keyParams + (style to f(currentKeyParams)))

/** Modifie les réglages de tonalité d'un style donné (pas forcément le style choisi). */
fun AnalysisSettings.withKeyParamsFor(style: MusicStyle, f: (KeyParams) -> KeyParams): AnalysisSettings =
    copy(keyParams = keyParams + (style to f(keyParams[style] ?: KeyParams.POP)))

/** Modifie les réglages de tonalité de tous les styles (expériences du banc d'essai). */
fun AnalysisSettings.withAllKeyParams(f: (KeyParams) -> KeyParams): AnalysisSettings =
    copy(keyParams = MusicStyle.entries.associateWith { f(keyParams[it] ?: KeyParams.POP) })
