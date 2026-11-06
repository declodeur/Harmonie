package fr.declodeur.harmonie.engine

/** Gammes utilisées pour l'improvisation et la mélodie, en demi-tons depuis la fondamentale de l'accord. */
enum class Scale(val label: String, val intervals: List<Int>) {
    IONIAN("ionien (majeur)", listOf(0, 2, 4, 5, 7, 9, 11)),
    DORIAN("dorien", listOf(0, 2, 3, 5, 7, 9, 10)),
    PHRYGIAN("phrygien", listOf(0, 1, 3, 5, 7, 8, 10)),
    LYDIAN("lydien", listOf(0, 2, 4, 6, 7, 9, 11)),
    MIXOLYDIAN("mixolydien", listOf(0, 2, 4, 5, 7, 9, 10)),
    AEOLIAN("éolien (mineur naturel)", listOf(0, 2, 3, 5, 7, 8, 10)),
    LOCRIAN("locrien", listOf(0, 1, 3, 5, 6, 8, 10)),
    LOCRIAN_NAT2("locrien ♮2", listOf(0, 2, 3, 5, 6, 8, 10)),
    MIXO_B9_B13("mixolydien ♭9 ♭13", listOf(0, 1, 4, 5, 7, 8, 10)),
    LYDIAN_DOMINANT("lydien ♭7", listOf(0, 2, 4, 6, 7, 9, 10)),
    ALTERED("altérée", listOf(0, 1, 3, 4, 6, 8, 10)),
    HALF_WHOLE("diminuée demi-ton / ton", listOf(0, 1, 3, 4, 6, 7, 9, 10)),
    WHOLE_HALF("diminuée ton / demi-ton", listOf(0, 2, 3, 5, 6, 8, 9, 11)),
    WHOLE_TONE("par tons", listOf(0, 2, 4, 6, 8, 10)),
    LYDIAN_AUGMENTED("lydien augmenté", listOf(0, 2, 4, 6, 8, 9, 11)),
    MELODIC_MINOR("mineur mélodique", listOf(0, 2, 3, 5, 7, 9, 11)),
    MAJOR_PENTATONIC("pentatonique majeure", listOf(0, 2, 4, 7, 9)),
    MINOR_PENTATONIC("pentatonique mineure", listOf(0, 3, 5, 7, 10)),
    BLUES("blues", listOf(0, 3, 5, 6, 7, 10)),
}

/** Une gamme proposée sur un accord. */
data class ScaleSuggestion(
    val scale: Scale,
    /** Classe de hauteur de la tonique de la gamme (fondamentale de l'accord). */
    val root: Int,
    /** Pourquoi cette gamme (en français). */
    val reason: String,
) {
    val pitchClasses: List<Int> get() = scale.intervals.map { Notes.pc(root + it) }

    fun name(spelling: Spelling = Spelling.DEFAULT): String = Notes.name(root, spelling) + " " + scale.label
}

/** Gammes pour un accord : la principale, des variantes, et le statut de chaque note. */
data class ChordScaleResult(
    val chord: ChordCandidate,
    val primary: ScaleSuggestion,
    val alternatives: List<ScaleSuggestion>,
) {
    /** Notes de la gamme principale à éviter sur cet accord (classes de hauteur). */
    val avoid: Set<Int> get() = ChordScales.avoidNotes(chord, primary)

    /** Tensions disponibles (libellés « 9 », « ♯11 », « 13 »…) dans la gamme principale. */
    val tensions: List<String> get() = ChordScales.tensions(chord, primary)

    /** Sur un accord sans septième : sixte et septième de la gamme, qu'on peut ajouter (« 6 », « ♭7 ») ; ce ne sont pas des tensions. */
    val additions: List<String> get() = ChordScales.additions(chord, primary)
}

/** Rôle d'une note de mélodie par rapport à l'accord et à sa gamme. */
enum class MelodyRole(val label: String) {
    CHORD_TONE("note de l'accord"),
    TENSION("tension"),
    AVOID("note à éviter"),
    CHROMATIC("chromatique (passage ?)"),
}

/**
 * Gammes d'accord (chord-scale theory, enseignement de Berklee et de Mark Levine) :
 * la gamme qui convient sur un accord dépend de son type, de sa fonction dans la tonalité
 * et, pour une dominante, de l'accord vers lequel elle résout.
 *
 * Notes à éviter : notes de la gamme situées un demi-ton au-dessus d'une note de l'accord
 * (elles « frottent » et appellent leur résolution), sauf la ♭9 et la ♭13 d'une dominante,
 * qui sont des tensions.
 */
object ChordScales {

