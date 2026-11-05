package fr.declodeur.harmonie.engine

import kotlin.math.abs

/** Qualité simplifiée d'un accord, pour les tables d'enchaînements. */
enum class Quality { MAJ, MIN, DOM, HDIM, DIM }

/** Un degré dans la tonalité : intervalle de la fondamentale depuis la tonique + qualité. */
data class Degree(val interval: Int, val quality: Quality)

/** Un nœud de l'arbre des suites probables. */
data class PredictionNode(
    val chord: ChordCandidate,
    val numeral: String,
    val function: HarmonicFunction?,
    /** Probabilité de cet accord sachant ce qui précède (affichée au premier niveau). */
    val probability: Double,
    /** Probabilité du chemin depuis maintenant (produit des probabilités), pour l'intensité. */
    val pathProbability: Double,
    /** Notes MIDI à jouer (premier niveau seulement) : conduite des voix minimale. */
    val voicing: List<Int>,
    val children: List<PredictionNode>,
)

/** Forme de l'arbre et taille du contexte. */
data class PredictionSettings(
    /** Nombre d'accords précédents pris en compte (1 à 4). */
    val contextSize: Int = 2,
    /** Nombre de propositions par niveau (niveau 1, 2, 3). */
    val level1: Int = 4,
    val level2: Int = 2,
    val level3: Int = 1,
)

/**
 * Prédiction des accords suivants à partir d'un modèle théorique (en attendant un corpus).
 *
 * 1. Table d'enchaînements par style et par mode : pour chaque degré, les degrés qui suivent
 *    habituellement, avec un poids (Pop/rock, Jazz, Blues, Classique ; majeur et mineur).
 * 2. Accords hors table : une dominante va vers sa cible (une quinte plus bas, ou un demi-ton
 *    plus bas pour une substitution tritonique), un diminué monte d'un demi-ton ; sinon on
 *    se rabat sur la fonction de l'accord (tonique, sous-dominante, dominante).
 * 3. Contexte (les n derniers accords) : un II-V appelle fortement son I ; et le morceau se
 *    répète souvent : si les derniers accords ont déjà été joués dans le même ordre, ce qui les
 *    suivait alors devient plus probable (d'autant plus que la suite reconnue est longue).
 */
object Predictor {
    private fun d(iv: Int, q: Quality) = Degree(iv, q)

    // Degrés courants (majeur)
    private val I = d(0, Quality.MAJ); private val ii = d(2, Quality.MIN); private val iii = d(4, Quality.MIN)
    private val IV = d(5, Quality.MAJ); private val V = d(7, Quality.MAJ); private val V7 = d(7, Quality.DOM)
    private val vi = d(9, Quality.MIN); private val viiHalf = d(11, Quality.HDIM); private val viiDim = d(11, Quality.DIM)
    private val bVII = d(10, Quality.MAJ); private val ivMin = d(5, Quality.MIN); private val bVI = d(8, Quality.MAJ)
    private val bIII = d(3, Quality.MAJ); private val I7 = d(0, Quality.DOM); private val II7 = d(2, Quality.DOM)
    private val III7 = d(4, Quality.DOM); private val VI7 = d(9, Quality.DOM); private val bII7 = d(1, Quality.DOM)
    private val bVII7 = d(10, Quality.DOM); private val IV7 = d(5, Quality.DOM); private val sharpIdim = d(1, Quality.DIM)
    private val sharpIVdim = d(6, Quality.DIM)

    // Degrés courants (mineur)
    private val i = d(0, Quality.MIN); private val iiHalf = d(2, Quality.HDIM); private val iiDim = d(2, Quality.DIM)
    private val iv = d(5, Quality.MIN); private val vMin = d(7, Quality.MIN)
    private val bIIIm = d(3, Quality.MAJ); private val bVIm = d(8, Quality.MAJ); private val bVIIm = d(10, Quality.MAJ)
    private val bVII7m = d(10, Quality.DOM)

