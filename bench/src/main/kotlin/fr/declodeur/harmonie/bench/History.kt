package fr.declodeur.harmonie.bench

import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Une ligne de l'historique des scores : un corpus lors d'un passage du banc d'essai. */
data class HistoryRow(
    val date: String,
    val commit: String,
    val corpus: String,
    val chords: Int,
    val chord: Double,
    val exact: Double,
    val key: Double,
    val numeral: Double,
)

/** Historique des scores, conservé d'un passage à l'autre (fichier TSV). */
object History {
    private const val HEADER = "date\tcommit\tcorpus\taccords\taccord\texact\ttonalite\tchiffrage"

    fun load(file: File?): List<HistoryRow> {
        if (file == null || !file.isFile) return emptyList()
        return file.readLines(Charsets.UTF_8).drop(1).mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 8) null
            else HistoryRow(f[0], f[1], f[2], f[3].toInt(), f[4].toDouble(), f[5].toDouble(), f[6].toDouble(), f[7].toDouble())
        }
    }

    fun update(file: File?, results: List<CorpusResult>, commit: String): List<HistoryRow> {
        val date = LocalDateTime.now(ZoneId.of("Europe/Paris")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val rows = results.map { r ->
            val v = r.verdicts
            HistoryRow(
                date, commit, r.corpus.id, v.size,
                pct(v.count { it.chordOk }, v.size), pct(v.count { it.exactOk }, v.size),
                pct(v.count { it.keyOk }, v.size), pct(v.count { it.numeralOk }, v.size),
            )
        }
        return load(file) + rows
    }

    fun save(file: File, rows: List<HistoryRow>) {
        file.parentFile?.mkdirs()
        file.writeText(
            (listOf(HEADER) + rows.map {
                listOf(it.date, it.commit, it.corpus, it.chords, "%.2f".format(java.util.Locale.ROOT, it.chord),
                    "%.2f".format(java.util.Locale.ROOT, it.exact), "%.2f".format(java.util.Locale.ROOT, it.key),
                    "%.2f".format(java.util.Locale.ROOT, it.numeral)).joinToString("\t")
            }).joinToString("\n") + "\n",
            Charsets.UTF_8,
        )
    }
}
