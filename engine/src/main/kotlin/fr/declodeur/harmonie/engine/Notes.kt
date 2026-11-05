package fr.declodeur.harmonie.engine

/** Orthographe des notes altérées. */
enum class Spelling {
    /** Sans contexte tonal : C♯ E♭ F♯ A♭ B♭. */
    DEFAULT,

    /** Tonalités à dièses : C♯ D♯ F♯ G♯ A♯. */
    SHARPS,

    /** Tonalités à bémols : D♭ E♭ G♭ A♭ B♭. */
    FLATS,
}

/**
 * Noms de notes et d'intervalles.
 *
 * Convention : C4 = note MIDI 60 (comme MIDI Monitor).
 */
object Notes {
    private val DEFAULT_NAMES = arrayOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
    private val SHARP_NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val FLAT_NAMES = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")
    private val BLACK = setOf(1, 3, 6, 8, 10)
    private val INTERVALS = arrayOf(
        "unisson", "seconde mineure", "seconde majeure", "tierce mineure", "tierce majeure",
        "quarte juste", "triton", "quinte juste", "sixte mineure", "sixte majeure",
        "septième mineure", "septième majeure",
    )

    /** Classe de hauteur (0 = C … 11 = B), toujours positive. */
    fun pc(note: Int): Int = ((note % 12) + 12) % 12

    fun name(pitchClass: Int, spelling: Spelling = Spelling.DEFAULT): String = when (spelling) {
        Spelling.DEFAULT -> DEFAULT_NAMES
        Spelling.SHARPS -> SHARP_NAMES
        Spelling.FLATS -> FLAT_NAMES
    }[pc(pitchClass)]

    /** Nom avec octave, C4 = 60. */
    fun nameWithOctave(note: Int, spelling: Spelling = Spelling.DEFAULT): String =
        name(note, spelling) + (Math.floorDiv(note, 12) - 1)

    fun isBlack(note: Int): Boolean = pc(note) in BLACK

    /** Nom d'un intervalle simple (ramené dans l'octave). */
    fun intervalName(semitones: Int): String = INTERVALS[pc(semitones)]
}
