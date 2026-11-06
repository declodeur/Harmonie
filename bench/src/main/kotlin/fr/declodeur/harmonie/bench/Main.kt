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
    /** Tempo de lecture (noires par minute) quand la partition n'en indique pas. */
    val defaultTempo: Int,
    /** Seuil d'alignement partition / analyse sous lequel un morceau est écarté. */
    val minAlignment: Double,
    /** Qui est l'expert B (second avis), s'il y en a un. */
    val secondExpert: String? = null,
    /** L'expert ne note que la tonique de la tonalité (pas le mode). */
    val tonicOnly: Boolean = false,
    /** Notes générées à partir des accords des experts (pas de partition). */
    val synthetic: Boolean = false,
)

val CORPORA = listOf(
    Corpus(
        "bach-chorales", "Chorals de Bach",
        "Chorals à quatre voix, analysés par Dmitri Tymoczko, Andrew Jones, Hamish Robb et al.",
        MusicStyle.CLASSICAL, 80, 0.8,
        secondExpert = "Bach Chorales Melody-Harmony Corpus (BCMH)",
    ),
    Corpus(
        "mozart-sonatas", "Sonates pour piano de Mozart",
        "Les 18 sonates (54 mouvements), analysées par le DCML (EPFL). Tempo d'après l'indication de chaque mouvement.",
        MusicStyle.CLASSICAL, 120, 0.65,
        secondExpert = "Dmitri Tymoczko",
    ),
    Corpus(
        "textbooks", "Exemples de manuels d'harmonie",
        "Exemples de modulation de Reger, Rimsky-Korsakov, Tchaïkovski, Kostka-Payne et Aldwell-Schachter " +
            "(jeu de données de Nápoles López et al.).",
        MusicStyle.CLASSICAL, 80, 0.8,
    ),
    Corpus(
        "bach-wtc1", "Clavier bien tempéré I, préludes",
        "Les 24 préludes du premier livre, analysés par Mark Gotham et al. Écriture en figurations (arpèges, notes de passage).",
        MusicStyle.CLASSICAL, 80, 0.65,
    ),
    Corpus(
        "rock-corpus", "Rock et pop : corpus RS 200",
        "195 chansons du classement « 500 Greatest Songs » de Rolling Stone, analysées par David Temperley (A) et " +
            "Trevor de Clercq (B). Pas de partition : l'accompagnement est généré à partir des accords de A " +
            "(teste la tonalité et le chiffrage, pas la reconnaissance). Seule la tonique est comparée.",
        MusicStyle.POP_ROCK, 60, 0.0,
        secondExpert = "Trevor de Clercq", tonicOnly = true, synthetic = true,
    ),
    Corpus(
        "isophonics", "Pop : Beatles, Queen, Carole King…",
        "224 chansons (Beatles, Queen, Carole King, Michael Jackson, Zweieck) analysées par le Centre for Digital " +
            "Music (Isophonics). Accompagnement généré à partir des accords, comme pour RS 200.",
        MusicStyle.POP_ROCK, 60, 0.0,
        synthetic = true,
    ),
    Corpus(
        "schubert-winterreise", "Schubert, Winterreise",
        "Les 24 lieder (voix et piano), analysés par Mark Gotham et al.",
        MusicStyle.CLASSICAL, 80, 0.8,
    ),
)

/** Comparaison d'une autre analyse (expert B ou logiciel) à l'expert A. */
class Reference(
    val label: String,
    /** (accord juste, tonalité juste) pour chaque accord de A. */
    val agreement: List<Pair<Boolean, Boolean>>,
    /** L'application sur les mêmes morceaux, pour une comparaison équitable. */
    val app: List<Verdict>,
)

/** Résultats d'un corpus pour un jeu de réglages. */
class CorpusResult(
    val corpus: Corpus,
    val pieces: List<Piece>,
    val excluded: List<Piece>,
    val verdicts: List<Verdict>,
    val expert: Reference?,
    val software: Reference?,
)

fun pct(n: Int, d: Int): Double = if (d == 0) 0.0 else 100.0 * n / d

/** Morceaux chargés une fois pour toutes (partagés entre les variantes). */
class LoadedCorpus(val corpus: Corpus, val pieces: List<Piece>, val excluded: List<Piece>)

