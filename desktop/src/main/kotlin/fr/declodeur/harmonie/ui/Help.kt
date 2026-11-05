package fr.declodeur.harmonie.ui

/** Textes des bulles d'aide. */
object Help {
    const val STYLE =
        "Le style oriente l'analyse harmonique. Blues : les septièmes de dominante sur les degrés I, IV et V " +
            "sont lues comme la couleur normale du blues (I7, IV7, V7), et non comme des dominantes secondaires. " +
            "Le style servira aussi aux prédictions d'enchaînements (versions suivantes)."

    const val INPUT =
        "L'instrument ou l'interface MIDI à écouter. Si votre instrument n'apparaît pas, branchez-le puis " +
            "cliquez sur Actualiser. S'il n'apparaît toujours pas, relancez Harmonie : Windows ne signale pas " +
            "toujours un appareil branché après le démarrage de l'application."

    const val MODE =
        "Complète : toutes les notes, sur tous les canaux, servent à reconnaître l'accord.\n\n" +
            "Accords + mélodie : seules les notes du canal des accords sont analysées. Les autres canaux " +
            "sont considérés comme la mélodie (en vert) et ne perturbent pas la reconnaissance."

    const val CHANNEL =
        "Canal MIDI qui porte les accords, par exemple la partie main gauche d'un arrangeur Yamaha. " +
            "Utilisé seulement en mode « Accords + mélodie »."

    const val CURRENT_CHORD =
        "Dernier accord reconnu. Il est validé quand les notes restent stables pendant le délai de stabilité " +
            "(voir Réglages), et seulement après une nouvelle note : relâcher des notes ne change pas l'accord.\n\n" +
            "Notation Yamaha : m = mineur, M7 = septième majeure, 7 = septième (de dominante), " +
            "(9) (11) (13) = tension ajoutée, ♭5 = quinte diminuée, aug = quinte augmentée, " +
            "dim = diminué, sus4 / sus2 = la tierce est remplacée par la quarte / la seconde, " +
            "1+5 = quinte à vide, 1+8 = note doublée. « /E » indique la basse quand ce n'est pas la fondamentale."

    const val INVERSION =
        "Quelle note de l'accord est à la basse. État fondamental : la fondamentale. 1er renversement : la tierce. " +
            "2e renversement : la quinte. 3e renversement : la septième. Le renversement change la couleur " +
            "et la conduite de la basse, pas la fonction de l'accord.\n\n" +
            "Renversement serré : la basse est proche des autres notes. L'accord est alors noté comme à l'état " +
            "fondamental (Cm plutôt que Cm/G), pour ne pas encombrer l'historique. La basse n'est notée que si " +
            "elle est détachée de l'accord (voir Réglages)."

    const val SLASH_GAP =
        "Écart minimal, en demi-tons, entre la note la plus grave et la note suivante pour noter la basse " +
            "(accord sur basse, par exemple Cm/G avec un sol grave sous l'accord). En dessous, un renversement " +
            "serré est noté Cm. 7 demi-tons (une quinte) par défaut ; 12 = une octave ; 0 = basse toujours notée.\n\n" +
            "Une basse détachée n'est pas comptée dans la lecture de l'accord : un F♯ grave sous C E♭ G donne " +
            "Cm/F♯. Exception : si la basse est la fondamentale d'un accord complet formé avec les notes du " +
            "dessus, cette lecture est gardée (un A grave sous C E G donne Am7, avec C/A en autre lecture)."

    const val ALTERNATIVES =
        "Les mêmes notes peuvent se nommer de plusieurs façons : C6 et Am7/C contiennent exactement les mêmes " +
            "notes, comme Cm7 et E♭6. La basse départage ; le contexte (tonalité, accords voisins) affinera " +
            "ce choix dans les versions suivantes."

    const val NOTES =
        "Chaque note qui sonne, avec son degré dans l'accord : 1 = fondamentale, 3 ou ♭3 = tierce majeure " +
            "ou mineure, 5 = quinte, 7 ou ♭7 = septième majeure ou mineure, 9 / 11 / 13 = tensions " +
            "(notes ajoutées au-dessus de la septième). « pédale » : touche relâchée, note tenue par la pédale."

