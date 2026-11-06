# Harmonie

Analyse harmonique en direct de ce qui est joué sur un instrument MIDI : accords, tonalité, gammes, fonctions harmoniques et propositions d'enchaînements.

Prototype Windows d'abord ; la version Android réutilisera le moteur d'analyse.

## Installer (Windows 10/11)

1. Ouvrir l'onglet **Releases** du dépôt et télécharger le dernier `Harmonie-windows-build-N.zip`.
2. Dézipper où vous voulez (aucune installation, aucun droit administrateur, Java est inclus).
3. Lancer `Harmonie\Harmonie.exe`.

Brancher l'instrument **avant** de lancer l'application : Windows ne signale pas toujours un appareil branché ensuite.

## Version 0.11.1 (étapes 1 à 5a, banc d'essai)

- Choix de l'entrée MIDI (reconnexion automatique à la dernière utilisée).
- Reconnaissance des accords du dictionnaire Yamaha (34 types × 12 fondamentales), notation Yamaha, renversements, lectures alternatives, quinte omise, note étrangère.
- Renversement serré noté comme l'accord à l'état fondamental (Cm plutôt que Cm/G) ; la basse n'est notée que si elle est détachée de l'accord (écart réglable, une quinte par défaut). Une basse détachée n'est pas comptée dans la lecture : F♯ grave sous C E♭ G = Cm/F♯ ; mais A grave sous C E G = Am7.
- Délai de stabilité réglable (60 ms par défaut) ; un accord n'est validé qu'après une nouvelle note.
- Pédale de sustain (incluse ou non).
- Deux modes : analyse complète, ou accords + mélodie séparés par canal MIDI.
- Clavier 88 touches coloré selon le rôle des notes, cliquable pour tester sans instrument.
- Historique des accords avec leur chiffrage coloré par fonction (légende), regroupés par tonalité (séparateur et nom de la tonalité sous chaque groupe), bulles d'aide.
- Tonalité probable en continu, affichée sur un cercle des quintes des 24 tonalités (probabilité, tonalités compatibles, tonalité retenue). Score = compatibilité avec la gamme + profil (Temperley en classique, mineur naturel en pop/rock, blues, jazz) + cadences ; harmonie et mélodie comptées séparément, poids de la mélodie réglable ; mémoire réglable.
- Chiffrage romain (suffixes Yamaha), fonction harmonique (tonique, sous-dominante, dominante) et nature de l'accord : diatonique, dominante secondaire (V7/II), substitution tritonique (SubV7), emprunt modal (IVm, ♭VII7), diminué de passage, dominante altérée, napolitain, couleur blues (style Blues).
- Orthographe des notes selon la tonalité (D♯ en E majeur, D♭ en A♭ majeur).
- Options pour les deux exceptions de lecture de la basse détachée.
- Écran organisé selon le temps : en haut accord courant (présent) | suites probables (avenir) | tonalité (contexte) ; au milieu l'historique (passé) ; en bas le clavier pleine largeur. Frontières déplaçables à la souris (disposition mémorisée) ; réglages compacts en deux colonnes.
- Historique annoté : flèche pleine (dominante qui résout une quinte plus bas), flèche pointillée (substitution tritonique), crochet sous les II-V, bordure pointillée des accords arpégés.
- Arbre des suites probables à droite de l'historique (forme réglable, 4-2-1 par défaut) : modèle théorique par style et mode, règles de contexte (II-V, dominantes, substitutions), mémoire des boucles du morceau sur 1 à 4 accords, mini clavier optionnel (masqué par défaut) avec conduite des voix minimale. Seules les branches assez probables sont prolongées (seuil réglable, 10 % par défaut) ; branches espacées ; intensité des cases selon la probabilité (0 % = fond sombre).
- Gamme de l'accord (étape 5a) : gamme conseillée selon le type, la fonction et la résolution (dorien sur IIm7, phrygien sur IIIm7, mixolydien ♭9 ♭13 sur une dominante vers un accord mineur, lydien ♭7 sur une substitution tritonique, altérée sur 7(♯9)…), notes à éviter, tensions disponibles, variantes (pentatoniques en pop / rock) ; repères de la gamme sur le clavier (option) ; notes de mélodie classées (note de l'accord, tension, à éviter, chromatique).
- Règles d'interprétation issues du banc d'essai (chacune désactivable) : en style Classique, sixte ajoutée lue comme septième renversée (C6 = Am7/C) et retards (Gsus4 lu G, retard 4-3) ; septième diminuée nommée d'après la sensible ; une ou deux notes de l'accord en cours ne le remplacent pas ; tierce seule lue comme accord. Nouveau modèle de tonalité (v0.10) : mémoire comptée en accords (elle suit le rythme harmonique), poids du temps passé sur l'accord de tonique (en pop, la tonique est l'accord sur lequel on revient), réglages propres à chaque style (le classique module souvent, la pop revient à sa tonique). Tonalité stable (v0.11) : tant que tout reste dans sa gamme, la tonalité retenue ne change pas ; en pop, le premier accord et les arrivées (V → I, ♭VII → i, IV → I) désignent la tonique ; probabilités moins tranchées et « Hésite avec… » quand deux tonalités restent proches. Bouton « Réglages par défaut ». Fenêtre harmonique et changement de tonalité sur cadence disponibles en option.
- Accords arpégés reconnus en secours quand aucun accord n'est tenu (plus longue suite de notes formant un accord, fenêtre réglable en notes et en secondes).

