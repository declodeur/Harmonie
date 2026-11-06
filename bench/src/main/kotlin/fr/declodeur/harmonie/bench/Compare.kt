package fr.declodeur.harmonie.bench

import fr.declodeur.harmonie.engine.ChordCandidate
import fr.declodeur.harmonie.engine.Key
import fr.declodeur.harmonie.engine.Notes

/** Qualité d'un accord réduite à l'essentiel, commune aux experts et à l'application. */
data class Quality(val triad: String, val seventh: String) {
    override fun toString() = triad + (if (seventh.isEmpty()) "" else " $seventh")

    companion object {
        /** D'après les intervalles depuis la fondamentale. */
        fun of(root: Int, pcs: Set<Int>): Quality {
            val iv = pcs.map { Notes.pc(it - root) }.toSet()
            val triad = when {
                4 in iv && 8 in iv && 7 !in iv -> "aug"
                4 in iv -> "maj"
                3 in iv && 6 in iv && 7 !in iv -> "dim"
                3 in iv -> "min"
                5 in iv && 7 in iv -> "sus4"
                2 in iv && 7 in iv -> "sus2"
                7 in iv -> "5"
                else -> "?"
            }
            val seventh = when {
                10 in iv -> "7"
                11 in iv -> "M7"
                triad == "dim" && 9 in iv -> "o7"
                else -> ""
            }
            return Quality(triad, seventh)
        }
    }
}

/** Un accord à comparer : fondamentale, classes de hauteur, qualité. */
data class Reading(val root: Int, val pitchClasses: Set<Int>, val bass: Int) {
    val quality: Quality get() = Quality.of(root, pitchClasses)

    companion object {
        fun of(c: ChordCandidate) = Reading(c.root, c.chordPitchClasses, c.bass)
        fun of(e: ExpertChord) = Reading(e.root, e.pitchClasses, e.bass)
    }
}

enum class ChordError(val label: String, val explanation: String) {
    NO_CHORD("Aucun accord affiché", "L'application n'affiche aucun accord (notes non reconnues)."),
    SPECIAL(
        "Accord spécial non reconnu",
        "Sixte augmentée (italienne, allemande, française) ou napolitain : l'application ne les connaît pas encore.",
    ),
    SAME_NOTES("Mêmes notes, autre nom", "Mêmes notes que l'expert, nommées sur une autre fondamentale (Am7 / C6, accords diminués…)."),
    PREVIOUS("Accord précédent encore affiché", "L'application montre encore l'accord d'avant : changement trop bref, ou sans nouvelle attaque."),
    FOREIGN_NOTE(
        "Note étrangère comptée dans l'accord",
        "Une note de passage, un retard ou une appoggiature est lue comme une note de l'accord.",
    ),
    PARTIAL("Accord incomplet lu autrement", "L'application lit une partie seulement des notes de l'expert, et la nomme autrement."),
    OTHER("Autre lecture", "Lecture différente sans cause simple."),
}

enum class KeyError(val label: String, val explanation: String) {
    NO_KEY("Pas encore de tonalité", "Début du morceau : l'application n'a pas encore assez d'indices."),
    LATE("Retard au changement de tonalité", "L'expert a changé de tonalité il y a moins de 4 temps ; l'application est encore dans l'ancienne."),
    BRIEF(
        "Modulation brève non suivie",
        "L'expert note une tonalité pendant moins de 2 mesures (tonicisation) ; l'application reste dans la tonalité principale.",
    ),
    RELATIVE("Tonalité relative", "Même armure (C majeur / A mineur)."),
    PARALLEL("Mode homonyme", "Même tonique, autre mode (C majeur / C mineur)."),
    NEIGHBOR("Tonalité voisine", "Une quinte au-dessus ou au-dessous (C / G, C / F)."),
    OTHER("Autre tonalité", "Tonalité éloignée."),
}

/** Résultat de la comparaison pour un accord de l'expert. */
data class Verdict(
    val piece: Piece,
    val expert: ExpertChord,
    val previousExpert: ExpertChord?,
    val shown: Display,
    val chordOk: Boolean,
    val exactOk: Boolean,
    val keyOk: Boolean,
    val chordError: ChordError?,
    val keyError: KeyError?,
    /** Sixte en trop ou en moins : accord juste mais septième différente. */
    val seventhNote: String?,
    /** Tonalité de l'historique (après réanalyse a posteriori) identique à celle de l'expert. */
    val historyKeyOk: Boolean = false,
) {
    val numeralOk: Boolean get() = chordOk && keyOk
}

object Compare {
    /** Fenêtre d'observation d'un accord de l'expert : après le délai de validation, jusqu'à sa fin. */
    const val SETTLE_MS = 120L

    fun chordMatches(expert: Reading, special: String?, app: Reading?): Boolean {
        app ?: return false
        if (special in setOf("It", "Ger", "Fr")) return app.pitchClasses == expert.pitchClasses
        return app.root == expert.root && app.quality.triad == expert.quality.triad
    }

    fun exactMatches(expert: Reading, special: String?, app: Reading?): Boolean {
        app ?: return false
        if (!chordMatches(expert, special, app)) return false
        if (special in setOf("It", "Ger", "Fr")) return true
        return app.quality.seventh == expert.quality.seventh
    }

