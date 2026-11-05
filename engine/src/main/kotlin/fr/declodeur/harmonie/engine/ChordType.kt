package fr.declodeur.harmonie.engine

/** Libellés de degrés par défaut, en demi-tons au-dessus de la fondamentale. */
private val DEFAULT_LABELS = mapOf(
    0 to "1", 1 to "♭9", 2 to "9", 3 to "♭3", 4 to "3", 5 to "11",
    6 to "♯11", 7 to "5", 8 to "♭13", 9 to "13", 10 to "♭7", 11 to "7",
)

/**
 * Types d'accords reconnus par les arrangeurs Yamaha (mode « Fingered »),
 * avec la notation Yamaha (suffixe affiché après la fondamentale).
 *
 * [ivs] : intervalles en demi-tons depuis la fondamentale.
 * [tensions] : intervalles qui sont des tensions (9, 11, 13 et altérations).
 * [complexity] : sert à départager deux lectures de même qualité (la plus simple gagne).
 */
enum class ChordType(
    val suffix: String,
    val frenchName: String,
    ivs: List<Int>,
    val tensions: Set<Int> = emptySet(),
    val complexity: Int,
    labelOverrides: Map<Int, String> = emptyMap(),
) {
    MAJOR("", "accord majeur", listOf(0, 4, 7), complexity = 0),
    SIXTH("6", "accord de sixte", listOf(0, 4, 7, 9), complexity = 2, labelOverrides = mapOf(9 to "6")),
    MAJ7("M7", "septième majeure", listOf(0, 4, 7, 11), complexity = 1),
    MAJ7_SHARP11("M7♯11", "septième majeure, onzième augmentée", listOf(0, 4, 6, 7, 11), setOf(6), complexity = 4),
    ADD9("add9", "majeur avec neuvième ajoutée", listOf(0, 2, 4, 7), setOf(2), complexity = 2),
    MAJ7_9("M7(9)", "septième majeure, neuvième", listOf(0, 2, 4, 7, 11), setOf(2), complexity = 3),
    SIXTH_9("6(9)", "sixte et neuvième", listOf(0, 2, 4, 7, 9), setOf(2), complexity = 3, labelOverrides = mapOf(9 to "6")),
    AUG("aug", "accord augmenté", listOf(0, 4, 8), complexity = 2, labelOverrides = mapOf(8 to "♯5")),
    MINOR("m", "accord mineur", listOf(0, 3, 7), complexity = 0),
    MINOR6("m6", "mineur sixte", listOf(0, 3, 7, 9), complexity = 2, labelOverrides = mapOf(9 to "6")),
    MINOR7("m7", "mineur septième", listOf(0, 3, 7, 10), complexity = 1),
    MINOR7_FLAT5("m7♭5", "mineur septième quinte bémol (demi-diminué)", listOf(0, 3, 6, 10), complexity = 2, labelOverrides = mapOf(6 to "♭5")),
    MINOR_ADD9("madd9", "mineur avec neuvième ajoutée", listOf(0, 2, 3, 7), setOf(2), complexity = 2),
    MINOR7_9("m7(9)", "mineur septième, neuvième", listOf(0, 2, 3, 7, 10), setOf(2), complexity = 3),
    MINOR7_11("m7(11)", "mineur septième, onzième", listOf(0, 3, 5, 7, 10), setOf(5), complexity = 3),
    MINOR_MAJ7("mM7", "mineur septième majeure", listOf(0, 3, 7, 11), complexity = 3),
    MINOR_MAJ7_9("mM7(9)", "mineur septième majeure, neuvième", listOf(0, 2, 3, 7, 11), setOf(2), complexity = 4),
    DIM("dim", "accord diminué", listOf(0, 3, 6), complexity = 2, labelOverrides = mapOf(6 to "♭5")),
    DIM7("dim7", "septième diminuée", listOf(0, 3, 6, 9), complexity = 2, labelOverrides = mapOf(6 to "♭5", 9 to "♭♭7")),
    DOM7("7", "septième (de dominante)", listOf(0, 4, 7, 10), complexity = 1),
    DOM7_SUS4("7sus4", "septième, quarte suspendue", listOf(0, 5, 7, 10), complexity = 2, labelOverrides = mapOf(5 to "4")),
    DOM7_FLAT5("7♭5", "septième quinte bémol", listOf(0, 4, 6, 10), complexity = 3, labelOverrides = mapOf(6 to "♭5")),
    DOM7_9("7(9)", "septième, neuvième", listOf(0, 2, 4, 7, 10), setOf(2), complexity = 2),
    DOM7_SHARP11("7♯11", "septième, onzième augmentée", listOf(0, 4, 6, 7, 10), setOf(6), complexity = 4),
    DOM7_13("7(13)", "septième, treizième", listOf(0, 4, 7, 9, 10), setOf(9), complexity = 3),
    DOM7_FLAT9("7(♭9)", "septième, neuvième bémol", listOf(0, 1, 4, 7, 10), setOf(1), complexity = 3),
    DOM7_FLAT13("7(♭13)", "septième, treizième bémol", listOf(0, 4, 7, 8, 10), setOf(8), complexity = 3),
    DOM7_SHARP9("7(♯9)", "septième, neuvième dièse", listOf(0, 3, 4, 7, 10), setOf(3), complexity = 3, labelOverrides = mapOf(3 to "♯9")),
    MAJ7_AUG("M7aug", "septième majeure quinte augmentée", listOf(0, 4, 8, 11), complexity = 4, labelOverrides = mapOf(8 to "♯5")),
    DOM7_AUG("7aug", "septième quinte augmentée", listOf(0, 4, 8, 10), complexity = 3, labelOverrides = mapOf(8 to "♯5")),
    ONE_PLUS_EIGHT("1+8", "note seule doublée à l'octave", listOf(0), complexity = 5),
    ONE_PLUS_FIVE("1+5", "quinte à vide (power chord)", listOf(0, 7), complexity = 3),
    SUS4("sus4", "quarte suspendue", listOf(0, 5, 7), complexity = 1, labelOverrides = mapOf(5 to "4")),
    SUS2("sus2", "seconde suspendue", listOf(0, 2, 7), complexity = 2, labelOverrides = mapOf(2 to "2")),
    ;

    val intervals: Set<Int> = ivs.toSet()

    /**
     * La quinte juste peut manquer (voicing courant) pour les accords de 4 sons et plus.
     * Pas pour 6 et m6 : sans quinte, ce ne sont que des renversements d'accords parfaits.
     */
    val canOmitFifth: Boolean = 7 in intervals && intervals.size >= 4 && suffix != "6" && suffix != "m6"

    private val labels: Map<Int, String> = DEFAULT_LABELS + labelOverrides

    /** Degré d'un intervalle dans cet accord : « 1 », « ♭3 », « 5 », « ♭7 », « 9 »… */
    fun degreeLabel(interval: Int): String = labels[Notes.pc(interval)] ?: "?"
}
