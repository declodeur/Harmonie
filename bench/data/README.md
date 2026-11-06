# Données du banc d'essai

Morceaux annotés par des experts, convertis pour le banc d'essai d'Harmonie par
`bench/tools/convert_when_in_rome.py all <When-in-Rome> <Chorale-Corpus> bench/data`.

Source principale : le méta-corpus [When in Rome](https://github.com/MarkGotham/When-in-Rome)
(Gotham, Micchi, Nápoles López, Sailor, 2023), version du 27 mai 2026 (commit `1c61fe4`).
Analystes : **A** = analyse de référence, **B** = second expert, **M** = AugmentedNet (analyse
automatique fournie avec When in Rome, logiciel entraîné sur ces corpus).

| Dossier | Contenu | A | B | Licence |
|---|---|---|---|---|
| `bach-chorales/` | 371 chorals de Bach | Tymoczko, Jones, Robb et al. | BCMH (100 chorals) | CC BY-SA 4.0 |
| `mozart-sonatas/` | 18 sonates pour piano (54 mouvements) | [DCML](https://github.com/DCMLab/mozart_piano_sonatas) | Dmitri Tymoczko | CC BY-NC-SA 4.0 (DCML) |
| `textbooks/` | 200 exemples de manuels (Reger, Rimsky-Korsakov, Tchaïkovski, Kostka-Payne, Aldwell-Schachter) | [key_modulation_dataset](https://github.com/DDMAL/key_modulation_dataset) (Nápoles López et al.) | – | voir la source |
| `bach-wtc1/` | 24 préludes du Clavier bien tempéré I | Mark Gotham et al. | – | CC BY-SA 4.0 |
| `schubert-winterreise/` | 24 lieder (partitions OpenScore, CC0) | Mark Gotham et al. | – | CC BY-SA 4.0 |

Partitions des chorals : [Chorale-Corpus](https://github.com/MarkGotham/Chorale-Corpus) (archive,
commit `50fd22c`), `short_score.mxl`, celles auxquelles renvoie When in Rome. Les autres partitions
viennent de When in Rome (`score.mxl`).

Ces fichiers convertis sont distribués sous la licence de leur source (usage non commercial pour
les sonates de Mozart).

Ligne `P` : titre, identifiant, qualité de l'alignement, tempo (noires par minute), indication de
tempo d'origine. La qualité mesure la part de la durée des notes qui appartient à l'accord de
l'expert pendant chaque accord ; les morceaux sous le seuil de leur corpus (mesures de la partition
qui ne correspondent pas à celles de l'analyse) sont écartés du banc d'essai. Le tempo vient de
l'indication de tempo (Allegro, Andante…) quand elle existe.

Format complet : voir l'en-tête du convertisseur.