    private val POP_MAJOR: Map<Degree, List<Pair<Degree, Double>>> = mapOf(
        I to listOf(IV to 30.0, V to 25.0, vi to 20.0, ii to 10.0, bVII to 6.0, iii to 5.0),
        ii to listOf(V to 45.0, IV to 20.0, I to 10.0, vi to 10.0, iii to 5.0),
        iii to listOf(vi to 40.0, IV to 35.0, ii to 10.0, I to 10.0),
        IV to listOf(I to 30.0, V to 30.0, vi to 12.0, ii to 10.0, ivMin to 8.0, bVII to 5.0),
        V to listOf(I to 45.0, vi to 20.0, IV to 20.0, ii to 5.0),
        V7 to listOf(I to 55.0, vi to 20.0, IV to 10.0),
        vi to listOf(IV to 35.0, V to 20.0, ii to 20.0, I to 12.0, iii to 8.0),
        bVII to listOf(I to 50.0, IV to 35.0, V to 10.0),
        ivMin to listOf(I to 70.0, V to 20.0),
        bVI to listOf(bVII to 45.0, I to 25.0, V to 15.0),
    )
    private val POP_MINOR: Map<Degree, List<Pair<Degree, Double>>> = mapOf(
        i to listOf(bVIm to 25.0, bVIIm to 20.0, iv to 20.0, bIIIm to 15.0, vMin to 8.0, V to 8.0),
        iv to listOf(i to 30.0, bVIIm to 25.0, V to 20.0, bVIm to 10.0),
        V to listOf(i to 60.0, bVIm to 20.0),
        V7 to listOf(i to 65.0, bVIm to 20.0),
        vMin to listOf(i to 40.0, bVIm to 30.0, iv to 15.0),
        bIIIm to listOf(bVIIm to 30.0, bVIm to 25.0, iv to 20.0, i to 15.0),
        bVIm to listOf(bVIIm to 40.0, bIIIm to 20.0, i to 15.0, iv to 10.0, V to 10.0),
        bVIIm to listOf(i to 40.0, bIIIm to 25.0, bVIm to 15.0),
    )
    private val JAZZ_MAJOR: Map<Degree, List<Pair<Degree, Double>>> = mapOf(
        I to listOf(vi to 25.0, ii to 20.0, VI7 to 15.0, IV to 15.0, iii to 10.0, I7 to 5.0, sharpIdim to 5.0),
        ii to listOf(V7 to 60.0, bII7 to 15.0, iii to 5.0, bVII7 to 5.0),
        iii to listOf(vi to 35.0, VI7 to 30.0, IV to 15.0),
        IV to listOf(V7 to 20.0, iii to 20.0, ivMin to 15.0, I to 15.0, sharpIVdim to 10.0),
        V7 to listOf(I to 60.0, vi to 15.0, iii to 5.0),
        vi to listOf(ii to 45.0, II7 to 25.0, IV to 15.0),
        VI7 to listOf(ii to 70.0, II7 to 10.0),
        II7 to listOf(V7 to 50.0, ii to 30.0),
        III7 to listOf(vi to 70.0, VI7 to 10.0),
        I7 to listOf(IV to 70.0, ivMin to 10.0),
        bII7 to listOf(I to 80.0),
        viiHalf to listOf(III7 to 70.0, iii to 10.0),
        ivMin to listOf(bVII7 to 50.0, I to 40.0),
        bVII7 to listOf(I to 80.0),
        sharpIdim to listOf(ii to 80.0),
        sharpIVdim to listOf(I to 50.0, V7 to 30.0),
    )
    private val JAZZ_MINOR: Map<Degree, List<Pair<Degree, Double>>> = mapOf(
        i to listOf(iv to 30.0, iiHalf to 25.0, bVIm to 20.0, bIIIm to 10.0),
        iiHalf to listOf(V7 to 80.0),
        V7 to listOf(i to 75.0, bVIm to 15.0),
        iv to listOf(bVII7m to 30.0, V7 to 25.0, i to 20.0),
        bVII7m to listOf(bIIIm to 60.0, i to 20.0),
        bIIIm to listOf(bVIm to 40.0, iiHalf to 30.0),
        bVIm to listOf(iiHalf to 40.0, V7 to 30.0),
    )
    private val BLUES: Map<Degree, List<Pair<Degree, Double>>> = mapOf(
        I7 to listOf(IV7 to 45.0, V7 to 20.0, VI7 to 10.0),
        IV7 to listOf(I7 to 50.0, sharpIVdim to 20.0, V7 to 15.0),
        V7 to listOf(IV7 to 45.0, I7 to 45.0),
        sharpIVdim to listOf(I7 to 80.0),
        VI7 to listOf(II7 to 50.0, ii to 30.0),
        II7 to listOf(V7 to 70.0),
        ii to listOf(V7 to 70.0),
    )
    private val CLASSICAL_MAJOR: Map<Degree, List<Pair<Degree, Double>>> = mapOf(
        I to listOf(IV to 25.0, V to 25.0, vi to 20.0, ii to 15.0, iii to 5.0, viiDim to 5.0),
        ii to listOf(V to 60.0, viiDim to 20.0, V7 to 15.0),
        iii to listOf(vi to 50.0, IV to 30.0),
        IV to listOf(V to 40.0, I to 25.0, ii to 20.0, viiDim to 10.0),
        V to listOf(I to 65.0, vi to 20.0, IV to 5.0),
        V7 to listOf(I to 75.0, vi to 20.0),
        vi to listOf(ii to 35.0, IV to 35.0, V to 10.0),
        viiDim to listOf(I to 85.0, iii to 5.0),
    )
    private val CLASSICAL_MINOR: Map<Degree, List<Pair<Degree, Double>>> = mapOf(
        i to listOf(V to 30.0, iv to 25.0, bVIm to 20.0, iiDim to 15.0, bIIIm to 10.0),
        iiDim to listOf(V to 80.0),
        iv to listOf(V to 50.0, i to 25.0, iiDim to 15.0),
        V to listOf(i to 70.0, bVIm to 25.0),
        V7 to listOf(i to 75.0, bVIm to 20.0),
        bVIm to listOf(V to 40.0, iiDim to 25.0, iv to 20.0),
        bIIIm to listOf(bVIm to 40.0, iv to 30.0),
        viiDim to listOf(i to 85.0),
    )