    /** Ce qu'affiche le plus longtemps l'application pendant [startMs, endMs). */
    fun dominant(timeline: Timeline, startMs: Long, endMs: Long): Display {
        val from = if (endMs - startMs > SETTLE_MS + 40) startMs + SETTLE_MS else maxOf(startMs, endMs - 30)
        val counts = LinkedHashMap<Display, Int>()
        var t = from
        while (t < endMs) {
            val d = timeline.at(t)
            val k = Display(d.chord, d.key, null)
            counts[k] = (counts[k] ?: 0) + 1
            t += timeline.stepMs
        }
        if (counts.isEmpty()) return timeline.at(from)
        val best = counts.maxByOrNull { it.value }!!.key
        // On reprend l'analyse (chiffrage) d'un échantillon qui correspond
        t = from
        while (t < endMs) {
            val d = timeline.at(t)
            if (d.chord == best.chord && d.key == best.key) return d
            t += timeline.stepMs
        }
        return best
    }

    /** [tonicOnly] : l'expert ne note que la tonique (rock corpus) ; le mode n'est pas comparé. */
    fun compare(
        piece: Piece,
        timeline: Timeline,
        chords: List<ExpertChord>,
        msPerQuarter: Double,
        tonicOnly: Boolean = false,
    ): List<Verdict> {
        val verdicts = ArrayList<Verdict>()
        // Longueur de chaque passage dans une même tonalité (pour repérer les tonicisations brèves)
        val runLength = DoubleArray(chords.size)
        var i = 0
        while (i < chords.size) {
            var j = i
            while (j + 1 < chords.size && chords[j + 1].tonic == chords[i].tonic && chords[j + 1].minor == chords[i].minor) j++
            val len = chords[j].end - chords[i].start
            for (k in i..j) runLength[k] = len
            i = j + 1
        }
        var keyChangeAt = Double.NEGATIVE_INFINITY
        var keyBefore: Key? = null
        for ((idx, e) in chords.withIndex()) {
            val prev = chords.getOrNull(idx - 1)
            val expertKey = Key(e.tonic, e.minor)
            if (prev != null && (prev.tonic != e.tonic || prev.minor != e.minor)) {
                keyChangeAt = e.start
                keyBefore = Key(prev.tonic, prev.minor)
            }
            val startMs = (e.start * msPerQuarter).toLong()
            val endMs = (e.end * msPerQuarter).toLong()
            val shown = dominant(timeline, startMs, endMs)
            val expert = Reading.of(e)
            val app = shown.chord?.let { Reading.of(it) }
            val chordOk = chordMatches(expert, e.special, app)
            val exactOk = exactMatches(expert, e.special, app)
            val keyOk = if (tonicOnly) shown.key?.tonic == expertKey.tonic else shown.key == expertKey
            val chordError = if (chordOk) null else chordError(e, prev, app)
            val keyError = if (keyOk) null else when {
                shown.key == null -> KeyError.NO_KEY
                e.start - keyChangeAt <= 4.0 && shown.key == keyBefore -> KeyError.LATE
                runLength[idx] < 8.0 -> KeyError.BRIEF
                shown.key == expertKey.relative -> KeyError.RELATIVE
                shown.key == expertKey.parallel -> KeyError.PARALLEL
                shown.key!!.minor == expertKey.minor && Notes.pc(shown.key!!.tonic - expertKey.tonic) in setOf(5, 7) -> KeyError.NEIGHBOR
                else -> KeyError.OTHER
            }
            val seventhNote = if (chordOk && !exactOk && app != null) {
                if (expert.quality.seventh.isEmpty()) "Septième en plus" else "Septième en moins ou différente"
            } else null
            val hk = timeline.historyKeyAt((startMs + endMs) / 2)
            val historyKeyOk = if (tonicOnly) hk?.tonic == expertKey.tonic else hk == expertKey
            verdicts += Verdict(piece, e, prev, shown, chordOk, exactOk, keyOk, chordError, keyError, seventhNote, historyKeyOk)
        }
        return verdicts
    }

    private fun chordError(e: ExpertChord, prev: ExpertChord?, app: Reading?): ChordError {
        app ?: return ChordError.NO_CHORD
        if (e.special in setOf("It", "Ger", "Fr", "N")) return ChordError.SPECIAL
        if (app.pitchClasses == e.pitchClasses) return ChordError.SAME_NOTES
        if (prev != null && chordMatches(Reading.of(prev), prev.special, app)) return ChordError.PREVIOUS
        if (!e.pitchClasses.containsAll(app.pitchClasses)) return ChordError.FOREIGN_NOTE
        if (app.pitchClasses.size < e.pitchClasses.size) return ChordError.PARTIAL
        return ChordError.OTHER
    }

    /** Accord entre deux experts : on compare l'expert B à l'expert A, accord par accord de A. */
    fun experts(piece: Piece, a: List<ExpertChord>, b: List<ExpertChord>, tonicOnly: Boolean = false): List<Pair<Boolean, Boolean>> =
        a.mapNotNull { ea ->
            val mid = ea.start + minOf(0.25, (ea.end - ea.start) / 2)
            val eb = b.firstOrNull { it.start <= mid && mid < it.end } ?: return@mapNotNull null
            val chordOk = chordMatches(Reading.of(ea), ea.special, Reading.of(eb))
            val keyOk = ea.tonic == eb.tonic && (tonicOnly || ea.minor == eb.minor)
            chordOk to keyOk
        }
}
