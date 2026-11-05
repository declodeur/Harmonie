package fr.declodeur.harmonie.engine

/**
 * Noms de notes et d'intervalles.
 *
 * Convention : C4 = note MIDI 60 (comme MIDI Monitor).
 * Orthographe par défaut (sans contexte tonal) : C C♯ D E♭ E F F♯ G A♭ A B♭ B.
 * L'orthographe dépendant de la tonalité viendra avec la détection de tonalité.
 */
object Notes {
    private val NAMES = arrayOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
    private val BLACK = setOf(1, 3, 6, 8, 10)
    private val INTERVALS = arrayOf(
        "unisson", "seconde mineure", "seconde majeure", "tierce mineure", "tierce majeure",
        "quarte juste", "triton", "quinte juste", "sixte mineure", "sixte majeure",
        "septième mineure", "septième majeure",
    )

    /** Classe de hauteur (0 = C … 11 = B), toujours positive. */
    fun pc(note: Int): Int = ((note % 12) + 12) % 12

    fun name(pitchClass: Int): String = NAMES[pc(pitchClass)]

    /** Nom avec octave, C4 = 60. */
    fun nameWithOctave(note: Int): String = name(note) + (Math.floorDiv(note, 12) - 1)

    fun isBlack(note: Int): Boolean = pc(note) in BLACK

    /** Nom d'un intervalle simple (ramené dans l'octave). */
    fun intervalName(semitones: Int): String = INTERVALS[pc(semitones)]
}
