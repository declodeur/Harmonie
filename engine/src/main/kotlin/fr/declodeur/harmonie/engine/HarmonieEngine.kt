package fr.declodeur.harmonie.engine

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
            while (recentReleases.size > MAX_RECENT_RELEASES || (recentReleases.isNotEmpty() && nowMs - recentReleases.first().second > RELEASE_MEMORY_MS)) {
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
        val old = settings
        settings = newSettings
        if (old.style != newSettings.style || old.degreeNotation != newSettings.degreeNotation ||
            old.contextualFunctions != newSettings.contextualFunctions
        ) {
            reanalyzeHistory()
        }
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
        lastTickMs = nowMs
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

    /**
     * Valide un accord quand les notes sont stables depuis [AnalysisSettings.stabilityMs] et qu'une note a
     * été attaquée : lecture des notes, puis enregistrement (tonalité, historique, suites probables).
     */
    private fun commitIfStable(nowMs: Long): Boolean {
        if (!pending || nowMs - lastChangeMs < settings.stabilityMs) return false
        pending = false
        if (!attackSinceCommit) return false
        val set = analysisSet.ifEmpty { peakSet }
        if (set.isEmpty()) return false
        if (keepsCurrentChord(set, nowMs)) {
            attackSinceCommit = false
            peakSet = emptySet()
            return false
        }
        val result = readChord(set, nowMs) ?: return false
        attackSinceCommit = false
        peakSet = emptySet()
        record(result, set, nowMs)
        return true
    }

    /** Note de passage, ou une ou deux notes de l'accord en cours : l'accord affiché continue. */
    private fun keepsCurrentChord(set: Set<Int>, nowMs: Long): Boolean {
        val interp = settings.interpretation
        if (interp.passingTones && isPassingTone(set, nowMs)) return true
        val pcs = set.map { Notes.pc(it) }.toSet()
        val cur = current?.best
        return pcs.size < 3 && interp.keepChordOnSubset && cur != null && pcs.isNotEmpty() && cur.chordPitchClasses.containsAll(pcs)
    }

    /** Lecture des notes tenues : accord, sinon arpège ou fenêtre harmonique, puis règles d'interprétation. */
    private fun readChord(set: Set<Int>, nowMs: Long): ChordResult? {
        val interp = settings.interpretation
        val key = keyEstimate?.current
        var result = ChordRecognizer.recognize(set, settings.slashMinGap, bassOptions)
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
        return result.takeUnless { it.best == null && it.pitchClasses.size < 2 }
    }

    /** Enregistre l'accord validé : conduite des voix, tonalité, historique, suites probables. */
    private fun record(result: ChordResult, set: Set<Int>, nowMs: Long) {
        val previousNotes = committedNotes
        committedNotes = set
        val previous = current?.best
        current = result
        val best = result.best
        voiceLeading = if (best != null && previous != null && previousNotes.isNotEmpty() && previousNotes != set) {
            val keySpelling = keyEstimate?.current?.spelling ?: Spelling.DEFAULT
            Voicings.lead(
                previousNotes.toList(), previous, set.toList(), best, flagParallels = true,
                prevSpelling = Spelling.forChord(previous, history.lastOrNull()?.analysis, keySpelling),
                nextSpelling = Spelling.forChord(best, analyze(best), keySpelling),
            )
        } else {
            voiceLeading.takeIf { previousNotes == set }
        }
        if (best != null) {
            updateKey(best, previous)
            appendToHistory(result, best, nowMs)
        }
        recomputePredictions()
    }

    /** Le nouvel accord nourrit la tonalité (rupture, cadence) puis la tonalité est recalculée. */
    private fun updateKey(best: ChordCandidate, previous: ChordCandidate?) {
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
    }

    /** Ajoute l'accord à l'historique (sauf s'il répète le précédent) et rechiffre la fonction du précédent. */
    private fun appendToHistory(result: ChordResult, best: ChordCandidate, nowMs: Long) {
        val last = history.lastOrNull()
        if (last?.chord?.best?.symbol == best.symbol) return
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
        refreshContext(history.size - 2)
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
        val gap = settings.slashMinGap.coerceAtLeast(1)
        val notes = cur.notes.let { n -> if (n.size >= 4 && n[1] - n[0] >= gap) n.drop(1) else n }
        predictions = Predictor.tree(degrees, key, settings.style, settings.prediction, notes, settings.degreeNotation)
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
            val r = ChordRecognizer.recognize(notes, settings.slashMinGap, bassOptions)
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
        val r = ChordRecognizer.recognize(notes, settings.slashMinGap, bassOptions)
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

    /** Dernier instant connu (tick), pour la durée de l'accord en cours. */
    private var lastTickMs = 0L

    /** Grille du morceau (tout l'historique gardé, pas seulement les derniers accords affichés). */
    @Synchronized
    fun grid(byDuration: Boolean, groupRepeats: Boolean): List<GridSection> =
        ChordGrid.build(history.toList(), lastTickMs, byDuration, groupRepeats)

    /**
     * Options de lecture de la basse pour le style choisi : la septième à la basse n'est gardée qu'en
     * Classique (banc d'essai : +1,3 point d'accords exacts sur Bach, −1,4 en pop, où l'on écrit C/B♭).
     */
    private val bassOptions: BassReadingOptions
        get() = settings.bassOptions.let { if (settings.style == MusicStyle.CLASSICAL) it else it.copy(keepSeventhInBass = false) }

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
                analysis = HarmonicAnalyzer.analyze(chord, new, settings.style, settings.degreeNotation),
                reanalyzed = true,
                pivotFrom = if (j == first) e.analysis else null,
            )
        }
        for (j in (first - 1).coerceAtLeast(0) until history.size - 1) refreshContext(j)
    }

    private fun analyze(chord: ChordCandidate): HarmonicAnalysis? =
        keyEstimate?.current?.let { HarmonicAnalyzer.analyze(chord, it, settings.style, settings.degreeNotation) }

    /** Fonction selon le contexte (VI, VII), si l'option est activée. */
    private fun withContext(
        a: HarmonicAnalysis?,
        chord: ChordCandidate,
        previous: ChordCandidate?,
        next: ChordCandidate?,
        predicted: Boolean,
    ): HarmonicAnalysis? {
        if (a == null || !settings.contextualFunctions) return a
        return HarmonicAnalyzer.inContext(a, chord, previous, next, predicted, settings.degreeNotation)
    }

    /** Recalcule la fonction de l'entrée [i] de l'historique d'après ses voisins (accord suivant joué). */
    private fun refreshContext(i: Int) {
        val e = history.getOrNull(i) ?: return
        val chord = e.chord.best ?: return
        val a = e.analysis ?: return
        val base = HarmonicAnalyzer.analyze(chord, a.key, settings.style, settings.degreeNotation)
        // Accord suivant : le premier de fondamentale différente (A♭ → A♭M7 → E♭ : E♭)
        val next = (i + 1 until history.size).asSequence().mapNotNull { history[it].chord.best }.firstOrNull { it.root != chord.root }
        history[i] = e.copy(analysis = withContext(base, chord, history.getOrNull(i - 1)?.chord?.best, next, false))
    }

    /** Rechiffre tout l'historique (après un changement de style, de notation ou de règle des fonctions). */
    private fun reanalyzeHistory() {
        for (i in history.indices) {
            val e = history[i]
            val chord = e.chord.best ?: continue
            val key = e.analysis?.key ?: continue
            history[i] = e.copy(
                analysis = HarmonicAnalyzer.analyze(chord, key, settings.style, settings.degreeNotation),
                pivotFrom = e.pivotFrom?.let { HarmonicAnalyzer.analyze(chord, it.key, settings.style, settings.degreeNotation) },
            )
        }
        for (i in 0 until history.size - 1) refreshContext(i)
    }

    @Synchronized
    fun snapshot(): EngineSnapshot {
        val best = current?.best
        // Accord suivant pas encore joué : la fonction de VI / VII vient de la suite la plus probable
        val predictedNext = predictions.firstOrNull()?.chord
        val lastIsCurrent = best != null && history.lastOrNull()?.chord?.best?.symbol == best.symbol
        val previousChord = (if (lastIsCurrent) history.getOrNull(history.size - 2) else history.lastOrNull())?.chord?.best
        val currentAnalysis = best?.let { withContext(analyze(it), it, previousChord, predictedNext, true) }
        val shownHistory = history.takeLast(MAX_HISTORY).let { h ->
            val last = h.lastOrNull()
            val lastChord = last?.chord?.best
            if (last == null || lastChord == null || !lastIsCurrent) h
            else h.dropLast(1) + last.copy(analysis = withContext(last.analysis, lastChord, previousChord, predictedNext, true))
        }
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
            version, notes, current, currentAnalysis, keyEstimate, shownHistory, predictions, scale,
            voicing = if (settings.voicing.showVoicing) best?.let { Voicings.describe(committedNotes.toList(), it, keyEstimate?.current) } else null,
            spelling = Spelling.forChord(best, currentAnalysis, keyEstimate?.current?.spelling ?: Spelling.DEFAULT),
            nowMs = lastTickMs,
            rhythm = HarmonicRhythm.of(history.takeLast(MAX_HISTORY), lastTickMs),
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
        private const val MAX_RECENT_RELEASES = 16
        private const val EMPTY_KEY_SIGNATURE = "null|"
    }
}
