package fr.declodeur.harmonie.bench

import fr.declodeur.harmonie.engine.AnalysisSettings

/**
 * Une hypothèse d'amélioration : une variante des réglages du moteur. Le banc joue la référence
 * (réglages par défaut) et la variante, puis compare. Chaque hypothèse correspond à un réglage de
 * l'application : on peut donc toujours revenir en arrière.
 */
data class Experiment(
    val id: String,
    val name: String,
    val hypothesis: String,
    /** Jouée par défaut à chaque passage du banc. */
    val active: Boolean,
    val apply: (AnalysisSettings) -> AnalysisSettings,
)

object Experiments {
    val ALL = listOf(
        Experiment(
            "memoire-6s", "Mémoire de tonalité 6 s",
            "La tonalité suit trop lentement les modulations : une mémoire plus courte (6 s au lieu de 12) devrait la rendre plus réactive.",
            active = false,
        ) { it.copy(keyMemorySeconds = 6) },
        Experiment(
            "memoire-4s", "Mémoire de tonalité 4 s",
            "Même hypothèse, poussée plus loin.",
            active = false,
        ) { it.copy(keyMemorySeconds = 4) },
        Experiment(
            "sans-changement-immediat", "Sans changement immédiat de tonalité",
            "Contrôle : effet de l'option « Changement immédiat sur un accord hors tonalité ».",
            active = false,
        ) { it.copy(fastKeyChange = false) },
    )

    /** [wanted] : ids séparés par des virgules, « all », « none », ou null pour les expériences actives. */
    fun select(wanted: String?): List<Experiment> = when {
        wanted == null -> ALL.filter { it.active }
        wanted == "all" -> ALL
        wanted == "none" || wanted.isBlank() -> emptyList()
        else -> wanted.split(',').map { it.trim() }.mapNotNull { id -> ALL.firstOrNull { it.id == id } }
    }
}

/** Une métrique du banc. */
enum class Metric(val label: String, val ok: (Verdict) -> Boolean) {
    CHORD("Accord", { it.chordOk }),
    EXACT("Accord exact", { it.exactOk }),
    KEY("Tonalité", { it.keyOk }),
    NUMERAL("Chiffrage", { it.numeralOk }),
}

/** Référence et variante pour un corpus, accord par accord (mêmes accords de l'expert, même ordre). */
class Pairing(val base: CorpusResult, val variant: CorpusResult) {
    fun score(r: CorpusResult, m: Metric) = pct(r.verdicts.count(m.ok), r.verdicts.size)
    fun delta(m: Metric) = score(variant, m) - score(base, m)

    /** Accords que la variante fait passer de faux à juste (gagnés) et de juste à faux (perdus). */
    fun gained(m: Metric) = base.verdicts.indices.filter { !m.ok(base.verdicts[it]) && m.ok(variant.verdicts[it]) }
    fun lost(m: Metric) = base.verdicts.indices.filter { m.ok(base.verdicts[it]) && !m.ok(variant.verdicts[it]) }
}

class ExperimentResult(val experiment: Experiment, base: List<CorpusResult>, variant: List<CorpusResult>) {
    val pairings = base.zip(variant) { b, v -> Pairing(b, v) }

    /** Écart global (tous corpus, pondéré par le nombre d'accords). */
    fun overallDelta(m: Metric): Double {
        val n = pairings.sumOf { it.base.verdicts.size }
        val b = pairings.sumOf { p -> p.base.verdicts.count(m.ok) }
        val v = pairings.sumOf { p -> p.variant.verdicts.count(m.ok) }
        return pct(v, n) - pct(b, n)
    }
}
