package fr.declodeur.harmonie.engine

/** Calcul de la tension harmonique. */
enum class TensionModel(val label: String) {
    /** Espace tonal de Lerdahl : distance à la tonique et au précédent, dissonance réelle, instabilité. */
    TONAL_SPACE("Espace tonal (Lerdahl)"),

    /** Modèle de la v0.15 : fonction + dissonance du type d'accord + éloignement (catégorie). */
    SIMPLE("Simple (v0.15)"),
}

/**
 * Tension harmonique d'un accord, de 0 (repos) à 1 (tension maximale), pour la courbe de tension.
 *
 * Modèle par défaut, d'après l'espace tonal de Lerdahl (Tonal Pitch Space, 2001 ; validé par Lerdahl et
 * Krumhansl, 2007, sur des jugements d'auditeurs) :
 * - distance δ entre deux accords = i + j + k : i = notes hors de la tonalité (changement de région),
 *   j = pas sur le cercle des quintes entre les fondamentales, k = notes nouvelles dans l'« espace de
 *   base » (fondamentale, quinte, accord, gamme). δ(I → V) = 5, δ(I → vi) = 7, δ(I → ii) = 8 ;
 * - tension = 3/4 de la distance à la tonique (on s'éloigne, on revient) + 1/4 de la distance à
 *   l'accord précédent (un saut surprend) ;
 * - plus la dissonance réelle des notes jouées (secondes, septièmes, tritons, notes étrangères à
 *   l'accord, mélodie comprise) et l'instabilité des accords sans quinte juste (diminués, augmentés :
 *   ils ne peuvent pas servir de repos) ; quarte et sixte de cadence (I avec la quinte à la basse)
 *   comptée comme la dominante qu'elle annonce.
 */
object Tension {
    /** Échelle : une tension brute de 20 (accord diminué éloigné, très dissonant) vaut 1. */
    private const val SCALE = 20.0

    /** Tensions d'une suite d'accords (le précédent compte). */
    fun series(history: List<HistoryEntry>, model: TensionModel = TensionModel.TONAL_SPACE): List<Double> =
        history.indices.map { i ->
            val e = history[i]
            when (model) {
                TensionModel.SIMPLE -> simple(e.chord.best, e.analysis)
                TensionModel.TONAL_SPACE -> tonalSpace(e.chord.best, e.analysis?.key, history.getOrNull(i - 1)?.chord?.best, e.notes)
            }
        }

    /** Espace de base de Lerdahl pour un accord : fondamentale, quinte, notes de l'accord. */
    private class Space(val root: Int, val fifth: Int, val tones: Set<Int>)

    private fun space(c: ChordCandidate): Space {
        val iv = c.type.intervals
        val fifthIv = listOf(7, 6, 8).firstOrNull { it in iv } ?: 7
        return Space(c.root, Notes.pc(c.root + fifthIv), c.chordPitchClasses)
    }

    private fun tonicSpace(key: Key): Space {
        val third = if (key.minor) 3 else 4
        return Space(key.tonic, Notes.pc(key.tonic + 7), setOf(key.tonic, Notes.pc(key.tonic + third), Notes.pc(key.tonic + 7)))
    }

    /** Pas sur le cercle des quintes entre deux classes de hauteur (0 à 6). */
    private fun fifths(a: Int, b: Int): Int {
        val n = Notes.pc((b - a) * 7)
        return minOf(n, 12 - n)
    }

    private fun scaleOf(key: Key): Set<Int> =
        (if (key.minor) KeyDetector.MINOR_SCALE else KeyDetector.MAJOR_SCALE).map { Notes.pc(it + key.tonic) }.toSet()

    /** Distance δ(x → y) de Lerdahl, dans la tonalité [key]. */
    private fun distance(x: Space, y: Space, key: Key?): Int {
        val outsideY = key?.let { k -> y.tones - scaleOf(k) } ?: emptySet()
        val outsideX = key?.let { k -> x.tones - scaleOf(k) } ?: emptySet()
        val i = (outsideY - outsideX).size
        val j = fifths(x.root, y.root)
        val k = (if (y.root != x.root) 1 else 0) +
            (setOf(y.root, y.fifth) - setOf(x.root, x.fifth)).size +
            (y.tones - x.tones).size +
            (outsideY - outsideX).size
        return i + j + k
    }

    /** Dissonance des intervalles entre les classes de hauteur qui sonnent. */
    private fun roughness(pcs: Set<Int>): Double {
        val l = pcs.toList()
        var r = 0.0
        for (a in l.indices) for (b in a + 1 until l.size) {
            r += when (Notes.pc(l[b] - l[a]).let { minOf(it, 12 - it) }) {
                1 -> 3.0 // seconde mineure, septième majeure
                2 -> 1.5 // seconde majeure, septième mineure
                6 -> 2.5 // triton
                else -> 0.0
            }
        }
        return r
    }

    /** Instabilité des accords sans quinte juste : ils ne peuvent pas servir de repos. */
    private fun instability(type: ChordType): Double = when (type) {
        ChordType.DIM7 -> 6.0
        ChordType.DIM -> 5.0
        ChordType.AUG, ChordType.MAJ7_AUG, ChordType.DOM7_AUG -> 9.0
        ChordType.MINOR7_FLAT5, ChordType.DOM7_FLAT5 -> 4.0
        ChordType.SUS4, ChordType.SUS2, ChordType.DOM7_SUS4 -> 1.5
        else -> 0.0
    }

    fun tonalSpace(chord: ChordCandidate?, key: Key?, previous: ChordCandidate?, notes: List<Int> = emptyList()): Double {
        chord ?: return 0.0
        val y = space(chord)
        var raw = 0.0
        if (key != null) {
            val tonic = tonicSpace(key)
            var fromTonic = distance(tonic, y, key).toDouble()
            // Quarte et sixte de cadence : accord de tonique avec la quinte à la basse, il annonce la dominante
            val cadential64 = chord.root == key.tonic && Notes.pc(chord.bass - key.tonic) == 7
            if (cadential64) fromTonic = maxOf(fromTonic, 7.0)
            val fromPrevious = previous?.let { distance(space(it), y, key).toDouble() } ?: fromTonic
            raw += 0.75 * fromTonic + 0.25 * fromPrevious
        }
        // Dissonance réelle : notes de l'accord et notes qui sonnent en plus (mélodie, notes étrangères)
        val sounding = notes.map { Notes.pc(it) }.toSet()
        val extra = (sounding - chord.chordPitchClasses).size.coerceAtMost(2)
        raw += roughness(chord.chordPitchClasses) + 2.0 * extra + instability(chord.type)
        if (chord.bass != chord.root) raw += 1.0
        return (raw / SCALE).coerceIn(0.0, 1.0)
    }

    /** Modèle de la v0.15 (conservé pour comparer). */
    fun simple(chord: ChordCandidate?, analysis: HarmonicAnalysis?): Double {

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
