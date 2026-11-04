# Référentiel CASE — programmes du collège

[`programmes-college.json`](../../../src/main/resources/referentiels/programmes-college.json) est un `CFPackage` CASE 1.1 qui
couvre **les programmes officiels de la sixième à la troisième**. Il complète celui de l'école
primaire ([`programmes-1d`](../programmes-1d/README.md)) : même chaîne d'extraction, mêmes types
d'énoncés, mêmes règles de dates. Un collège y trouve tous ses niveaux dans un seul programme.

| | |
|---|---|
| Énoncés | 1 581, dont 1 287 énoncés terminaux (940 en vigueur au 04/11/2026) |
| Arborescence | Cycle › Enseignement › Domaine › Sous-domaine › énoncé |
| Niveaux | `educationLevel` : `6E` `5E` `4E` `3E` (les codes de niveau d'exercizer) |
| Codes | `humanCodingScheme` : `C4.MAT.1.2.3`, `C3.HGE.1.4` |
| Identifiants | UUID v5 dans un espace de noms propre au collège, stables d'une génération à l'autre |

La sixième appartient au cycle 3 : ses énoncés viennent des textes du CM1 et du CM2 (les lignes
« Sixième » de leurs tableaux), rangés ici sous un « Cycle 3 » distinct de celui du primaire.

## Textes repris

D'après le calendrier de mise en œuvre d'Éduscol (07.2026).

| Texte | BO | Niveaux et rentrée |
|---|---|---|
| Français, mathématiques (cycle 3) | n° 16 du 17 avril 2025 | 6e 2025 |
| Histoire-géographie, EPS (cycle 3) | n° 22 du 28 mai 2026 | 6e 2027 |
| Enseignement moral et civique | n° 24 du 13 juin 2024 | 5e 2024, 4e 2025, 6e et 3e 2026 |
| Éducation à la vie affective et relationnelle, et à la sexualité | n° 6 du 6 février 2025 | 6e-3e 2025 |
| Français, mathématiques (cycle 4) | n° 10 du 5 mars 2026 | 5e 2026, 4e 2027, 3e 2028 |
| Technologie (cycle 4) | n° 9 du 29 février 2024 | 5e 2024, 4e 2025, 3e 2026 |
| Programme de 2020, cycle 3 : histoire-géographie, sciences et technologie, EPS | n° 31 du 30 juillet 2020, n° 25 du 22 juin 2023 | 6e jusqu'au 31/08/2027 |
| Programme de 2020, cycle 3 : arts plastiques, éducation musicale, histoire des arts | n° 31 du 30 juillet 2020 | 6e, sans successeur |
| Programme de 2020, cycle 4 : français, mathématiques, langues vivantes | n° 31 du 30 juillet 2020 | 4e et 3e, jusqu'à l'arrivée des nouveaux textes |
| Programme de 2020, cycle 4 : arts, EPS, histoire-géographie, physique-chimie, SVT, EMI | n° 31 du 30 juillet 2020 | 5e-3e, sans successeur |

Pas encore repris :

- les **langues vivantes** de sixième (2025) et de cinquième (2026) : leurs nouveaux programmes
  (BO n° 22 du 29 mai 2025 et suivants) n'ont pas d'adresse de fichier connue ;
- les **sciences et technologie** de sixième de 2026 (applicables en 2027) : le PDF n'a pas de couche
  texte et la transcription du primaire n'en couvre que le CM1 et le CM2.

Comme pour le primaire, on reprend des textes de 2020 les attendus de fin de cycle et les
compétences travaillées, rangés sous « Programme de 2020 (jusqu'au …) » quand un successeur est
publié. L'histoire-géographie de 2020 du cycle 4 apporte en plus ses repères annuels (classe › thème
› chapitre), la technologie de 2024 ses repères de progressivité par classe, et le français de
cycle 4 les entrées de culture littéraire et artistique de chaque année (type *Thème*).

## Régénérer

```bash
./fetch.sh /tmp/college
python3 build.py /tmp/college/txt > ../../../src/main/resources/referentiels/programmes-college.json
```

`build.py` déclare les textes et emprunte le reste à `../programmes-1d/build.py` et `extract.py`.
`corrections.json` complète celui du primaire, qui s'applique aussi aux textes communs.

Le référentiel est livré avec le module comme celui du primaire (`BundledFrameworks`). Le document
se nomme `fe4e3e2a-7573-5952-b16c-f50ea54dd1b8`.
