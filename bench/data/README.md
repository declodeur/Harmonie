# Données du banc d'essai

Morceaux annotés par des experts, convertis pour le banc d'essai d'Harmonie par
`bench/tools/convert_when_in_rome.py`.

## Chorals de Bach (`bach-chorales/`)

- **Analyses (A)** : corpus [When in Rome](https://github.com/MarkGotham/When-in-Rome)
  (Gotham, Micchi, Nápoles López, Sailor, 2023), `analysis.txt`, analyses de Dmitri Tymoczko,
  Andrew Jones, Hamish Robb et al. (version du 27 mai 2026, commit `1c61fe4`).
- **Analyses (B)**, pour 100 chorals : *Bach Chorales Melody-Harmony Corpus* (BCMH), traduction
  automatique en RomanText par Néstor Nápoles López, `analysis_BCMH.txt` dans When in Rome.
- **Partitions** : [Chorale-Corpus](https://github.com/MarkGotham/Chorale-Corpus) (archive, commit `50fd22c`),
  `short_score.mxl`, celles auxquelles renvoie When in Rome.
- **Licence** : CC BY-SA 4.0 (licence de When in Rome et de Chorale-Corpus). Ces fichiers convertis
  sont distribués sous la même licence.

Le champ qualité de la ligne `P` mesure l'alignement entre les accords de l'expert et les notes de
la partition (part des notes qui sonnent au début d'un accord et qui lui appartiennent). Les chorals
sous 0,8 (mesures de la partition qui ne correspondent pas à celles de l'analyse) sont écartés du
banc d'essai.

Format : voir l'en-tête du convertisseur.
