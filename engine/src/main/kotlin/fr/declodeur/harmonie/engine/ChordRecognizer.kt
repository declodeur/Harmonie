package fr.declodeur.harmonie.engine

/** Rôle d'une note par rapport à un accord reconnu. */
enum class ToneRole { ROOT, CHORD_TONE, TENSION, EXTRA, BASS, NONE }

/** Une lecture possible d'un ensemble de notes. */
data class ChordCandidate(
    val root: Int,
    val type: ChordType,
    /** Classe de hauteur de la note la plus grave. */
    val bass: Int,
    /** L'accord est reconnu sans sa quinte. */
    val omittedFifth: Boolean,
    /** Classes de hauteur jouées qui n'appartiennent pas à l'accord (hors basse détachée). */
    val extraNotes: Set<Int>,
    val score: Int,
    /**
     * La basse est assez éloignée du reste de l'accord pour être notée (« /E »).
     * Sinon l'accord est noté comme à l'état fondamental, même s'il est renversé.
     */
    val bassDetached: Boolean = true,
) {
    val isExact: Boolean get() = !omittedFifth && extraNotes.isEmpty()
    val rootName: String get() = rootNameIn(Spelling.DEFAULT)

    fun rootNameIn(spelling: Spelling): String = Notes.name(root, spelling)

    fun slashIn(spelling: Spelling): String? = if (bass != root && bassDetached) Notes.name(bass, spelling) else null

    /** Symbole Yamaha avec l'orthographe de la tonalité (« D♭M7 », « F♯m/C♯ »). */
    fun symbolIn(spelling: Spelling): String =
        rootNameIn(spelling) + type.suffix + (slashIn(spelling)?.let { "/$it" } ?: "")

    val chordPitchClasses: Set<Int> get() = type.intervals.map { (root + it) % 12 }.toSet()

    /** Basse détachée qui n'appartient pas à l'accord : accord sur basse étrangère (Cm/F♯). */
    val foreignBass: Boolean get() = bassDetached && bass !in chordPitchClasses && bass !in extraNotes

    /** Basse notée d'un accord sur basse (« /E »), sinon null. */
    val slash: String? get() = if (bass != root && bassDetached) Notes.name(bass) else null

    /** Symbole complet en notation Yamaha, par exemple « CM7 », « Am7/G ». */
    val symbol: String get() = rootName + type.suffix + (slash?.let { "/$it" } ?: "")

    fun roleOf(note: Int): ToneRole {
        val p = Notes.pc(note)
        if (p in extraNotes) return ToneRole.EXTRA
        if (p == bass && foreignBass) return ToneRole.BASS
        val iv = Notes.pc(p - root)
        return when {
            iv == 0 -> ToneRole.ROOT
            iv !in type.intervals -> ToneRole.NONE
            iv in type.tensions -> ToneRole.TENSION
            else -> ToneRole.CHORD_TONE
        }
    }

    /** Degré d'une note dans l'accord (« 1 », « 3 », « ♭7 »…), ou null si étrangère. */
    fun degreeOf(note: Int): String? {
        val iv = Notes.pc(note - root)
        return if (iv in type.intervals) type.degreeLabel(iv) else null
    }

    /** Description du renversement, en français. */
    val inversion: String get() = inversionIn(Spelling.DEFAULT)

    fun inversionIn(spelling: Spelling): String {
        if (bass == root) return "état fondamental"
        if (foreignBass) return "accord sur basse ${Notes.name(bass, spelling)} (basse étrangère à l'accord)"
        val iv = Notes.pc(bass - root)
        if (iv !in type.intervals) return "basse étrangère à l'accord (${Notes.name(bass, spelling)})"
        val label = type.degreeLabel(iv)
        if (iv in type.tensions) return "$label à la basse"
        return when (label) {
            "♭3", "3" -> "1er renversement (tierce à la basse)"
            "5", "♭5", "♯5" -> "2e renversement (quinte à la basse)"
            "♭7", "7", "♭♭7" -> "3e renversement (septième à la basse)"
            "6" -> "sixte à la basse"
            "4" -> "quarte à la basse"
            "2" -> "seconde à la basse"
            else -> "$label à la basse"
        }
    }

    /**
     * La même lecture placée sur une autre basse (détachée), avec un score recalculé pour cette
     * basse : bonus si c'est la fondamentale, aucune pénalité si elle est hors de l'accord
     * (c'est justement un accord sur basse).
     */
    internal fun onBass(newBass: Int): ChordCandidate {
        val b = Notes.pc(newBass)
        val tones = chordPitchClasses
        val oldTerm = when {
            bass == root -> ROOT_BASS_BONUS
            bass in tones -> 0
            else -> FOREIGN_BASS_PENALTY
        }
        val newTerm = if (b == root) ROOT_BASS_BONUS else 0
        return copy(bass = b, bassDetached = true, score = score - oldTerm + newTerm)
    }

    internal companion object {
        const val ROOT_BASS_BONUS = 15
        const val FOREIGN_BASS_PENALTY = -10
    }
}

