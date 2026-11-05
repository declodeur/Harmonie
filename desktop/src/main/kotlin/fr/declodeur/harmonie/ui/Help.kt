package fr.declodeur.harmonie.ui

/** Textes des bulles d'aide. */
object Help {
    const val STYLE =
        "Le style oriente l'analyse : accords attendus, règles de fonction harmonique et probabilités " +
            "des enchaînements. Il servira à la détection de tonalité et aux prédictions (versions suivantes)."

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
            "et la conduite de la basse, pas la fonction de l'accord."

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
            "Un même accord rejoué n'est pas répété. Les versions suivantes y ajouteront le chiffrage romain " +
            "et la notation d'analyse (flèches de résolution, crochets des II-V)."

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
}
