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
    private fun AnalysisSettings.interp(f: (fr.declodeur.harmonie.engine.InterpretationSettings) -> fr.declodeur.harmonie.engine.InterpretationSettings) =
        copy(interpretation = f(interpretation))

    val ALL = listOf(
        Experiment(
            "version-0.8", "Moteur de la version 0.8",
            "Contrôle : toutes les règles d'interprétation désactivées, mémoire de tonalité de 12 s et inertie de 3, comme avant le banc d'essai. " +
                "Mesure le chemin parcouru.",
            active = true,
        ) {
            it.copy(
                keyMemorySeconds = 12,
                keyInertiaPercent = 3,
                interpretation = it.interpretation.copy(
                    sixthAsSeventh = false, diminishedByKey = false, suspensions = false,
                    keepChordOnSubset = false, completeDyads = false, harmonicWindowMs = 0,
                ),
            )
        },
        Experiment(
            "sans-sixte-septieme", "Sans : sixte ajoutée lue comme septième",
            "Retire la règle : en style classique, C6 est lu Am7/C et Cm6 Am7♭5/C, comme vi6/5 et ii6/5 chez les experts.",
            active = false,
        ) { it.interp { i -> i.copy(sixthAsSeventh = false) } },
        Experiment(
            "sans-diminue-sensible", "Sans : septième diminuée nommée d'après la sensible",
            "Retire la règle : une septième diminuée est nommée d'après la note qui monte vers un degré de la tonalité.",
            active = false,
        ) { it.interp { i -> i.copy(diminishedByKey = false) } },
        Experiment(
            "sans-retards", "Sans : retards (sus4 lu avec sa tierce)",
            "Retire la règle : en style classique, une quarte à la place de la tierce est un retard ; l'accord est nommé avec sa tierce.",
            active = false,
        ) { it.interp { i -> i.copy(suspensions = false) } },
        Experiment(
            "sans-sous-ensemble", "Sans : une ou deux notes de l'accord ne le remplacent pas",
            "Retire la règle : quand il ne reste qu'une ou deux notes de l'accord en cours, l'accord continue.",
            active = false,
        ) { it.interp { i -> i.copy(keepChordOnSubset = false) } },
        Experiment(
            "sans-dyades", "Sans : tierces et quintes seules lues comme accords",
            "Retire la règle : deux notes à la tierce ou à la sixte (et à la quinte en style classique) sont lues comme l'accord parfait.",
            active = false,
        ) { it.interp { i -> i.copy(completeDyads = false) } },
        Experiment(
            "fenetre-600", "Fenêtre harmonique de 600 ms",
            "Quand une ou deux notes seulement sont tenues, compléter avec les notes jouées dans les 600 dernières ms. " +
                "Essai du 6 novembre : utile seule, mais n'apporte plus rien une fois les autres règles en place (désactivée par défaut).",
            active = false,
        ) { it.interp { i -> i.copy(harmonicWindowMs = 600) } },
        Experiment("fenetre-400", "Fenêtre harmonique de 400 ms", "Même essai avec une fenêtre plus courte.", active = false) {
            it.interp { i -> i.copy(harmonicWindowMs = 400) }
        },
        Experiment("memoire-12s", "Mémoire de tonalité 12 s", "Ancienne valeur par défaut (4 s aujourd'hui).", active = false) {
            it.copy(keyMemorySeconds = 12)
        },
        Experiment("memoire-3s", "Mémoire de tonalité 3 s", "Mémoire plus courte que la valeur par défaut (4 s).", active = false) {
            it.copy(keyMemorySeconds = 3)
        },
        Experiment("inertie-3", "Inertie de tonalité 3", "Ancienne valeur de l'hystérésis de la tonalité retenue (1 aujourd'hui).", active = false) {
            it.copy(keyInertiaPercent = 3)
        },
        Experiment(
            "cadence", "Cadence parfaite : nouvelle tonalité tout de suite",
            "Un V7 → I vers une tonalité plausible (parmi les 3 plus probables) la fait retenir immédiatement.",
            active = false,
        ) { it.copy(cadenceKeyChange = true) },
        Experiment(
            "sans-changement-immediat", "Sans changement immédiat de tonalité",
            "Effet de l'option « Changement immédiat sur un accord hors tonalité ».",
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
