package fr.declodeur.harmonie.engine

/** Fonction harmonique d'un accord dans la tonalité. */
enum class HarmonicFunction(val label: String, val short: String) {
    TONIC("Tonique", "T"),
    SUBDOMINANT("Sous-dominante", "SD"),
    DOMINANT("Dominante", "D"),
}

/** Notation des degrés en mineur. */
enum class DegreeNotation(val label: String) {
    /** Degrés comptés dans la gamme de la tonalité : III, VI, VII en mineur (analyse classique). */
    CLASSICAL("Classique"),

    /** Degrés comptés dans la gamme majeure de la tonique : ♭III, ♭VI, ♭VII en mineur (jazz, variété). */
    JAZZ("Jazz / variété"),
}

/** Nature de l'accord par rapport à la tonalité. */
enum class HarmonicCategory(val label: String) {
    DIATONIC("Diatonique"),
    ALTERED_DOMINANT("Dominante altérée"),
    SECONDARY_DOMINANT("Dominante secondaire"),
    SUBSTITUTE_DOMINANT("Substitution tritonique"),
    BORROWED("Emprunt modal"),
    PASSING_DIMINISHED("Diminué de passage"),
    BLUES("Couleur blues"),
    CHROMATIC("Chromatique"),
}

/** Analyse d'un accord dans une tonalité. */
data class HarmonicAnalysis(
    val key: Key,
    /** Chiffrage romain avec les suffixes Yamaha : « IIm7 », « V7 », « V7/II », « SubV7 », « ♭VIM7 ». */
    val numeral: String,
    val function: HarmonicFunction?,
    val category: HarmonicCategory,
    /** Explication en français. */
    val description: String,
    /** Nom du degré (« tonique », « médiante », « sus-dominante », « sous-tonique »…), pour les accords de la gamme. */
    val degreeName: String? = null,
    /**
     * Fonction choisie d'après l'accord suivant (VI, VII) : explication courte, ou null si la fonction
     * vient de la table des degrés.
     */
    val context: String? = null,
    /** L'accord suivant n'est pas encore joué : la fonction vient de la suite la plus probable. */
    val contextPredicted: Boolean = false,
    /** Accord qui tient le rôle de la tonique ou de la dominante sans en être le degré (III, VI ; VII sous-tonique). */
    val substitute: Boolean = false,
)

/**
 * Analyse fonctionnelle d'un accord dans une tonalité.
 *
 * Chiffrage : chiffre romain du degré de la fondamentale (relatif à la gamme majeure de la
 * tonique, comme dans l'analyse jazz : ♭III, ♭VI, ♭VII en mineur), suivi du suffixe Yamaha.
 *
 * Ordre de lecture : accord diatonique ; couleur blues (style Blues) ; dominante altérée ;
 * dominante secondaire (V7/x) ; emprunt au mode homonyme (♭VII7, IVm…) ; substitution
 * tritonique (SubV7/x) ; diminué de passage ; accord napolitain ; sinon chromatique.
 */
object HarmonicAnalyzer {
    private val ROMAN = arrayOf("I", "♭II", "II", "♭III", "III", "IV", "♯IV", "V", "♭VI", "VI", "♭VII", "VII")

    /** Notation classique en mineur : degrés de la gamme mineure (B♭ et B sont tous deux VII en C mineur). */
    private val ROMAN_MINOR = arrayOf("I", "♭II", "II", "III", "♯III", "IV", "♯IV", "V", "VI", "♯VI", "VII", "VII")

    private fun roman(iv: Int, key: Key, notation: DegreeNotation): String =
        if (key.minor && notation == DegreeNotation.CLASSICAL) ROMAN_MINOR[Notes.pc(iv)] else ROMAN[Notes.pc(iv)]

    private val DEGREE_NAMES_MAJOR = mapOf(
        0 to "tonique", 2 to "sus-tonique", 3 to "médiante", 4 to "médiante", 5 to "sous-dominante", 7 to "dominante",
        8 to "sus-dominante", 9 to "sus-dominante", 10 to "sous-tonique", 11 to "sensible",
    )