/** Exceptions à la règle « une basse détachée n'est pas comptée dans la lecture ». */
data class BassReadingOptions(
    /** Si la basse est la fondamentale d'un accord exact avec le dessus, garder cette lecture (Am7 plutôt que C/A). */
    val keepRootPositionReading: Boolean = true,
    /** Si le dessus a moins de 3 notes différentes, compter la basse (C/E plutôt que C1+5/E). */
    val keepFullReadingWhenUpperIncomplete: Boolean = true,
)

/** Résultat de l'analyse d'un ensemble de notes jouées ensemble. */
data class ChordResult(
    /** Notes MIDI, triées. */
    val notes: List<Int>,
    val pitchClasses: Set<Int>,
    /** Classe de hauteur de la note la plus grave, null si aucune note. */
    val bass: Int?,
    val best: ChordCandidate?,
    val alternatives: List<ChordCandidate>,
    /** Accord reconstitué à partir de notes jouées successivement (arpège) : c'est une interprétation. */
    val arpeggiated: Boolean = false,
) {
    /** Pour deux notes qui ne forment pas un accord : nom de l'intervalle. */
    val intervalDescription: String?
        get() {
            if (best != null || pitchClasses.size != 2 || bass == null) return null
            val other = pitchClasses.first { it != bass }
            return "${Notes.intervalName(other - bass)} (${Notes.name(bass)} – ${Notes.name(other)})"
        }
}

/**
 * Reconnaissance d'accords par comparaison avec les modèles Yamaha.
 *
 * Toutes les fondamentales jouées sont essayées avec tous les types. Une lecture est
 * retenue si elle correspond exactement, ou s'il manque seulement la quinte, ou s'il
 * y a une seule note étrangère. Le score favorise : la correspondance exacte, la
 * fondamentale à la basse, puis l'accord le plus simple. Les autres lectures proches
 * sont gardées comme alternatives (C6 / Am7/C par exemple).
 *
 * Basse détachée (écart ≥ seuil) : l'accord est lu sur les notes du dessus, sans la
 * basse, puis noté sur cette basse (F♯ + C E♭ G = Cm/F♯). Exception : si la basse est la
 * fondamentale d'un accord exact formé avec les notes du dessus (A + C E G = Am7,
 * C + E G B♭ = C7), cette lecture complète est gardée.
 */
object ChordRecognizer {
    private const val MAX_EXTRA = 1
    private const val ALT_WINDOW = 20
    private const val MAX_ALTERNATIVES = 3

