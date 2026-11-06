package fr.declodeur.harmonie.engine

/**
 * Orthographe des notes : le nom de chacune des 12 classes de hauteur.
 *
 * - [forKey] : une lettre par degré de la tonalité (C D E F G A B en C majeur), la sensible haussée en
 *   mineur (G♯ en la mineur, C♯ en ré mineur), et pour les autres notes la forme la plus courante en
 *   harmonie : ♭2, ♭3, ♯4, ♭6, ♭7 en majeur ; ♭2, ♮3, ♯4, ♮6, ♮7 en mineur.
 * - [forChord] : les notes d'un accord s'écrivent par tierces depuis sa fondamentale (E7 = E G♯ B D
 *   même en C majeur) ; la fondamentale d'une dominante secondaire, d'une substitution tritonique ou
 *   d'un diminué de passage s'écrit d'après l'accord qu'elle annonce (F♯dim7 vers G, D♭7 vers C).
 */
data class Spelling(val names: List<String>) {
    init {
        require(names.size == 12)
    }

    fun name(pitchClass: Int): String = names[Notes.pc(pitchClass)]

    /** Lettre (0 = C … 6 = B) sous laquelle cette classe de hauteur est écrite. */
    fun letter(pitchClass: Int): Int = LETTERS.indexOf(name(pitchClass)[0])

    /** Même orthographe, quelques classes de hauteur renommées. */
    fun with(overrides: Map<Int, String>): Spelling =
        if (overrides.isEmpty()) this else Spelling(names.mapIndexed { i, n -> overrides[i] ?: n })

    companion object {
        private const val LETTERS = "CDEFGAB"
        private val NATURAL = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        /** Sans contexte tonal : C♯ E♭ F♯ A♭ B♭. */
        val DEFAULT = Spelling(listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"))
        val SHARPS = Spelling(listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"))
        val FLATS = Spelling(listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B"))

        /** Décalage de lettre de chaque intervalle depuis la tonique (même table en majeur et en mineur). */
        private val DEGREE_LETTER = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 5, 5, 6, 6)

        /** Nom de [pitchClass] écrit avec la lettre [letter] (0 = C), avec ♯, ♭, ♯♯ ou ♭♭. */
        fun spell(letter: Int, pitchClass: Int): String {
            val l = ((letter % 7) + 7) % 7
            var acc = Notes.pc(pitchClass - NATURAL[l])
            if (acc > 6) acc -= 12
            val sign = when (acc) {
                0 -> ""
                1 -> "♯"
                2 -> "♯♯"
                -1 -> "♭"
                -2 -> "♭♭"
                else -> return DEFAULT.name(pitchClass) // jamais pour une lettre bien choisie
            }
            return LETTERS[l] + sign
        }

        /** Armure de la tonalité : sert à choisir le nom de la tonique (D♭ et non C♯ majeur). */
        private fun signatureOf(key: Key): Spelling {
            val major = if (key.minor) Notes.pc(key.tonic + 3) else key.tonic
            return when (major) {
                0 -> DEFAULT
                5, 10, 3, 8, 1 -> FLATS
                6 -> if (key.minor) FLATS else SHARPS // E♭m (6 bémols), F♯ (6 dièses)
                else -> SHARPS
            }
        }

        fun forKey(key: Key): Spelling {
            val tonicLetter = signatureOf(key).letter(key.tonic)
            return Spelling((0 until 12).map { pc -> spell(tonicLetter + DEGREE_LETTER[Notes.pc(pc - key.tonic)], pc) })
        }

        /** Décalage de lettre d'un degré d'accord (« 3 » → 2, « ♭7 » → 6, « ♯11 » → 3, « 13 » → 5). */
        private fun chordLetter(label: String): Int? {
            val n = label.filter { it.isDigit() }.toIntOrNull() ?: return null
            return (n - 1) % 7
        }

        /**
         * Orthographe pour afficher [chord] : celle de la tonalité ([base]), les notes de l'accord
         * écrites par tierces depuis la fondamentale.
         */
        fun forChord(chord: ChordCandidate?, analysis: HarmonicAnalysis?, base: Spelling): Spelling {
            if (chord == null) return base
            val root = chord.root
            val rootLetter = when (analysis?.category) {
                HarmonicCategory.SECONDARY_DOMINANT -> base.letter(root - 7) + 4
                HarmonicCategory.SUBSTITUTE_DOMINANT -> base.letter(root - 1) + 1
                HarmonicCategory.PASSING_DIMINISHED -> base.letter(root + 1) - 1
                else -> base.letter(root)
            }
            val overrides = HashMap<Int, String>()
            for (iv in chord.type.intervals) {
                val offset = chordLetter(chord.type.degreeLabel(iv)) ?: continue
                overrides[Notes.pc(root + iv)] = spell(rootLetter + offset, root + iv)
            }
            return base.with(overrides)
        }

        /** Note un demi-ton sous [target], écrite comme sa sensible (F♯ sous G, et non G♭). */
        fun leadingTone(target: Int, base: Spelling): String = spell(base.letter(target) - 1, target - 1)
    }
}

/** Orthographe des gammes : une gamme de 7 notes prend les 7 lettres dans l'ordre (C♯ altérée, pas de doublon). */
fun ScaleSuggestion.spelling(base: Spelling): Spelling {
    val pcs = pitchClasses
    if (pcs.size != 7) return base
    val rootLetter = base.letter(root)
    return base.with(pcs.mapIndexed { i, pc -> pc to Spelling.spell(rootLetter + i, pc) }.toMap())
}