    /** Nom du degré de la fondamentale (7e degré : sous-tonique un ton sous la tonique, sensible un demi-ton). */
    fun degreeName(interval: Int): String? = DEGREE_NAMES_MAJOR[Notes.pc(interval)]

    /** Les diminués se nomment d'après la note qu'ils font monter : ♯Idim7 vers IIm. */
    private val ROMAN_SHARP = mapOf(1 to "♯I", 3 to "♯II", 6 to "♯IV", 8 to "♯V", 10 to "♯VI")

    private val MAJOR_SCALE = setOf(0, 2, 4, 5, 7, 9, 11)
    private val MINOR_SCALES = listOf(
        setOf(0, 2, 3, 5, 7, 8, 10), // mineur naturel
        setOf(0, 2, 3, 5, 7, 8, 11), // mineur harmonique
        setOf(0, 2, 3, 5, 7, 9, 11), // mineur mélodique
    )

    private enum class Quality { MAJOR, MINOR, DIM }

    /** Qualité de l'accord parfait sur chaque degré (mineur : naturel, sauf V majeur). */
    private val MAJOR_TRIADS = mapOf(0 to Quality.MAJOR, 2 to Quality.MINOR, 4 to Quality.MINOR, 5 to Quality.MAJOR,
        7 to Quality.MAJOR, 9 to Quality.MINOR, 11 to Quality.DIM)
    private val MINOR_TRIADS = mapOf(0 to Quality.MINOR, 2 to Quality.DIM, 3 to Quality.MAJOR, 5 to Quality.MINOR,
        7 to Quality.MAJOR, 8 to Quality.MAJOR, 10 to Quality.MAJOR)

    private val MAJOR_FUNCTIONS = mapOf(
        0 to HarmonicFunction.TONIC, 2 to HarmonicFunction.SUBDOMINANT, 4 to HarmonicFunction.TONIC,
        5 to HarmonicFunction.SUBDOMINANT, 7 to HarmonicFunction.DOMINANT, 9 to HarmonicFunction.TONIC,
        11 to HarmonicFunction.DOMINANT,
    )
    private val MINOR_FUNCTIONS = mapOf(
        0 to HarmonicFunction.TONIC, 2 to HarmonicFunction.SUBDOMINANT, 3 to HarmonicFunction.TONIC,
        5 to HarmonicFunction.SUBDOMINANT, 7 to HarmonicFunction.DOMINANT, 8 to HarmonicFunction.SUBDOMINANT,
        9 to HarmonicFunction.SUBDOMINANT, 10 to HarmonicFunction.SUBDOMINANT, 11 to HarmonicFunction.DOMINANT,
    )

