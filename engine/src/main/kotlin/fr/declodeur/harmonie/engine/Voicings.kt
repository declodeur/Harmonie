package fr.declodeur.harmonie.engine

import kotlin.math.abs

/** Affichage du voicing et de la conduite des voix (cadre « Accord courant »). */
data class VoicingSettings(
    val showVoicing: Boolean = true,
    val showVoiceLeading: Boolean = true,
    /** Signaler les quintes et octaves parallèles (interdites en écriture classique). */
    val flagParallels: Boolean = true,
    /** … dans tous les styles, et pas seulement en style Classique. */
    val parallelsInAllStyles: Boolean = false,
)

/** Comment un accord est disposé sur le clavier. */
data class VoicingInfo(
    /** Disposition : « position serrée », « position large », « drop 2 », « en quartes »… */
    val layout: String,
    /** Notes doublées, par degré (« fondamentale doublée »). */
    val doublings: List<String>,
    /** Degré de la note la plus aiguë (« 3 » : soprano sur la tierce). */
    val sopranoDegree: String?,
    /** Le même degré en toutes lettres (« tierce »). */
    val sopranoName: String? = null,
    /** Mise en garde (classique) : « sensible doublée ». */
    val warning: String?,
    /** Écart entre la note la plus grave et la plus aiguë, en demi-tons. */
    val span: Int,
)

/** Passage d'un accord au suivant, voix par voix. */
data class VoiceLeadingInfo(
    val commonTones: Int,
    val steps: Int,
    val leaps: Int,
    /** Mouvement des voix extrêmes (basse et soprano) : contraire, parallèle, oblique, direct. */
    val outerMotion: String?,
    /** Quintes et octaves consécutives, ou parallèles (« quintes consécutives : basse et soprano »). */
    val parallels: List<String>,
    /** Guide tones : 3ce et 7e qui s'enchaînent (« 7e F → 3ce E »). */
    val guideTones: List<String>,
    /** Symbole de l'accord précédent. */
    val from: String,
)

object Voicings {

    private fun degreeName(label: String): String = when (label) {
        "1", "R" -> "fondamentale"
        "3", "♭3" -> "tierce"
        "5", "♭5", "♯5" -> "quinte"
        "7", "♭7", "M7", "♭♭7" -> "septième"
        "6" -> "sixte"
        "4" -> "quarte"
        "2", "9", "♭9", "♯9" -> "neuvième"
        "11", "♯11" -> "onzième"
        "13", "♭13" -> "treizième"
        else -> label
    }

    /** Disposition de [notes] (MIDI) pour l'accord [chord]. */
    fun describe(notes: List<Int>, chord: ChordCandidate, key: Key?): VoicingInfo? {
        val n = notes.sorted()
        if (n.size < 3) return null
        val pcs = chord.chordPitchClasses
        // Doublures : classes de hauteur présentes plusieurs fois
        val byPc = n.groupBy { Notes.pc(it) }
        val doubled = byPc.filter { it.value.size > 1 && it.key in pcs }.keys
        val doublings = doubled.mapNotNull { pc -> chord.degreeOf(pc)?.let { degreeName(it) + " doublée" } }
        // Classique : la sensible ne se double pas dans l'accord de dominante (elle doit monter à la tonique)
        val leading = key?.let { Notes.pc(it.tonic - 1) }
        val onDominant = key != null && Notes.pc(chord.root - key.tonic) == 7
        val warning = if (leading != null && onDominant && leading in doubled) "sensible doublée" else null
        // Basse séparée (main gauche) : une octave ou plus sous les autres voix
        val separated = n.size >= 4 && n[1] - n[0] >= SEPARATED_BASS
        val layout = layout(n, pcs, chord) + if (separated && !layout(n, pcs, chord).startsWith("drop")) " sur basse séparée" else ""
        val soprano = chord.degreeOf(n.last())
        return VoicingInfo(layout, doublings, soprano, sopranoName = soprano?.let { degreeName(it) }, warning = warning, span = n.last() - n.first())
    }

    /** Écart (demi-tons) à partir duquel la basse est séparée des autres voix. */
    const val SEPARATED_BASS = 12

    /** Voix serrées : moins d'une octave (l'octave comprise), aucune note de l'accord sautée entre deux voix voisines. */
    private fun isClose(v: List<Int>, pcs: Set<Int>): Boolean {
        if (v.last() - v.first() > 12) return false
        for (i in 0 until v.size - 1) {
            for (m in v[i] + 1 until v[i + 1]) if (Notes.pc(m) in pcs) return false
        }
        return true
    }