## Banc d'essai

Le moteur est mesuré sur de la vraie musique analysée par des experts, issue du corpus
[When in Rome](https://github.com/MarkGotham/When-in-Rome) : chorals de Bach, sonates pour piano de Mozart, exemples
de manuels d'harmonie, préludes du Clavier bien tempéré, Winterreise de Schubert ; et pour la pop, le corpus RS 200
(de Clercq et Temperley) et Isophonics (Beatles, Queen…), via [ChoCo](https://github.com/smashub/choco), avec un
accompagnement généré à partir des accords (environ 1 070 morceaux, 79 000 accords).
Chaque morceau est rejoué dans le moteur comme en direct, puis l'accord, la tonalité et le chiffrage affichés sont
comparés à ceux de l'expert, ainsi que le nombre de changements de tonalité (stabilité), et, quand il existe, à un second expert et à un logiciel d'analyse (AugmentedNet).

Expériences : chaque hypothèse d'amélioration est un réglage du moteur ; le banc joue la version actuelle et la
variante, et montre les accords gagnés et perdus (`bench/src/.../Experiments.kt`, lancement à la demande avec
l'identifiant de l'expérience).

- Lancé automatiquement quand le moteur change, ou à la demande (onglet Actions, « Banc d'essai », *Run workflow*) ;
  il ne bloque jamais la compilation.
- Rapport et historique des scores : branche `bench-results`.
- En local : `./gradlew :bench:run --args="bench/data bench/build/report - all"` (`all` : toutes les expériences).
- Données : `bench/data` (CC BY-SA 4.0, voir son README), converties par `bench/tools/convert_when_in_rome.py`.

## Organisation

- `engine/` : moteur d'analyse en Kotlin pur (aucune dépendance à Windows ou Android), avec ses tests.
- `desktop/` : application Windows (Compose Multiplatform, API MIDI de Java).
- `bench/` : banc d'essai (données annotées, convertisseur, comparaison et rapport).
- Compilation automatique par GitHub Actions à chaque push sur `main` : tests, puis zip portable publié dans Releases (tag `build-N`).

## Feuille de route

1. ~~Squelette, entrée MIDI, clavier, accord courant~~
2. ~~Tonalité probable et chiffrage romain~~
3. ~~Historique annoté (flèches, crochets II-V, accords tenus / arpégés)~~
4. ~~Arbre des accords suivants (modèle théorique par style)~~ ; statistiques réelles avec le futur corpus
5. Analyses avancées : ~~5a gammes d'accord, notes à éviter, mélodie classée~~ ; 5b voicings, conduite des voix, séparation harmonie / mélodie ; 5c parcours sur le cercle, courbe de tension, rythme harmonique, grille exportable

Backlog : réglages différents selon le style choisi (aujourd'hui seule la tonalité a ses réglages par style ; étendre aux autres réglages : reconnaissance, interprétation, arpèges, suites probables…) ; outil de création de corpus (à partir de fichiers MIDI) pour les statistiques d'enchaînements ; choix dièses / bémols pour les tonalités et accords enharmoniques (F♯ / G♭…) ; mise au point de la forme par défaut de l'arbre des suites ; réanalyse a posteriori, lors d'un changement de tonalité, des accords précédents compatibles avec la nouvelle tonalité.