    fun analyze(
        chord: ChordCandidate,
        key: Key,
        style: MusicStyle = MusicStyle.POP_ROCK,
        notation: DegreeNotation = DegreeNotation.CLASSICAL,
    ): HarmonicAnalysis {
        val sp = key.spelling
        val iv = Notes.pc(chord.root - key.tonic)
        val rel = chord.chordPitchClasses.map { Notes.pc(it - key.tonic) }.toSet()
        val type = chord.type
        val numeral = numeral(iv, type, key, notation)
        val functions = if (key.minor) MINOR_FUNCTIONS else MAJOR_FUNCTIONS
        val keyName = key.name
        fun rn(d: Int) = roman(d, key, notation)
        fun tn(degree: Int) = targetNumeral(key, degree, notation)

        fun result(n: String, f: HarmonicFunction?, c: HarmonicCategory, d: String) = HarmonicAnalysis(
            key, n, f, c, d,
            degreeName = if (c == HarmonicCategory.DIATONIC || c == HarmonicCategory.BORROWED || c == HarmonicCategory.ALTERED_DOMINANT) degreeName(iv) else null,
            substitute = f == HarmonicFunction.TONIC && iv != 0 && (c == HarmonicCategory.DIATONIC || c == HarmonicCategory.BORROWED),
        )

        // 1. Diatonique
        if (isDiatonic(rel, key.minor)) {
            val f = functions[iv]
            val fText = f?.let { ", fonction de ${it.label.lowercase()}" } ?: ""
            return result(numeral, f, HarmonicCategory.DIATONIC, "Accord diatonique de $keyName$fText.")
        }

        // 2. Blues : septièmes de dominante sur I, IV et V
        if (style == MusicStyle.BLUES && type.isDominant && iv in setOf(0, 5, 7)) {
            val f = MAJOR_FUNCTIONS[iv]
            return result(numeral, f, HarmonicCategory.BLUES,
                "Septième de blues sur le degré ${rn(iv)} : couleur typique du blues, pas une dominante à résoudre.")
        }

        if (type.isDominant || (type.isMajorTriadBased && type.intervals.size == 3)) {
            // 3. Dominante principale avec tensions altérées
            if (iv == 7) {
                return result(numeral, HarmonicFunction.DOMINANT, HarmonicCategory.ALTERED_DOMINANT,
                    "Dominante de $keyName avec des tensions hors gamme (♭9, ♯9, ♭13…) : tension accrue vers ${key.tonicName}.")
            }
            // 4. Dominante secondaire : la fondamentale est une quinte au-dessus d'un degré de la gamme
            val target = Notes.pc(iv - 7)
            if (target != 0 && isTarget(target, key.minor)) {
                val targetName = targetChordName(key, target)
                return result("V${type.suffix}/${rn(target)}", HarmonicFunction.DOMINANT,
                    HarmonicCategory.SECONDARY_DOMINANT,
                    "Dominante secondaire : dominante de $targetName (${tn(target)}). Elle tend à résoudre vers $targetName.")
            }
        }

        // 5. Emprunt au mode homonyme
        if (isDiatonic(rel, !key.minor)) {
            val parallel = key.parallel
            val f = (if (parallel.minor) MINOR_FUNCTIONS else MAJOR_FUNCTIONS)[iv]
            val colour = if (parallel.minor) "couleur plus sombre" else "couleur plus lumineuse"
            val fText = f?.let { ", fonction de ${it.label.lowercase()}" } ?: ""
            return result(numeral, f, HarmonicCategory.BORROWED,
                "Emprunt à ${parallel.name} (mode homonyme) : $colour$fText.")
        }

        // 6. Substitution tritonique : dominante un demi-ton au-dessus de sa cible
        if (type.isDominant) {
            val target = Notes.pc(iv - 1)
            if (target == 0 || isTarget(target, key.minor)) {
                val targetName = if (target == 0) key.tonicName + (if (key.minor) "m" else "") else targetChordName(key, target)
                val replaced = Notes.name(key.tonic + target + 7, sp) + "7"
                val n = if (target == 0) "SubV${type.suffix}" else "SubV${type.suffix}/${rn(target)}"
                return result(n, HarmonicFunction.DOMINANT, HarmonicCategory.SUBSTITUTE_DOMINANT,
                    "Substitution tritonique : remplace $replaced (à un triton) et résout un demi-ton plus bas vers $targetName.")
            }
        }

        // 7. Diminué de passage : un demi-ton sous un degré de la gamme
        if (type.isDiminished) {
            val target = Notes.pc(iv + 1)
            if (target == 0 || isTarget(target, key.minor)) {
                val targetName = if (target == 0) key.tonicName + (if (key.minor) "m" else "") else targetChordName(key, target)
                return result(numeral, HarmonicFunction.DOMINANT, HarmonicCategory.PASSING_DIMINISHED,
                    "Diminué de passage : monte d'un demi-ton vers $targetName. Il sonne comme une dominante (7(♭9) sans fondamentale).")
            }
        }

        // 8. Accord napolitain : accord majeur sur le ♭II
        if (iv == 1 && type.isMajorTriadBased) {
            return result(numeral, HarmonicFunction.SUBDOMINANT, HarmonicCategory.CHROMATIC,
                "Accord napolitain (♭II) : couleur de sous-dominante, souvent placé juste avant la dominante.")
        }

        return result(numeral, null, HarmonicCategory.CHROMATIC,
            "Accord hors de $keyName. S'il se confirme, il peut annoncer une modulation.")
    }