    private fun layout(v: List<Int>, pcs: Set<Int>, chord: ChordCandidate): String {
        val ivs = v.zipWithNext { a, b -> b - a }
        val distinct = v.map { Notes.pc(it) }.toSet()
        if (v.size >= 3 && ivs.all { it == 5 || it == 6 } && ivs.count { it == 5 } >= ivs.size - 1) return "en quartes"
        // Shell (jazz) : fondamentale, tierce et septième, sans quinte
        val rel = distinct.map { Notes.pc(it - chord.root) }.toSet()
        val hasThird = 3 in rel || 4 in rel
        val hasSeventh = 10 in rel || 11 in rel
        if (distinct.size == 3 && 0 in rel && hasThird && hasSeventh && 7 !in rel) return "shell (fondamentale, tierce, septième)"
        if (isClose(v, pcs)) return "position serrée"
        // Drop 2 / drop 3 (jazz, 4 sons) : une voix de la position serrée descendue d'une octave
        if (v.size == 4 && distinct.size == 4) {
            val raised = (v.drop(1) + (v[0] + 12)).sorted()
            if (isClose(raised, pcs)) {
                when (raised.indexOf(v[0] + 12)) {
                    2 -> return "drop 2"
                    1 -> return "drop 3"
                }
            }
        }
        // À quatre voix et plus, la position se lit sur les voix supérieures (définition classique)
        if (v.size >= 4 && isClose(v.drop(1), pcs)) return "position serrée"
        return "position large"
    }

    private fun voiceNames(count: Int): List<String> = when (count) {
        4 -> listOf("basse", "ténor", "alto", "soprano")
        3 -> listOf("basse", "voix du milieu", "soprano")
        else -> List(count) { i -> if (i == 0) "basse" else if (i == count - 1) "soprano" else "voix ${i + 1}" }
    }

    /** Appariement des voix : basse avec basse, soprano avec soprano, voix intérieures au plus proche. */
    private fun pair(a: List<Int>, b: List<Int>): List<Pair<Int, Int>> {
        if (a.size == b.size) return a.zip(b)
        val pairs = mutableListOf(a.first() to b.first(), a.last() to b.last())
        val innerA = a.drop(1).dropLast(1).toMutableList()
        for (x in b.drop(1).dropLast(1)) {
            val nearest = innerA.minByOrNull { abs(it - x) } ?: continue
            pairs.add(pairs.size - 1, nearest to x)
            innerA.remove(nearest)
        }
        return pairs
    }

    /** Conduite des voix de [prev] (notes MIDI de l'accord précédent) vers [next]. */
    fun lead(
        prev: List<Int>,
        prevChord: ChordCandidate,
        next: List<Int>,
        nextChord: ChordCandidate,
        flagParallels: Boolean,
        prevSpelling: Spelling = Spelling.DEFAULT,
        nextSpelling: Spelling = prevSpelling,
    ): VoiceLeadingInfo? {
        val a = prev.sorted()
        val b = next.sorted()
        if (a.size < 2 || b.size < 2) return null
        val pairs = pair(a, b)
        val common = pairs.count { it.first == it.second }
        val steps = pairs.count { abs(it.second - it.first) in 1..2 }
        val leaps = pairs.count { abs(it.second - it.first) >= 3 }
        val bassMove = b.first() - a.first()
        val sopMove = b.last() - a.last()
        val outer = when {
            bassMove == 0 && sopMove == 0 -> null
            bassMove == 0 || sopMove == 0 -> "oblique"
            (bassMove > 0) != (sopMove > 0) -> "contraire"
            (b.last() - b.first()) == (a.last() - a.first()) -> "parallèle"
            else -> "direct"
        }
        val parallels = mutableListOf<String>()
        if (flagParallels && a.size == b.size) {
            val names = voiceNames(b.size)
            for (i in a.indices) for (j in i + 1 until a.size) {
                val mi = b[i] - a[i]
                val mj = b[j] - a[j]
                if (mi == 0 || mj == 0 || (mi > 0) != (mj > 0)) continue
                val before = Notes.pc(a[j] - a[i])
                val after = Notes.pc(b[j] - b[i])
                if (before == 7 && after == 7) parallels += "quintes consécutives : ${names[i]} et ${names[j]}"
                if (before == 0 && after == 0 && a[j] != a[i]) parallels += "octaves consécutives : ${names[i]} et ${names[j]}"
            }
        }
        // Guide tones : la 7e et la 3ce de l'accord précédent vers la 3ce ou la 7e du suivant
        val guide = mutableListOf<String>()
        fun role(note: Int, c: ChordCandidate): String? = when (Notes.pc(note - c.root)) {
            3, 4 -> "3ce"
            10, 11 -> "7e"
            else -> null
        }
        if (prevChord.type.intervals.any { it == 10 || it == 11 }) {
            for ((x, y) in pairs) {
                val rx = role(x, prevChord) ?: continue
                val ry = role(y, nextChord) ?: continue
                if (abs(y - x) <= 2 && rx != ry) {
                    guide += "$rx ${Notes.name(x, prevSpelling)} → $ry ${Notes.name(y, nextSpelling)}"
                }
            }
        }
        return VoiceLeadingInfo(common, steps, leaps, outer, parallels.distinct(), guide, prevChord.symbolIn(prevSpelling))
    }
}