    fun suggest(chord: ChordCandidate, analysis: HarmonicAnalysis?, key: Key?, style: MusicStyle): ChordScaleResult {
        val r = chord.root
        val t = chord.type
        val iv = key?.let { Notes.pc(r - it.tonic) }
        val minorKey = key?.minor == true
        val cat = analysis?.category
        fun s(scale: Scale, reason: String) = ScaleSuggestion(scale, r, reason)
        val alts = mutableListOf<ScaleSuggestion>()

        val primary: ScaleSuggestion = when {
            // Dominantes : d'abord les tensions écrites dans l'accord, puis la fonction
            t.isDominant -> when {
                t == ChordType.DOM7_SHARP9 || t == ChordType.DOM7_FLAT13 || t == ChordType.DOM7_AUG ->
                    s(Scale.ALTERED, "Dominante altérée : la gamme altérée contient ♭9, ♯9, ♯11 et ♭13.")
                t == ChordType.DOM7_FLAT5 ->
                    s(Scale.WHOLE_TONE, "Septième quinte bémol : la gamme par tons contient la ♭5.").also {
                        alts += s(Scale.ALTERED, "Variante plus tendue.")
                    }
                t == ChordType.DOM7_SHARP11 ->
                    s(Scale.LYDIAN_DOMINANT, "Le ♯11 de l'accord appelle le lydien ♭7.")
                t == ChordType.DOM7_FLAT9 && !resolvesToMinor(chord, key) ->
                    s(Scale.HALF_WHOLE, "♭9 avec 13 naturelle vers un accord majeur : diminuée demi-ton / ton.").also {
                        alts += s(Scale.MIXO_B9_B13, "Variante avec ♭13.")
                    }
                cat == HarmonicCategory.SUBSTITUTE_DOMINANT ->
                    s(Scale.LYDIAN_DOMINANT, "Substitution tritonique : le lydien ♭7 garde les notes de la dominante remplacée.")
                cat == HarmonicCategory.BLUES ->
                    s(Scale.MIXOLYDIAN, "Septième de blues : mixolydien, à mélanger avec la gamme blues.").also {
                        alts += ScaleSuggestion(Scale.BLUES, key?.tonic ?: r, "Gamme blues de la tonalité, par-dessus toute la grille.")
                    }
                cat == HarmonicCategory.BORROWED && iv == 10 && !minorKey ->
                    s(Scale.LYDIAN_DOMINANT, "♭VII7 (dominante « backdoor ») : lydien ♭7.")
                t == ChordType.DOM7_SUS4 ->
                    s(Scale.MIXOLYDIAN, "Septième suspendue : mixolydien (la quarte remplace la tierce).")
                resolvesToMinor(chord, key) ->
                    s(Scale.MIXO_B9_B13, "Dominante qui résout vers un accord mineur : ♭9 et ♭13 annoncent la couleur mineure.").also {
                        alts += s(Scale.ALTERED, "Variante plus tendue (jazz).")
                    }
                else -> s(Scale.MIXOLYDIAN, "Dominante qui résout vers un accord majeur : mixolydien.").also {
                    if (style == MusicStyle.JAZZ) alts += s(Scale.LYDIAN_DOMINANT, "Variante avec ♯11.")
                    if (style == MusicStyle.JAZZ) alts += s(Scale.ALTERED, "Variante altérée, plus tendue.")
                }
            }
            t == ChordType.DIM7 || t == ChordType.DIM ->
                s(Scale.WHOLE_HALF, "Accord diminué : gamme diminuée ton / demi-ton.")
            t == ChordType.MINOR7_FLAT5 ->
                if (minorKey && iv == 2) s(Scale.LOCRIAN_NAT2, "IIm7♭5 en mineur : locrien ♮2 (la 9 naturelle sonne mieux).").also {
                    alts += s(Scale.LOCRIAN, "Variante (mode de la tonalité).")
                }
                else s(Scale.LOCRIAN, "Demi-diminué : locrien.").also {
                    alts += s(Scale.LOCRIAN_NAT2, "Variante avec 9 naturelle.")
                }
            t == ChordType.AUG -> s(Scale.WHOLE_TONE, "Accord augmenté : gamme par tons.")
            t == ChordType.MAJ7_AUG -> s(Scale.LYDIAN_AUGMENTED, "Septième majeure quinte augmentée : lydien augmenté.")
            t == ChordType.MINOR_MAJ7 || t == ChordType.MINOR_MAJ7_9 || t == ChordType.MINOR6 ->
                s(Scale.MELODIC_MINOR, "Mineur avec 7e majeure ou 6te : mineur mélodique.")
            t.isMinorTriadBased || t == ChordType.MINOR7_11 -> minorScale(chord, iv, minorKey, cat, ::s)
            t == ChordType.MAJ7_SHARP11 -> s(Scale.LYDIAN, "Le ♯11 de l'accord appelle le lydien.")
            t == ChordType.ONE_PLUS_FIVE || t == ChordType.ONE_PLUS_EIGHT ->
                s(Scale.MINOR_PENTATONIC, "Quinte à vide : ni majeur ni mineur, la pentatonique mineure (rock) convient.").also {
                    alts += s(Scale.MAJOR_PENTATONIC, "Variante plus claire.")
                }
            t == ChordType.SUS4 || t == ChordType.SUS2 -> s(Scale.MIXOLYDIAN, "Accord suspendu : mixolydien.")
            else -> majorScale(chord, key, iv, minorKey, cat, ::s)
        }

        // Pentatoniques en pop/rock/blues pour les accords parfaits
        if (style == MusicStyle.POP_ROCK || style == MusicStyle.BLUES) {
            if (t == ChordType.MAJOR || t == ChordType.SIXTH || t == ChordType.ADD9) {
                alts += s(Scale.MAJOR_PENTATONIC, "Pentatonique majeure : sans note à éviter, idéale pour improviser.")
            }
            if (t == ChordType.MINOR || t == ChordType.MINOR7) {
                alts += s(Scale.MINOR_PENTATONIC, "Pentatonique mineure : sans note à éviter, idéale pour improviser.")
            }
        }
        return ChordScaleResult(chord, primary, alts.distinctBy { it.scale to it.root }.filter { it.scale != primary.scale })
    }