    /** Nombre de notes de l'accord hors de la gamme de la tonalité (mineur : naturel ∪ harmonique ∪ mélodique). */
    fun notesOutsideKey(chord: ChordCandidate, key: Key): Int {
        val scale = if (key.minor) MINOR_SCALES.flatten().toSet() else MAJOR_SCALE
        return chord.chordPitchClasses.count { Notes.pc(it - key.tonic) !in scale }
    }

    /**
     * L'accord sort franchement de la tonalité : au moins deux notes hors gamme, et aucune
     * fonction ne l'explique (ni dominante secondaire, ni emprunt, ni substitution…).
     */
    fun breaksKey(chord: ChordCandidate, key: Key, style: MusicStyle): Boolean =
        notesOutsideKey(chord, key) >= 2 && analyze(chord, key, style).category == HarmonicCategory.CHROMATIC

    /** Chiffre romain du degré + suffixe Yamaha. */
    fun numeral(interval: Int, type: ChordType, key: Key? = null, notation: DegreeNotation = DegreeNotation.JAZZ): String {
        val iv = Notes.pc(interval)
        val classicalMinor = key != null && key.minor && notation == DegreeNotation.CLASSICAL
        val base = when {
            type.isDiminished && classicalMinor -> if (iv == 1 || iv == 6) ROMAN_SHARP.getValue(iv) else ROMAN_MINOR[iv]
            type.isDiminished -> ROMAN_SHARP[iv] ?: ROMAN[iv]
            iv == 6 -> "♭V"
            classicalMinor -> ROMAN_MINOR[iv]
            else -> ROMAN[iv]
        }
        return when (type) {
            ChordType.ONE_PLUS_EIGHT, ChordType.ONE_PLUS_FIVE -> base
            else -> base + type.suffix
        }
    }

    private fun isDiatonic(rel: Set<Int>, minor: Boolean): Boolean =
        if (minor) MINOR_SCALES.any { it.containsAll(rel) } else MAJOR_SCALE.containsAll(rel)

    /** Degré qui peut être la cible d'une dominante : accord parfait majeur ou mineur de la gamme. */
    private fun isTarget(degree: Int, minor: Boolean): Boolean {
        val q = (if (minor) MINOR_TRIADS else MAJOR_TRIADS)[degree] ?: return false
        return q != Quality.DIM
    }

    private fun targetChordName(key: Key, degree: Int): String {
        val q = (if (key.minor) MINOR_TRIADS else MAJOR_TRIADS)[degree]
        return Notes.name(key.tonic + degree, key.spelling) + if (q == Quality.MINOR) "m" else ""
    }

    private fun targetNumeral(key: Key, degree: Int, notation: DegreeNotation): String {
        val q = (if (key.minor) MINOR_TRIADS else MAJOR_TRIADS)[degree]
        return roman(degree, key, notation) + if (q == Quality.MINOR) "m" else ""
    }