    private fun table(style: MusicStyle, minor: Boolean): Map<Degree, List<Pair<Degree, Double>>> = when (style) {
        MusicStyle.POP_ROCK -> if (minor) POP_MINOR else POP_MAJOR
        MusicStyle.JAZZ -> if (minor) JAZZ_MINOR else JAZZ_MAJOR
        MusicStyle.BLUES -> if (minor) POP_MINOR else BLUES
        MusicStyle.CLASSICAL -> if (minor) CLASSICAL_MINOR else CLASSICAL_MAJOR
    }

    /** Qualité des accords parfaits de la gamme (cibles des dominantes). */
    private val MAJOR_TARGETS = mapOf(0 to Quality.MAJ, 2 to Quality.MIN, 4 to Quality.MIN, 5 to Quality.MAJ,
        7 to Quality.MAJ, 9 to Quality.MIN)
    private val MINOR_TARGETS = mapOf(0 to Quality.MIN, 3 to Quality.MAJ, 5 to Quality.MIN, 7 to Quality.MAJ,
        8 to Quality.MAJ, 10 to Quality.MAJ)

    fun quality(type: ChordType): Quality = when {
        type.isDominant -> Quality.DOM
        type == ChordType.MINOR7_FLAT5 -> Quality.HDIM
        type == ChordType.DIM || type == ChordType.DIM7 -> Quality.DIM
        type.isMinorTriadBased -> Quality.MIN
        else -> Quality.MAJ
    }

    fun degreeOf(chord: ChordCandidate, key: Key): Degree = Degree(Notes.pc(chord.root - key.tonic), quality(chord.type))

