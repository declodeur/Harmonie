package fr.declodeur.harmonie.bench

import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.KeyParams
import fr.declodeur.harmonie.engine.withAllKeyParams

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
            "Contrôle : toutes les règles d'interprétation désactivées, ancien modèle de tonalité (mémoire de 12 s, inertie 3, sans terme tonique), comme avant le banc d'essai. " +
                "Mesure le chemin parcouru.",
            active = true,
        ) {
            it.withAllKeyParams { KeyParams.LEGACY }.copy(
                keyMemorySeconds = 12,
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
        Experiment(
            "notes-de-passage", "Notes de passage : l'accord continue",
            "Séparation harmonie / mélodie : une seule voix qui bouge par degré conjoint vers une note étrangère, les autres restant dans l'accord, ne change pas l'accord. " +
                "Essai du 6 novembre : +1,9 point sur les préludes de Bach, −0,9 point sur l'ensemble (désactivée par défaut).",
            active = false,
        ) { it.interp { i -> i.copy(passingTones = true) } },
        Experiment(
            "sans-reanalyse", "Sans réanalyse a posteriori",
            "Retire la réanalyse : à un changement de tonalité, les accords précédents ne sont pas rechiffrés depuis l'accord pivot. " +
                "Essai du 6 novembre : tonalité de l'historique −5,3 points (chorals de Bach −13,4).",
            active = false,
        ) { it.copy(reanalysisDepth = 0) },
        Experiment("fenetre-400", "Fenêtre harmonique de 400 ms", "Même essai avec une fenêtre plus courte.", active = false) {
            it.interp { i -> i.copy(harmonicWindowMs = 400) }
        },
        Experiment(
            "tonalite-0.9", "Modèle de tonalité de la version 0.9",
            "Contrôle : mémoire de 4 s, inertie 1, sans terme tonique ni mémoire en accords (modèle de la version 0.9).",
            active = false,
        ) { it.withAllKeyParams { KeyParams(0, 0, 0, 1) }.copy(keyMemorySeconds = 4) },
        Experiment(
            "tonalite-0.10", "Modèle de tonalité de la version 0.10",
            "Contrôle : inertie 1, sans maintien dans la gamme, sans arrivées ni premier accord (modèle de la version 0.10).",
            active = true,
        ) { it.withAllKeyParams { p -> p.copy(inertiaPercent = 1, holdPercent = 0, arrivalPercent = 0, primacyPercent = 0) } },
        Experiment(
            "sans-maintien", "Sans le maintien dans la gamme",
            "Retire la règle : la tonalité retenue garde la main tant que tout ce qu'on joue reste dans sa gamme.",
            active = false,
        ) { it.withAllKeyParams { p -> p.copy(holdPercent = 0) } },
        Experiment(
            "sans-arrivees", "Sans les arrivées ni le premier accord",
            "Retire les indices de tonique tirés des enchaînements (V → I, ♭VII → i, IV → I) et du premier accord (pop).",
            active = false,
        ) { it.withAllKeyParams { p -> p.copy(arrivalPercent = 0, primacyPercent = 0) } },
        Experiment(
            "arrivees-classique", "Arrivées et premier accord aussi en classique",
            "Contrôle : les indices de la pop appliqués au classique (essai du 6 novembre : −2,6 points).",
            active = false,
        ) { it.withAllKeyParams { p -> p.copy(arrivalPercent = 20, primacyPercent = 20) } },
        Experiment(
            "sans-terme-tonique", "Sans le terme tonique",
            "Retire le poids du temps passé sur l'accord de tonique (mémoire en accords conservée).",
            active = false,
        ) { it.withAllKeyParams { p -> p.copy(tonicPercent = 0, dominantPercent = 0) } },
        Experiment(
            "memoire-secondes", "Mémoire en secondes (4 s)",
            "Retire la mémoire comptée en accords : retour à une demi-vie de 4 s (terme tonique conservé).",
            active = false,
        ) { it.withAllKeyParams { p -> p.copy(memoryChords = 0) }.copy(keyMemorySeconds = 4) },
        Experiment(
            "reglages-pop-partout", "Réglages de tonalité pop pour tous les styles",
            "Contrôle : un seul jeu de réglages (celui de la pop) au lieu d'un par style.",
            active = false,
        ) { it.withAllKeyParams { KeyParams.POP } },
        Experiment(
            "reglages-classique-partout", "Réglages de tonalité classiques pour tous les styles",
            "Contrôle : un seul jeu de réglages (celui du classique) au lieu d'un par style.",
            active = false,
        ) { it.withAllKeyParams { KeyParams.CLASSICAL } },
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
        else -> wanted.split(';').map { it.trim() }.flatMap { part ->
            if (part.startsWith("reglage:")) listOfNotNull(custom(part))
            else part.split(',').map { it.trim() }.mapNotNull { id -> ALL.firstOrNull { it.id == id } }
        }
    }

    /**
     * Expérience ad hoc pour explorer des réglages : « reglage:t=40,d=30,m=8,i=1 »
     * (t = terme tonique %, d = part de la dominante %, m = mémoire s, i = inertie, c = cadence 0/1,
     * f = changement immédiat 0/1). Plusieurs expériences séparées par « ; ».
     */
    private fun custom(spec: String): Experiment? {
        val values = spec.removePrefix("reglage:").split(',').mapNotNull {
            val (k, v) = it.split('=').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
            k.trim() to (v.trim().toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
        if (values.isEmpty()) return null
        return Experiment(spec, "Réglages " + spec.removePrefix("reglage:"), "Exploration de réglages.", false) { s ->
            var r = s
            values["t"]?.let { v -> r = r.withAllKeyParams { it.copy(tonicPercent = v) } }
            values["d"]?.let { v -> r = r.withAllKeyParams { it.copy(dominantPercent = v) } }
            values["m"]?.let { r = r.copy(keyMemorySeconds = it) }
            values["n"]?.let { v -> r = r.withAllKeyParams { it.copy(memoryChords = v) } }
            values["i"]?.let { v -> r = r.withAllKeyParams { it.copy(inertiaPercent = v) } }
            values["h"]?.let { v -> r = r.withAllKeyParams { it.copy(holdPercent = v) } }
            values["r"]?.let { r = r.copy(reanalysisDepth = it) }
            values["np"]?.let { v -> r = r.copy(interpretation = r.interpretation.copy(passingTones = v == 1)) }
            values["a"]?.let { v -> r = r.withAllKeyParams { it.copy(arrivalPercent = v) } }
            values["p"]?.let { v -> r = r.withAllKeyParams { it.copy(primacyPercent = v) } }
            values["c"]?.let { r = r.copy(cadenceKeyChange = it == 1) }
            values["f"]?.let { r = r.copy(fastKeyChange = it == 1) }
            r
        }
    }
}

/** Une métrique du banc. */
enum class Metric(val label: String, val ok: (Verdict) -> Boolean) {
    CHORD("Accord", { it.chordOk }),
    EXACT("Accord exact", { it.exactOk }),
    KEY("Tonalité", { it.keyOk }),
    KEY_LATE_OK("Tonalité, retard toléré", { it.keyOk || it.keyError == KeyError.LATE }),
    KEY_HISTORY("Tonalité de l'historique", { it.historyKeyOk }),
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
