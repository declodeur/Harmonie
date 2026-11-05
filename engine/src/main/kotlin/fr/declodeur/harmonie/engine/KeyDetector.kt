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
    /** Les tonalités les plus probables, de la plus probable à la moins probable. */
    val ranking: List<KeyProbability>,
)

/**
 * Détection de la tonalité la plus probable, en continu.
 *
 * Méthode : on accumule un « poids » par classe de hauteur (durée de chaque note qui sonne,
 * plus un petit poids à chaque attaque et pour chaque accord reconnu). Ce poids s'efface
 * progressivement (demi-vie réglable : la « mémoire » de la tonalité). Le profil obtenu est
 * comparé (corrélation) aux profils de référence des 24 tonalités, établis par David Temperley
 * à partir d'un corpus de musique tonale. Une cadence parfaite (accord de dominante qui
 * résout une quinte plus bas) renforce la tonalité d'arrivée.
 */
class KeyDetector(halfLifeMs: Long = 12_000) {
    var halfLifeMs: Long = halfLifeMs
        set(value) {
            field = value.coerceAtLeast(1_000)
        }

    private val weights = DoubleArray(12)
    private val bonus = DoubleArray(24)
    private var lastMs = -1L

    var current: Key? = null
        private set

    fun reset() {
        weights.fill(0.0)
        bonus.fill(0.0)
        lastMs = -1L
        current = null
    }

    /**
     * Fait passer le temps : ajoute la durée des notes qui sonnent et efface progressivement
     * la mémoire. La mémoire ne s'efface que pendant que des notes sonnent : un silence ne
     * fait pas oublier la tonalité.
     */
    fun advance(nowMs: Long, soundingPitchClasses: Collection<Int>) {
        if (lastMs < 0) {
            lastMs = nowMs
            return
        }
        val dt = nowMs - lastMs
        if (dt <= 0) return
        lastMs = nowMs
        if (soundingPitchClasses.isEmpty()) return
        val factor = 0.5.pow(dt.toDouble() / halfLifeMs)
        for (i in weights.indices) weights[i] *= factor
        for (i in bonus.indices) bonus[i] *= factor
        val add = dt / 1000.0
        for (pc in soundingPitchClasses.toSet()) weights[Notes.pc(pc)] += add
    }

    fun addAttack(note: Int) {
        weights[Notes.pc(note)] += ATTACK_WEIGHT
    }

    /** Un accord vient d'être reconnu ; [previous] est l'accord précédent. */
    fun addChord(chord: ChordCandidate, previous: ChordCandidate?) {
        for (pc in chord.chordPitchClasses) weights[pc] += CHORD_WEIGHT
        weights[chord.root] += CHORD_WEIGHT
        if (previous != null && previous.type.isDominant && Notes.pc(previous.root - chord.root) == 7) {
            when {
                chord.type.isMajorTriadBased -> bonus[Key(chord.root, false).index] += CADENCE_BONUS
                chord.type.isMinorTriadBased -> bonus[Key(chord.root, true).index] += CADENCE_BONUS
            }
        }
    }

    /** Calcule l'estimation et met à jour la tonalité retenue. */
    fun estimate(): KeyEstimate {
        val total = weights.sum()
        if (total < MIN_TOTAL_WEIGHT) return KeyEstimate(current, emptyList())
        val scores = Key.ALL.map { key -> key to score(key) }
        val (bestKey, bestScore) = scores.maxBy { it.second }
        val currentScore = current?.let { score(it) }
        if (current == null || currentScore == null || bestScore > currentScore + SWITCH_MARGIN) {
            current = bestKey
        }
        // Peu d'indices = probabilités plus étalées (un seul accord ne suffit pas à conclure)
        val evidence = (total / FULL_EVIDENCE_WEIGHT).coerceIn(MIN_EVIDENCE, 1.0)
        val beta = SOFTMAX_BETA * evidence
        val exps = scores.map { (k, s) -> k to exp(beta * (s - bestScore)) }
        val sum = exps.sumOf { it.second }
        val ranking = exps.map { (k, e) -> KeyProbability(k, e / sum) }.sortedByDescending { it.probability }
        return KeyEstimate(current, ranking)
    }

    private fun score(key: Key): Double {
        val profile = if (key.minor) MINOR_PROFILE else MAJOR_PROFILE
        val rotated = DoubleArray(12) { pc -> profile[Notes.pc(pc - key.tonic)] }
        return correlation(weights, rotated) + bonus[key.index]
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
        /** Profils de Temperley (corpus Kostka-Payne), du degré 1 au degré 7 majeur, par demi-ton. */
        val MAJOR_PROFILE = doubleArrayOf(0.748, 0.060, 0.488, 0.082, 0.670, 0.460, 0.096, 0.715, 0.104, 0.366, 0.057, 0.400)
        val MINOR_PROFILE = doubleArrayOf(0.712, 0.084, 0.474, 0.618, 0.049, 0.460, 0.105, 0.747, 0.404, 0.067, 0.133, 0.330)

        const val ATTACK_WEIGHT = 0.25
        const val CHORD_WEIGHT = 0.4
        const val CADENCE_BONUS = 0.12
        const val SWITCH_MARGIN = 0.06
        const val SOFTMAX_BETA = 18.0
        const val MIN_TOTAL_WEIGHT = 1.0
        /** Quantité d'indices (≈ secondes de notes cumulées) pour une confiance pleine. */
        const val FULL_EVIDENCE_WEIGHT = 40.0
        const val MIN_EVIDENCE = 0.2
    }
}
