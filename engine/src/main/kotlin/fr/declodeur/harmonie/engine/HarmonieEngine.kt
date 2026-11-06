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

/** Modifie les réglages de tonalité de tous les styles (expériences du banc d'essai). */
fun AnalysisSettings.withAllKeyParams(f: (KeyParams) -> KeyParams): AnalysisSettings =
    copy(keyParams = MusicStyle.entries.associateWith { f(keyParams[it] ?: KeyParams.POP) })

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
)

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
)

/**
 * Moteur d'analyse, indépendant de la plateforme (Windows, Android).
 *
 * Les événements MIDI arrivent avec un horodatage en millisecondes ; [tick] doit être
 * appelé régulièrement (toutes les 5 à 10 ms) pour valider les accords quand les notes
 * sont restées stables pendant [AnalysisSettings.stabilityMs].
 *
 * Un nouvel accord n'est validé qu'après une nouvelle attaque : relâcher des notes ne
 * change pas l'accord affiché. Toutes les méthodes publiques sont synchronisées.
 */
class HarmonieEngine(
    settings: AnalysisSettings = AnalysisSettings(),
    /** Nombre d'accords gardés dans l'historique (le banc d'essai garde tout le morceau). */
    private val historyLimit: Int = MAX_HISTORY,
) {
    var settings: AnalysisSettings = settings
        private set

    private val tracker = NoteTracker()
    private var analysisSet: Set<Int> = emptySet()

    /** Notes de l'accord validé en dernier, et notes relâchées récemment (notes de passage). */
    private var committedNotes: Set<Int> = emptySet()

    /** Conduite des voix calculée au dernier accord validé. */
    private var voiceLeading: VoiceLeadingInfo? = null
    private val recentReleases = ArrayDeque<Pair<Int, Long>>()
    private var peakSet: Set<Int> = emptySet()
    private var lastChangeMs = 0L
    private var pending = false
    private var attackSinceCommit = false
    private var current: ChordResult? = null
    private val history = ArrayDeque<HistoryEntry>()
    private var version = 0L
    private val keyDetector = KeyDetector(settings.keyMemorySeconds * 1000L).apply {
        style = settings.style
        applyParams(settings.currentKeyParams)
        melodyWeight = settings.melodyWeightPercent / 100.0
    }

    /** Notes (canal, note) considérées comme mélodie pour la tonalité. */
    private val melodicNotes = HashSet<Pair<Int, Int>>()

    /** Dernières attaques sur les canaux d'accords (note, instant), pour reconnaître les arpèges. */
    private val recentAttacks = ArrayDeque<Pair<Int, Long>>()

    /** Attaques récentes sur les canaux d'accords, pas encore classées accord / note seule. */
    private val burst = ArrayList<Pair<Int, Int>>()
    private var burstLastMs = 0L
    private var keyEstimate: KeyEstimate? = null
    private var keySignature: String = EMPTY_KEY_SIGNATURE
    private var predictions: List<PredictionNode> = emptyList()
    private var lastKeyRefreshMs = Long.MIN_VALUE / 2

    @Synchronized
    fun noteOn(channel: Int, note: Int, velocity: Int, nowMs: Long) {
        tracker.noteOn(channel, note, velocity)
        if (velocity > 0) {
            melodicNotes.remove(channel to note)
            if (isChordChannel(channel)) {
                attackSinceCommit = true
                burst += channel to note
                burstLastMs = nowMs
                recentAttacks.addLast(note to nowMs)
                while (recentAttacks.size > MAX_RECENT_ATTACKS) recentAttacks.removeFirst()
            } else {
                melodicNotes += channel to note
                keyDetector.addAttack(note, melodic = true)
            }
        }
        onChange(nowMs)
    }

    @Synchronized
    fun noteOff(channel: Int, note: Int, nowMs: Long) {
        tracker.noteOff(channel, note)
        if (isChordChannel(channel)) {
            recentReleases.addLast(note to nowMs)
            while (recentReleases.size > 16 || (recentReleases.isNotEmpty() && nowMs - recentReleases.first().second > RELEASE_MEMORY_MS)) {
                recentReleases.removeFirst()
            }
        }
        onChange(nowMs)
    }

    @Synchronized
    fun controlChange(channel: Int, controller: Int, value: Int, nowMs: Long) {
        tracker.controlChange(channel, controller, value)
        onChange(nowMs)
    }

    @Synchronized
    fun isHeld(channel: Int, note: Int): Boolean = tracker.isHeld(channel, note)

    @Synchronized
    fun updateSettings(newSettings: AnalysisSettings, nowMs: Long) {
        settings = newSettings
        keyDetector.halfLifeMs = newSettings.keyMemorySeconds * 1000L
        keyDetector.style = newSettings.style
        keyDetector.melodyWeight = newSettings.melodyWeightPercent / 100.0
        keyDetector.applyParams(newSettings.currentKeyParams)
        attackSinceCommit = true
        peakSet = emptySet()
        analysisSet = emptySet()
        recomputePredictions()
        onChange(nowMs)
    }

    /** Relâche toutes les notes (« panique »). */
    @Synchronized
    fun releaseAll(nowMs: Long) {
        tracker.reset()
        onChange(nowMs)
    }

    /** Historique complet gardé par le moteur (jusqu'à `historyLimit` accords), pour le banc d'essai. */
    @Synchronized
    fun historyLog(): List<HistoryEntry> = history.toList()

    /** Efface l'historique et repart de zéro pour la tonalité (nouveau morceau). */
    @Synchronized
    fun clearHistory() {
        history.clear()
        previousKey = null
        current = null
        keyDetector.reset()
        recentAttacks.clear()
        recentReleases.clear()
        committedNotes = emptySet()
        voiceLeading = null
        melodicNotes.clear()
        burst.clear()
        keyEstimate = null
        keySignature = EMPTY_KEY_SIGNATURE
        predictions = emptyList()
        version++
    }

    /**
     * À appeler toutes les 5 à 10 ms : fait avancer la mémoire de tonalité, valide un accord
     * si les notes sont stables, rafraîchit la tonalité. Renvoie true si l'affichage a changé.
     */
    @Synchronized
    fun tick(nowMs: Long): Boolean {
        resolveBurst(nowMs)
        val (melodic, harmonic) = tracker.sounding()
            .partition { (it.channel to it.note) in melodicNotes || !isChordChannel(it.channel) }
        keyDetector.advance(nowMs, harmonic.map { it.note }, melodic.map { it.note })
        var changed = commitIfStable(nowMs)
        if (nowMs - lastKeyRefreshMs >= KEY_REFRESH_MS) {
            lastKeyRefreshMs = nowMs
            if (refreshKey()) changed = true
        }
        if (changed) version++
        return changed
    }

    /**
     * Classe les attaques récentes : en mode Complète, une note attaquée seule (sans autre
     * attaque dans le délai de stabilité) est considérée comme mélodie ; plusieurs notes
     * attaquées ensemble forment l'harmonie.
     */
    private fun resolveBurst(nowMs: Long) {
        if (burst.isEmpty() || nowMs - burstLastMs < settings.stabilityMs) return
        val melodic = settings.mode == AnalysisMode.FULL && burst.size == 1
        for ((ch, note) in burst) {
            keyDetector.addAttack(note, melodic)
            if (melodic && tracker.isHeld(ch, note)) melodicNotes += ch to note
        }
        burst.clear()
    }

    private fun commitIfStable(nowMs: Long): Boolean {
        if (!pending || nowMs - lastChangeMs < settings.stabilityMs) return false
        pending = false
        if (!attackSinceCommit) return false
        val set = analysisSet.ifEmpty { peakSet }
        if (set.isEmpty()) return false
        var result = ChordRecognizer.recognize(set, settings.slashMinGap, settings.bassOptions)
        val interp = settings.interpretation
        val key = keyEstimate?.current
        if (interp.passingTones && isPassingTone(set, nowMs)) {
            attackSinceCommit = false
            peakSet = emptySet()
            return false
        }
        if (result.pitchClasses.size < 3) {
            // Une ou deux notes de l'accord en cours ne le remplacent pas
            val cur = current?.best
            if (interp.keepChordOnSubset && cur != null && result.pitchClasses.isNotEmpty() &&
                cur.chordPitchClasses.containsAll(result.pitchClasses)
            ) {
                attackSinceCommit = false
                peakSet = emptySet()
                return false
            }
        }
        // Reconnaissance d'arpège seulement en secours, quand aucun accord n'est tenu : d'abord la
        // plus longue suite de notes formant un accord exact, sinon la fenêtre harmonique
        val holdsChord = result.best != null && result.pitchClasses.size >= 3
        if (!holdsChord && settings.arpeggioDetection) {
            val arp = arpeggio(nowMs)
            if (arp != null) result = arp
            else if (interp.harmonicWindowMs > 0 && result.pitchClasses.size < 3) windowChord(nowMs, set)?.let { result = it }
        }
        if (interp.completeDyads && result.pitchClasses.size == 2 &&
            (result.best == null || result.best?.type == ChordType.ONE_PLUS_FIVE)
        ) {
            result = ChordInterpreter.dyad(result, key, settings.style)
        }
        result = ChordInterpreter.interpret(result, key, settings.style, interp)
        if (result.best == null && result.pitchClasses.size < 2) return false
        attackSinceCommit = false
        peakSet = emptySet()
        val previousNotes = committedNotes
        committedNotes = set
        val previous = current?.best
        current = result
        val best = result.best
        voiceLeading = if (best != null && previous != null && previousNotes.isNotEmpty() && previousNotes != set) {
            Voicings.lead(previousNotes.toList(), previous, set.toList(), best, flagParallels = true, keyEstimate?.current?.spelling ?: Spelling.DEFAULT)
        } else {
            voiceLeading.takeIf { previousNotes == set }
        }
        if (best != null) {
            val key = keyEstimate?.current
            if (settings.fastKeyChange && key != null && HarmonicAnalyzer.breaksKey(best, key, settings.style)) {
                keyDetector.forget()
            }
            keyDetector.addChord(best, previous)
            keyDetector.chordSounding(best)
            if (settings.cadenceKeyChange && previous != null && previous.type.isDominant &&
                Notes.pc(previous.root - best.root) == 7 && (best.type.isMajorTriadBased || best.type.isMinorTriadBased) &&
                !best.type.isDominant
            ) {
                keyDetector.cadenceTo(Key(best.root, best.type.isMinorTriadBased))
            }
            refreshKey()
            val last = history.lastOrNull()
            if (last?.chord?.best?.symbol != best.symbol) {
                val lastBest = last?.chord?.best
                history.addLast(
                    HistoryEntry(
                        chord = result,
                        analysis = analyze(best),
                        resolution = lastBest?.let { ChordRelations.resolution(it, last.analysis?.function, best) },
                        closesTwoFive = lastBest?.let { ChordRelations.isTwoFive(it, best) } ?: false,
                        timeMs = nowMs,
                        notes = result.notes,
                    ),
                )
                while (history.size > historyLimit) history.removeFirst()
            }
        }
        recomputePredictions()
        return true
    }

    /**
     * Note de passage ou broderie : une seule note nouvelle depuis l'accord validé, étrangère à
     * l'accord en cours, atteinte par degré conjoint (1 ou 2 demi-tons) depuis une note qui vient
     * d'être relâchée, et toutes les autres notes appartiennent encore à l'accord.
     */
    private fun isPassingTone(set: Set<Int>, nowMs: Long): Boolean {
        val cur = current?.best ?: return false
        if (committedNotes.isEmpty()) return false
        val added = set - committedNotes
        if (added.size != 1) return false
        val removed = committedNotes - set
        if (removed.size > 1) return false
        val n = added.first()
        if (Notes.pc(n) in cur.chordPitchClasses) return false
        val others = (set - n).map { Notes.pc(it) }.toSet()
        if (others.size < 2 || !cur.chordPitchClasses.containsAll(others)) return false
        val sources = removed + recentReleases.filter { nowMs - it.second <= RELEASE_MEMORY_MS }.map { it.first }
        return sources.any { kotlin.math.abs(it - n) in 1..2 }
    }

    /** Recalcule l'arbre des suites probables d'après l'historique et la tonalité retenue. */
    private fun recomputePredictions() {
        val key = keyEstimate?.current
        val cur = current
        if (key == null || cur?.best == null) {
            predictions = emptyList()
            return
        }
        val degrees = history.mapNotNull { it.chord.best?.let { b -> Predictor.degreeOf(b, key) } }
        // Notes de départ pour la conduite des voix : l'accord joué, sans une basse détachée
        val notes = cur.notes.let { n -> if (n.size >= 4 && n[1] - n[0] >= 7) n.drop(1) else n }
        predictions = Predictor.tree(degrees, key, settings.style, settings.prediction, notes)
    }

    /**
     * Cherche un accord arpégé dans les dernières notes jouées : on remonte note par note
     * (au plus [AnalysisSettings.arpeggioMaxNotes] notes et [AnalysisSettings.arpeggioWindowMs] ms)
     * et l'on garde la plus longue suite qui forme exactement un accord d'au moins 3 notes.
     */
    private fun arpeggio(nowMs: Long): ChordResult? {
        val recent = recentAttacks.filter { nowMs - it.second <= settings.arpeggioWindowMs }
            .takeLast(settings.arpeggioMaxNotes)
            .map { it.first }
        if (recent.size < 3) return null
        var found: ChordResult? = null
        for (k in 3..recent.size) {
            val notes = recent.takeLast(k)
            val r = ChordRecognizer.recognize(notes, settings.slashMinGap, settings.bassOptions)
            val b = r.best
            val ok = b != null && b.isExact && r.pitchClasses.size >= 3 &&
                b.type != ChordType.ONE_PLUS_FIVE && b.type != ChordType.ONE_PLUS_EIGHT
            if (ok) found = r.copy(arpeggiated = true) else if (found != null) break
        }
        return found
    }

    /**
     * Fenêtre harmonique : notes tenues + notes attaquées dans les [InterpretationSettings.harmonicWindowMs]
     * dernières ms. Gardée si elle forme un accord d'au moins trois notes (exact, ou sans sa quinte).
     */
    private fun windowChord(nowMs: Long, held: Set<Int>): ChordResult? {
        val window = settings.interpretation.harmonicWindowMs
        val notes = held + recentAttacks.filter { nowMs - it.second <= window }.map { it.first }
        val r = ChordRecognizer.recognize(notes, settings.slashMinGap, settings.bassOptions)
        val b = r.best ?: return null
        if (r.pitchClasses.size < 3 || b.extraNotes.isNotEmpty()) return null
        if (b.type == ChordType.ONE_PLUS_FIVE || b.type == ChordType.ONE_PLUS_EIGHT) return null
        return r.copy(arpeggiated = true)
    }

    /** Recalcule la tonalité ; renvoie true si ce qui est affiché a changé. */
    private fun refreshKey(): Boolean {
        val estimate = keyDetector.estimate()
        val signature = estimate.current?.name + "|" + estimate.ranking.take(3)
            .joinToString { it.key.name + ":" + (it.probability * 100).toInt() }
        keyEstimate = if (estimate.ranking.isEmpty()) keyEstimate else estimate
        if (signature == keySignature) return false
        val keyChanged = keySignature.substringBefore('|') != signature.substringBefore('|')
        keySignature = signature
        val newKey = estimate.current
        if (keyChanged && newKey != null && previousKey != null && previousKey != newKey) reanalyze(previousKey!!, newKey)
        previousKey = newKey ?: previousKey
        if (keyChanged) recomputePredictions()
        return true
    }

    private var previousKey: Key? = null

    /**
     * Réanalyse a posteriori : la tonalité vient de passer de [old] à [new]. On remonte l'historique
     * (au plus [AnalysisSettings.reanalysisDepth] accords, dans l'ancienne tonalité) tant que les
     * accords appartiennent aussi à la nouvelle tonalité : la modulation a commencé là. Ces accords
     * sont rechiffrés dans la nouvelle tonalité ; le premier est l'accord pivot.
     */
    private fun reanalyze(old: Key, new: Key) {
        val depth = settings.reanalysisDepth
        if (depth <= 0 || history.isEmpty()) return
        var first = history.size
        var i = history.size - 1
        while (i >= 0 && history.size - i <= depth) {
            val e = history[i]
            val chord = e.chord.best ?: break
            if (e.analysis?.key != old) break
            val inNew = HarmonicAnalyzer.analyze(chord, new, settings.style)
            if (inNew.category != HarmonicCategory.DIATONIC) break
            first = i
            i--
        }
        for (j in first until history.size) {
            val e = history[j]
            val chord = e.chord.best ?: continue
            history[j] = e.copy(
                analysis = HarmonicAnalyzer.analyze(chord, new, settings.style),
                reanalyzed = true,
                pivotFrom = if (j == first) e.analysis else null,
            )
        }
    }

    private fun analyze(chord: ChordCandidate): HarmonicAnalysis? =
        keyEstimate?.current?.let { HarmonicAnalyzer.analyze(chord, it, settings.style) }

    @Synchronized
    fun snapshot(): EngineSnapshot {
        val best = current?.best
        val currentAnalysis = best?.let { analyze(it) }
        val scale = best?.let { ChordScales.suggest(it, currentAnalysis, keyEstimate?.current, settings.style) }
        val notes = tracker.sounding().map { n ->
            val melody = !isChordChannel(n.channel)
            val solo = !melody && (n.channel to n.note) in melodicNotes
            SoundingNote(
                note = n.note,
                channel = n.channel,
                velocity = n.velocity,
                sustained = n.sustained,
                melody = melody,
                analysed = !melody && (settings.includeSustain || !n.sustained),
                solo = solo,
                melodyRole = if ((melody || solo) && scale != null) ChordScales.classify(n.note, scale) else null,
            )
        }
        return EngineSnapshot(
            version, notes, current, currentAnalysis, keyEstimate, history.takeLast(MAX_HISTORY), predictions, scale,
            voicing = if (settings.voicing.showVoicing) best?.let { Voicings.describe(committedNotes.toList(), it, keyEstimate?.current) } else null,
            voiceLeading = if (settings.voicing.showVoiceLeading) voiceLeading?.let { vl ->
                val v = settings.voicing
                val parallels = v.flagParallels && (v.parallelsInAllStyles || settings.style == MusicStyle.CLASSICAL)
                if (parallels) vl else vl.copy(parallels = emptyList())
            } else null,
        )
    }

    private fun isChordChannel(channel: Int): Boolean =
        settings.mode == AnalysisMode.FULL || channel == settings.chordChannel

    private fun computeAnalysisSet(): Set<Int> =
        tracker.sounding()
            .filter { isChordChannel(it.channel) && (settings.includeSustain || !it.sustained) }
            .map { it.note }
            .toSet()

    private fun onChange(nowMs: Long) {
        version++
        val set = computeAnalysisSet()
        if (set != analysisSet) {
            analysisSet = set
            lastChangeMs = nowMs
            pending = true
            if (attackSinceCommit && set.size >= peakSet.size) peakSet = set
        }
    }

    companion object {
        const val MAX_HISTORY = 32
        const val RELEASE_MEMORY_MS = 600L
        const val KEY_REFRESH_MS = 250L
        private const val MAX_RECENT_ATTACKS = 32
        private const val EMPTY_KEY_SIGNATURE = "null|"
    }
}
