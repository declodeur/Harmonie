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
    val keyMemorySeconds: Int = 12,
)

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
)

/** Un accord de l'historique, avec son analyse dans la tonalité du moment. */
data class HistoryEntry(
    val chord: ChordResult,
    val analysis: HarmonicAnalysis?,
)

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
class HarmonieEngine(settings: AnalysisSettings = AnalysisSettings()) {
    var settings: AnalysisSettings = settings
        private set

    private val tracker = NoteTracker()
    private var analysisSet: Set<Int> = emptySet()
    private var peakSet: Set<Int> = emptySet()
    private var lastChangeMs = 0L
    private var pending = false
    private var attackSinceCommit = false
    private var current: ChordResult? = null
    private val history = ArrayDeque<HistoryEntry>()
    private var version = 0L
    private val keyDetector = KeyDetector(settings.keyMemorySeconds * 1000L)
    private var keyEstimate: KeyEstimate? = null
    private var keySignature: String = EMPTY_KEY_SIGNATURE
    private var lastKeyRefreshMs = Long.MIN_VALUE / 2

    @Synchronized
    fun noteOn(channel: Int, note: Int, velocity: Int, nowMs: Long) {
        tracker.noteOn(channel, note, velocity)
        if (velocity > 0) {
            keyDetector.addAttack(note)
            if (isChordChannel(channel)) attackSinceCommit = true
        }
        onChange(nowMs)
    }

    @Synchronized
    fun noteOff(channel: Int, note: Int, nowMs: Long) {
        tracker.noteOff(channel, note)
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
        attackSinceCommit = true
        peakSet = emptySet()
        analysisSet = emptySet()
        onChange(nowMs)
    }

    /** Relâche toutes les notes (« panique »). */
    @Synchronized
    fun releaseAll(nowMs: Long) {
        tracker.reset()
        onChange(nowMs)
    }

    /** Efface l'historique et repart de zéro pour la tonalité (nouveau morceau). */
    @Synchronized
    fun clearHistory() {
        history.clear()
        current = null
        keyDetector.reset()
        keyEstimate = null
        keySignature = EMPTY_KEY_SIGNATURE
        version++
    }

    /**
     * À appeler toutes les 5 à 10 ms : fait avancer la mémoire de tonalité, valide un accord
     * si les notes sont stables, rafraîchit la tonalité. Renvoie true si l'affichage a changé.
     */
    @Synchronized
    fun tick(nowMs: Long): Boolean {
        keyDetector.advance(nowMs, tracker.sounding().map { it.note })
        var changed = commitIfStable(nowMs)
        if (nowMs - lastKeyRefreshMs >= KEY_REFRESH_MS) {
            lastKeyRefreshMs = nowMs
            if (refreshKey()) changed = true
        }
        if (changed) version++
        return changed
    }

    private fun commitIfStable(nowMs: Long): Boolean {
        if (!pending || nowMs - lastChangeMs < settings.stabilityMs) return false
        pending = false
        if (!attackSinceCommit) return false
        val set = analysisSet.ifEmpty { peakSet }
        if (set.isEmpty()) return false
        val result = ChordRecognizer.recognize(set, settings.slashMinGap, settings.bassOptions)
        if (result.best == null && result.pitchClasses.size < 2) return false
        attackSinceCommit = false
        peakSet = emptySet()
        val previous = current?.best
        current = result
        val best = result.best
        if (best != null) {
            keyDetector.addChord(best, previous)
            refreshKey()
            if (history.lastOrNull()?.chord?.best?.symbol != best.symbol) {
                history.addLast(HistoryEntry(result, analyze(best)))
                while (history.size > MAX_HISTORY) history.removeFirst()
            }
        }
        return true
    }

    /** Recalcule la tonalité ; renvoie true si ce qui est affiché a changé. */
    private fun refreshKey(): Boolean {
        val estimate = keyDetector.estimate()
        val signature = estimate.current?.name + "|" + estimate.ranking.take(3)
            .joinToString { it.key.name + ":" + (it.probability * 100).toInt() }
        keyEstimate = if (estimate.ranking.isEmpty()) keyEstimate else estimate
        if (signature == keySignature) return false
        keySignature = signature
        return true
    }

    private fun analyze(chord: ChordCandidate): HarmonicAnalysis? =
        keyEstimate?.current?.let { HarmonicAnalyzer.analyze(chord, it, settings.style) }

    @Synchronized
    fun snapshot(): EngineSnapshot {
        val notes = tracker.sounding().map { n ->
            val melody = !isChordChannel(n.channel)
            SoundingNote(
                note = n.note,
                channel = n.channel,
                velocity = n.velocity,
                sustained = n.sustained,
                melody = melody,
                analysed = !melody && (settings.includeSustain || !n.sustained),
            )
        }
        val currentAnalysis = current?.best?.let { analyze(it) }
        return EngineSnapshot(version, notes, current, currentAnalysis, keyEstimate, history.toList())
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
        const val KEY_REFRESH_MS = 250L
        private const val EMPTY_KEY_SIGNATURE = "null|"
    }
}
