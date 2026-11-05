# Harmonie

Analyse harmonique en direct de ce qui est joué sur un instrument MIDI : accords, tonalité, gammes, fonctions harmoniques et propositions d'enchaînements.

Prototype Windows d'abord ; la version Android réutilisera le moteur d'analyse.

## Installer (Windows 10/11)

1. Ouvrir l'onglet **Releases** du dépôt et télécharger le dernier `Harmonie-windows-build-N.zip`.
2. Dézipper où vous voulez (aucune installation, aucun droit administrateur, Java est inclus).
3. Lancer `Harmonie\Harmonie.exe`.

Brancher l'instrument **avant** de lancer l'application : Windows ne signale pas toujours un appareil branché ensuite.

## Version 0.2 (étape 1)

- Choix de l'entrée MIDI (reconnexion automatique à la dernière utilisée).
- Reconnaissance des accords du dictionnaire Yamaha (34 types × 12 fondamentales), notation Yamaha, renversements, lectures alternatives, quinte omise, note étrangère.
- Renversement serré noté comme l'accord à l'état fondamental (Cm plutôt que Cm/G) ; la basse n'est notée que si elle est détachée de l'accord (écart réglable, une quinte par défaut).
- Délai de stabilité réglable (60 ms par défaut) ; un accord n'est validé qu'après une nouvelle note.
- Pédale de sustain (incluse ou non).
- Deux modes : analyse complète, ou accords + mélodie séparés par canal MIDI.
- Clavier 88 touches coloré selon le rôle des notes, cliquable pour tester sans instrument.
- Historique des accords, bulles d'aide.

## Organisation

- `engine/` : moteur d'analyse en Kotlin pur (aucune dépendance à Windows ou Android), avec ses tests.
- `desktop/` : application Windows (Compose Multiplatform, API MIDI de Java).
- Compilation automatique par GitHub Actions à chaque push sur `main` : tests, puis zip portable publié dans Releases (tag `build-N`).

## Feuille de route

1. ~~Squelette, entrée MIDI, clavier, accord courant~~
2. Tonalité probable et chiffrage romain
3. Historique annoté (flèches, crochets II-V, accords tenus / arpégés)
4. Arbre des accords suivants (modèle théorique par style, puis statistique)
5. Analyses avancées : gammes d'accord, conduite des voix, voicings, séparation harmonie / mélodie

Backlog : outil de création de corpus (à partir de fichiers MIDI) pour les statistiques d'enchaînements.
