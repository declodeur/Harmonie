package fr.declodeur.harmonie.bench

import java.io.File

/** Une note de la partition (temps en noires). */
data class ScoreNote(val start: Double, val duration: Double, val midi: Int)

/** Un accord tel que l'a noté un expert. */
data class ExpertChord(
    val analyst: String,
    val start: Double,
    val end: Double,
    val measure: Int,
    val beat: String,
    val tonic: Int,
    val minor: Boolean,
    /** Chiffrage tel qu'écrit par l'expert (« V6/5/V »). */
    val figure: String,
    val root: Int,
    val bass: Int,
    val pitchClasses: Set<Int>,
    /** Accord spécial : It, Ger, Fr (sixtes augmentées), N (napolitain), Cad (quarte et sixte de cadence). */
    val special: String?,
)

/** Un morceau du banc d'essai. */
data class Piece(
    val id: String,
    val title: String,
    /** Qualité de l'alignement entre l'analyse et la partition (0 à 1). */
    val alignment: Double,
    /** Tempo de lecture en noires par minute (0 = tempo par défaut du corpus). */
    val tempo: Int,
    /** Indication de tempo d'origine (« allegro »), vide si inconnue. */
    val tempoText: String,
    val notes: List<ScoreNote>,
    val chords: Map<String, List<ExpertChord>>,
)

object PieceLoader {
    fun load(file: File): Piece {
        var id = file.nameWithoutExtension
        var title = id
        var alignment = 1.0
        var tempo = 0
        var tempoText = ""
        val notes = mutableListOf<ScoreNote>()
        val chords = mutableListOf<ExpertChord>()
        file.forEachLine(Charsets.UTF_8) { line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val f = line.split('\t')
            when (f[0]) {
                "P" -> {
                    title = f[1]
                    id = f[2]
                    alignment = f.getOrNull(3)?.toDoubleOrNull() ?: 1.0
                    tempo = f.getOrNull(4)?.toIntOrNull() ?: 0
                    tempoText = f.getOrNull(5).orEmpty()
                }
                "N" -> notes += ScoreNote(f[1].toDouble(), f[2].toDouble(), f[3].toInt())
                "A" -> chords += ExpertChord(
                    analyst = f[1],
                    start = f[2].toDouble(),
                    end = f[3].toDouble(),
                    measure = f[4].toInt(),
                    beat = f[5],
                    tonic = f[6].toInt(),
                    minor = f[7] == "m",
                    figure = f[8],
                    root = f[9].toInt(),
                    bass = f[10].toInt(),
                    pitchClasses = f[11].split(',').map { it.toInt() }.toSet(),
                    special = f[12].takeIf { it != "-" },
                )
            }
        }
        return Piece(id, title, alignment, tempo, tempoText, notes, chords.groupBy { it.analyst })
    }

    fun loadDir(dir: File): List<Piece> =
        dir.listFiles { f -> f.extension == "tsv" }.orEmpty().sortedBy { it.name }.map { load(it) }
}