    private fun minorScale(
        chord: ChordCandidate,
        iv: Int?,
        minorKey: Boolean,
        cat: HarmonicCategory?,
        s: (Scale, String) -> ScaleSuggestion,
    ): ScaleSuggestion {
        if (iv != null && cat != HarmonicCategory.CHROMATIC) {
            if (!minorKey) when (iv) {
                2 -> return s(Scale.DORIAN, "IIm : dorien (mode du IIe degré).")
                4 -> return s(Scale.PHRYGIAN, "IIIm : phrygien (mode du IIIe degré).")
                9 -> return s(Scale.AEOLIAN, "VIm : éolien (mode du VIe degré).")
                5 -> return s(Scale.DORIAN, "IVm (emprunt au mineur) : dorien.")
            } else when (iv) {
                0 -> return s(Scale.AEOLIAN, "Im : éolien, le mineur naturel de la tonalité.")
                5 -> return s(Scale.DORIAN, "IVm en mineur : dorien.")
                7 -> return s(Scale.PHRYGIAN, "Vm (mineur naturel) : phrygien.")
            }
        }
        return s(Scale.DORIAN, "Accord mineur : le dorien est la gamme la plus polyvalente.")
    }

    private fun majorScale(
        chord: ChordCandidate,
        key: Key?,
        iv: Int?,
        minorKey: Boolean,
        cat: HarmonicCategory?,
        s: (Scale, String) -> ScaleSuggestion,
    ): ScaleSuggestion {
        if (cat == HarmonicCategory.SECONDARY_DOMINANT || cat == HarmonicCategory.ALTERED_DOMINANT) {
            return if (resolvesToMinor(chord, key)) {
                s(Scale.MIXO_B9_B13, "Dominante qui résout vers un accord mineur : ♭9 et ♭13 annoncent la couleur mineure.")
            } else s(Scale.MIXOLYDIAN, "Fonction de dominante vers un accord majeur : mixolydien.")
        }
        if (iv != null && cat != HarmonicCategory.CHROMATIC) {
            if (!minorKey) when (iv) {
                0 -> return s(Scale.IONIAN, "I : ionien, la gamme de la tonalité.")
                5 -> return s(Scale.LYDIAN, "IV : lydien (mode du IVe degré).")
                7 -> return s(Scale.MIXOLYDIAN, "V (fonction de dominante) : mixolydien.")
                8, 3 -> return s(Scale.LYDIAN, "Emprunt au mineur (♭VI, ♭III) : lydien.")
                10 -> return s(Scale.MIXOLYDIAN, "♭VII (emprunt au mineur) : mixolydien.")
            } else when (iv) {
                3 -> return s(Scale.IONIAN, "♭III (relatif majeur) : ionien.")
                8 -> return s(Scale.LYDIAN, "♭VI : lydien (mode du VIe degré du mineur).")
                10 -> return s(Scale.MIXOLYDIAN, "♭VII : mixolydien.")
                7 -> return s(Scale.MIXO_B9_B13, "V majeur en mineur : mixolydien ♭9 ♭13 (mineur harmonique).")
            }
        }
        if (cat == HarmonicCategory.CHROMATIC || iv == null) {
            return if (chord.type == ChordType.MAJ7 || chord.type == ChordType.MAJ7_9) {
                s(Scale.LYDIAN, "Septième majeure hors tonalité : le lydien évite le frottement de la quarte.")
            } else s(Scale.IONIAN, "Accord majeur : ionien.")
        }
        return s(Scale.IONIAN, "Accord majeur : ionien.")
    }

