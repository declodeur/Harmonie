package fr.declodeur.harmonie.bench

import fr.declodeur.harmonie.engine.Degree
import fr.declodeur.harmonie.engine.Key
import fr.declodeur.harmonie.engine.MusicStyle
import fr.declodeur.harmonie.engine.Notes
import fr.declodeur.harmonie.engine.Predictor
import fr.declodeur.harmonie.engine.ProgressionModel
import java.io.File
import fr.declodeur.harmonie.engine.Quality as Q

/**
 * Mesure des suites probables : à chaque changement d'accord d'un expert, le modèle reçoit les
 * accords précédents (tels que l'expert les a chiffrés) et doit deviner le suivant. On compte
 * combien de fois l'accord de l'expert est la première proposition, ou l'une des quatre.
 * Les statistiques des corpus sont mesurées en validation croisée : chaque morceau est prédit
 * par un modèle appris sur les autres morceaux (cinq groupes), jamais sur lui-même.
 */
object PredictionEval {
    const val FOLDS = 5
    const val CONTEXT = 2

    class Score(var n: Int = 0, var top1: Int = 0, var top4: Int = 0) {
        fun add(rank: Int) {
            n++
            if (rank == 0) top1++
            if (rank in 0..3) top4++
        }
        val p1 get() = pct(top1, n)
        val p4 get() = pct(top4, n)
    }

    class Result(val corpus: Corpus, val theory: Score, val learned: Score)

    /** Degré d'un accord d'expert dans sa tonalité, avec la qualité simplifiée des tables. */
    fun degree(e: ExpertChord): Degree {
        val q = Quality.of(e.root, e.pitchClasses)
        val quality = when {
            e.special in setOf("It", "Ger", "Fr") -> Q.DOM
            q.triad == "dim" && q.seventh == "7" -> Q.HDIM
            q.triad == "dim" -> Q.DIM
            q.triad == "min" -> Q.MIN
            q.seventh == "7" -> Q.DOM
            else -> Q.MAJ
        }
        return Degree(Notes.pc(e.root - e.tonic), quality)
    }

    /**
     * Suites de degrés d'un morceau, coupées à chaque changement de tonalité. Pour un expert qui ne
     * note que la tonique (RS 200), le mode d'un passage se déduit de son accord de tonique.
     */
    fun sequences(piece: Piece, style: MusicStyle, tonicOnly: Boolean): List<ProgressionModel.Sequence> {
        val chords = piece.chords["A"] ?: return emptyList()
        val runs = mutableListOf<MutableList<ExpertChord>>()
        for (c in chords) {
            val last = runs.lastOrNull()?.lastOrNull()
            if (last == null || last.tonic != c.tonic || last.minor != c.minor) runs += mutableListOf(c) else runs.last() += c
        }
        return runs.map { run ->
            val degrees = run.map { degree(it) }
            val minor = if (!tonicOnly) run.first().minor else {
                val tonicTime = run.zip(degrees).filter { it.second.interval == 0 }
                val minTime = tonicTime.filter { it.second.quality == Q.MIN }.sumOf { it.first.end - it.first.start }
                val majTime = tonicTime.filter { it.second.quality != Q.MIN }.sumOf { it.first.end - it.first.start }
                minTime > majTime
            }
            ProgressionModel.Sequence(style, minor, degrees)
        }.filter { it.degrees.size >= 2 }
    }

    private fun score(seq: ProgressionModel.Sequence, model: ProgressionModel?, into: Score) {
        val d = seq.degrees.fold(mutableListOf<Degree>()) { acc, x -> if (acc.lastOrNull() != x) acc += x; acc }
        val key = Key(0, seq.minor)
        for (i in 1 until d.size) {
            val proposals = Predictor.next(d.subList(0, i), key, seq.style, CONTEXT, model).map { it.first }
            into.add(proposals.indexOf(d[i]))
        }
    }

    fun run(loaded: List<LoadedCorpus>): List<Result> {
        // Morceaux de chaque corpus, avec leur groupe de validation croisée
        val all = loaded.flatMap { lc ->
            lc.pieces.mapIndexed { i, p -> Triple(lc.corpus, i % FOLDS, sequences(p, lc.corpus.style, lc.corpus.tonicOnly)) }
        }
        val models = (0 until FOLDS).associateWith { fold ->
            ProgressionModel.train(all.filter { it.second != fold }.flatMap { it.third })
        }
        return loaded.map { lc ->
            val theory = Score()
            val learned = Score()
            for ((c, fold, seqs) in all) {
                if (c != lc.corpus) continue
                for (s in seqs) {
                    score(s, null, theory)
                    score(s, models.getValue(fold), learned)
                }
            }
            Result(lc.corpus, theory, learned)
        }
    }

    /** Modèle appris sur tous les morceaux, écrit dans la ressource du moteur. */
    fun trainAll(loaded: List<LoadedCorpus>, out: File) {
        val seqs = loaded.flatMap { lc -> lc.pieces.flatMap { sequences(it, lc.corpus.style, lc.corpus.tonicOnly) } }
        val model = ProgressionModel.train(seqs, pruneLong = true)
        out.parentFile?.mkdirs()
        val header = "# Sources : corpus When in Rome (CC BY-SA 4.0 ; sonates de Mozart : DCML, CC BY-NC-SA 4.0) et ChoCo\n" +
            "# (RS 200, Isophonics ; CC BY 4.0). Voir bench/data/README.md. Généré par le banc d'essai (objet Train).\n"
        out.writeText(header + model.serialize(), Charsets.UTF_8)
        println("Modèle d'enchaînements : ${model.size} contextes, ${seqs.size} passages → ${out.path}")
    }
}

/** Apprend les statistiques d'enchaînements sur tous les corpus et les écrit pour l'application. */
object Train {
    @JvmStatic
    fun main(args: Array<String>) {
        val dataDir = File(args.getOrElse(0) { "bench/data" })
        val out = File(args.getOrElse(1) { "engine/src/main/resources/fr/declodeur/harmonie/engine/progressions.tsv" })
        val loaded = CORPORA.mapNotNull { loadCorpus(it, dataDir) }
        PredictionEval.trainAll(loaded, out)
    }
}
