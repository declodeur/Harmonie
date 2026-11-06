package fr.declodeur.harmonie.engine

/**
 * Tension harmonique d'un accord, de 0 (repos) à 1 (tension maximale), pour la courbe de tension.
 *
 * Trois composantes, comme on l'enseigne en analyse :
 * - la fonction : tonique (repos), sous-dominante (éloignement), dominante (appel de la tonique) ;
 * - la dissonance : septième, septième diminuée, tensions ajoutées (9, 11, 13), altérations (♭9, ♯9,
 *   ♭5, ♯5, ♭13), quarte suspendue ;
 * - l'éloignement de la tonalité : dominante secondaire, substitution tritonique, emprunt,
 *   diminué de passage, accord chromatique ; un renversement ajoute un peu d'instabilité.
 */
object Tension {
    fun of(chord: ChordCandidate?, analysis: HarmonicAnalysis?): Double {
        chord ?: return 0.0
        val type = chord.type
        var t = when (analysis?.function) {
            HarmonicFunction.TONIC -> if (analysis.substitute) 0.2 else 0.0
            HarmonicFunction.SUBDOMINANT -> 0.35
            HarmonicFunction.DOMINANT -> 0.6
            null -> if (analysis == null) 0.2 else 0.5
        }
        // Dissonance
        val iv = type.intervals
        t += when {
            type == ChordType.DIM7 -> 0.2
            type.isDominant -> 0.15
            10 in iv || 11 in iv -> 0.08 // septième mineure ou majeure (m7, M7, m7♭5)
            else -> 0.0
        }
        val altered = setOf(1, 8).count { it in type.tensions } + (if (type == ChordType.DOM7_SHARP9) 1 else 0) +
            (if (type == ChordType.AUG || type == ChordType.DOM7_AUG || type == ChordType.MAJ7_AUG || type == ChordType.DOM7_FLAT5) 1 else 0)
        t += 0.1 * altered
        t += 0.04 * (type.tensions.size - setOf(1, 8).count { it in type.tensions }).coerceAtLeast(0)
        if (type == ChordType.SUS4 || type == ChordType.DOM7_SUS4 || type == ChordType.SUS2) t += 0.05
        if (type == ChordType.DIM) t += 0.1
        // Éloignement de la tonalité
        t += when (analysis?.category) {
            HarmonicCategory.SECONDARY_DOMINANT -> 0.08
            HarmonicCategory.SUBSTITUTE_DOMINANT -> 0.12
            HarmonicCategory.ALTERED_DOMINANT -> 0.05
            HarmonicCategory.BORROWED -> 0.05
            HarmonicCategory.PASSING_DIMINISHED -> 0.05
            HarmonicCategory.CHROMATIC -> 0.15
            else -> 0.0
        }
        if (chord.bass != chord.root) t += 0.04
        return t.coerceIn(0.0, 1.0)
    }
}

/** Rythme harmonique : à quelle fréquence l'harmonie change. */
data class RhythmInfo(
    /** Durée moyenne d'un accord, en secondes (accords terminés seulement). */
    val meanSeconds: Double,
    /** Changements d'accord par minute. */
    val perMinute: Double,
    /** Les accords qui précèdent la dernière cadence sont nettement plus courts que la moyenne. */
    val acceleratesBeforeCadence: Boolean,
)

object HarmonicRhythm {
    /** Durées des accords de l'historique en ms (le dernier : depuis son attaque jusqu'à [nowMs]). */
    fun durations(history: List<HistoryEntry>, nowMs: Long): List<Long> =
        history.indices.map { i ->
            val end = history.getOrNull(i + 1)?.timeMs ?: nowMs
            (end - history[i].timeMs).coerceAtLeast(0)
        }

    /** Synthèse sur les [window] derniers accords terminés ; null s'il y en a moins de 3. */
    fun of(history: List<HistoryEntry>, nowMs: Long, window: Int = 16): RhythmInfo? {
        if (history.size < 4) return null
        val done = durations(history, nowMs).dropLast(1)
        val recent = done.takeLast(window)
        if (recent.size < 3) return null
        val mean = recent.average()
        if (mean <= 0) return null
        // Dernière cadence : une dominante qui résout une quinte plus bas sur la tonique
        val entries = history.dropLast(1).takeLast(window)
        val cadence = entries.indexOfLast { e ->
            e.resolution == Resolution.FIFTH && e.analysis?.function == HarmonicFunction.TONIC && e.analysis?.substitute == false
        }
        val accelerates = cadence >= 2 && run {
            val before = recent.subList(cadence - 2, cadence)
            before.average() < 0.7 * mean
        }
        return RhythmInfo(mean / 1000.0, 60_000.0 / mean, accelerates)
    }
}