    /** Type d'accord concret pour un degré, selon le style (accords parfaits en pop, septièmes en jazz). */
    fun typeFor(degree: Degree, style: MusicStyle): ChordType = when (degree.quality) {
        Quality.MAJ -> when (style) {
            MusicStyle.JAZZ -> ChordType.MAJ7
            MusicStyle.BLUES -> ChordType.DOM7
            else -> ChordType.MAJOR
        }
        Quality.MIN -> if (style == MusicStyle.JAZZ || style == MusicStyle.BLUES) ChordType.MINOR7 else ChordType.MINOR
        Quality.DOM -> ChordType.DOM7
        Quality.HDIM -> ChordType.MINOR7_FLAT5
        Quality.DIM -> if (style == MusicStyle.JAZZ || style == MusicStyle.BLUES) ChordType.DIM7 else ChordType.DIM
    }

    /**
     * Probabilités des accords suivants, sachant les accords précédents [history] (degrés dans la
     * tonalité, du plus ancien au plus récent). [contextSize] limite la mémoire (1 à 4 accords).
     */
    fun next(history: List<Degree>, key: Key, style: MusicStyle, contextSize: Int): List<Pair<Degree, Double>> {
        val last = history.lastOrNull() ?: Degree(0, if (key.minor) Quality.MIN else Quality.MAJ)
        val scores = LinkedHashMap<Degree, Double>()
        for ((deg, w) in row(last, key, style)) scores[deg] = (scores[deg] ?: 0.0) + w

        // II-V : le I devient très probable
        val n = contextSize.coerceIn(1, 4)
        val ctx = history.takeLast(n)
        if (ctx.size >= 2) {
            val (a, b) = ctx.takeLast(2)
            val isTwoFive = (a.quality == Quality.MIN || a.quality == Quality.HDIM) && b.quality == Quality.DOM &&
                Notes.pc(b.interval - a.interval) == 5
            if (isTwoFive) {
                val target = Degree(Notes.pc(b.interval - 7), if (a.quality == Quality.HDIM) Quality.MIN else Quality.MAJ)
                scores[target] = ((scores[target] ?: 5.0) * 2.0)
            }
        }
        // Un accord n'est pas suivi de lui-même (l'historique ne répète pas)
        scores.remove(last)
        val table = normalize(scores)

        // Mémoire du morceau : suites déjà jouées dans le même ordre
        val (loop, matchLength) = loopEvidence(history, n)
        if (loop.isEmpty()) return table.sortedByDescending { it.second }
        val lambda = (0.3 * matchLength).coerceAtMost(0.8)
        val mixed = LinkedHashMap<Degree, Double>()
        for ((deg, p) in table) mixed[deg] = (1 - lambda) * p
        for ((deg, p) in loop) mixed[deg] = (mixed[deg] ?: 0.0) + lambda * p
        mixed.remove(last)
        return normalize(mixed).sortedByDescending { it.second }
    }

    /** Ligne de la table pour [last], avec les règles de secours pour les accords hors table. */
    private fun row(last: Degree, key: Key, style: MusicStyle): List<Pair<Degree, Double>> {
        val t = table(style, key.minor)
        t[last]?.let { return it }
        val targets = if (key.minor) MINOR_TARGETS else MAJOR_TARGETS
        val result = mutableListOf<Pair<Degree, Double>>()
        when (last.quality) {
            Quality.DOM -> {
                val fifthBelow = Notes.pc(last.interval - 7)
                val halfBelow = Notes.pc(last.interval - 1)
                targets[fifthBelow]?.let { result += Degree(fifthBelow, it) to 60.0 }
                targets[halfBelow]?.let { result += Degree(halfBelow, it) to 25.0 }
            }
            Quality.DIM, Quality.HDIM -> {
                val up = Notes.pc(last.interval + 1)
                targets[up]?.let { result += Degree(up, it) to 60.0 }
                val fifthBelow = Notes.pc(last.interval - 7)
                if (last.quality == Quality.HDIM) result += Degree(fifthBelow, Quality.DOM) to 40.0
            }
            else -> {}
        }
        if (result.isNotEmpty()) return result
        // Même degré, autre qualité (C6 se comporte comme C)
        t.entries.firstOrNull { it.key.interval == last.interval }?.let { return it.value }
        // Sinon : la ligne de la tonique
        return t[Degree(0, if (key.minor) Quality.MIN else Quality.MAJ)] ?: t.values.first()
    }

