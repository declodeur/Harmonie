package fr.declodeur.harmonie.engine

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** Une tonalité : tonique + mode majeur ou mineur. */
data class Key(val tonic: Int, val minor: Boolean) {
    /** Orthographe des notes dans cette tonalité, d'après son armure. */
    val spelling: Spelling
        get() {
            val major = if (minor) Notes.pc(tonic + 3) else tonic
            return when (major) {
                0 -> Spelling.DEFAULT
                5, 10, 3, 8, 1 -> Spelling.FLATS
                6 -> if (minor) Spelling.FLATS else Spelling.SHARPS // E♭m (6 bémols), F♯ (6 dièses)
                else -> Spelling.SHARPS
            }
        }

    val tonicName: String get() = Notes.name(tonic, spelling)

    /** « C majeur », « F♯ mineur ». */
    val name: String get() = tonicName + if (minor) " mineur" else " majeur"

    /** Tonalité homonyme (même tonique, autre mode). */
    val parallel: Key get() = Key(tonic, !minor)

    /** Tonalité relative (même armure). */
    val relative: Key get() = if (minor) Key(Notes.pc(tonic + 3), false) else Key(Notes.pc(tonic + 9), true)

    internal val index: Int get() = tonic + if (minor) 12 else 0

    companion object {
        val ALL: List<Key> = (0 until 12).map { Key(it, false) } + (0 until 12).map { Key(it, true) }
    }
}

data class KeyProbability(val key: Key, val probability: Double)

/** Estimation de tonalité affichable. */
data class KeyEstimate(
    /** Tonalité retenue (avec hystérésis pour éviter les changements intempestifs). */
    val current: Key?,
    /** Les 24 tonalités, de la plus probable à la moins probable. */
    val ranking: List<KeyProbability>,
    /** Tonalités dont la gamme contient (presque) toutes les notes jouées récemment. */
    val compatible: Set<Key> = emptySet(),
) {
    fun probabilityOf(key: Key): Double = ranking.firstOrNull { it.key == key }?.probability ?: 0.0
}

/**
 * Détection de la tonalité la plus probable, en continu.
 *
 * Deux comptes de poids par classe de hauteur, qui s'effacent progressivement (demi-vie
 * réglable, seulement pendant le jeu) :
 * - l'harmonie : durée des notes des accords, attaques, accords reconnus ;
 * - la mélodie : notes jouées seules (mode Complète) ou sur les autres canaux (mode
 *   Accords + mélodie).
 * Chaque compte est normalisé, puis la mélodie est ajoutée avec son poids relatif.
 *
 * Score d'une tonalité = compatibilité (part des notes qui appartiennent à sa gamme)
 * + ressemblance avec le profil de référence de la tonalité (centre de gravité)
 * + bonus de cadence (dominante qui résout une quinte plus bas).
 * Quand plusieurs tonalités contiennent toutes les notes, seuls le profil et les cadences
 * les départagent : leurs probabilités restent alors proches.
 *
 * Profils : Temperley (corpus classique Kostka-Payne) pour le style Classique ; pour les
 * autres styles, mineur naturel (la 7e mineure y est normale) et 7e mineure tolérée en majeur.
 */
class KeyDetector(halfLifeMs: Long = 12_000) {
    var halfLifeMs: Long = halfLifeMs
        set(value) {
            field = value.coerceAtLeast(1_000)
        }

    var style: MusicStyle = MusicStyle.POP_ROCK

    /** Poids de la mélodie par rapport à l'harmonie (0 = ignorée, 1 = autant que l'harmonie). */
    var melodyWeight: Double = 0.7
        set(value) {
            field = value.coerceIn(0.0, 1.0)
        }

    private val harmony = DoubleArray(12)
    private val melody = DoubleArray(12)
    private val bonus = DoubleArray(24)
    private var lastMs = -1L

    var current: Key? = null
        private set

    fun reset() {
        harmony.fill(0.0)
        melody.fill(0.0)
        bonus.fill(0.0)
        lastMs = -1L
        current = null
    }

    /**
     * Fait passer le temps : ajoute la durée des notes qui sonnent et efface progressivement
     * la mémoire. La mémoire ne s'efface que pendant que des notes sonnent : un silence ne
     * fait pas oublier la tonalité.
     */
    fun advance(nowMs: Long, harmonicNotes: Collection<Int>, melodicNotes: Collection<Int> = emptyList()) {
        if (lastMs < 0) {
            lastMs = nowMs
            return
        }
        val dt = nowMs - lastMs
        if (dt <= 0) return
        lastMs = nowMs
        if (harmonicNotes.isEmpty() && melodicNotes.isEmpty()) return
        val factor = 0.5.pow(dt.toDouble() / halfLifeMs)
        for (i in 0 until 12) {
            harmony[i] *= factor
            melody[i] *= factor
        }
        for (i in bonus.indices) bonus[i] *= factor
        val add = dt / 1000.0
        for (pc in harmonicNotes.map(Notes::pc).toSet()) harmony[pc] += add
        for (pc in melodicNotes.map(Notes::pc).toSet()) melody[pc] += add
    }

    fun addAttack(note: Int, melodic: Boolean = false) {
        if (melodic) melody[Notes.pc(note)] += ATTACK_WEIGHT else harmony[Notes.pc(note)] += ATTACK_WEIGHT
    }

    /** Un accord vient d'être reconnu ; [previous] est l'accord précédent. */
    fun addChord(chord: ChordCandidate, previous: ChordCandidate?) {
        for (pc in chord.chordPitchClasses) harmony[pc] += CHORD_WEIGHT
        harmony[chord.root] += CHORD_WEIGHT
        if (previous != null && previous.type.isDominant && Notes.pc(previous.root - chord.root) == 7) {
            when {
                chord.type.isMajorTriadBased -> bonus[Key(chord.root, false).index] += CADENCE_BONUS
                chord.type.isMinorTriadBased -> bonus[Key(chord.root, true).index] += CADENCE_BONUS
            }
        }
    }

