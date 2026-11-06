package fr.declodeur.harmonie.bench

import fr.declodeur.harmonie.engine.AnalysisSettings
import fr.declodeur.harmonie.engine.MusicStyle
import java.io.File

/** Un corpus du banc d'essai et la façon de le jouer. */
data class Corpus(
    val id: String,
    val name: String,
    val description: String,
    val style: MusicStyle,
    /** Tempo de lecture (noires par minute). */
    val tempo: Int,
)

val CORPORA = listOf(
    Corpus(
        "bach-chorales",
        "Chorals de Bach",
        "371 chorals à quatre voix, analysés par Dmitri Tymoczko, Andrew Jones, Hamish Robb et al. " +
            "(corpus When in Rome) ; 100 d'entre eux analysés aussi par un second corpus (BCMH).",
        MusicStyle.CLASSICAL,
        80,
    ),
)

/** Seuil d'alignement partition / analyse sous lequel un morceau est écarté. */
const val MIN_ALIGNMENT = 0.8

/** Résultats d'un corpus. */
class CorpusResult(
    val corpus: Corpus,
    val pieces: List<Piece>,
    val excluded: List<Piece>,
    val verdicts: List<Verdict>,
    /** Comparaison de l'expert B à l'expert A (accord, tonalité). */
    val experts: List<Pair<Boolean, Boolean>>,
    /** Mêmes morceaux que [experts], application comparée à l'expert A (pour une comparaison équitable). */
    val appOnExpertPieces: List<Verdict>,
)

fun runCorpus(corpus: Corpus, dataDir: File): CorpusResult {
    val all = PieceLoader.loadDir(File(dataDir, corpus.id))
    val (pieces, excluded) = all.partition { it.alignment >= MIN_ALIGNMENT }
    val msPerQuarter = 60_000.0 / corpus.tempo
    val settings = AnalysisSettings(style = corpus.style)
    val verdicts = ArrayList<Verdict>()
    val experts = ArrayList<Pair<Boolean, Boolean>>()
    val appOnExpert = ArrayList<Verdict>()
    for (p in pieces) {
        val a = p.chords["A"] ?: continue
        val timeline = Player.play(p, settings, msPerQuarter)
        val v = Compare.compare(p, timeline, a, msPerQuarter)
        verdicts += v
        val b = p.chords["B"]
        if (b != null) {
            experts += Compare.experts(p, a, b)
            appOnExpert += v
        }
    }
    return CorpusResult(corpus, pieces, excluded, verdicts, experts, appOnExpert)
}

fun pct(n: Int, d: Int): Double = if (d == 0) 0.0 else 100.0 * n / d

fun main(args: Array<String>) {
    val dataDir = File(args.getOrElse(0) { "bench/data" })
    val outDir = File(args.getOrElse(1) { "bench/build/report" })
    val historyFile = args.getOrNull(2)?.let { File(it) }
    val commit = System.getenv("GITHUB_SHA")?.take(7) ?: "local"
    outDir.mkdirs()
    val started = System.currentTimeMillis()
    val results = CORPORA.map { runCorpus(it, dataDir) }
    for (r in results) {
        val v = r.verdicts
        println(
            "%s : %d morceaux, %d accords — accord %.1f %%, exact %.1f %%, tonalité %.1f %%, chiffrage %.1f %%".format(
                r.corpus.name, r.pieces.size, v.size,
                pct(v.count { it.chordOk }, v.size), pct(v.count { it.exactOk }, v.size),
                pct(v.count { it.keyOk }, v.size), pct(v.count { it.numeralOk }, v.size),
            ),
        )
        if (r.experts.isNotEmpty()) {
            println(
                "  experts entre eux : accord %.1f %%, tonalité %.1f %% ; application sur les mêmes : accord %.1f %%, tonalité %.1f %%".format(
                    pct(r.experts.count { it.first }, r.experts.size), pct(r.experts.count { it.second }, r.experts.size),
                    pct(r.appOnExpertPieces.count { it.chordOk }, r.appOnExpertPieces.size),
                    pct(r.appOnExpertPieces.count { it.keyOk }, r.appOnExpertPieces.size),
                ),
            )
        }
    }
    val history = History.update(historyFile, results, commit)
    Report.write(File(outDir, "index.html"), results, history, commit, System.currentTimeMillis() - started)
    historyFile?.let { History.save(it, history) }
    println("Rapport : " + File(outDir, "index.html").path)
}