    /**
     * Cherche dans l'historique les endroits où les derniers accords (jusqu'à [n]) ont déjà été
     * joués dans le même ordre, et ce qui les suivait. Renvoie les probabilités et la longueur
     * de la plus longue suite reconnue.
     */
    private fun loopEvidence(history: List<Degree>, n: Int): Pair<List<Pair<Degree, Double>>, Int> {
        for (k in minOf(n, history.size - 1) downTo 1) {
            val suffix = history.takeLast(k)
            val counts = LinkedHashMap<Degree, Double>()
            for (j in 0 until history.size - k) {
                if (history.subList(j, j + k) == suffix) {
                    val next = history[j + k]
                    counts[next] = (counts[next] ?: 0.0) + 1.0
                }
            }
            if (counts.isNotEmpty()) return normalize(counts) to k
        }
        return emptyList<Pair<Degree, Double>>() to 0
    }

    private fun normalize(m: Map<Degree, Double>): List<Pair<Degree, Double>> {
        val total = m.values.sum()
        if (total <= 0) return emptyList()
        return m.map { (k, v) -> k to v / total }
    }

    /** Construit l'arbre des suites probables. */
    fun tree(
        history: List<Degree>,
        key: Key,
        style: MusicStyle,
        settings: PredictionSettings,
        currentNotes: List<Int>,
    ): List<PredictionNode> {
        val widths = listOf(settings.level1, settings.level2, settings.level3)
        fun expand(ctx: List<Degree>, depth: Int, parentP: Double): List<PredictionNode> {
            val width = widths.getOrNull(depth - 1) ?: 0
            if (width <= 0) return emptyList()
            return next(ctx, key, style, settings.contextSize).take(width).map { (deg, p) ->
                val chord = ChordCandidate(
                    root = Notes.pc(key.tonic + deg.interval),
                    type = typeFor(deg, style),
                    bass = Notes.pc(key.tonic + deg.interval),
                    omittedFifth = false,
                    extraNotes = emptySet(),
                    score = 0,
                )
                val analysis = HarmonicAnalyzer.analyze(chord, key, style)
                PredictionNode(
                    chord = chord,
                    numeral = analysis.numeral,
                    function = analysis.function,
                    probability = p,
                    pathProbability = parentP * p,
                    voicing = if (depth == 1) Voicing.lead(currentNotes, chord) else emptyList(),
                    children = expand(ctx + deg, depth + 1, parentP * p),
                )
            }
        }
        return expand(history, 1, 1.0)
    }
}

/** Choix d'une position d'accord avec un minimum de mouvement depuis l'accord joué. */
object Voicing {
    /**
     * Position serrée de [chord] (tous les renversements, plusieurs octaves) qui minimise le
     * déplacement des doigts depuis [from] (notes MIDI de l'accord courant).
     */
    fun lead(from: List<Int>, chord: ChordCandidate): List<Int> {
        val pcs = chord.type.intervals.sorted().map { (chord.root + it) % 12 }
        val ref = from.ifEmpty { listOf(60, 64, 67) }
        val center = ref.average()
        var best: List<Int> = emptyList()
        var bestCost = Double.MAX_VALUE
        for (inv in pcs.indices) {
            val order = pcs.drop(inv) + pcs.take(inv)
            for (octave in 3..6) {
                val notes = mutableListOf(order[0] + 12 * octave)
                for (pc in order.drop(1)) {
                    var n = notes.last() + 1
                    while (Notes.pc(n) != pc) n++
                    notes += n
                }
                if (notes.first() < 36 || notes.last() > 96) continue
                val cost = movement(ref, notes) + 0.05 * abs(notes.average() - center)
                if (cost < bestCost) {
                    bestCost = cost
                    best = notes
                }
            }
        }
        return best
    }

    /** Mouvement total : chaque note va vers la note la plus proche de l'autre accord (dans les deux sens). */
    fun movement(a: List<Int>, b: List<Int>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val ab = b.sumOf { x -> a.minOf { abs(it - x) } }
        val ba = a.sumOf { x -> b.minOf { abs(it - x) } }
        return (ab + ba).toDouble()
    }
}
