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
        "Les notes de l'accord, avec leur octave (C4 = do central) et leur degré : 1 = fondamentale, 3 ou ♭3 = " +
            "tierce majeure ou mineure, 5 = quinte, 7 ou ♭7 = septième majeure ou mineure, 9 / 11 / 13 = tensions " +
            "(notes ajoutées au-dessus de la septième). Couleurs comme sur le clavier ; couleur pâle : note tenue " +
            "par la pédale. Les notes de mélodie s'affichent en dessous, classées par rapport à l'accord."

    const val HISTORY =
        "Les accords reconnus successivement, du plus ancien (à gauche) au plus récent (encadré). Un même " +
            "accord rejoué n'est pas répété. Sous chaque accord : son chiffrage dans la tonalité du moment, " +
            "coloré selon sa fonction.\n\n" +
            "Notation d'analyse (inspirée de l'analyse jazz) :\n" +
            "Flèche pleine : une dominante résout une quinte plus bas (V7 → I, V7/II → IIm).\n" +
            "Flèche pointillée : une dominante résout un demi-ton plus bas (substitution tritonique, SubV7 → I).\n" +
            "Crochet : II-V, un accord mineur suivi d'une dominante une quarte plus haut (Dm7 – G7).\n" +
            "Bordure pointillée : accord arpégé, reconstitué à partir de notes jouées successivement.\n\n" +
            "Réanalyse a posteriori : quand une modulation se confirme, les derniers accords qui appartiennent " +
            "déjà à la nouvelle tonalité y sont rechiffrés (au plus 4, réglable). Le premier est l'accord " +
            "pivot, noté comme en analyse classique « vi = ii » : son chiffrage dans l'ancienne tonalité, puis " +
            "dans la nouvelle. L'affichage en direct, lui, ne change pas.\n\n" +
            "Au-dessus des accords, la courbe de tension (de « repos » à « max ») ; en dessous, la durée de " +
            "chaque accord et le rythme harmonique. « Grille » ouvre la grille du morceau, à copier ou imprimer.\n\n" +
            "Fonction de VI et VII : celle du dernier accord est provisoire (d'après la suite la plus probable) ; " +
            "elle est corrigée dès que l'accord suivant est joué.\n\n" +
            "Les accords sont regroupés par tonalité : un séparateur marque chaque changement, et le nom de " +
            "la tonalité court sous ses accords. Le dernier accord reste visible ; Maj + molette ou pavé tactile " +
            "pour revoir les accords plus anciens. « Effacer » vide " +
            "l'historique et remet la tonalité à zéro (nouveau morceau)."

    const val KEYBOARD =
        "Les touches qui sonnent sont colorées selon leur rôle dans l'accord courant. " +
            "Cliquez sur une touche pour l'enfoncer, cliquez à nouveau pour la relâcher : pratique pour " +
            "tester sans instrument. « Relâcher tout » remet le clavier à zéro.\n\n" +
            "Repères de gamme (désactivables dans les Réglages) : un point sur chaque note de la gamme de " +
            "l'accord courant, plus gros sur sa tonique ; un cercle rouge sur les notes à éviter."

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
            "quinte plus bas), ainsi que de la tonique : premier accord du morceau, accords sur lesquels on " +
            "arrive (V → I, ♭VII → i, IV → I) et temps passé dessus. Trait blanc : tonalité retenue ; tant que " +
            "tout ce qu'on joue reste dans sa gamme, elle ne change pas (il faut des notes nouvelles ou une " +
            "rupture), pour éviter les changements intempestifs. Quand une autre tonalité reste presque aussi " +
            "probable, la ligne sous le nom l'indique (« Hésite avec… »).\n\n" +
            "Quand plusieurs tonalités contiennent toutes les notes (par exemple C majeur et A mineur), " +
            "leurs probabilités restent proches : seuls la tonique, les cadences et les notes caractéristiques " +
            "(G♯ en la mineur) les départagent, notamment dans la mélodie. Le style compte aussi : en " +
            "pop/rock, blues et jazz, le mineur naturel (avec la 7e mineure) est normal ; en classique, " +
            "le mineur attend la sensible."

    const val KEY_MEMORY =
        "Durée au bout de laquelle le poids des notes jouées est divisé par deux. Courte : l'analyse suit " +
            "vite les modulations progressives, mais s'égare plus facilement. Longue : plus stable. La mémoire " +
            "ne s'efface que pendant que l'on joue : un silence ne fait pas oublier la tonalité. Les ruptures " +
            "franches sont traitées à part (voir « Changement immédiat »).\n\n" +
            "Utilisée seulement si la mémoire en accords est à 0. Banc d'essai : la mémoire en accords fait mieux, surtout en pop."

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
            "Tonique (T) : repos, point d'arrivée (I ; III et VI comme toniques substitutives).\n" +
            "Sous-dominante (SD) : éloignement, préparation de la dominante (IV, II).\n" +
            "Dominante (D) : tension qui appelle la tonique (V, VII).\n" +
            "« Substitutive » : l'accord joue le rôle de la tonique ou de la dominante sans en être le degré.\n\n" +
            "VI et VII changent de rôle selon l'accord qui suit (voir Réglages, « Fonction de VI et VII selon " +
            "l'accord suivant »). Tant que cet accord n'est pas joué, la fonction est provisoire : elle " +
            "vient de la suite la plus probable, et l'historique la corrige dès qu'on joue l'accord suivant.\n\n" +
            "Diatonique : toutes les notes appartiennent à la tonalité. Emprunt modal : accord pris au mode " +
            "homonyme (C mineur en C majeur), comme IVm ou ♭VII7. Chromatique : hors tonalité, peut annoncer " +
            "une modulation."

    const val KEEP_ROOT_POSITION =
        "Quand la basse est détachée et qu'elle est la fondamentale d'un accord complet formé avec les notes " +
            "du dessus, garder cette lecture : un A grave sous C E G donne Am7. Désactivé : la basse n'est " +
            "jamais comptée et l'on obtient C/A."

    const val KEEP_SEVENTH_IN_BASS =
        "Quand la basse détachée est la septième d'un accord complet formé avec les notes du dessus, garder " +
            "l'accord de septième au 3e renversement : F grave sous G B D donne G7/F (V4/2 en classique), D " +
            "grave sous E G B♭ donne Em7♭5/D. Désactivé : la basse n'est pas comptée (G/F, Edim/D), comme on " +
            "l'écrit en variété pour une basse qui descend (C – C/B – C/B♭).\n\nStyle Classique seulement : " +
            "le banc d'essai y gagne des accords exacts (chorals, manuels), la pop en perd."

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

    const val PREDICTIONS =
        "Les accords qui peuvent suivre, en éventail : au premier niveau les suites les plus probables, " +
            "avec leur pourcentage et un mini clavier qui montre comment les jouer ; puis, pour chacune, les " +
            "suites suivantes. L'intensité de chaque case suit la probabilité du chemin ; le chiffrage est " +
            "coloré selon la fonction.\n\n" +
            "En classique et en pop/rock, les probabilités viennent des statistiques de corpus analysés par " +
            "des experts (environ 79 000 accords : Bach, Mozart, manuels d'harmonie, Schubert, RS 200, Beatles…), " +
            "lissées par le modèle théorique. En jazz et en blues (pas encore de corpus) et si l'option est " +
            "désactivée, c'est le modèle théorique seul : une table " +
            "d'enchaînements par style et par mode (pop/rock, jazz, blues, classique), des règles de contexte " +
            "(un II-V appelle son I, une dominante secondaire sa cible, une substitution tritonique résout un " +
            "demi-ton plus bas) et la mémoire du morceau : si les derniers accords ont déjà été joués dans le " +
            "même ordre, ce qui les suivait devient plus probable.\n\n" +
            "Mini clavier : la position choisie bouge le moins possible depuis l'accord que vous jouez " +
            "(conduite des voix minimale). Couleurs comme sur le grand clavier."

    const val PREDICTION_CONTEXT =
        "Nombre d'accords précédents pris en compte pour prédire la suite (1 à 4). Avec 1, seul l'accord " +
            "courant compte ; avec plus, les enchaînements typiques (II-V-I) et les boucles du morceau déjà " +
            "jouées sont mieux reconnus."

    const val PREDICTION_SHAPE =
        "Nombre de propositions à chaque niveau de l'arbre : niveau 1 = accords qui peuvent suivre tout de " +
            "suite (avec mini clavier), niveau 2 = suites de chacun, niveau 3 = suite de ces suites. 0 au " +
            "niveau 2 n'affiche que le premier niveau. Par défaut 4-2-1."

    const val MINI_KEYBOARDS =
        "Affiche sous chaque proposition du premier niveau un mini clavier montrant une position à jouer, " +
            "choisie pour bouger le moins possible depuis l'accord courant. Masqué par défaut pour gagner de " +
            "la place et de la lisibilité."

    const val SCALE =
        "Gamme de l'accord : la gamme qui convient pour improviser ou écrire une mélodie sur cet accord. " +
            "Elle dépend du type de l'accord, de sa fonction dans la tonalité (un IIm7 appelle le dorien, un " +
            "IIIm7 le phrygien) et, pour une dominante, de l'accord vers lequel elle résout (mixolydien vers " +
            "un accord majeur, mixolydien ♭9 ♭13 vers un accord mineur, altérée si l'accord contient ♯9 ou ♭13).\n\n" +
            "Couleurs des notes : bleu foncé = fondamentale, bleu clair = notes de l'accord, orange = tensions " +
            "disponibles, rouge = notes à éviter.\n\n" +
            "Note à éviter : note de la gamme située un demi-ton au-dessus d'une note de l'accord (le F sur CM7). " +
            "Tenue longtemps, elle frotte et sonne comme une fausse note ; en note de passage, elle est très " +
            "bien. Exceptions : la ♭9 et la ♭13 d'une dominante sont des tensions.\n\n" +
            "« Aussi » : d'autres gammes possibles (pentatoniques en pop / rock, variantes plus tendues en jazz)."

    const val SCALE_KEYBOARD =
        "Repère sur le grand clavier chaque note de la gamme de l'accord courant (un point, plus gros sur " +
            "sa tonique) et chaque note à éviter (un cercle rouge). Utile pour voir où placer les doigts quand " +
            "on improvise."

    const val MELODY_ROLES =
        "Notes de mélodie : celles des autres canaux en mode « Accords + mélodie », ou les notes jouées " +
            "seules en mode « Complète ». Chacune est classée par rapport à l'accord courant : bleu clair = " +
            "note de l'accord (avec son degré), orange = tension de la gamme (9, 11, 13…), rouge = note à " +
            "éviter, gris = note chromatique, hors de la gamme (note de passage, broderie, ou couleur voulue)."

    const val INTERPRETATION =
        "Cette lecture vient d'une règle d'interprétation (Réglages → Interprétation des accords). Ces règles " +
            "ont été mises au point en comparant l'application à des analyses d'experts (banc d'essai) ; chacune " +
            "peut être désactivée."

    const val SIXTH_AS_SEVENTH =
        "Style Classique seulement. Do mi sol la peut se lire C6 (sixte ajoutée, notation de variété et de jazz) " +
            "ou Am7/C (la mineur septième, 1er renversement). Les analyses classiques notent toujours la seconde " +
            "lecture (vi6/5, ii6/5). De même, Cm6 devient Am7♭5/C.\n\n" +
            "Banc d'essai : +3 points sur les chorals de Bach."

    const val SUSPENSIONS =
        "Style Classique seulement. Une quarte à la place de la tierce (Gsus4 = sol do ré) est le plus souvent " +
            "un retard : le do va descendre sur si. L'accord est alors nommé avec sa tierce (G), la quarte étant " +
            "montrée comme note étrangère. La tierce (majeure ou mineure) est prise dans la tonalité. Même chose " +
            "pour 7sus4 (lu 7) et sus2.\n\nBanc d'essai : +3 à +4 points selon les corpus."

    const val DIMINISHED_BY_KEY =
        "Les quatre notes d'une septième diminuée sont à égale distance (tierces mineures) : n'importe laquelle " +
            "peut être la fondamentale, et D°7, F°7, A♭°7 et B°7 sont le même accord. On choisit la note qui monte " +
            "d'un demi-ton vers un degré de la tonalité : la sensible (B°7 en do majeur = vii°7), sinon la sensible " +
            "d'un autre degré (F♯°7 = vii°7/V)."

    const val KEEP_ON_SUBSET =
        "Quand il ne reste qu'une ou deux notes de l'accord en cours (basse d'Alberti, notes répétées, une voix " +
            "qui continue seule), l'accord affiché ne change pas. Sans cette option, ces notes isolées effacent " +
            "l'accord.\n\nBanc d'essai : +1 point environ sur la musique pour piano."

    const val COMPLETE_DYADS =
        "Deux notes à la tierce (ou à la sixte, son renversement) suffisent pour nommer l'accord : la–do♯ = A, " +
            "mi–sol = Em (la quinte est sous-entendue). En style Classique, une quinte seule est aussi complétée " +
            "avec la tierce de la tonalité ; dans les autres styles, elle reste une quinte à vide (power chord).\n\n" +
            "Banc d'essai : +5 points sur les sonates de Mozart."

    const val HARMONIC_WINDOW =
        "Quand une ou deux notes seulement sont tenues, l'accord est lu sur ces notes et sur celles jouées juste " +
            "avant, dans ce délai (figurations, basse d'Alberti). 0 = désactivée.\n\nBanc d'essai : utile seule, " +
            "elle n'apporte plus rien une fois les autres règles activées ; désactivée par défaut."

    const val CADENCE_KEY_CHANGE =
        "Une cadence parfaite (V7 → I) vers une tonalité plausible (parmi les trois plus probables) la fait " +
            "retenir tout de suite.\n\nBanc d'essai : mieux sur les chorals et les manuels, moins bien sur Mozart ; " +
            "désactivée par défaut."

    const val KEY_INERTIA =
        "La tonalité retenue ne change que si une autre la dépasse nettement. Plus d'inertie : tonalité plus " +
            "stable mais plus lente à suivre une modulation. 0 = toujours la plus probable.\n\nBanc d'essai : " +
            "1 donne les meilleurs résultats (3 auparavant)."

    const val KEY_PER_STYLE =
        "La tonalité ne se pense pas de la même façon selon le style : un choral de Bach module souvent, " +
            "une chanson pop revient sans cesse à la même tonique. Le modèle de tonalité a donc ses propres " +
            "réglages pour chaque style. Choisissez le style à régler avec les boutons (le point marque le style " +
            "joué, choisi en haut de l'écran) ; c'est le seul groupe de réglages propre à chaque style. La " +
            "mémoire en secondes, elle, est commune à tous les styles. " +
            "« Valeurs du banc d'essai » remet les valeurs mesurées comme les meilleures (Classique : chorals, " +
            "Mozart, manuels, préludes, lieder ; Pop/rock : RS 200 et Isophonics ; Jazz et Blues : pas encore " +
            "de corpus, réglages de la pop)."

    const val KEY_MEMORY_CHORDS =
        "Mémoire comptée en changements d'accord : le poids des notes passées est divisé par deux tous les N " +
            "accords. Elle suit le rythme harmonique (un accord par temps dans un choral, un par mesure en pop), " +
            "ce qu'une mémoire en secondes ne fait pas. 0 = mémoire en secondes.\n\nBanc d'essai : 4 accords en " +
            "classique, 12 en pop."

    const val KEY_TONIC_WEIGHT =
        "En plus des notes de la gamme, la tonalité tient compte du temps passé sur l'accord de tonique (et, " +
            "en partie, sur sa dominante). En rock, F et E♭ tiennent tous les deux dans la gamme de B♭ majeur, " +
            "mais la tonique, l'accord sur lequel on revient, est F. 0 = seulement les notes.\n\nBanc d'essai : " +
            "+10 points environ en pop, +1 à +2 en classique."

    const val DEFAULT_SETTINGS =
        "Remet tous les réglages de cette fenêtre à leur valeur par défaut (celles mesurées sur le banc " +
            "d'essai), pour tous les styles. Le style, le mode d'analyse et le canal des accords, choisis dans " +
            "la barre du haut, sont conservés ; la disposition de l'écran a son propre bouton. Une confirmation " +
            "est demandée."

    const val KEY_ARRIVAL =
        "Indices de tonique tirés des enchaînements. Arrivées : un accord atteint par sa dominante (V → I), " +
            "par le ♭VII (B♭ → Cm, la cadence typique du rock) ou par sa sous-dominante (IV → I) est sans doute " +
            "la tonique. Premier accord : un morceau commence le plus souvent sur sa tonique ; l'indice est " +
            "gardé jusqu'à une rupture ou un nouveau morceau (« Effacer »). 0 = désactivé.\n\nBanc d'essai : " +
            "+5 points en pop ; défavorable en classique, où les modulations sont fréquentes."

    const val KEY_HOLD =
        "Tant que tout ce qu'on joue reste dans la gamme de la tonalité retenue, elle garde la main : il faut " +
            "des notes nouvelles (ou une rupture) pour en changer. C'est ce qu'entend un musicien : Cm – A♭ – E♭ " +
            "– B♭ reste en C mineur d'un bout à l'autre, au lieu d'hésiter à chaque accord avec E♭ majeur. " +
            "0 = désactivé.\n\nBanc d'essai : divise par deux à trois les changements de tonalité inutiles."

    const val PREDICTION_THRESHOLD =
        "Une branche de l'arbre n'est prolongée au niveau suivant que si sa probabilité atteint ce seuil " +
            "(probabilité du chemin depuis l'accord joué). Avec une proposition à 90 % et deux à 5 %, seules " +
            "les suites de la première sont calculées : l'arbre montre ce qui compte et se lit mieux. " +
            "0 = toutes les branches sont prolongées."

    const val PREDICTION_SOURCE =
        "Activé : les probabilités des suites viennent des enchaînements réellement écrits dans les corpus " +
            "analysés par des experts (classique : chorals de Bach, sonates de Mozart, manuels, préludes, " +
            "Winterreise ; pop/rock : RS 200 et Isophonics), le modèle théorique servant de base pour les " +
            "enchaînements rares. Désactivé : modèle théorique seul. Jazz et blues : modèle théorique (pas " +
            "encore de corpus).\n\nBanc d'essai, accord suivant parmi les 4 premières propositions : chorals " +
            "de Bach 68 % → 84 %, Mozart 70 % → 82 %, Winterreise 68 % → 80 % ; en pop, la mémoire du morceau " +
            "(boucles) faisait déjà l'essentiel (RS 200 96 %)."

    const val REANALYSIS =
        "Quand la tonalité change, les derniers accords de l'historique qui appartiennent aussi à la nouvelle " +
            "tonalité y sont rechiffrés : la modulation avait commencé là, à l'accord pivot (noté « vi = ii »). " +
            "Un expert, qui connaît la suite, la place à cet endroit ; en direct, on ne l'entend qu'après. " +
            "Nombre maximal d'accords rechiffrés ; 0 = désactivée."

    const val PASSING_TONES =
        "Séparation harmonie / mélodie (utile en mode Complète, quand la mélodie et l'accompagnement sont sur " +
            "le même canal) : si une seule voix bouge d'un demi-ton ou d'un ton vers une note étrangère à " +
            "l'accord, les autres restant dans l'accord, c'est une note de passage ou une broderie : l'accord " +
            "affiché continue.\n\nBanc d'essai : +1,9 point d'accords justes sur les préludes de Bach, mais " +
            "−0,9 point sur l'ensemble (les experts y voient souvent un nouvel accord). Désactivé par défaut."

    const val VOICING =
        "Disposition des notes de l'accord :\n" +
            "• position serrée : les voix sont collées, aucune note de l'accord ne peut s'intercaler entre deux " +
            "voix voisines (l'octave au plus). À quatre voix, comme en harmonie classique, on regarde les trois " +
            "voix du dessus : C3 + G3 C4 E♭4 est en position serrée ;\n" +
            "• position large : une note de l'accord pourrait s'intercaler entre deux voix (C3 G3 E4 C5) ;\n" +
            "• drop 2 / drop 3 : position serrée dont la 2e (ou 3e) voix en partant du haut est descendue d'une " +
            "octave, voicing courant du jazz à 4 sons ;\n" +
            "• shell : fondamentale, tierce et septième seulement (jazz, main gauche) ;\n" +
            "• en quartes : notes empilées par quartes (jazz modal) ;\n" +
            "• sur basse séparée : une basse à une octave ou plus sous les autres voix (main gauche).\n" +
            "Suivent les notes doublées et la note du soprano (la plus aiguë). En classique, la sensible " +
            "doublée dans l'accord de dominante est signalée : elle doit monter à la tonique, doublée elle " +
            "produit des octaves consécutives."

    const val VOICE_LEADING =
        "Comment chaque voix passe de l'accord précédent à celui-ci (la basse avec la basse, le soprano avec " +
            "le soprano, les voix intérieures au plus proche) : notes communes gardées, mouvements par degré " +
            "conjoint (1 ou 2 demi-tons) et sauts (mouvement disjoint). Mouvement des voix extrêmes (basse et " +
            "soprano) : contraire (elles s'écartent ou se rapprochent), oblique (l'une ne bouge pas), direct " +
            "(même sens), parallèle (même sens, même intervalle).\n\n" +
            "Notes guides (« guide tones » en jazz) : la tierce et la septième, qui définissent l'accord ; dans " +
            "un enchaînement de quintes (II – V – I), la septième descend d'un degré sur la tierce de l'accord " +
            "suivant (7e F → 3ce E dans G7 → C). Cette ligne est le fil conducteur des improvisateurs.\n\n" +
            "Couleurs : bleu = notes guides ; orange = faute d'écriture classique (quintes ou octaves " +
            "consécutives, sensible doublée)."

    const val PARALLELS =
        "Quintes (ou octaves) consécutives, dites aussi parallèles : deux voix forment une quinte (ou une " +
            "octave) puis une autre quinte (ou octave) en bougeant dans le même sens. Les deux voix perdent " +
            "leur indépendance : c'est la faute d'écriture la plus connue de l'harmonie classique. En pop, en " +
            "rock (power chords) ou en jazz, c'est courant et voulu. Signalé en orange."

    const val PARALLELS_ALL_STYLES =
        "Désactivé : les quintes et octaves consécutives ne sont signalées qu'en style Classique. Activé : " +
            "dans tous les styles."

    const val DEGREE_NAME =
        "Nom du degré sur lequel est bâti l'accord :\n" +
            "I tonique, II sus-tonique, III médiante, IV sous-dominante, V dominante, VI sus-dominante, " +
            "VII sensible (un demi-ton sous la tonique : B en C) ou sous-tonique (un ton sous la tonique : " +
            "B♭ en C mineur).\n\n" +
            "À ne pas confondre avec la fonction (badge coloré) : la sus-dominante (degré VI) peut avoir une " +
            "fonction de tonique ou de sous-dominante selon ce qui suit."

    const val DEGREE_NOTATION =
        "Comment numéroter les degrés en mineur.\n" +
            "Désactivé (classique, comme les analyses d'experts du banc d'essai) : les degrés sont comptés dans " +
            "la gamme de la tonalité. En C mineur : Cm = Im, E♭ = III, A♭ = VI, B♭ = VII.\n" +
            "Activé (jazz, variété) : les degrés sont comptés dans la gamme majeure de la tonique, et l'on " +
            "note ce qui est abaissé. En C mineur : E♭ = ♭III, A♭ = ♭VI, B♭ = ♭VII.\n" +
            "En majeur, les deux notations sont identiques (un emprunt reste ♭VI, ♭VII)."

    const val CONTEXT_FUNCTIONS =
        "Certains accords changent de rôle selon ce qui vient après. Exemples en C mineur :\n" +
            "• A♭ (VI) suivi de E♭ ou de Cm : il prolonge la tonique → tonique substitutive (T).\n" +
            "• A♭ (VI) suivi de G, de Fm ou de B♭ : il prépare la dominante → sous-dominante (SD).\n" +
            "• A♭ juste après G : la dominante annonçait Cm, A♭ prend sa place (cadence rompue) → T.\n" +
            "• B♭ (VII) suivi de Cm : il ramène à la tonique comme le ferait G → dominante substitutive (D) " +
            "(cadence par la sous-tonique ; « éolienne » quand A♭ précède : VI – VII – I ; en majeur, B♭ → C : " +
            "cadence mixolydienne, typique du rock).\n" +
            "• B♭ (VII) suivi de E♭ : c'est la dominante du relatif majeur → D ; B♭7 → E♭ est chiffré V7/III.\n" +
            "• C majeur en C mineur : suivi de F ou de Fm, c'est la dominante de IV (V/IV) ; sinon, c'est la " +
            "tierce picarde (I), souvent sur le dernier accord.\n\n" +
            "En jouant, on ne connaît pas encore l'accord suivant : la fonction affichée est provisoire, " +
            "tirée de la suite la plus probable (cadre « Suites probables »), signalée par « ? » dans " +
            "l'historique, puis corrigée dès que l'accord suivant est joué.\n\n" +
            "Désactivé : chaque degré a toujours la même fonction (convention jazz : VI et VII sous-dominante " +
            "en mineur)."

    const val TENSION =
        "Courbe de tension : un point par accord, de « repos » (en bas) à « max » (en haut, ligne " +
            "pointillée). La tension combine trois choses :\n" +
            "• la fonction : la tonique est le repos, la sous-dominante s'éloigne, la dominante appelle la " +
            "tonique ;\n" +
            "• la dissonance : septième, septième diminuée, tensions ajoutées (9, 11, 13), altérations (♭9, ♯9, " +
            "♭5, ♯5), quarte suspendue ;\n" +
            "• l'éloignement de la tonalité : dominante secondaire, substitution tritonique, emprunt, accord " +
            "chromatique, renversement.\n" +
            "On y lit la phrase musicale : la tension monte vers la cadence et retombe sur la tonique. C'est une " +
            "estimation : aucun corpus d'experts ne la mesure."

    const val RHYTHM =
        "Rythme harmonique : à quelle fréquence l'harmonie change. Sous chaque accord, une barre donne sa durée " +
            "(la plus longue occupe toute la case) ; « en cours » pour l'accord joué. La ligne de synthèse donne " +
            "la durée moyenne d'un accord et signale quand les accords qui précèdent une cadence sont nettement " +
            "plus courts (l'harmonie accélère vers la fin de la phrase, un procédé classique).\n\n" +
            "Harmonie ne connaît ni le tempo ni la mesure : les durées sont en secondes.\n\nBanc d'essai : " +
            "nombre d'accords différents affichés, comparé à celui de l'expert (pop : autant ; Bach : une fois et " +
            "demie plus ; piano en figurations : deux à trois fois plus, à cause des notes de passage)."

    const val CIRCLE_PATH =
        "Parcours des derniers accords sur le cercle des quintes : accords majeurs et de dominante sur l'anneau " +
            "extérieur, mineurs et diminués sur l'anneau intérieur. Les flèches les plus foncées sont les plus " +
            "récentes ; l'accord actuel est entouré. Un enchaînement de quintes (II – V – I) tourne pas à pas " +
            "dans le sens inverse des aiguilles d'une montre ; un emprunt ou une modulation fait un saut. Le " +
            "nombre d'accords se règle dans Réglages, onglet « Suites et affichage » (0 = masqué)."

    const val GRID =
        "Grille du morceau joué depuis le dernier « Effacer » : quatre cases par ligne, une section par " +
            "tonalité (avec l'accord pivot d'une modulation). Une boucle jouée plusieurs fois de suite est " +
            "écrite une fois entre barres de reprise (‖: … :‖ ×3).\n\n« Copier le texte » met la grille dans " +
            "le presse-papiers (à coller dans un mail ou un document). « Imprimer / PDF » ouvre l'impression ; " +
            "sous Windows, choisir l'imprimante « Microsoft Print to PDF » pour obtenir un fichier PDF."

    const val GRID_NUMERALS = "Afficher le chiffrage (degré et fonction) sous chaque accord."

    const val GRID_REPEATS =
        "Une suite de 2 à 8 accords jouée plusieurs fois de suite est écrite une seule fois, entre barres de " +
            "reprise, avec le nombre de fois."

    const val GRID_DURATION =
        "Harmonie ne connaît pas la mesure : avec cette option, l'unité est la durée la plus courante d'un " +
            "accord. Un accord deux fois plus long occupe deux cases, la seconde notée « % » (comme dans une " +
            "grille de musicien : même accord que la case précédente)."
}
