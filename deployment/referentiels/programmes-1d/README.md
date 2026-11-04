# Référentiel CASE — programmes de l'école primaire

[`programmes-1d.json`](../../../src/main/resources/referentiels/programmes-1d.json) est un `CFPackage` CASE 1.1 qui couvre **tous les programmes officiels de la
petite section au CM2**. C'est la grille de rangement commune des contenus d'Open ENT (exercices,
wikis, cartes mentales, frises, casiers) et le référentiel auquel s'alignent les badges Open Badges.

| | |
|---|---|
| Énoncés | 2 743, dont 2 292 énoncés terminaux (1 839 en vigueur au 04/11/2026) |
| Arborescence | Cycle › Enseignement › Domaine › Sous-domaine › énoncé |
| Niveaux | `educationLevel` : `PS` `MS` `GS` `CP` `CE1` `CE2` `CM1` `CM2` |
| Validité | `statusStartDate` / `statusEndDate`, par niveau et par texte |
| Codes | `humanCodingScheme` : `C2.MAT.1.3.2` (cycle, enseignement, rang dans chaque rubrique) |
| Identifiants | UUID v5, stables d'une génération à l'autre |

## Ce que contient un énoncé

Le texte officiel, mot pour mot. Seule la typographie des énumérations est normalisée : initiale en
capitale, « ; » final retiré. Le champ `notes` porte :

- l'échéance quand le texte en donne une (« Échéance : En fin de période 1. ») ;
- les colonnes voisines du tableau officiel : exemples de réussite, attendus, repères, notions ;
- la source : numéro du BO et adresse du PDF.

Le type (`CFItemType`) dit ce que le texte officiel en fait : *Objectif d'apprentissage*, *Attendu
de fin de cycle*, *Compétence travaillée*, *Contenu d'enseignement* (EMC), *Notion et compétence*
(EVAR) ou *Thème*.

## Textes repris

Le calendrier de mise en œuvre d'Éduscol (07.2026) fixe, niveau par niveau, la rentrée où chaque
texte s'applique. Le référentiel contient les textes **en vigueur** et ceux **déjà publiés pour une
rentrée à venir**, chacun avec ses dates. Un énoncé qui ne s'applique qu'à partir de 2027 a donc
`statusStartDate: 2027-09-01`.

| Texte | BO | Niveaux et rentrée |
|---|---|---|
| École maternelle (hors langage et mathématiques) | n° 19 du 7 mai 2026 | PS-GS 2026 |
| Langage oral et écrit, premiers outils mathématiques (cycle 1) | n° 41 du 31 octobre 2024 | PS-GS 2025 |
| Français, mathématiques (cycle 2) | n° 41 du 31 octobre 2024 | CP-CE2 2025 |
| Français, mathématiques (cycle 3) | n° 16 du 17 avril 2025 | CM1 2025, CM2 2026 |
| EPS, histoire-géographie (cycles 2 et 3) | n° 22 du 28 mai 2026 | CP et CM1 2026, CE1, CE2, CM2 2027 |
| Sciences et technologie (cycles 2 et 3) | n° 24 du 11 juin 2026 | CP et CM1 2026, CE1, CE2, CM2 2027 |
| Langues vivantes étrangères et régionales (cycles 2 et 3) | — | CP et CM1 2026, CE1, CE2, CM2 2027 |
| Enseignement moral et civique | n° 24 du 13 juin 2024 | CP, CM1 2024 ; CE1, CM2 2025 ; CE2 2026 |
| Éducation à la vie affective et relationnelle | n° 6 du 6 février 2025 | PS-CM2 2025 |
| Programme de 2020, cycle 2 : questionner le monde, EPS, langues vivantes | n° 31 du 30 juillet 2020 | CE1-CE2 jusqu'au 31/08/2027 |
| Programme de 2020, cycle 2 : arts plastiques, éducation musicale | n° 31 du 30 juillet 2020 | CP-CE2, sans successeur |
| Programme de 2020 et 2023, cycle 3 : histoire-géographie, sciences, EPS, langues vivantes | n° 31 du 30 juillet 2020, n° 25 du 22 juin 2023 | CM2 jusqu'au 31/08/2027 |
| Programme de 2020, cycle 3 : arts plastiques, éducation musicale, histoire des arts | n° 31 du 30 juillet 2020 | CM1-CM2, sans successeur |

Les contenus d'un texte remplacé à la rentrée suivante sont rangés sous un domaine
« Programme de 2020 (jusqu'au 31/08/2027) », pour ne pas se mêler à ceux du texte qui lui succède.
Pour ces textes transitoires, on reprend les attendus de fin de cycle et les compétences
travaillées, pas le détail des connaissances associées. La sixième et le collège forment un référentiel
à part, [`programmes-college`](../programmes-college/README.md), tiré de la même chaîne.

## Régénérer

```bash
./fetch.sh /tmp/programmes                     # PDF officiels → texte (pdftotext -layout)
python3 build.py /tmp/programmes/txt > ../../../src/main/resources/referentiels/programmes-1d.json
```

- `extract.py` lit chaque texte : l'arborescence vient du **sommaire** du programme, retrouvé dans
  le corps du texte ; les tableaux à plusieurs colonnes sont découpés sur la gouttière blanche
  verticale. `python3 extract.py <texte.txt>` affiche ce qu'il en tire.
- `transcriptions/` : les deux programmes de sciences et technologie de 2026 sont des PDF **sans
  couche texte**. Leurs objectifs d'apprentissage ont été transcrits à la lecture des pages, mot pour
  mot ; les propositions d'activités ne l'ont pas été.
- `corrections.json` : les énoncés que la mise en page a brouillés (colonnes qui se chevauchent),
  rétablis à la main depuis le PDF. `python3 build.py … | …` puis une relecture des énoncés courts, des
  majuscules au milieu d'une phrase et des chiffres isolés (« 1, 5 » : domaines du socle) repère ceux
  qui restent.
- Deux générations sur les mêmes textes produisent les **mêmes identifiants**. Changer `NS` ou le
  calcul des clés dans `build.py` rendrait orphelins les badges délivrés et les contenus classés.

## Chargement

Le référentiel est **livré avec le module** : au démarrage, `BundledFrameworks` le lit dans le jar,
le convertit comme un import et l'enregistre au nom de l'usager technique `open-ent-catalogue`
(`case.bundled-owner`), le même que le catalogue d'exercices. Aucun compte nominatif n'en est
propriétaire, et aucune manipulation n'est nécessaire en production.

- Il n'est réécrit que si le fichier a changé (empreinte SHA-256 gardée dans `source_uri`), en une
  transaction ; les identifiants inchangés restent valides.
- Il est publié au premier chargement. S'il a été retiré depuis (`PUT /case/admin/documents/:id/publication`),
  une mise à jour du module ne le republie pas.
- `case.bundled` (liste de ressources) remplace la liste par défaut.

Le document se nomme `2b931521-4371-534c-8201-b5c0ab29f0d8` ; il est servi publiquement sous
`/ims/case/v1p0/CFPackages/2b931521-4371-534c-8201-b5c0ab29f0d8`.
