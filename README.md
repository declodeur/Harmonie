# Harmonie

Analyse harmonique en direct de ce qui est joué sur un instrument MIDI : accords, tonalité, gammes, fonctions harmoniques et propositions d'enchaînements.

Prototype Windows d'abord ; la version Android réutilisera le moteur d'analyse.

## Installer (Windows 10/11)

1. Ouvrir l'onglet **Releases** du dépôt et télécharger le dernier `Harmonie-windows-build-N.zip`.
2. Dézipper où vous voulez (aucune installation, aucun droit administrateur, Java est inclus).
3. Lancer `Harmonie\Harmonie.exe`.

Brancher l'instrument **avant** de lancer l'application : Windows ne signale pas toujours un appareil branché ensuite.

## Version 0.7 (étapes 1 à 4)

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
- Frontières des cadres déplaçables à la souris (disposition mémorisée) ; réglages compacts en deux colonnes.
- Historique annoté : flèche pleine (dominante qui résout une quinte plus bas), flèche pointillée (substitution tritonique), crochet sous les II-V, bordure pointillée des accords arpégés.
- Arbre des suites probables à droite de l'historique (forme réglable, 4-2-1 par défaut) : modèle théorique par style et mode, règles de contexte (II-V, dominantes, substitutions), mémoire des boucles du morceau sur 1 à 4 accords, mini clavier avec conduite des voix minimale.
- Accords arpégés reconnus en secours quand aucun accord n'est tenu (plus longue suite de notes formant un accord, fenêtre réglable en notes et en secondes).

## Organisation

- `engine/` : moteur d'analyse en Kotlin pur (aucune dépendance à Windows ou Android), avec ses tests.
- `desktop/` : application Windows (Compose Multiplatform, API MIDI de Java).
- Compilation automatique par GitHub Actions à chaque push sur `main` : tests, puis zip portable publié dans Releases (tag `build-N`).

## Feuille de route

1. ~~Squelette, entrée MIDI, clavier, accord courant~~
2. ~~Tonalité probable et chiffrage romain~~
3. ~~Historique annoté (flèches, crochets II-V, accords tenus / arpégés)~~
4. ~~Arbre des accords suivants (modèle théorique par style)~~ ; statistiques réelles avec le futur corpus
5. Analyses avancées : gammes d'accord, conduite des voix, voicings, séparation harmonie / mélodie

Backlog : outil de création de corpus (à partir de fichiers MIDI) pour les statistiques d'enchaînements ; choix dièses / bémols pour les tonalités et accords enharmoniques (F♯ / G♭…).
