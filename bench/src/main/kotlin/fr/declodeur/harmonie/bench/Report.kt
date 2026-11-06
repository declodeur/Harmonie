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

    fun write(file: File, results: List<CorpusResult>, history: List<HistoryRow>, commit: String, elapsedMs: Long) {
        val head = head()
        val body = body(results, history, commit, elapsedMs)
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
  --accent: #2f6fe0; --accent-soft: #dbe6fb; --expert: #c06a12; --good: #1f8a5b; --bad: #c4423a;
  --display: "Literata", Georgia, "Times New Roman", serif;
  --body: "Source Sans 3", "Segoe UI", system-ui, sans-serif;
  --mono: "JetBrains Mono", Consolas, "Courier New", monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #14161a; --surface: #1d2026; --fg: #eceef2; --muted: #9aa1ad; --line: #343944;
  --accent: #5b9bff; --accent-soft: #23324d; --expert: #f0a050; --good: #4fd1a1; --bad: #ff7b72; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #14161a; --surface: #1d2026; --fg: #eceef2; --muted: #9aa1ad; --line: #343944;
  --accent: #5b9bff; --accent-soft: #23324d; --expert: #f0a050; --good: #4fd1a1; --bad: #ff7b72; color-scheme: dark }
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

    private fun body(results: List<CorpusResult>, history: List<HistoryRow>, commit: String, elapsedMs: Long): String {
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
        for (r in results) corpus(sb, r, history)
        method(sb, results)
        sb.append("</main>")
        return sb.toString()
    }

    // ------------------------------------------------------------------ Un corpus

    private fun corpus(sb: StringBuilder, r: CorpusResult, history: List<HistoryRow>) {
        val v = r.verdicts
        val n = v.size
        val ref = r.appOnExpertPieces
        val ex = r.experts
        sb.append("<section>\n<h2>${esc(r.corpus.name)}</h2>\n")
        sb.append(
            "<p class=\"muted\">${esc(r.corpus.description)} ${r.pieces.size} morceaux rejoués, ${fmtInt(n)} accords comparés ; " +
                "tempo ${r.corpus.tempo} à la noire, style ${esc(r.corpus.style.label)}.</p>\n",
        )
        sb.append("<div class=\"card\">\n<h3>Scores</h3>\n<div class=\"scores\">\n")
        fun row(label: String, sub: String, ok: (Verdict) -> Boolean, expertOk: ((Pair<Boolean, Boolean>) -> Boolean)?) {
            val value = pct(v.count(ok), n)
            val mark = if (expertOk != null && ex.isNotEmpty()) pct(ex.count(expertOk), ex.size) else null
            sb.append("<div class=\"label\">${esc(label)}<small>${esc(sub)}</small></div>")
            sb.append("<div class=\"bar\" role=\"img\" aria-label=\"${esc(label)} : ${p1(value)}\">")
            sb.append("<div class=\"fill\" style=\"width:${css(value)}%\"></div>")
            if (mark != null) sb.append("<div class=\"mark\" style=\"left:${css(mark)}%\" title=\"Experts entre eux : ${p1(mark)}\"></div>")
            sb.append("</div><div class=\"value\">${p1(value)}</div>\n")
        }
        row("Accord", "bonne fondamentale et bon type (majeur, mineur, diminué…)", { it.chordOk }, { it.first })
        row("Accord exact", "même chose, septième comprise", { it.exactOk }, null)
        row("Tonalité", "même tonalité que l'expert à cet instant", { it.keyOk }, { it.second })
        row("Chiffrage", "accord et tonalité justes, donc même chiffrage romain", { it.numeralOk }, { it.first && it.second })
        sb.append("</div>\n")
        if (ex.isNotEmpty()) {
            sb.append("<div class=\"legend\"><span><span class=\"swatch\"></span>application</span>")
            sb.append("<span><span class=\"swatch expert\"></span>accord entre deux corpus d'experts (${fmtInt(ex.size)} accords de ${countPieces(ref)} chorals)</span></div>\n")
            sb.append(
                "<p class=\"muted\">Sur ces mêmes ${countPieces(ref)} chorals, l'application obtient ${p1(pct(ref.count { it.chordOk }, ref.size))} " +
                    "pour l'accord et ${p1(pct(ref.count { it.keyOk }, ref.size))} pour la tonalité. Le repère orange montre jusqu'où " +
                    "il est raisonnable d'espérer aller : au-delà, on ne ferait que copier les habitudes d'un expert.</p>\n",
            )
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
        sb.append("<table>\n<tr><th>Choral</th><th>Mesure</th><th>Notes jouées</th><th>Expert</th><th>Application</th></tr>\n")
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
            sb.append("<tr><td>${esc(v.piece.id)}</td><td class=\"num\">m${e.measure} t${esc(e.beat)}</td>")
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
        sb.append("<li>Chaque morceau est joué à tempo constant, toutes les voix sur un seul canal (mode d'analyse Complète), sans pédale, avec les réglages par défaut de l'application et le style indiqué.</li>\n")
        sb.append("<li>Pour chaque accord de l'expert, on retient ce que l'application affiche le plus longtemps pendant sa durée, en laissant d'abord ${Compare.SETTLE_MS} ms à l'application pour valider l'accord.</li>\n")
        sb.append("<li>Accord juste : même fondamentale et même type d'accord parfait (majeur, mineur, diminué, augmenté). La basse et le renversement ne comptent pas. Sixtes augmentées : mêmes notes.</li>\n")
        sb.append("<li>Les chiffrages des experts sont traduits en notes selon les conventions de music21, référence de ces corpus (en mineur, vi et vii sur les degrés hauts, VI et VII sur les degrés naturels ; X/Y dans la tonalité de Y).</li>\n")
        sb.append("<li>Les deux corpus d'experts ne sont pas indépendants de toute convention : leur accord mesure une limite raisonnable, pas une vérité absolue.</li>\n")
        sb.append("</ul>\n<h3>Sources</h3>\n<ul>\n")
        sb.append("<li><a href=\"https://github.com/MarkGotham/When-in-Rome\" target=\"_blank\" rel=\"noopener\">When in Rome</a> (Gotham, Micchi, Nápoles López, Sailor), analyses en chiffrage romain, licence CC BY-SA 4.0.</li>\n")
        sb.append("<li><a href=\"https://github.com/MarkGotham/Chorale-Corpus\" target=\"_blank\" rel=\"noopener\">Chorale-Corpus</a> pour les partitions des chorals, et le Bach Chorales Melody-Harmony Corpus (BCMH) pour la seconde analyse.</li>\n")
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
    private fun css(x: Double) = "%.2f".format(Locale.ROOT, x.coerceIn(0.0, 100.0))
    private fun fmtInt(n: Int) = "%,d".format(Locale.FRENCH, n).replace(' ', ' ')
}