    /**
     * Lecture selon le contexte (l'accord suivant, joué ou le plus probable) :
     * - VI (mineur) : suivi de I ou de III, il prolonge la tonique (tonique substitutive) ; suivi de II, IV,
     *   V ou VII, il prépare la dominante (sous-dominante) ; juste après la dominante, sans suite connue,
     *   il remplace la tonique (cadence rompue).
     * - VII sous-tonique (un ton sous la tonique, en mineur ou emprunté en majeur) : suivi de I, il joue
     *   le rôle de dominante (dominante substitutive ; cadence par la sous-tonique, éolienne après VI,
     *   mixolydienne en majeur) ; en mineur, suivi de III, c'est la dominante du relatif ; sinon
     *   sous-dominante.
     * - Septième de dominante de la gamme (VII7, IV7 en mineur) qui descend d'une quinte vers un accord
     *   de la gamme : dominante secondaire (B♭7 → E♭ en C mineur = V7/III).
     * - Accord parfait majeur de tonique en mineur : V/IV s'il va vers IV, sinon tierce picarde (I).
     * Un accord suivant de même fondamentale (A♭ → A♭M7) ne compte pas : la suite reste inconnue.
     */
    fun inContext(
        a: HarmonicAnalysis,
        chord: ChordCandidate,
        previous: ChordCandidate?,
        nextChord: ChordCandidate?,
        nextPredicted: Boolean = false,
        notation: DegreeNotation = DegreeNotation.CLASSICAL,
    ): HarmonicAnalysis {
        val next = nextChord?.takeIf { it.root != chord.root }
        val key = a.key
        val iv = Notes.pc(chord.root - key.tonic)
        val nextIv = next?.let { Notes.pc(it.root - key.tonic) }
        val nextName = next?.symbolIn(key.spelling)
        val after = if (nextPredicted) "s'il est suivi de $nextName (suite la plus probable)" else "suivi de $nextName"

        // Tierce picarde : accord de tonique majeur en mineur, qui ne va pas vers IV
        if (key.minor && iv == 0 && chord.type.isMajorTriadBased && a.category == HarmonicCategory.SECONDARY_DOMINANT && nextIv != 5) {
            return a.copy(
                numeral = numeral(0, chord.type, key, notation),
                function = HarmonicFunction.TONIC,
                category = HarmonicCategory.BORROWED,
                description = "Tierce picarde : accord de tonique majeur dans une tonalité mineure, souvent en fin de morceau" +
                    (if (next != null) " ($after)." else "."),
                degreeName = degreeName(0),
                context = "tierce picarde",
                contextPredicted = nextPredicted && next != null,
            )
        }
        // Septième de dominante de la gamme qui résout une quinte plus bas : dominante secondaire
        if (a.category == HarmonicCategory.DIATONIC && chord.type.isDominant && iv != 7 && nextIv != null &&
            nextIv == Notes.pc(iv - 7) && isTarget(nextIv, key.minor)
        ) {
            val target = nextIv
            return a.copy(
                numeral = "V${chord.type.suffix}/${roman(target, key, notation)}",
                function = HarmonicFunction.DOMINANT,
                category = HarmonicCategory.SECONDARY_DOMINANT,
                description = "Dominante secondaire : dominante de ${targetChordName(key, target)} " +
                    "(${targetNumeral(key, target, notation)}), $after.",
                degreeName = null,
                context = "dominante secondaire",
                contextPredicted = nextPredicted,
                substitute = false,
            )
        }

        if (a.category != HarmonicCategory.DIATONIC && a.category != HarmonicCategory.BORROWED) return a
        val majorBased = chord.type.isMajorTriadBased || chord.type.isDominant
        if (!majorBased) return a
        val (f, why) = when {
            key.minor && iv == 8 -> when {
                nextIv == 0 || nextIv == 3 -> HarmonicFunction.TONIC to "tonique substitutive : $after, il prolonge la tonique"
                nextIv != null -> HarmonicFunction.SUBDOMINANT to "sous-dominante : $after, il prépare la dominante"
                previous != null && Notes.pc(previous.root - key.tonic) == 7 && (previous.type.isDominant || previous.type.isMajorTriadBased) ->
                    HarmonicFunction.TONIC to "tonique substitutive : après la dominante, il remplace la tonique (cadence rompue)"
                else -> return a
            }
            iv == 10 -> when {
                nextIv == 0 -> {
                    val afterSix = previous != null && Notes.pc(previous.root - key.tonic) == 8
                    val cadence = when {
                        !key.minor -> "cadence mixolydienne, typique du rock"
                        afterSix -> "cadence éolienne VI – VII – I"
                        else -> "cadence par la sous-tonique"
                    }
                    HarmonicFunction.DOMINANT to "dominante substitutive : $after, la sous-tonique ramène à la tonique ($cadence)"
                }
                key.minor && nextIv == 3 -> HarmonicFunction.DOMINANT to "dominante substitutive : $after, c'est la dominante du relatif majeur"
                nextIv != null -> HarmonicFunction.SUBDOMINANT to "sous-dominante : $after"
                else -> return a
            }
            else -> return a
        }
        val base = a.description.substringBefore(", fonction de").removeSuffix(".")
        return a.copy(
            function = f, description = "$base, fonction de $why.", context = why, contextPredicted = nextPredicted,
            substitute = f != HarmonicFunction.SUBDOMINANT,
        )
    }
}