    /** Calcule l'estimation et met à jour la tonalité retenue. */
    fun estimate(): KeyEstimate {
        val total = harmony.sum() + melody.sum()
        if (total < MIN_TOTAL_WEIGHT) return KeyEstimate(current, emptyList())
        val combined = combined()
        val scored = Key.ALL.map { key -> Triple(key, score(key, combined), fit(key, combined)) }
        val (bestKey, bestScore) = scored.maxBy { it.second }.let { it.first to it.second }
        val currentScore = current?.let { score(it, combined) }
        if (current == null || currentScore == null || bestScore > currentScore + SWITCH_MARGIN) {
            current = bestKey
        }
        // Peu d'indices = probabilités plus étalées (un seul accord ne suffit pas à conclure)
        val evidence = (total / FULL_EVIDENCE_WEIGHT).coerceIn(MIN_EVIDENCE, 1.0)
        val beta = SOFTMAX_BETA * evidence
        val exps = scored.map { (k, s, _) -> k to exp(beta * (s - bestScore)) }
        val sum = exps.sumOf { it.second }
        val ranking = exps.map { (k, e) -> KeyProbability(k, e / sum) }.sortedByDescending { it.probability }
        val compatible = scored.filter { it.third >= COMPATIBLE_FIT }.map { it.first }.toSet()
        return KeyEstimate(current, ranking, compatible)
    }

    /** Profil de poids : harmonie normalisée + mélodie normalisée × poids relatif. */
    private fun combined(): DoubleArray {
        val h = harmony.sum()
        val m = melody.sum()
        return DoubleArray(12) { i ->
            (if (h > 0) harmony[i] / h else 0.0) + (if (m > 0) melodyWeight * melody[i] / m else 0.0)
        }
    }

    /** Part du poids qui appartient à la gamme de la tonalité (1 = toutes les notes y sont). */
    private fun fit(key: Key, w: DoubleArray): Double {
        val total = w.sum()
        if (total <= 0) return 0.0
        val scale = if (key.minor) MINOR_SCALE else MAJOR_SCALE
        var inside = 0.0
        for (pc in 0 until 12) if (Notes.pc(pc - key.tonic) in scale) inside += w[pc]
        return inside / total
    }

    private fun score(key: Key, w: DoubleArray): Double {
        val profile = profileFor(key.minor)
        val rotated = DoubleArray(12) { pc -> profile[Notes.pc(pc - key.tonic)] }
        return FIT_WEIGHT * fit(key, w) + PROFILE_WEIGHT * correlation(w, rotated) + bonus[key.index]
    }

    private fun profileFor(minor: Boolean): DoubleArray = when (style) {
        MusicStyle.CLASSICAL -> if (minor) CLASSICAL_MINOR else CLASSICAL_MAJOR
        MusicStyle.BLUES -> if (minor) POP_MINOR else BLUES_MAJOR
        else -> if (minor) POP_MINOR else POP_MAJOR
    }

    private fun correlation(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average()
        val mb = b.average()
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in a.indices) {
            val x = a[i] - ma
            val y = b[i] - mb
            num += x * y
            da += x * x
            db += y * y
        }
        return if (da == 0.0 || db == 0.0) 0.0 else num / sqrt(da * db)
    }

    companion object {
        /** Profils de Temperley (corpus Kostka-Payne), par demi-ton depuis la tonique. */
        val CLASSICAL_MAJOR = doubleArrayOf(0.748, 0.060, 0.488, 0.082, 0.670, 0.460, 0.096, 0.715, 0.104, 0.366, 0.057, 0.400)
        val CLASSICAL_MINOR = doubleArrayOf(0.712, 0.084, 0.474, 0.618, 0.049, 0.460, 0.105, 0.747, 0.404, 0.067, 0.133, 0.330)

        /** Variantes pour la pop, le rock et le jazz : 7e mineure normale en mineur, tolérée en majeur. */
        val POP_MAJOR = doubleArrayOf(0.748, 0.060, 0.488, 0.082, 0.670, 0.460, 0.096, 0.715, 0.104, 0.366, 0.150, 0.400)
        val POP_MINOR = doubleArrayOf(0.712, 0.084, 0.474, 0.618, 0.049, 0.460, 0.105, 0.747, 0.404, 0.067, 0.420, 0.180)

        /** Blues : tierce mineure (blue note) et 7e mineure fréquentes en majeur. */
        val BLUES_MAJOR = doubleArrayOf(0.748, 0.060, 0.488, 0.220, 0.670, 0.460, 0.150, 0.715, 0.104, 0.366, 0.330, 0.250)

        /** Gammes utilisées pour la compatibilité (le mineur accepte les deux 7es). */
        val MAJOR_SCALE = setOf(0, 2, 4, 5, 7, 9, 11)
        val MINOR_SCALE = setOf(0, 2, 3, 5, 7, 8, 10, 11)

        const val FIT_WEIGHT = 1.0
        const val PROFILE_WEIGHT = 0.35
        const val ATTACK_WEIGHT = 0.25
        const val CHORD_WEIGHT = 0.4
        const val CADENCE_BONUS = 0.06
        const val SWITCH_MARGIN = 0.03
        const val SOFTMAX_BETA = 30.0
        const val MIN_TOTAL_WEIGHT = 1.0
        const val COMPATIBLE_FIT = 0.97

        /** Quantité d'indices (≈ secondes de notes cumulées) pour une confiance pleine. */
        const val FULL_EVIDENCE_WEIGHT = 40.0
        const val MIN_EVIDENCE = 0.2
    }
}