    const val HISTORY =
        "Les accords reconnus successivement, du plus ancien (à gauche) au plus récent (encadré). " +
            "Un même accord rejoué n'est pas répété. Sous chaque accord : son chiffrage dans la tonalité du " +
            "moment, coloré selon sa fonction (voir la légende). Les accords sont regroupés par tonalité : un " +
            "séparateur marque chaque changement de tonalité, et le nom de la tonalité court sous ses accords. " +
            "« Effacer » vide l'historique et repart de zéro pour la tonalité " +
            "(utile pour commencer un nouveau morceau). Les flèches de résolution et crochets des II-V viendront " +
            "à l'étape suivante."

    const val KEYBOARD =
        "Les touches qui sonnent sont colorées selon leur rôle dans l'accord courant. " +
            "Cliquez sur une touche pour l'enfoncer, cliquez à nouveau pour la relâcher : pratique pour " +
            "tester sans instrument. « Relâcher tout » remet le clavier à zéro."

    const val STABILITY =
        "Durée pendant laquelle les notes doivent rester stables avant que l'accord soit validé. Plus court : " +
            "plus réactif. Plus long : moins d'accords parasites quand les doigts n'arrivent pas tous en même temps."

    const val SUSTAIN =
        "Si activé, les notes tenues par la pédale de sustain font partie de l'accord. Désactivez-le si vous " +
            "jouez beaucoup avec la pédale et que des notes de l'accord précédent perturbent la reconnaissance."

    const val KEY =
        "Tonalité la plus probable d'après ce qui a été joué récemment. Chaque note compte selon sa durée ; " +
            "le profil obtenu est comparé aux profils de référence des 24 tonalités (établis par le musicologue " +
            "David Temperley sur un corpus de musique tonale). Une cadence (accord de dominante qui résout une " +
            "quinte plus bas) renforce la tonalité d'arrivée.\n\n" +
            "Les pourcentages sont prudents tant qu'il y a peu de notes, puis se précisent. La tonalité affichée " +
            "en grand ne change que si une autre devient nettement plus probable, pour éviter les changements " +
            "intempestifs. Une tonalité majeure et sa relative mineure (C majeur / A mineur) ont les mêmes " +
            "notes : seuls la tonique, les cadences et la sensible (G♯ en la mineur) les départagent."

    const val KEY_MEMORY =
        "Durée au bout de laquelle le poids des notes jouées est divisé par deux. Courte : l'analyse suit " +
            "vite les modulations, mais s'égare plus facilement. Longue : plus stable, mais plus lente à suivre " +
            "un changement de tonalité. La mémoire ne s'efface que pendant que l'on joue : un silence ne fait " +
            "pas oublier la tonalité."

    const val NUMERAL =
        "Chiffrage romain : degré de la fondamentale dans la tonalité, suivi du suffixe de l'accord en " +
            "notation Yamaha (IIm7, V7, IM7). Les degrés sont comptés sur la gamme majeure de la tonique, comme " +
            "en analyse jazz : en mineur, les accords sur la 3ce, la 6te et la 7e mineures s'écrivent ♭III, ♭VI, ♭VII.\n\n" +
            "V7/II : dominante secondaire, dominante du IIe degré. SubV7 : substitution tritonique de la " +
            "dominante. ♯Idim7 : diminué qui monte d'un demi-ton vers le degré suivant."

    const val FUNCTION =
        "Fonction harmonique, le rôle de l'accord dans la phrase :\n" +
            "Tonique (T) : repos, point d'arrivée (I, VI, III).\n" +
            "Sous-dominante (SD) : éloignement, préparation (IV, II).\n" +
            "Dominante (D) : tension qui appelle la tonique (V, VII).\n\n" +
            "Diatonique : toutes les notes appartiennent à la tonalité. Emprunt modal : accord pris au mode " +
            "homonyme (C mineur en C majeur), comme IVm ou ♭VII7. Chromatique : hors tonalité, peut annoncer " +
            "une modulation."

    const val KEEP_ROOT_POSITION =
        "Quand la basse est détachée et qu'elle est la fondamentale d'un accord complet formé avec les notes " +
            "du dessus, garder cette lecture : un A grave sous C E G donne Am7. Désactivé : la basse n'est " +
            "jamais comptée et l'on obtient C/A."

    const val KEEP_FULL_WHEN_INCOMPLETE =
        "Quand la basse est détachée mais que le dessus n'a que deux notes différentes, compter quand même la " +
            "basse : E grave sous C G donne C/E. Désactivé : on obtient C1+5/E."
}
