package fr.declodeur.harmonie.engine

/**
 * Noms de notes et d'intervalles.
 *
 * Convention : C4 = note MIDI 60 (comme MIDI Monitor).
 */
object Notes {
    private val BLACK = setOf(1, 3, 6, 8, 10)
    private val INTERVALS = arrayOf(
        "unisson", "seconde mineure", "seconde majeure", "tierce mineure", "tierce majeure",
        "quarte juste", "triton", "quinte juste", "sixte mineure", "sixte majeure",
        "septième mineure", "septième majeure",
    )

    /** Classe de hauteur (0 = C … 11 = B), toujours positive. */
    fun pc(note: Int): Int = ((note % 12) + 12) % 12

    fun name(pitchClass: Int, spelling: Spelling = Spelling.DEFAULT): String = spelling.name(pitchClass)

    /** Nom avec octave, C4 = 60. */
    fun nameWithOctave(note: Int, spelling: Spelling = Spelling.DEFAULT): String {
        val name = name(note, spelling)
        // C♭ appartient à l'octave du dessus, B♯ à celle du dessous (C♭5 = B4, B♯3 = C4)
        val shift = when {
            name[0] == 'C' && pc(note) >= 10 -> 1
            name[0] == 'B' && pc(note) <= 1 -> -1
            else -> 0
        }
        return name + (Math.floorDiv(note, 12) - 1 + shift)
    }

    fun isBlack(note: Int): Boolean = pc(note) in BLACK

    /** Nom d'un intervalle simple (ramené dans l'octave). */
    fun intervalName(semitones: Int): String = INTERVALS[pc(semitones)]
}
