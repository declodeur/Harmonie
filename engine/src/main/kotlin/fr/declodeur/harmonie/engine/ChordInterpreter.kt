package fr.declodeur.harmonie.engine

/**
 * Règles d'interprétation contextuelle des accords, issues du banc d'essai (comparaison avec des
 * analyses d'experts). Chacune est une hypothèse mesurée, réglable dans l'application ; on peut donc
 * toujours revenir en arrière.
 */
data class InterpretationSettings(
    /**
     * Style Classique : un accord de sixte ajoutée est lu comme un accord de septième renversé
     * (C6 = Am7/C, Cm6 = Am7♭5/C), comme le font les analyses classiques.
     */
    val sixthAsSeventh: Boolean = true,
    /** Accord de septième diminuée nommé d'après la sensible qu'il contient (B°7 en C, et non D°7). */
    val diminishedByKey: Boolean = true,
    /**
     * Style Classique : quarte (ou seconde) à la place de la tierce = retard ; l'accord est lu avec sa
     * tierce (Gsus4 → G, retard 4-3), la tierce étant prise dans la tonalité.
     */
    val suspensions: Boolean = true,
    /** Une ou deux notes qui appartiennent à l'accord en cours ne le remplacent pas (C puis E seul = toujours C). */
    val keepChordOnSubset: Boolean = true,
    /**
     * Deux notes à la tierce ou à la sixte sont lues comme l'accord parfait correspondant (A–C♯ = A) ;
     * en style Classique, une quinte seule aussi (avec la tierce de la tonalité).
     */
    val completeDyads: Boolean = true,
    /**
     * Fenêtre harmonique (ms, 0 = désactivée) : quand moins de trois notes sonnent, l'accord est lu sur
     * les notes tenues et les notes attaquées dans ce délai (basse d'Alberti, figurations).
     */
    val harmonicWindowMs: Int = 0,
    /**
     * Séparation harmonie / mélodie (mode Complète) : une seule voix qui bouge par degré conjoint vers
     * une note étrangère à l'accord en cours, les autres voix restant dans l'accord, est une note de
     * passage ou une broderie : l'accord continue.
     */
    val passingTones: Boolean = false,
)

object ChordInterpreter {

    /** Applique les règles qui ne dépendent que de l'accord et de la tonalité. */
    fun interpret(result: ChordResult, key: Key?, style: MusicStyle, s: InterpretationSettings): ChordResult {
        val best = result.best ?: return result
        var c = best
        if (s.sixthAsSeventh && style == MusicStyle.CLASSICAL) c = sixthAsSeventh(c)
        if (s.suspensions && style == MusicStyle.CLASSICAL) c = suspension(c, key)
        if (s.diminishedByKey && key != null) c = diminishedRoot(c, key)
        if (c === best) return result
        val alternatives = (listOf(best) + result.alternatives).filter { it.symbol != c.symbol }.take(3)
        return result.copy(best = c, alternatives = alternatives)
    }

    /** C6 → Am7/C ; Cm6 → Am7♭5/C (mêmes notes). */
    fun sixthAsSeventh(c: ChordCandidate): ChordCandidate {
        val newType = when (c.type) {
            ChordType.SIXTH -> ChordType.MINOR7
            ChordType.MINOR6 -> ChordType.MINOR7_FLAT5
            else -> return c
        }
        if (!c.isExact) return c
        return c.copy(
            root = Notes.pc(c.root + 9),
            type = newType,
            interpretation = "Lu comme ${Notes.name(c.root + 9)}${newType.suffix} renversé (style classique) ; " +
                "en notation de variété : ${c.rootName}${c.type.suffix}.",
        )
    }

