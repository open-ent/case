# Open ENT — module `case` (CASE 1.1)

Module Vert.x qui fait d'Open ENT un **serveur de référentiels de compétences** au format
[1EdTech CASE 1.1](https://www.1edtech.org/standards/case), et un **consommateur** des paquets
publiés par des tiers.

| Spécification | Support |
|---|---|
| CASE Service 1.1, binding REST/JSON (lecture) | ✅ `CFDocuments`, `CFPackages`, `CFItems`, `CFAssociations`, `CFItemAssociations`, définitions |
| Import d'un `CFPackage` complet | ✅ avec rapport de conversion |
| Export d'un `CFPackage` complet | ✅ `GET /CFPackages/{id}` |
| Pagination `limit`/`offset`, `X-Total-Count`, `Link` ; `filter`, `sort`/`orderBy`, `fields` | ✅ |
| Erreurs `imsx_StatusInfo` | ✅ |
| Écriture par API (CASE n'en normalise pas) | ❌ l'administration de l'ENT fait foi |

Contexte et feuille de route : [`docs/international/interoperabilite-1edtech.md`](../../docs/international/interoperabilite-1edtech.md).

## Ce qu'un référentiel CASE est, et n'est pas

**Un référentiel CASE n'a pas d'arbre.** Il a des énoncés (`CFItem`) et des liens
(`CFAssociation`). La hiérarchie est portée par les liens `isChildOf`, et par eux seuls — un
énoncé peut donc avoir deux parents, ce qu'aucune arborescence ne sait représenter. C'est la
différence de fond avec le **LSUN** que code [`modules/competences`](../competences), où le
domaine contient ses composantes.

**Les identifiants sont ce qui a de la valeur.** Un énoncé est désigné par son UUID partout où il
est cité, y compris depuis un autre référentiel et depuis un autre serveur (`exactMatchOf`). À
l'import, ils sont donc **conservés tels quels** : les renuméroter couperait toutes les
correspondances, c'est-à-dire exactement ce que CASE sert à établir.

## Architecture

```
fr.openent.casestandards
├── Case.java                BaseServer : schéma SQL, contrôleurs
├── CaseContext.java         configuration et dépendances partagées
├── core/                    vocabulaire des liens, champs de la norme, URI, erreurs
├── pkg/CasePackage.java     conversion lignes ↔ objets CASE, dans les deux sens (pure, testée)
├── api/                     Query (pagination, tri, projection), Filter (grammaire), Http
├── service/                 accès PostgreSQL (schéma casestd)
└── controller/
    ├── CaseController       le service normatif, public, sous /ims/case/v1p0
    └── AdminController      import, publication, suppression — super administrateur
```

Aucune dépendance au-delà de `org.entcore:common`.

## Points d'entrée

### Service normatif — `/ims/case/v1p0`, **public**

| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/CFDocuments` | Les référentiels publiés |
| GET | `/CFDocuments/:id` | Un référentiel |
| GET | `/CFPackages/:id` | Le paquet complet : référentiel, définitions, énoncés, liens |
| GET | `/CFItems/:id` | Un énoncé |
| GET | `/CFItemAssociations/:id` | Les liens d'un énoncé, entrants **et** sortants |
| GET | `/CFAssociations/:id` | Un lien |
| GET | `/CFItemTypes/:id`, `/CFLicenses/:id`, `/CFSubjects/:id`, `/CFConcepts/:id`, `/CFAssociationGroupings/:id` | Les définitions |

### Administration — `/case/admin`, super administrateur

| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/admin/configuration` | Adresses à communiquer à un consommateur |
| GET | `/admin/documents` | Tous les référentiels, publiés ou non |
| POST | `/admin/documents/import` | Dépose un `CFPackage` |
| PUT | `/admin/documents/:id/publication` | Publie ou retire un référentiel |
| DELETE | `/admin/documents/:id` | Supprime un référentiel et tout ce qui en dépend |
| GET | `/admin/imports` | Journal des imports |

## Sécurité

**Les routes normatives sont publiques, et c'est délibéré.** Elles ne portent pas
d'`@SecuredAction` — sans cette annotation, entcore ne les soumet à aucun filtre — parce qu'un
consommateur CASE est par définition un tiers qui n'a pas de compte ici, et qu'un référentiel de
programmes est une donnée publique. C'est ce que font les serveurs CASE publics.

La contrepartie est stricte : **seuls les référentiels publiés sont servis**. La condition est
portée par les requêtes SQL elles-mêmes, jointure comprise pour les énoncés, les liens et les
définitions — lire un énoncé par son URI directe ne contourne pas l'état de publication de son
référentiel. Un import arrive **non publié** : on le relit, puis on le publie.

## Déploiement

Le module est déclaré dans `starter/ent-core.yaml` (port **8172**, schéma **`casestd`**) et routé
en local par Traefik sur `/case` et `/ims/case`. La version vit dans `starter/.env*`
(`CASE_VERSION`).

Le schéma SQL s'appelle `casestd` et non `case` : **`CASE` est un mot réservé de SQL**, et un
schéma qui porte ce nom doit être cité entre guillemets dans chaque requête. Le paquet Java est
`fr.openent.casestandards` pour la même raison, `case` étant un mot réservé de Java.

**Pour la production**, une fois `fr.openent:case` publié sur Nexus, deux entrées à ajouter
*ensemble* dans `helm/openent-monolithe/values.yaml` — poser la version sans la route fait tourner
le module sans jamais l'exposer, et rien ne le signale :

```yaml
env:
  # Module case — CASE 1.1 (1EdTech), référentiels de compétences.
  CASE_VERSION: "0.1.0"

service:
  modules:
    - name: case
      port: 8172
      paths: ["/case", "/ims/case"]
```

## Tests

```bash
mvn test
```

36 tests sur les couches pures : conversion d'un paquet dans les deux sens, conservation des
identifiants, URI d'une destination hébergée ailleurs, rejet d'un énoncé sans `fullStatement` ou
d'un lien hors vocabulaire, grammaire du `filter`, pagination, tri et projection.