    /**
     * [slashMinGap] : écart minimal, en demi-tons, entre la note la plus grave et la note
     * suivante (d'un autre nom) pour que la basse soit détachée et notée (« Cm/G »).
     * En dessous, un renversement serré est noté comme l'accord à l'état fondamental.
     * 0 : la basse est toujours notée, et toujours comptée dans la lecture.
     */
    fun recognize(
        notes: Collection<Int>,
        slashMinGap: Int = 0,
        options: BassReadingOptions = BassReadingOptions(),
    ): ChordResult {
        val sorted = notes.distinct().sorted()
        if (sorted.isEmpty()) return ChordResult(emptyList(), emptySet(), null, null, emptyList())
        val pcs = sorted.map(Notes::pc).toSet()
        val bass = Notes.pc(sorted.first())
        val nextOther = sorted.firstOrNull { Notes.pc(it) != bass }
        val detached = nextOther == null || nextOther - sorted.first() >= slashMinGap

        val full = rank(sorted, detached)
        var ranked = full
        if (slashMinGap > 0 && detached && nextOther != null) {
            val upperNotes = sorted.drop(1)
            val upper = rank(upperNotes, true).map { it.onBass(bass) }
            val fullBest = full.firstOrNull()
            val upperBest = upper.firstOrNull()
            val rootPosition = fullBest != null && fullBest.root == bass && fullBest.isExact
            val upperComplete = upperNotes.map(Notes::pc).toSet().size >= 3
            val upperIsSolid = upperBest != null && upperBest.isExact &&
                (upperComplete || !options.keepFullReadingWhenUpperIncomplete)
            ranked = when {
                // La lecture du dessus (C/A sous Am7) remplace la lecture complète de même nom,
                // puis tout est retrié par score (sinon FM7 sous un F grave passait en dernier).
                rootPosition && options.keepRootPositionReading ->
                    upper.take(1).let { u -> full.filter { f -> u.none { it.symbol == f.symbol } } + u }
                        .sortedByDescending { it.score }
                upperIsSolid || (fullBest == null && upperBest != null) -> upper
                else -> full
            }
            ranked = ranked.distinctBy { it.symbol }
        }

        val best = ranked.firstOrNull()
        // Si la meilleure lecture est exacte, les lectures approximatives n'apportent rien
        val alternatives = if (best == null) emptyList()
        else ranked.drop(1)
            .filter { it.score >= best.score - ALT_WINDOW && (!best.isExact || it.isExact) }
            .take(MAX_ALTERNATIVES)
        return ChordResult(sorted, pcs, bass, best, alternatives)
    }

    /** Toutes les lectures possibles d'un ensemble de notes (triées), de la meilleure à la moins bonne. */
    private fun rank(sorted: List<Int>, detached: Boolean): List<ChordCandidate> {
        if (sorted.isEmpty()) return emptyList()
        val pcs = sorted.map(Notes::pc).toSet()
        val bass = Notes.pc(sorted.first())
        val candidates = mutableListOf<ChordCandidate>()

        if (pcs.size == 1) {
            if (sorted.size >= 2) {
                candidates += ChordCandidate(bass, ChordType.ONE_PLUS_EIGHT, bass, false, emptySet(), 100, detached)
            }
        } else {
            for (root in pcs) {
                for (type in ChordType.entries) {
                    if (type == ChordType.ONE_PLUS_EIGHT) continue
                    val tones = type.intervals.map { (root + it) % 12 }.toSet()
                    val missing = tones - pcs
                    val omitted = when {
                        missing.isEmpty() -> false
                        type.canOmitFifth && missing == setOf((root + 7) % 12) -> true
                        else -> continue
                    }
                    val extra = pcs - tones
                    if (extra.size > MAX_EXTRA) continue
                    if (extra.isNotEmpty() && (omitted || type == ChordType.ONE_PLUS_FIVE)) continue
                    var score = 100 - 2 * type.complexity
                    if (omitted) score -= 12
                    score -= 35 * extra.size
                    score += when {
                        bass == root -> ChordCandidate.ROOT_BASS_BONUS
                        bass in tones -> 0
                        else -> ChordCandidate.FOREIGN_BASS_PENALTY
                    }
                    candidates += ChordCandidate(root, type, bass, omitted, extra, score, detached)
                }
            }
        }

        return candidates
            .sortedWith(compareByDescending<ChordCandidate> { it.score }.thenBy { it.type.complexity })
            .distinctBy { it.symbol }
    }
}