fun loadCorpus(corpus: Corpus, dataDir: File): LoadedCorpus? {
    val dir = File(dataDir, corpus.id)
    if (!dir.isDirectory) return null
    val all = PieceLoader.loadDir(dir).filter { it.chords["A"] != null }
    val (pieces, excluded) = all.partition { it.alignment >= corpus.minAlignment }
    return LoadedCorpus(corpus, pieces, excluded)
}

fun runCorpus(lc: LoadedCorpus, settings: AnalysisSettings): CorpusResult {
    val corpus = lc.corpus
    val s = settings.copy(style = corpus.style)
    // Les morceaux sont indépendants : un moteur par morceau, en parallèle
    val perPiece = lc.pieces.parallelStream().map { p ->
        val msPerQuarter = 60_000.0 / (if (p.tempo > 0) p.tempo else corpus.defaultTempo)
        val timeline = Player.play(p, s, msPerQuarter)
        p to Compare.compare(p, timeline, p.chords.getValue("A"), msPerQuarter, corpus.tonicOnly)
    }.toList()
    val verdicts = perPiece.flatMap { it.second }
    fun reference(analyst: String, label: String?): Reference? {
        val agreement = ArrayList<Pair<Boolean, Boolean>>()
        val app = ArrayList<Verdict>()
        for ((p, v) in perPiece) {
            val other = p.chords[analyst] ?: continue
            agreement += Compare.experts(p, p.chords.getValue("A"), other, corpus.tonicOnly)
            app += v
        }
        return if (agreement.isEmpty() || label == null) null else Reference(label, agreement, app)
    }
    return CorpusResult(
        corpus, lc.pieces, lc.excluded, verdicts,
        expert = reference("B", corpus.secondExpert),
        software = reference("M", "AugmentedNet"),
    )
}

fun summary(r: CorpusResult): String {
    val v = r.verdicts
    return "%s : %d morceaux, %d accords — accord %.1f %%, exact %.1f %%, tonalité %.1f %%, chiffrage %.1f %%".format(
        r.corpus.name, r.pieces.size, v.size,
        pct(v.count { it.chordOk }, v.size), pct(v.count { it.exactOk }, v.size),
        pct(v.count { it.keyOk }, v.size), pct(v.count { it.numeralOk }, v.size),
    )
}

/**
 * Arguments : dossier des données, dossier du rapport, [fichier d'historique], [ids d'expériences
 * séparés par des virgules, ou « all » ; par défaut celles marquées actives].
 */
fun main(args: Array<String>) {
    val dataDir = File(args.getOrElse(0) { "bench/data" })
    val outDir = File(args.getOrElse(1) { "bench/build/report" })
    val historyFile = args.getOrNull(2)?.takeIf { it.isNotBlank() && it != "-" }?.let { File(it) }
    val wanted = args.getOrNull(3)
    val commit = System.getenv("GITHUB_SHA")?.take(7) ?: "local"
    outDir.mkdirs()
    val started = System.currentTimeMillis()
    val loaded = CORPORA.mapNotNull { loadCorpus(it, dataDir) }
    val base = AnalysisSettings()
    val results = loaded.map { runCorpus(it, base) }
    results.forEach { println(summary(it)) }
    println("Moyenne des corpus : tonalité %.1f %%".format(results.map { pct(it.verdicts.count { v -> v.keyOk }, it.verdicts.size) }.average()))
    val experiments = Experiments.select(wanted).map { e ->
        val variant = loaded.map { runCorpus(it, e.apply(base)) }
        println("Expérience « ${e.name} » :")
        variant.forEach { println("  " + summary(it)) }
        println("  Moyenne des corpus : tonalité %.1f %%, accord %.1f %%".format(
            variant.map { pct(it.verdicts.count { v -> v.keyOk }, it.verdicts.size) }.average(),
            variant.map { pct(it.verdicts.count { v -> v.chordOk }, it.verdicts.size) }.average()))
        ExperimentResult(e, results, variant)
    }
    val history = History.update(historyFile, results, commit)
    Report.write(File(outDir, "index.html"), results, experiments, history, commit, System.currentTimeMillis() - started)
    historyFile?.let { History.save(it, history) }
    println("Rapport : " + File(outDir, "index.html").path)
}
