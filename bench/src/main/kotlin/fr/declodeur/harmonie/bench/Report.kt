package fr.declodeur.harmonie.bench

import fr.declodeur.harmonie.engine.Key
import fr.declodeur.harmonie.engine.Notes
import fr.declodeur.harmonie.engine.Spelling
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Rapport HTML du banc d'essai. Deux fichiers : `index.html` (page complète, à ouvrir dans un
 * navigateur) et `artifact.html` (même contenu sans squelette HTML, pour la publication en page web).
 */
object Report {
    private const val MAX_EXAMPLES = 8

    fun write(
        file: File,
        results: List<CorpusResult>,
        experiments: List<ExperimentResult>,
        history: List<HistoryRow>,
        commit: String,
        elapsedMs: Long,
        predictions: List<PredictionEval.Result> = emptyList(),
    ) {
        val head = head()
        val body = body(results, experiments, history, commit, elapsedMs, predictions)
        file.writeText(
            "<!doctype html>\n<html lang=\"fr\">\n<head>\n<meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">\n" +
                head + "\n</head>\n<body>\n" + body + "\n</body>\n</html>\n",
            Charsets.UTF_8,
        )
        File(file.parentFile, "artifact.html").writeText(head + "\n" + body + "\n", Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ Mise en page

    private fun head() = """
<title>Banc d'essai Harmonie</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Literata:opsz,wght@7..72,500;7..72,650&family=Source+Sans+3:wght@400;600&family=JetBrains+Mono:wght@400;600&display=swap">
<style>
/* Rapport d'analyse : une colonne de lecture, des barres à l'échelle 0-100 %, les exemples en tableaux défilants. */
:root {
  --bg: #f6f7f9; --surface: #ffffff; --fg: #1c2230; --muted: #5d6676; --line: #dde1e8;
  --accent: #2f6fe0; --accent-soft: #dbe6fb; --expert: #c06a12; --soft: #7a52c7; --good: #1f8a5b; --bad: #c4423a;
  --display: "Literata", Georgia, "Times New Roman", serif;
  --body: "Source Sans 3", "Segoe UI", system-ui, sans-serif;
  --mono: "JetBrains Mono", Consolas, "Courier New", monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #14161a; --surface: #1d2026; --fg: #eceef2; --muted: #9aa1ad; --line: #343944;
  --accent: #5b9bff; --accent-soft: #23324d; --expert: #f0a050; --soft: #b98cff; --good: #4fd1a1; --bad: #ff7b72; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #14161a; --surface: #1d2026; --fg: #eceef2; --muted: #9aa1ad; --line: #343944;
  --accent: #5b9bff; --accent-soft: #23324d; --expert: #f0a050; --soft: #b98cff; --good: #4fd1a1; --bad: #ff7b72; color-scheme: dark }
body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.55 var(--body); }
.page { max-width: 1040px; margin: 0 auto; padding-block: 32px 64px;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px));
  display: flex; flex-direction: column; gap: 40px; }
h1, h2, h3 { font-family: var(--display); font-weight: 650; text-wrap: balance; margin: 0; line-height: 1.2; }
h1 { font-size: 2.1rem; }
h2 { font-size: 1.45rem; }
h3 { font-size: 1.1rem; font-weight: 500; }
p { margin: 0; max-width: 68ch; }
section { display: flex; flex-direction: column; gap: 16px; }
.meta { color: var(--muted); font-size: 0.92rem; display: flex; flex-wrap: wrap; gap: 4px 18px; }
.lead { font-size: 1.05rem; }
.muted { color: var(--muted); }
.mono { font-family: var(--mono); font-size: 0.9em; }
.num { font-variant-numeric: tabular-nums; }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 20px; display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.scores { display: grid; grid-template-columns: minmax(130px, 220px) 1fr 64px; gap: 10px 16px; align-items: center; }
.scores .label { font-weight: 600; }
.scores .label small { display: block; font-weight: 400; color: var(--muted); font-size: 0.82rem; line-height: 1.3; }
.bar { position: relative; height: 14px; background: var(--accent-soft); border-radius: 7px; }
.bar .fill { position: absolute; inset: 0 auto 0 0; background: var(--accent); border-radius: 7px; }
.bar .mark { position: absolute; top: -5px; bottom: -5px; width: 3px; margin-left: -1px; background: var(--expert); border-radius: 2px; }
.bar .mark.soft { background: var(--soft); }
.swatch.soft { width: 3px; height: 14px; background: var(--soft); border-radius: 2px; }
.ref { display: block; color: var(--muted); font-size: 0.78rem; font-weight: 400; white-space: nowrap; }
td.delta { font-variant-numeric: tabular-nums; text-align: right; white-space: nowrap; }
.pill { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: 0.8rem; font-weight: 600; background: var(--accent-soft); color: var(--accent); }
.value { text-align: right; font-weight: 600; font-variant-numeric: tabular-nums; }
.legend { display: flex; flex-wrap: wrap; gap: 6px 20px; font-size: 0.88rem; color: var(--muted); align-items: center; }
.swatch { display: inline-block; width: 12px; height: 12px; border-radius: 3px; background: var(--accent); vertical-align: -1px; margin-right: 6px; }
.swatch.expert { width: 3px; height: 14px; background: var(--expert); border-radius: 2px; }
.category { display: flex; flex-direction: column; gap: 10px; border-top: 1px solid var(--line); padding-top: 16px; }
.category header { display: flex; flex-wrap: wrap; gap: 4px 14px; align-items: baseline; }
.category header .count { font-variant-numeric: tabular-nums; color: var(--muted); }
.share { height: 6px; background: var(--accent-soft); border-radius: 3px; max-width: 360px; }
.share > div { height: 100%; background: var(--bad); border-radius: 3px; }
.scroll { overflow-x: auto; min-width: 0; }
table { border-collapse: collapse; width: 100%; font-size: 0.92rem; }
th, td { text-align: left; padding: 6px 10px; border-bottom: 1px solid var(--line); vertical-align: top; }
th { font-weight: 600; color: var(--muted); font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.04em; white-space: nowrap; }
td.n, th.n { text-align: right; font-variant-numeric: tabular-nums; }
.app { color: var(--accent); }
.exp { color: var(--expert); }
.up { color: var(--good); } .down { color: var(--bad); }
details summary { cursor: pointer; color: var(--accent); font-weight: 600; }
details summary:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
details[open] summary { margin-bottom: 10px; }
a { color: var(--accent); }
ul { margin: 0; padding-left: 1.2em; display: flex; flex-direction: column; gap: 4px; max-width: 72ch; }
@media (max-width: 560px) {
  .scores { grid-template-columns: 1fr 56px; }
  .scores .label { grid-column: 1 / -1; margin-top: 6px; }
  h1 { font-size: 1.7rem; }
}
</style>"""

    private fun body(
        results: List<CorpusResult>,
        experiments: List<ExperimentResult>,
        history: List<HistoryRow>,
        commit: String,
        elapsedMs: Long,
        predictions: List<PredictionEval.Result>,
    ): String {
        val date = LocalDateTime.now(ZoneId.of("Europe/Paris")).format(DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH))
        val sb = StringBuilder()
        sb.append("<main class=\"page\">\n")
        sb.append("<header style=\"display:flex;flex-direction:column;gap:10px\">\n")
        sb.append("<h1>Banc d'essai Harmonie</h1>\n")
        sb.append("<div class=\"meta\"><span>$date</span><span>moteur au commit <span class=\"mono\">${esc(commit)}</span></span>")
        sb.append("<span>calcul : ${"%.0f".format(elapsedMs / 1000.0)} s</span></div>\n")
        sb.append(
            "<p class=\"lead\">Des morceaux analysés par des experts sont rejoués dans le moteur d'Harmonie, note par note, " +
                "comme si on les jouait en direct. Pour chaque accord noté par l'expert, on compare ce que l'application " +
                "affiche à ce que l'expert a écrit.</p>\n",
        )
        sb.append("</header>\n")
        overview(sb, results)
        if (predictions.isNotEmpty()) predictionSection(sb, predictions)
        if (experiments.isNotEmpty()) experimentsSection(sb, experiments)
        for (r in results) corpus(sb, r, history)
        method(sb, results)
        sb.append("</main>")
        return sb.toString()
    }

    // ------------------------------------------------------------------ Un corpus

    private val METRIC_SUB = mapOf(
        Metric.CHORD to "bonne fondamentale et bon type (majeur, mineur, diminué…)",
        Metric.EXACT to "même chose, septième comprise",
        Metric.KEY to "même tonalité que l'expert à cet instant",
        Metric.KEY_HISTORY to "tonalité des accords dans l'historique en fin de morceau, après la réanalyse a posteriori (accord pivot)",
        Metric.KEY_LATE_OK to "en acceptant l'ancienne tonalité pendant les 4 temps qui suivent une modulation de l'expert (il la place dès l'accord pivot ; en direct, on ne l'entend qu'après)",
        Metric.NUMERAL to "accord et tonalité justes, donc même chiffrage romain",
    )

    /** Accord d'une référence (expert B ou logiciel) avec l'expert A pour une métrique, si elle a un sens. */
    private fun refScore(ref: Reference?, m: Metric): Double? {
        ref ?: return null
        val a = ref.agreement
        return when (m) {
            Metric.CHORD -> pct(a.count { it.first }, a.size)
            Metric.KEY -> pct(a.count { it.second }, a.size)
            Metric.NUMERAL -> pct(a.count { it.first && it.second }, a.size)
            Metric.EXACT, Metric.KEY_LATE_OK, Metric.KEY_HISTORY -> null
        }
    }

    private fun overview(sb: StringBuilder, results: List<CorpusResult>) {
        sb.append("<section>\n<h2>Vue d'ensemble</h2>\n<div class=\"card\">\n<div class=\"scroll\"><table>\n<tr><th>Corpus</th><th class=\"n\">Morceaux</th><th class=\"n\">Accords</th>")
        Metric.entries.forEach { sb.append("<th class=\"n\">${esc(it.label)}</th>") }
        sb.append("<th class=\"n\">Changements de tonalité</th><th class=\"n\">Changements d'accord</th>")
        sb.append("</tr>\n")
        for (r in results) {
            sb.append("<tr><td><a href=\"#${r.corpus.id}\">${esc(r.corpus.name)}</a></td><td class=\"n\">${r.pieces.size}</td><td class=\"n\">${fmtInt(r.verdicts.size)}</td>")
            for (m in Metric.entries) {
                sb.append("<td class=\"n\">${p1(pct(r.verdicts.count(m.ok), r.verdicts.size))}")
                refScore(r.expert, m)?.let { sb.append("<span class=\"ref exp\">experts ${p1(it)}</span>") }
                refScore(r.software, m)?.let { sb.append("<span class=\"ref\" style=\"color:var(--soft)\">logiciel ${p1(it)}</span>") }
                sb.append("</td>")
            }
            sb.append("<td class=\"n\">${ratio(r.changeRatio)}<span class=\"ref\">${fmtInt(r.appKeyChanges)} contre ${fmtInt(r.expertKeyChanges)}</span></td>")
            sb.append("<td class=\"n\">${"×%.2f".format(java.util.Locale.FRANCE, r.chordChangeRatio)}<span class=\"ref\">${fmtInt(r.appChordChanges)} contre ${fmtInt(r.expertChordChanges)}</span></td>")
            sb.append("</tr>\n")
        }
        sb.append("</table></div>\n")
        sb.append(
            "<p class=\"muted\">Changements de tonalité : combien de fois la tonalité retenue par l'application change, " +
                "rapporté aux changements notés par l'expert (×1 = autant ; ×3 = trois fois trop). Une tonalité qui " +
                "hésite d'un accord à l'autre se voit ici même quand son score reste correct. Changements d'accord : le " +
                "rythme harmonique, nombre d'accords différents affichés par l'application rapporté à ceux de l'expert " +
                "(au-dessus de ×1, l'application voit des accords là où l'expert lit une seule harmonie : notes de " +
                "passage, figurations).</p>\n",
        )
        sb.append(
            "<p class=\"muted\">Sous chaque score : l'accord entre deux experts (quand le corpus a un second avis) et l'accord " +
                "entre l'expert et AugmentedNet, logiciel d'analyse qui lit la partition entière hors ligne (il a été entraîné " +
                "sur ces corpus, il est donc avantagé). L'application, elle, analyse en direct sans connaître la suite.</p>\n",
        )
        sb.append("</div>\n</section>\n")
    }

    private fun predictionSection(sb: StringBuilder, predictions: List<PredictionEval.Result>) {
        sb.append("<section id=\"suites\">\n<h2>Suites probables</h2>\n<div class=\"card\">\n")
        sb.append(
            "<p class=\"muted\">À chaque changement d'accord de l'expert, le modèle reçoit les accords précédents " +
                "(chiffrés par l'expert) et doit deviner le suivant. Score = part des cas où l'accord de l'expert est la " +
                "première proposition de l'arbre, ou l'une des quatre premières. Les statistiques des corpus sont " +
                "mesurées en validation croisée : chaque morceau est prédit par un modèle appris sur les autres.</p>\n",
        )
        sb.append("<div class=\"scroll\"><table>\n<tr><th>Corpus</th><th class=\"n\">Enchaînements</th>")
        sb.append("<th class=\"n\">Théorie : 1re</th><th class=\"n\">Théorie : 4 premières</th>")
        sb.append("<th class=\"n\">Corpus : 1re</th><th class=\"n\">Corpus : 4 premières</th></tr>\n")
        fun cell(v: Double, ref: Double) = when {
            v >= ref + 0.05 -> "<td class=\"n up\">${p1(v)}</td>"
            v <= ref - 0.05 -> "<td class=\"n down\">${p1(v)}</td>"
            else -> "<td class=\"n\">${p1(v)}</td>"
        }
        for (p in predictions) {
            sb.append("<tr><td>${esc(p.corpus.name)}</td><td class=\"n\">${fmtInt(p.theory.n)}</td>")
            sb.append("<td class=\"n\">${p1(p.theory.p1)}</td><td class=\"n\">${p1(p.theory.p4)}</td>")
            sb.append(cell(p.learned.p1, p.theory.p1) + cell(p.learned.p4, p.theory.p4) + "</tr>\n")
        }
        sb.append("</table></div>\n</div>\n</section>\n")
    }

    private fun experimentsSection(sb: StringBuilder, experiments: List<ExperimentResult>) {
        sb.append("<section>\n<h2>Expériences</h2>\n")
        sb.append("<p class=\"muted\">Chaque expérience joue une variante des réglages et la compare à la version actuelle, accord par accord.</p>\n")
        for (x in experiments) {
            val e = x.experiment
            sb.append("<div class=\"card\">\n<header style=\"display:flex;flex-wrap:wrap;gap:6px 14px;align-items:baseline\"><h3>${esc(e.name)}</h3><span class=\"pill\">${esc(e.id)}</span></header>\n")
            sb.append("<p>${esc(e.hypothesis)}</p>\n<div class=\"scroll\"><table>\n<tr><th>Corpus</th>")
            Metric.entries.forEach { sb.append("<th class=\"n\">${esc(it.label)}</th>") }
            sb.append("<th class=\"n\">Changements de tonalité</th>")
            sb.append("</tr>\n")
            fun deltaCell(d: Double) = when {
                d >= 0.05 -> "<td class=\"delta up\">+${p1n(d)}</td>"
                d <= -0.05 -> "<td class=\"delta down\">−${p1n(-d)}</td>"
                else -> "<td class=\"delta muted\">=</td>"
            }
            for (p in x.pairings) {
                sb.append("<tr><td>${esc(p.base.corpus.name)}</td>")
                Metric.entries.forEach { sb.append(deltaCell(p.delta(it))) }
                val better = p.variant.changeRatio < p.base.changeRatio - 0.05
                val worse = p.variant.changeRatio > p.base.changeRatio + 0.05
                val cls = if (better) "up" else if (worse) "down" else "muted"
                sb.append("<td class=\"delta $cls\">${ratio(p.base.changeRatio)} → ${ratio(p.variant.changeRatio)}</td>")
                sb.append("</tr>\n")
            }
            sb.append("<tr><td><b>Ensemble</b></td>")
            Metric.entries.forEach { sb.append(deltaCell(x.overallDelta(it)).replace("<td class=\"delta", "<td style=\"font-weight:600\" class=\"delta")) }
            sb.append("<td></td></tr>\n</table></div>\n<p class=\"muted\">Écarts en points de pourcentage ; changements de tonalité : version actuelle → variante, rapportés à l'expert (moins = plus stable).</p>\n")
            for (m in listOf(Metric.CHORD, Metric.KEY)) {
                val gained = x.pairings.flatMap { p -> p.gained(m).map { p.variant.verdicts[it] } }
                val lost = x.pairings.flatMap { p -> p.lost(m).map { p.variant.verdicts[it] } }
                if (gained.isEmpty() && lost.isEmpty()) continue
                sb.append("<p><b>${esc(m.label)}</b> : ${fmtInt(gained.size)} accords gagnés, ${fmtInt(lost.size)} perdus.</p>\n")
                if (gained.isNotEmpty()) {
                    sb.append("<details><summary>Exemples gagnés</summary><div class=\"scroll\">")
                    examplesTable(sb, spread(gained, MAX_EXAMPLES))
                    sb.append("</div></details>\n")
                }
                if (lost.isNotEmpty()) {
                    sb.append("<details><summary>Exemples perdus</summary><div class=\"scroll\">")
                    examplesTable(sb, spread(lost, MAX_EXAMPLES))
                    sb.append("</div></details>\n")
                }
            }
            sb.append("</div>\n")
        }
        sb.append("</section>\n")
    }

    private fun corpus(sb: StringBuilder, r: CorpusResult, history: List<HistoryRow>) {
        val v = r.verdicts
        val n = v.size
        sb.append("<section id=\"${r.corpus.id}\">\n<h2>${esc(r.corpus.name)}</h2>\n")
        sb.append(
            "<p class=\"muted\">${esc(r.corpus.description)} ${r.pieces.size} morceaux rejoués, ${fmtInt(n)} accords comparés ; " +
                "style ${esc(r.corpus.style.label)}.</p>\n",
        )
        sb.append("<div class=\"card\">\n<h3>Scores</h3>\n<div class=\"scores\">\n")
        for (m in Metric.entries) {
            val value = pct(v.count(m.ok), n)
            sb.append("<div class=\"label\">${esc(m.label)}<small>${esc(METRIC_SUB.getValue(m))}</small></div>")
            sb.append("<div class=\"bar\" role=\"img\" aria-label=\"${esc(m.label)} : ${p1(value)}\">")
            sb.append("<div class=\"fill\" style=\"width:${css(value)}%\"></div>")
            refScore(r.expert, m)?.let { sb.append("<div class=\"mark\" style=\"left:${css(it)}%\" title=\"Experts entre eux : ${p1(it)}\"></div>") }
            refScore(r.software, m)?.let { sb.append("<div class=\"mark soft\" style=\"left:${css(it)}%\" title=\"Logiciel AugmentedNet : ${p1(it)}\"></div>") }
            sb.append("</div><div class=\"value\">${p1(value)}</div>\n")
        }
        sb.append("</div>\n")
        if (r.expert != null || r.software != null) {
            sb.append("<div class=\"legend\"><span><span class=\"swatch\"></span>application</span>")
            r.expert?.let {
                sb.append("<span><span class=\"swatch expert\"></span>second expert (${esc(it.label)}, ${fmtInt(it.agreement.size)} accords de ${countPieces(it.app)} morceaux)</span>")
            }
            r.software?.let { sb.append("<span><span class=\"swatch soft\"></span>logiciel AugmentedNet</span>") }
            sb.append("</div>\n")
            r.expert?.let { ref ->
                sb.append(
                    "<p class=\"muted\">Sur les ${countPieces(ref.app)} morceaux qui ont un second avis, l'application obtient " +
                        "${p1(pct(ref.app.count { it.chordOk }, ref.app.size))} pour l'accord et ${p1(pct(ref.app.count { it.keyOk }, ref.app.size))} " +
                        "pour la tonalité. Le repère orange montre jusqu'où il est raisonnable d'espérer aller : au-delà, on ne ferait " +
                        "que copier les habitudes d'un expert.</p>\n",
                )
            }
        }
        sb.append("</div>\n")
        evolution(sb, r, history)
        chordErrors(sb, r)
        seventhErrors(sb, r)
        keyErrors(sb, r)
        piecesTable(sb, r)
        sb.append("</section>\n")
    }

    private fun countPieces(v: List<Verdict>) = v.map { it.piece.id }.distinct().size

    private fun evolution(sb: StringBuilder, r: CorpusResult, history: List<HistoryRow>) {
        val rows = history.filter { it.corpus == r.corpus.id }.takeLast(10)
        if (rows.size < 2) return
        sb.append("<div class=\"card\">\n<h3>Évolution</h3>\n<div class=\"scroll\"><table>\n")
        sb.append("<tr><th>Date</th><th>Commit</th><th class=\"n\">Accord</th><th class=\"n\">Exact</th><th class=\"n\">Tonalité</th><th class=\"n\">Chiffrage</th></tr>\n")
        var prev: HistoryRow? = null
        for (h in rows) {
            fun cell(x: Double, p: Double?): String {
                val d = if (p == null) "" else {
                    val delta = x - p
                    when {
                        delta >= 0.05 -> " <span class=\"up\">+${p1n(delta)}</span>"
                        delta <= -0.05 -> " <span class=\"down\">−${p1n(-delta)}</span>"
                        else -> ""
                    }
                }
                return "<td class=\"n\">${p1(x)}$d</td>"
            }
            sb.append("<tr><td>${esc(h.date)}</td><td class=\"mono\">${esc(h.commit)}</td>")
            sb.append(cell(h.chord, prev?.chord) + cell(h.exact, prev?.exact) + cell(h.key, prev?.key) + cell(h.numeral, prev?.numeral))
            sb.append("</tr>\n")
            prev = h
        }
        sb.append("</table></div>\n</div>\n")
    }

    private fun chordErrors(sb: StringBuilder, r: CorpusResult) {
        val wrong = r.verdicts.filter { !it.chordOk }
        if (wrong.isEmpty()) return
        sb.append("<div class=\"card\">\n<h3>Désaccords sur l'accord</h3>\n")
        sb.append("<p class=\"muted\">${fmtInt(wrong.size)} accords sur ${fmtInt(r.verdicts.size)}, classés par cause probable, la plus fréquente d'abord.</p>\n")
        for ((cat, list) in wrong.groupBy { it.chordError!! }.toList().sortedByDescending { it.second.size }) {
            category(sb, cat.label, cat.explanation, list, wrong.size)
        }
        sb.append("</div>\n")
    }

    private fun seventhErrors(sb: StringBuilder, r: CorpusResult) {
        val list = r.verdicts.filter { it.seventhNote != null }
        if (list.isEmpty()) return
        sb.append("<div class=\"card\">\n<h3>Accord juste, septième différente</h3>\n")
        sb.append("<p class=\"muted\">Comptés justes pour l'accord, faux pour l'accord exact.</p>\n")
        for ((label, l) in list.groupBy { it.seventhNote!! }.toList().sortedByDescending { it.second.size }) {
            val expl = if (label == "Septième en plus") "L'application ajoute une septième que l'expert considère comme une note de passage."
            else "L'expert note une septième que l'application ne lit pas (souvent une septième de passage, ou absente au moment observé)."
            category(sb, label, expl, l, list.size)
        }
        sb.append("</div>\n")
    }

    private fun keyErrors(sb: StringBuilder, r: CorpusResult) {
        val wrong = r.verdicts.filter { !it.keyOk }
        if (wrong.isEmpty()) return
        sb.append("<div class=\"card\">\n<h3>Désaccords sur la tonalité</h3>\n")
        sb.append("<p class=\"muted\">${fmtInt(wrong.size)} accords sur ${fmtInt(r.verdicts.size)}. Rappel : l'expert connaît la suite du morceau, l'application seulement le passé.</p>\n")
        for ((cat, list) in wrong.groupBy { it.keyError!! }.toList().sortedByDescending { it.second.size }) {
            category(sb, cat.label, cat.explanation, list, wrong.size)
        }
        sb.append("</div>\n")
    }

    private fun category(sb: StringBuilder, label: String, explanation: String, list: List<Verdict>, total: Int) {
        val share = pct(list.size, total)
        sb.append("<div class=\"category\">\n<header><h3>${esc(label)}</h3><span class=\"count\">${fmtInt(list.size)} · ${p1(share)} des désaccords</span></header>\n")
        sb.append("<div class=\"share\" aria-hidden=\"true\"><div style=\"width:${css(share)}%\"></div></div>\n")
        sb.append("<p class=\"muted\">${esc(explanation)}</p>\n")
        val examples = spread(list, MAX_EXAMPLES)
        sb.append("<div class=\"scroll\">")
        examplesTable(sb, examples.take(4))
        sb.append("</div>\n")
        if (examples.size > 4) {
            sb.append("<details><summary>${examples.size - 4} autres exemples</summary><div class=\"scroll\">")
            examplesTable(sb, examples.drop(4))
            sb.append("</div></details>\n")
        }
        sb.append("</div>\n")
    }

    private fun examplesTable(sb: StringBuilder, list: List<Verdict>) {
        sb.append("<table>\n<tr><th>Morceau</th><th>Moment</th><th>Notes jouées</th><th>Expert</th><th>Application</th></tr>\n")
        for (v in list) {
            val e = v.expert
            val key = Key(e.tonic, e.minor)
            val sp = key.spelling
            val t = e.start + minOf(0.25, (e.end - e.start) / 2)
            val sounding = v.piece.notes.filter { it.start <= t + 1e-6 && t < it.start + it.duration - 1e-6 }
                .map { it.midi }.distinct().sorted()
                .joinToString(" ") { Notes.nameWithOctave(it, sp) }
            val app = v.shown
            val appSp = app.key?.spelling ?: sp
            val appText = buildString {
                append(app.chord?.symbolIn(appSp) ?: "aucun accord")
                app.analysis?.let { append(" · ").append(it.numeral) }
                append(" · ").append(app.key?.name ?: "pas de tonalité")
            }
            val where = if (e.measure < 0) "${esc(e.beat)} s" else "m${e.measure} t${esc(e.beat)}"
            val who = if (v.piece.id.startsWith("isophonics_")) v.piece.title else v.piece.id
            sb.append("<tr><td>${esc(who)}</td><td class=\"num\">$where</td>")
            sb.append("<td class=\"mono\">${esc(sounding)}</td>")
            sb.append("<td class=\"exp\"><span class=\"mono\">${esc(e.figure)}</span> en ${esc(key.name)} = <b>${esc(expertSymbol(e, sp))}</b></td>")
            sb.append("<td class=\"app\">${esc(appText)}</td></tr>\n")
        }
        sb.append("</table>")
    }

    private fun piecesTable(sb: StringBuilder, r: CorpusResult) {
        val rows = r.verdicts.groupBy { it.piece }.map { (p, v) ->
            listOf(pct(v.count { it.chordOk }, v.size), pct(v.count { it.keyOk }, v.size), pct(v.count { it.numeralOk }, v.size)) to (p to v.size)
        }.sortedBy { it.first[0] }
        sb.append("<div class=\"card\">\n<h3>Morceau par morceau</h3>\n")
        sb.append("<p class=\"muted\">Du moins bien analysé au mieux analysé (score d'accord).</p>\n")
        fun table(list: List<Pair<List<Double>, Pair<Piece, Int>>>) {
            sb.append("<div class=\"scroll\"><table>\n<tr><th>Morceau</th><th class=\"n\">Accords</th><th class=\"n\">Accord</th><th class=\"n\">Tonalité</th><th class=\"n\">Chiffrage</th></tr>\n")
            for ((s, pn) in list) {
                sb.append("<tr><td>${esc(pn.first.title)}</td><td class=\"n\">${pn.second}</td>")
                s.forEach { sb.append("<td class=\"n\">${p1(it)}</td>") }
                sb.append("</tr>\n")
            }
            sb.append("</table></div>\n")
        }
        table(rows.take(10))
        if (rows.size > 10) {
            sb.append("<details><summary>Les ${rows.size - 10} autres</summary>")
            table(rows.drop(10))
            sb.append("</details>\n")
        }
        if (r.excluded.isNotEmpty()) {
            sb.append(
                "<p class=\"muted\">Écartés (${r.excluded.size}) car les mesures de la partition ne correspondent pas à celles de l'analyse : " +
                    esc(r.excluded.joinToString(", ") { it.id }) + ".</p>\n",
            )
        }
        sb.append("</div>\n")
    }

    private fun method(sb: StringBuilder, results: List<CorpusResult>) {
        sb.append("<section>\n<h2>Méthode</h2>\n<ul>\n")
        sb.append("<li>Chaque morceau est joué à tempo constant (d'après son indication de tempo quand elle existe, sinon 80 ou 120 à la noire), toutes les voix sur un seul canal (mode d'analyse Complète), sans pédale, avec les réglages par défaut de l'application et le style indiqué.</li>\n")
        sb.append("<li>Pour chaque accord de l'expert, on retient ce que l'application affiche le plus longtemps pendant sa durée, en laissant d'abord ${Compare.SETTLE_MS} ms à l'application pour valider l'accord.</li>\n")
        sb.append("<li>Accord juste : même fondamentale et même type d'accord parfait (majeur, mineur, diminué, augmenté). La basse et le renversement ne comptent pas. Sixtes augmentées : mêmes notes.</li>\n")
        sb.append("<li>Les chiffrages des experts sont traduits en notes selon les conventions de music21, référence de ces corpus (en mineur, vi et vii sur les degrés hauts, VI et VII sur les degrés naturels ; X/Y dans la tonalité de Y).</li>\n")
        sb.append("<li>Les deux corpus d'experts ne sont pas indépendants de toute convention : leur accord mesure une limite raisonnable, pas une vérité absolue.</li>\n")
        sb.append("</ul>\n<h3>Sources</h3>\n<ul>\n")
        sb.append("<li><a href=\"https://github.com/MarkGotham/When-in-Rome\" target=\"_blank\" rel=\"noopener\">When in Rome</a> (Gotham, Micchi, Nápoles López, Sailor), analyses en chiffrage romain, licence CC BY-SA 4.0.</li>\n")
        sb.append("<li><a href=\"https://github.com/MarkGotham/Chorale-Corpus\" target=\"_blank\" rel=\"noopener\">Chorale-Corpus</a> pour les partitions des chorals, et le Bach Chorales Melody-Harmony Corpus (BCMH) pour la seconde analyse.</li>\n")
        sb.append("<li>Sonates de Mozart : partitions et analyses du <a href=\"https://github.com/DCMLab/mozart_piano_sonatas\" target=\"_blank\" rel=\"noopener\">DCML</a> (CC BY-NC-SA 4.0) ; second avis de Dmitri Tymoczko.</li>\n")
        sb.append("<li>Exemples de manuels : <a href=\"https://github.com/DDMAL/key_modulation_dataset\" target=\"_blank\" rel=\"noopener\">key_modulation_dataset</a> (Nápoles López et al.). Préludes de Bach et Winterreise : analyses de Mark Gotham et al. ; partitions OpenScore (CC0).</li>\n")
        sb.append("<li>AugmentedNet (Nápoles López) : analyses automatiques fournies avec When in Rome.</li>\n")
        sb.append("</ul>\n</section>\n")
    }

    // ------------------------------------------------------------------ Outils

    /** Symbole de l'accord de l'expert, dans la notation de l'application quand c'est possible. */
    fun expertSymbol(e: ExpertChord, sp: Spelling): String {
        when (e.special) {
            "It" -> return "sixte italienne (" + e.pitchClasses.joinToString(" ") { Notes.name(it, sp) } + ")"
            "Ger" -> return "sixte allemande (" + e.pitchClasses.joinToString(" ") { Notes.name(it, sp) } + ")"
            "Fr" -> return "sixte française (" + e.pitchClasses.joinToString(" ") { Notes.name(it, sp) } + ")"
        }
        val q = Quality.of(e.root, e.pitchClasses)
        val suffix = when (q.triad to q.seventh) {
            "maj" to "" -> ""
            "maj" to "7" -> "7"
            "maj" to "M7" -> "M7"
            "min" to "" -> "m"
            "min" to "7" -> "m7"
            "min" to "M7" -> "mM7"
            "dim" to "" -> "dim"
            "dim" to "7" -> "m7♭5"
            "dim" to "o7" -> "dim7"
            "aug" to "" -> "aug"
            "aug" to "7" -> "7aug"
            "aug" to "M7" -> "M7aug"
            else -> "(" + q + ")"
        }
        val slash = if (e.bass != e.root) "/" + Notes.name(e.bass, sp) else ""
        return Notes.name(e.root, sp) + suffix + slash
    }

    /** Au plus [k] éléments répartis régulièrement dans la liste (exemples variés, toujours les mêmes). */
    private fun <T> spread(list: List<T>, k: Int): List<T> =
        if (list.size <= k) list else (0 until k).map { list[it * list.size / k] }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun p1(x: Double) = "%.1f".format(Locale.FRENCH, x).replace(' ', ' ') + " %"
    private fun p1n(x: Double) = "%.1f".format(Locale.FRENCH, x)
    private fun ratio(x: Double) = "×" + "%.1f".format(Locale.FRENCH, x)
    private fun css(x: Double) = "%.2f".format(Locale.ROOT, x.coerceIn(0.0, 100.0))
    private fun fmtInt(n: Int) = "%,d".format(Locale.FRENCH, n).replace(' ', ' ')
}
