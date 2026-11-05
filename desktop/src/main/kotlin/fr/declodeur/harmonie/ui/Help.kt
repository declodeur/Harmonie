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
        "Les accords reconnus successivement, du plus ancien (à gauche) au plus récent (encadré). Un même " +
            "accord rejoué n'est pas répété. Sous chaque accord : son chiffrage dans la tonalité du moment, " +
            "coloré selon sa fonction.\n\n" +
            "Notation d'analyse (inspirée de l'analyse jazz) :\n" +
            "Flèche pleine : une dominante résout une quinte plus bas (V7 → I, V7/II → IIm).\n" +
            "Flèche pointillée : une dominante résout un demi-ton plus bas (substitution tritonique, SubV7 → I).\n" +
            "Crochet : II-V, un accord mineur suivi d'une dominante une quarte plus haut (Dm7 – G7).\n" +
            "Bordure pointillée : accord arpégé, reconstitué à partir de notes jouées successivement.\n\n" +
            "Les accords sont regroupés par tonalité : un séparateur marque chaque changement, et le nom de " +
            "la tonalité court sous ses accords. Le dernier accord reste visible, suivi d'un espace libre pour " +
            "la suite ; Maj + molette ou pavé tactile pour revoir les accords plus anciens. « Effacer » vide " +
            "l'historique et remet la tonalité à zéro (nouveau morceau)."

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
        "Tonalité la plus probable d'après ce qui a été joué récemment, et cercle des quintes des 24 " +
            "tonalités : majeures sur l'anneau extérieur, relatives mineures à l'intérieur. Deux tonalités " +
            "voisines ne diffèrent que d'une altération : les tonalités plausibles forment donc une zone.\n\n" +
            "Contour orange : tonalité compatible, sa gamme contient (presque) toutes les notes jouées. " +
            "Remplissage bleu : probabilité, qui dépend de la compatibilité, du centre de gravité des notes " +
            "(comparé aux profils de référence de chaque tonalité) et des cadences (dominante qui résout une " +
            "quinte plus bas). Trait blanc : tonalité retenue ; elle ne change que si une autre devient " +
            "nettement plus probable, pour éviter les changements intempestifs.\n\n" +
            "Quand plusieurs tonalités contiennent toutes les notes (par exemple C majeur et A mineur), " +
            "leurs probabilités restent proches : seuls la tonique, les cadences et les notes caractéristiques " +
            "(G♯ en la mineur) les départagent, notamment dans la mélodie. Le style compte aussi : en " +
            "pop/rock, blues et jazz, le mineur naturel (avec la 7e mineure) est normal ; en classique, " +
            "le mineur attend la sensible."

    const val KEY_MEMORY =
        "Durée au bout de laquelle le poids des notes jouées est divisé par deux. Courte : l'analyse suit " +
            "vite les modulations progressives, mais s'égare plus facilement. Longue : plus stable. La mémoire " +
            "ne s'efface que pendant que l'on joue : un silence ne fait pas oublier la tonalité. Les ruptures " +
            "franches sont traitées à part (voir « Changement immédiat »)."

    const val FAST_KEY_CHANGE =
        "Comme à l'oreille : dès qu'un accord sort franchement de la tonalité, on sait qu'elle a changé. " +
            "Un accord est une rupture s'il a au moins deux notes hors de la gamme et qu'aucune fonction ne " +
            "l'explique (ni dominante secondaire, ni emprunt, ni substitution tritonique, ni diminué de passage). " +
            "Exemple : F♯ en C majeur. La mémoire de tonalité est alors effacée et la nouvelle tonalité se " +
            "construit à partir de cet accord. A7, Fm, B♭7 ou D♭7 en C majeur ne déclenchent rien."

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

    const val MELODY_WEIGHT =
        "Poids de la mélodie par rapport à l'harmonie dans la détection de tonalité. 0 % : la mélodie est " +
            "ignorée ; 100 % : elle pèse autant que l'harmonie. La mélodie, ce sont les notes des autres canaux " +
            "en mode « Accords + mélodie », et les notes jouées seules (sans autre note attaquée en même temps) " +
            "en mode « Complète ». Elle départage souvent deux tonalités relatives grâce à leurs notes " +
            "caractéristiques."

    const val ARPEGGIO =
        "Quand aucun accord n'est tenu (notes jouées une par une), Harmonie cherche un accord dans les " +
            "dernières notes : il remonte note par note et garde la plus longue suite qui forme exactement un " +
            "accord d'au moins 3 notes différentes (C E G B♭ donne C7 plutôt que Edim). C'est une " +
            "interprétation : une mélodie peut aussi dessiner un accord. Ces accords sont signalés par une " +
            "bordure pointillée dans l'historique."

    const val ARPEGGIO_WINDOW =
        "Jusqu'où remonter pour reconnaître un arpège : nombre maximal de notes (premier curseur) et durée " +
            "maximale (second curseur). Plus loin : arpèges lents ou longs reconnus, mais risque de mélanger " +
            "deux accords successifs."

    const val LAYOUT =
        "Les frontières entre les cadres de l'écran principal se déplacent à la souris : survolez l'espace " +
            "entre deux cadres (le curseur devient une double flèche) puis faites glisser. La disposition est " +
            "mémorisée. Ce bouton revient à la disposition d'origine."
}
