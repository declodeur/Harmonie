package fr.declodeur.harmonie.engine

/**
 * Statistiques d'enchaînements apprises sur des analyses d'experts (corpus du banc d'essai) :
 * pour chaque style et chaque mode, combien de fois chaque degré a suivi une suite de 1 à
 * [MAX_CONTEXT] degrés.
 *
 * Lissage : on part de la distribution du modèle théorique (a priori), puis chaque contexte plus
 * long la corrige d'après ses comptes (lissage de Dirichlet de poids [PRIOR_WEIGHT]) : un
 * enchaînement rare reste proche de la théorie, un enchaînement fréquent suit le corpus.
 */
class ProgressionModel(
    private val counts: Map<String, Map<Degree, Int>>,
    private val priorWeight: Double = PRIOR_WEIGHT,
) {

    fun withPriorWeight(w: Double) = ProgressionModel(counts, w)

    val size: Int get() = counts.size

    /** Styles pour lesquels le modèle a des données. */
    val styles: Set<MusicStyle> = counts.keys.mapNotNull { k -> MusicStyle.entries.firstOrNull { k.startsWith(it.name + "|") } }.toSet()

    /**
     * Distribution du degré suivant, corrigée par le corpus, à partir de l'a priori [prior]
     * (modèle théorique). Null si le corpus n'a rien pour ce style.
     */
    fun distribution(
        history: List<Degree>,
        minor: Boolean,
        style: MusicStyle,
        contextSize: Int,
        prior: List<Pair<Degree, Double>>,
    ): List<Pair<Degree, Double>>? {
        if (style !in styles) return null
        var p: Map<Degree, Double> = prior.toMap()
        val maxK = minOf(contextSize, MAX_CONTEXT, history.size)
        for (k in 1..maxK) {
            val c = counts[key(style, minor, history.takeLast(k))] ?: break
            val total = c.values.sum().toDouble()
            val degrees = p.keys + c.keys
            p = degrees.associateWith { d -> ((c[d] ?: 0) + priorWeight * (p[d] ?: 0.0)) / (total + priorWeight) }
        }
        return p.toList()
    }

    /** Texte du modèle (une ligne par contexte et degré suivant). */
    fun serialize(): String = buildString {
        append("# Statistiques d'enchaînements apprises sur les corpus du banc d'essai Harmonie\n")
        append("# style|mode|contexte\tdegré suivant\tcompte\n")
        for ((k, m) in counts.toSortedMap()) {
            for ((d, n) in m.entries.sortedByDescending { it.value }) append(k).append('\t').append(enc(d)).append('\t').append(n).append('\n')
        }
    }

    /** Une suite de degrés dans une même tonalité (un morceau, ou un passage entre deux modulations). */
    data class Sequence(val style: MusicStyle, val minor: Boolean, val degrees: List<Degree>)

    companion object {
        const val MAX_CONTEXT = 3
        const val PRIOR_WEIGHT = 3.0

        /** Comptes de contexte inférieurs à ce seuil oubliés (contextes de 2 et 3 accords), pour la taille. */
        const val MIN_COUNT_LONG = 2

        fun key(style: MusicStyle, minor: Boolean, ctx: List<Degree>): String =
            style.name + "|" + (if (minor) "m" else "M") + "|" + ctx.joinToString(" ") { enc(it) }

        fun enc(d: Degree) = "${d.interval}:${d.quality.name}"

        fun dec(s: String): Degree? {
            val (iv, q) = s.split(':').takeIf { it.size == 2 } ?: return null
            return Degree(iv.toIntOrNull() ?: return null, Quality.entries.firstOrNull { it.name == q } ?: return null)
        }

        /** Apprend les comptes sur des suites de degrés (répétitions consécutives retirées). */
        fun train(sequences: List<Sequence>, pruneLong: Boolean = false): ProgressionModel {
            val counts = HashMap<String, HashMap<Degree, Int>>()
            for (s in sequences) {
                val d = s.degrees.fold(mutableListOf<Degree>()) { acc, x -> if (acc.lastOrNull() != x) acc += x; acc }
                for (i in 1 until d.size) {
                    for (k in 1..minOf(MAX_CONTEXT, i)) {
                        val m = counts.getOrPut(key(s.style, s.minor, d.subList(i - k, i))) { HashMap() }
                        m[d[i]] = (m[d[i]] ?: 0) + 1
                    }
                }
            }
            val kept = if (!pruneLong) counts else counts.mapValues { (k, m) ->
                val ctxLen = k.substringAfterLast('|').split(' ').size
                if (ctxLen == 1) m else HashMap(m.filterValues { it >= MIN_COUNT_LONG })
            }.filterValues { it.isNotEmpty() }
            return ProgressionModel(kept)
        }

        fun parse(text: String): ProgressionModel {
            val counts = HashMap<String, HashMap<Degree, Int>>()
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("#")) continue
                val f = line.split('\t')
                if (f.size < 3) continue
                val d = dec(f[1]) ?: continue
                counts.getOrPut(f[0]) { HashMap() }[d] = f[2].toIntOrNull() ?: continue
            }
            return ProgressionModel(counts)
        }

        /** Modèle livré avec l'application (ressource du moteur), ou null s'il manque. */
        val bundled: ProgressionModel? by lazy {
            ProgressionModel::class.java.getResourceAsStream("/fr/declodeur/harmonie/engine/progressions.tsv")
                ?.use { parse(it.readBytes().toString(Charsets.UTF_8)) }
        }
    }
}
