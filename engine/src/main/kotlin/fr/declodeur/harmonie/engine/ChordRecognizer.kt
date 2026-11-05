package fr.declodeur.harmonie.engine

/** Rôle d'une note par rapport à un accord reconnu. */
enum class ToneRole { ROOT, CHORD_TONE, TENSION, EXTRA, NONE }

/** Une lecture possible d'un ensemble de notes. */
data class ChordCandidate(
    val root: Int,
    val type: ChordType,
    /** Classe de hauteur de la note la plus grave. */
    val bass: Int,
    /** L'accord est reconnu sans sa quinte. */
    val omittedFifth: Boolean,
    /** Classes de hauteur jouées qui n'appartiennent pas à l'accord. */
    val extraNotes: Set<Int>,
    val score: Int,
    /**
     * La basse est assez éloignée du reste de l'accord pour être notée (« /E »).
     * Sinon l'accord est noté comme à l'état fondamental, même s'il est renversé.
     */
    val bassDetached: Boolean = true,
) {
    val isExact: Boolean get() = !omittedFifth && extraNotes.isEmpty()
    val rootName: String get() = Notes.name(root)

    /** Basse notée d'un accord sur basse (« /E »), sinon null. */
    val slash: String? get() = if (bass != root && bassDetached) Notes.name(bass) else null

    /** Symbole complet en notation Yamaha, par exemple « CM7 », « Am7/G ». */
    val symbol: String get() = rootName + type.suffix + (slash?.let { "/$it" } ?: "")

    fun roleOf(note: Int): ToneRole {
        val p = Notes.pc(note)
        if (p in extraNotes) return ToneRole.EXTRA
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
    val inversion: String
        get() {
            if (bass == root) return "état fondamental"
            val iv = Notes.pc(bass - root)
            if (iv !in type.intervals) return "basse étrangère à l'accord (${Notes.name(bass)})"
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
}

/** Résultat de l'analyse d'un ensemble de notes jouées ensemble. */
data class ChordResult(
    /** Notes MIDI, triées. */
    val notes: List<Int>,
    val pitchClasses: Set<Int>,
    /** Classe de hauteur de la note la plus grave, null si aucune note. */
    val bass: Int?,
    val best: ChordCandidate?,
    val alternatives: List<ChordCandidate>,
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
 */
object ChordRecognizer {
    private const val MAX_EXTRA = 1
    private const val ALT_WINDOW = 20

    /**
     * [slashMinGap] : écart minimal, en demi-tons, entre la note la plus grave et la note
     * suivante (d'un autre nom) pour que la basse soit notée (accord sur basse « Cm/G »).
     * En dessous, un renversement serré est noté comme l'accord à l'état fondamental.
     * 0 : la basse est toujours notée.
     */
    fun recognize(notes: Collection<Int>, slashMinGap: Int = 0): ChordResult {
        val sorted = notes.distinct().sorted()
        if (sorted.isEmpty()) return ChordResult(emptyList(), emptySet(), null, null, emptyList())
        val pcs = sorted.map(Notes::pc).toSet()
        val bass = Notes.pc(sorted.first())
        val nextOther = sorted.firstOrNull { Notes.pc(it) != bass }
        val detached = nextOther == null || nextOther - sorted.first() >= slashMinGap
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
                        bass == root -> 15
                        bass in tones -> 0
                        else -> -10
                    }
                    candidates += ChordCandidate(root, type, bass, omitted, extra, score, detached)
                }
            }
        }

        val ranked = candidates
            .sortedWith(compareByDescending<ChordCandidate> { it.score }.thenBy { it.type.complexity })
            .distinctBy { it.symbol }
        val best = ranked.firstOrNull()
        // Si la meilleure lecture est exacte, les lectures approximatives n'apportent rien
        val alternatives = if (best == null) emptyList()
        else ranked.drop(1)
            .filter { it.score >= best.score - ALT_WINDOW && (!best.isExact || it.isExact) }
            .take(3)
        return ChordResult(sorted, pcs, bass, best, alternatives)
    }
}