    /** Gsus4 → G (retard 4-3), G7sus4 → G7, Csus2 → C ; tierce majeure ou mineure selon la tonalité. */
    fun suspension(c: ChordCandidate, key: Key?): ChordCandidate {
        val (suspended, isSeventh) = when (c.type) {
            ChordType.SUS4 -> 5 to false
            ChordType.DOM7_SUS4 -> 5 to true
            ChordType.SUS2 -> 2 to false
            else -> return c
        }
        val minorThird = key != null && !isSeventh && thirdInKey(c.root, key) == 3
        val type = when {
            isSeventh -> ChordType.DOM7
            minorThird -> ChordType.MINOR
            else -> ChordType.MAJOR
        }
        val label = if (suspended == 5) "retard de la tierce par la quarte (4-3)" else "seconde à la place de la tierce (retard 2-3 ou 9-8)"
        return c.copy(
            type = type,
            extraNotes = c.extraNotes + Notes.pc(c.root + suspended),
            omittedFifth = false,
            interpretation = "${c.rootName}${c.type.suffix} lu comme ${c.rootName}${type.suffix} : $label.",
        )
    }

    /** Tierce (3 ou 4 demi-tons) de la gamme de la tonalité au-dessus de [root], ou null. */
    private fun thirdInKey(root: Int, key: Key): Int? {
        val scale = if (key.minor) KeyDetector.MINOR_SCALE else KeyDetector.MAJOR_SCALE
        val rel = Notes.pc(root - key.tonic)
        val major = Notes.pc(rel + 4) in scale
        val minor = Notes.pc(rel + 3) in scale
        return when {
            major && !minor -> 4
            minor && !major -> 3
            else -> null
        }
    }

    /**
     * Septième diminuée : ses quatre notes sont à égale distance, n'importe laquelle peut être la
     * fondamentale. On choisit la sensible (un demi-ton sous la tonique, puis sous la dominante, puis
     * sous un autre degré de la gamme), comme dans le chiffrage vii°7, vii°7/V…
     */
    fun diminishedRoot(c: ChordCandidate, key: Key): ChordCandidate {
        if (c.type != ChordType.DIM7) return c
        val scale = if (key.minor) KeyDetector.MINOR_SCALE else KeyDetector.MAJOR_SCALE
        val roots = c.chordPitchClasses
        val order = listOf(0, 7, 9, 2, 5, 4, 3, 8, 10, 11, 1, 6) // degrés visés, par priorité
        val target = order.firstOrNull { t -> Notes.pc(t) in scale && Notes.pc(key.tonic + t - 1) in roots } ?: return c
        val root = Notes.pc(key.tonic + target - 1)
        if (root == c.root) return c
        return c.copy(root = root, interpretation = "Septième diminuée nommée d'après la sensible (${Spelling.leadingTone(root + 1, key.spelling)}).")
    }

    /**
     * Deux notes : tierce majeure ou mineure (ou sixte, son renversement) → accord parfait sur la note
     * inférieure de la tierce ; quinte → accord parfait avec la tierce de la tonalité.
     */
    fun dyad(result: ChordResult, key: Key?, style: MusicStyle = MusicStyle.CLASSICAL): ChordResult {
        if (result.pitchClasses.size != 2 || result.bass == null) return result
        val bass = result.bass
        val other = result.pitchClasses.first { it != bass }
        val iv = Notes.pc(other - bass)
        val (root, type) = when (iv) {
            4 -> bass to ChordType.MAJOR
            3 -> bass to ChordType.MINOR
            8 -> other to ChordType.MAJOR // sixte mineure = tierce majeure renversée
            9 -> other to ChordType.MINOR // sixte majeure = tierce mineure renversée
            7, 5 -> {
                if (style != MusicStyle.CLASSICAL) return result // quinte à vide (power chord) en variété
                val r = if (iv == 7) bass else other
                val third = key?.let { thirdInKey(r, it) } ?: return result
                r to if (third == 3) ChordType.MINOR else ChordType.MAJOR
            }
            else -> return result
        }
        val candidate = ChordCandidate(
            root, type, bass, omittedFifth = iv in setOf(3, 4, 8, 9), extraNotes = emptySet(), score = 50,
            bassDetached = false,
            interpretation = "Deux notes seulement : lues comme l'accord ${Notes.name(root)}${type.suffix} incomplet.",
        )
        return result.copy(best = candidate, alternatives = listOfNotNull(result.best))
    }
}