    /** La dominante résout-elle (une quinte plus bas) vers un accord mineur de la tonalité ? */
    fun resolvesToMinor(chord: ChordCandidate, key: Key?): Boolean {
        key ?: return false
        val target = Notes.pc(chord.root - 7 - key.tonic)
        return if (key.minor) target in setOf(0, 5) else target in setOf(2, 4, 9)
    }

    /** Notes de la gamme situées un demi-ton au-dessus d'une note de l'accord (sauf ♭9 et ♭13 des dominantes). */
    fun avoidNotes(chord: ChordCandidate, suggestion: ScaleSuggestion): Set<Int> {
        val tones = chord.chordPitchClasses
        return suggestion.pitchClasses.filter { pc ->
            pc !in tones &&
                tones.any { Notes.pc(pc - it) == 1 } &&
                !(chord.type.isDominant && Notes.pc(pc - chord.root) in setOf(1, 8))
        }.toSet()
    }

    /** Libellés des tensions disponibles : notes de la gamme hors accord et non à éviter (9, 11, 13 et altérations). */
    fun tensions(chord: ChordCandidate, suggestion: ScaleSuggestion): List<String> {
        val tones = chord.chordPitchClasses
        val avoid = avoidNotes(chord, suggestion)
        return suggestion.pitchClasses
            .filter { it !in tones && it !in avoid }
            .map { Notes.pc(it - chord.root) }
            .filter { !isAddition(it, chord) }
            .sortedBy { TENSION_ORDER.indexOf(it).let { i -> if (i < 0) 99 else i } }
            .map { TENSION_LABELS[it] ?: "?" }
    }

    private fun hasSeventh(chord: ChordCandidate) = chord.type.intervals.any { it == 10 || it == 11 } || chord.type == ChordType.DIM7

    /** Septième (toujours) et sixte (sur un accord sans septième) : notes d'accord qu'on ajoute, pas des tensions. */
    private fun isAddition(iv: Int, chord: ChordCandidate) = iv == 10 || iv == 11 || (iv == 9 && !hasSeventh(chord))

    /** Sixte et septième de la gamme sur un accord sans septième (« 6 », « ♭7 », « 7 »). */
    fun additions(chord: ChordCandidate, suggestion: ScaleSuggestion): List<String> {
        if (hasSeventh(chord)) return emptyList()
        val tones = chord.chordPitchClasses
        val avoid = avoidNotes(chord, suggestion)
        return suggestion.pitchClasses
            .filter { it !in tones && it !in avoid }
            .map { Notes.pc(it - chord.root) }
            .filter { isAddition(it, chord) }
            .sorted()
            .map { when (it) { 9 -> "6"; 10 -> "♭7"; else -> "7" } }
    }

    /** Libellé d'une note de la gamme par rapport à la fondamentale de l'accord (« ♭9 », « 11 », « 13 »…). */
    fun intervalLabel(pc: Int, chord: ChordCandidate): String =
        if (Notes.pc(pc - chord.root) == 0) "1" else TENSION_LABELS[Notes.pc(pc - chord.root)] ?: "?"

    private val TENSION_ORDER = listOf(1, 2, 3, 5, 6, 8, 9, 10, 11, 4, 7)
    private val TENSION_LABELS = mapOf(
        1 to "♭9", 2 to "9", 3 to "♯9", 5 to "11", 6 to "♯11", 8 to "♭13", 9 to "13",
        10 to "♭7", 11 to "7", 4 to "3", 7 to "5",
    )

    /** Rôle d'une note de mélodie sur cet accord. */
    fun classify(note: Int, result: ChordScaleResult): MelodyRole {
        val pc = Notes.pc(note)
        return when {
            pc in result.chord.chordPitchClasses -> MelodyRole.CHORD_TONE
            pc in result.avoid -> MelodyRole.AVOID
            pc in result.primary.pitchClasses -> MelodyRole.TENSION
            result.alternatives.any { pc in it.pitchClasses } -> MelodyRole.TENSION
            else -> MelodyRole.CHROMATIC
        }
    }
}
