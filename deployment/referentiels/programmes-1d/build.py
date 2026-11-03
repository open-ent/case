#!/usr/bin/env python3
"""
Référentiel CASE des programmes de l'école primaire, de la petite section au CM2.

Assemble, en un seul CFPackage, tous les textes officiels applicables à l'école primaire : ceux
qui s'appliquent pendant l'année en cours, et ceux qui sont déjà publiés pour une rentrée à venir.
Chaque énoncé porte ses niveaux (`educationLevel`) et ses dates d'application
(`statusStartDate` / `statusEndDate`). Un texte remplacé l'année suivante y figure donc avec sa
date de fin, et son successeur avec sa date de début.

    ./fetch.sh                                         # PDF officiels → textes (hors dépôt)
    python3 build.py <dossier des textes> > ../../../src/main/resources/referentiels/programmes-1d.json

Les identifiants sont des UUID v5, calculés à partir du chemin de l'énoncé : un nouvel import
conserve ceux des énoncés inchangés. C'est indispensable, parce qu'un badge délivré ou un exercice
classé désigne un énoncé par son identifiant.
"""
import json
import os
import sys
import uuid
from datetime import date

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract import IGNORED, extract, norm  # noqa: E402

# Date d'édition du référentiel, à avancer quand son contenu change. Elle n'est pas tirée de
# l'horloge : le module ne recharge un référentiel livré que si son fichier a changé.
EDITION = "2026-11-03"
NS = uuid.UUID("6f4a1e0c-5b0e-4c8e-9a35-0d3c1e7a1d10")   # espace de noms du référentiel, à ne jamais changer
DOCUMENT_KEY = "open-ent:programmes-ecole-primaire"
HERE = os.path.dirname(os.path.abspath(__file__))

CYCLES = {
    "C1": ("Cycle 1 — Cycle des apprentissages premiers (école maternelle)", ["PS", "MS", "GS"]),
    "C2": ("Cycle 2 — Cycle des apprentissages fondamentaux (CP, CE1, CE2)", ["CP", "CE1", "CE2"]),
    "C3": ("Cycle 3 — Cycle de consolidation (CM1, CM2)", ["CM1", "CM2"]),
}

# Codes des enseignements (humanCodingScheme) et des domaines de la maternelle.
CODES = {
    "francais": "FRA", "mathematiques": "MAT", "enseignement moral et civique": "EMC",
    "education a la vie affective et relationnelle": "EVAR", "education physique et sportive": "EPS",
    "histoire-geographie": "HGE", "histoire et geographie": "HGE", "sciences et technologie": "SCT",
    "questionner le monde": "QLM", "langues vivantes etrangeres et regionales": "LVE",
    "langues vivantes (etrangeres ou regionales)": "LVE", "arts plastiques": "APL",
    "education musicale": "EMU", "histoire des arts": "HDA",
    "le developpement et la structuration du langage oral et ecrit": "LAN",
    "l'acquisition des premiers outils mathematiques": "OUM",
    "agir, s'exprimer, comprendre a travers les activites physiques": "APH",
    "agir, s'exprimer, comprendre a travers les activites artistiques": "AAR",
    "se reperer dans le temps et l'espace": "RTE",
    "decouvrir le monde du vivant, de la matiere et des objets": "DMV",
}

# Rentrées : un texte s'applique à un niveau du 1er septembre de sa rentrée au 31 août qui précède
# la rentrée de son successeur (calendrier de mise en œuvre des nouveaux programmes, Éduscol, 07.2026).
R = {y: f"{y}-09-01" for y in range(2015, 2030)}
FIN = {y: f"{y}-08-31" for y in range(2015, 2030)}

BO_2020 = "BO n° 31 du 30 juillet 2020"
SOURCES = [
    # ── Cycle 1 ──────────────────────────────────────────────────────────────────────────────────
    dict(id="c1-2026", cycle="C1", enseignement=None, bo="BO n° 19 du 7 mai 2026",
         url="https://www.education.gouv.fr/sites/default/files/document/annexe-programme-d-enseignement-de-l-ecole-maternelle-cycle-1-516107.pdf",
         levels={"PS": (R[2026], None), "MS": (R[2026], None), "GS": (R[2026], None)}),
    dict(id="c1-langage-2024", cycle="C1", bo="BO n° 41 du 31 octobre 2024",
         enseignement="Le développement et la structuration du langage oral et écrit",
         url="https://www.education.gouv.fr/sites/default/files/document/Annexe%201%20%E2%80%93%20Programme%20d%E2%80%98enseignement%20pour%20le%20d%C3%A9veloppement%20et%20la%20structuration%20du%20langage%20oral%20et%20%C3%A9crit%20du%20cycle%201-403812.pdf",
         levels={"PS": (R[2025], None), "MS": (R[2025], None), "GS": (R[2025], None)}),
    dict(id="c1-maths-2024", cycle="C1", bo="BO n° 41 du 31 octobre 2024",
         enseignement="L’acquisition des premiers outils mathématiques",
         url="https://www.education.gouv.fr/sites/default/files/document/Annexe%202%20%E2%80%93%20Programme%20d%E2%80%99enseignement%20pour%20l%E2%80%99acquisition%20des%20premiers%20outils%20math%C3%A9matiques%20du%20cycle%201-403815.pdf",
         levels={"PS": (R[2025], None), "MS": (R[2025], None), "GS": (R[2025], None)}),
    dict(id="c1-evar", cycle="C1", bo="BO n° 6 du 6 février 2025", outline="evar",
         enseignement="Éducation à la vie affective et relationnelle", drop_root=True,
         url="https://www.education.gouv.fr/sites/default/files/document/Programme%20d%E2%80%99%C3%A9ducation%20%C3%A0%20la%20vie%20affective%20et%20relationnelle%20%C3%A0%20l%E2%80%99%C3%A9cole%20maternelle-405258.pdf",
         levels={"PS": (R[2025], None), "MS": (R[2025], None), "GS": (R[2025], None)}),
    # ── Cycle 2 ──────────────────────────────────────────────────────────────────────────────────
    dict(id="c2-francais-2024", cycle="C2", enseignement="Français", bo="BO n° 41 du 31 octobre 2024",
         url="https://www.education.gouv.fr/sites/default/files/document/Annexe%203%20%E2%80%93%20Programme%20de%20fran%C3%A7ais%20du%20cycle%202-403818.pdf",
         levels={"CP": (R[2025], None), "CE1": (R[2025], None), "CE2": (R[2025], None)}),
    dict(id="c2-maths-2024", cycle="C2", enseignement="Mathématiques", bo="BO n° 41 du 31 octobre 2024",
         url="https://www.education.gouv.fr/sites/default/files/document/Annexe%204%20%E2%80%93%20Programme%20de%20math%C3%A9matiques%20du%20cycle%202-403821.pdf",
         levels={"CP": (R[2025], None), "CE1": (R[2025], None), "CE2": (R[2025], None)}),
    dict(id="c2-eps-2026", cycle="C2", enseignement="Éducation physique et sportive", bo="BO n° 22 du 28 mai 2026",
         url="https://www.education.gouv.fr/sites/default/files/document/annexe-1-programme-d-education-physique-et-sportive-cycle-2-516770.pdf",
         levels={"CP": (R[2026], None), "CE1": (R[2027], None), "CE2": (R[2027], None)}),
    dict(id="c2-hg-2026", cycle="C2", enseignement="Histoire-géographie", bo="BO n° 22 du 28 mai 2026",
         url="https://www.education.gouv.fr/sites/default/files/document/annexe-3-programme-d-histoire-geographie-cycle-2-516776.pdf",
         levels={"CP": (R[2026], None), "CE1": (R[2027], None), "CE2": (R[2027], None)}),
    dict(id="c2-sciences-2026", cycle="C2", enseignement="Sciences et technologie", bo="BO n° 24 du 11 juin 2026",
         transcription="c2-sciences-2026.json",
         url="https://www.education.gouv.fr/sites/default/files/document/annexe-1-programme-de-sciences-et-technologie-du-cycle-2-517289.pdf",
         levels={"CP": (R[2026], None), "CE1": (R[2027], None), "CE2": (R[2027], None)}),
    dict(id="c2-lver-2026", cycle="C2", enseignement="Langues vivantes étrangères et régionales", bo=None,
         url="https://www.education.gouv.fr/sites/default/files/document/Annexe%201%20%E2%80%93%20Programme%20de%20langues%20vivantes%20%C3%A9trang%C3%A8res%20et%20r%C3%A9gionales%20pour%20le%20cycle%202%20-481187.pdf",
         levels={"CP": (R[2026], None), "CE1": (R[2027], None), "CE2": (R[2027], None)}),
    dict(id="emc-2024", cycle=None, enseignement="Enseignement moral et civique", bo="BO n° 24 du 13 juin 2024",
         url="https://www.education.gouv.fr/sites/default/files/document/Annexe%20%E2%80%94%20Programme%20d%E2%80%99enseignement%20moral%20et%20civique%20du%20cours%20pr%C3%A9paratoire%20%C3%A0%20la%20classe%20terminale%20des%20voies%20g%C3%A9n%C3%A9rale%2C%20technologique%20et%20professionnelle%20et%20des%20classes%20pr%C3%A9parant%20au%20CAP-402159.pdf",
         levels={"CP": (R[2024], None), "CE1": (R[2025], None), "CE2": (R[2026], None),
                 "CM1": (R[2024], None), "CM2": (R[2025], None)}),
    dict(id="elem-evar", cycle=None, bo="BO n° 6 du 6 février 2025", outline="evar",
         enseignement="Éducation à la vie affective et relationnelle", drop_root=True,
         url="https://www.education.gouv.fr/sites/default/files/document/Programme%20d%E2%80%99%C3%A9ducation%20%C3%A0%20la%20vie%20affective%20et%20relationnelle%20%C3%A0%20l%E2%80%99%C3%A9cole%20%C3%A9l%C3%A9mentaire-405261.pdf",
         levels={lv: (R[2025], None) for lv in ["CP", "CE1", "CE2", "CM1", "CM2"]}),
    # Texte de 2020 : ce qui n'a pas encore de successeur (arts) ou n'en a qu'à la rentrée 2027.
    dict(id="c2-2020", cycle="C2", enseignement=None, bo=BO_2020,
         url="https://www.education.gouv.fr/bo/20/Hebdo31/MENE2018714A.htm",
         keep={"questionner le monde": ["CE1", "CE2"],
               "education physique et sportive": ["CE1", "CE2"],
               "langues vivantes (etrangeres ou regionales)": ["CE1", "CE2"],
               "arts plastiques": ["CP", "CE1", "CE2"],
               "education musicale": ["CP", "CE1", "CE2"]},
         levels={"CP": (R[2020], None), "CE1": (R[2020], FIN[2027]), "CE2": (R[2020], FIN[2027])},
         open_ended={"arts plastiques", "education musicale"}),
    # ── Cycle 3 ──────────────────────────────────────────────────────────────────────────────────
    dict(id="c3-francais-2025", cycle="C3", enseignement="Français", bo="BO n° 16 du 17 avril 2025",
         url="https://www.education.gouv.fr/sites/default/files/programme-de-fran-ais-pour-le-cycle-3-439824.pdf",
         levels={"CM1": (R[2025], None), "CM2": (R[2026], None)}),
    dict(id="c3-maths-2025", cycle="C3", enseignement="Mathématiques", bo="BO n° 16 du 17 avril 2025",
         url="https://www.education.gouv.fr/sites/default/files/programme-de-math-matiques-pour-le-cycle-3-439827.pdf",
         levels={"CM1": (R[2025], None), "CM2": (R[2026], None)}),
    dict(id="c3-eps-2026", cycle="C3", enseignement="Éducation physique et sportive", bo="BO n° 22 du 28 mai 2026",
         url="https://www.education.gouv.fr/sites/default/files/document/annexe-2-programme-d-education-physique-et-sportive-cycle-3-516773.pdf",
         levels={"CM1": (R[2026], None), "CM2": (R[2027], None)}),
    dict(id="c3-hg-2026", cycle="C3", enseignement="Histoire-géographie", bo="BO n° 22 du 28 mai 2026",
         url="https://www.education.gouv.fr/sites/default/files/document/annexe-4-programme-d-histoire-geographie-cycle-3-516779.pdf",
         levels={"CM1": (R[2026], None), "CM2": (R[2027], None)}),
    dict(id="c3-sciences-2026", cycle="C3", enseignement="Sciences et technologie", bo="BO n° 24 du 11 juin 2026",
         transcription="c3-sciences-2026.json",
         url="https://www.education.gouv.fr/sites/default/files/document/annexe-2-programme-de-sciences-et-technologie-du-cycle-3-517292.pdf",
         levels={"CM1": (R[2026], None), "CM2": (R[2027], None)}),
    dict(id="c3-lver-2026", cycle="C3", enseignement="Langues vivantes étrangères et régionales", bo=None,
         url="https://www.education.gouv.fr/sites/default/files/document/Annexe%202%20%E2%80%93%20Programme%20de%20langues%20vivantes%20%C3%A9trang%C3%A8res%20et%20r%C3%A9gionales%20pour%20les%20classes%20de%20cours%20moyen%20%28cycle%203%29-481190.pdf",
         levels={"CM1": (R[2026], None), "CM2": (R[2027], None)}),
    dict(id="c3-2020-2023-consolide", cycle="C3", enseignement=None, bo=BO_2020 + " et BO n° 25 du 22 juin 2023",
         url="https://eduscol.education.fr/document/50990/download",
         keep={"histoire et geographie": ["CM2"], "sciences et technologie": ["CM2"],
               "education physique et sportive": ["CM2"],
               "langues vivantes (etrangeres ou regionales)": ["CM2"],
               "arts plastiques": ["CM1", "CM2"], "education musicale": ["CM1", "CM2"],
               "histoire des arts": ["CM1", "CM2"]},
         levels={"CM1": (R[2016], None), "CM2": (R[2016], FIN[2027])},
         open_ended={"arts plastiques", "education musicale", "histoire des arts"}),
]

LEAF_TYPES = [
    ("contenus d", "Contenu d’enseignement"),
    ("notions et competences", "Notion et compétence"),
    ("attendus de fin de cycle", "Attendu de fin de cycle"),
    ("competences travaillees", "Compétence travaillée"),
    ("theme", "Thème"),
]
ITEM_TYPES = ["Cycle", "Enseignement", "Domaine", "Sous-domaine", "Objectif d’apprentissage",
              "Contenu d’enseignement", "Notion et compétence", "Attendu de fin de cycle",
              "Compétence travaillée", "Thème"]


# Les textes de 2020 nomment autrement deux enseignements renouvelés.
ALIASES = {"langues vivantes (etrangeres ou regionales)": "Langues vivantes étrangères et régionales",
           "histoire et geographie": "Histoire-géographie"}


def uid(*parts):
    return str(uuid.uuid5(NS, "\x1f".join(parts)))


def leaf_type(table):
    t = norm(table or "")
    return next((label for prefix, label in LEAF_TYPES if t.startswith(prefix)), "Objectif d’apprentissage")


def tidy(statement):
    """Typographie de l'énoncé publié : initiale en capitale, sans le « ; » d'énumération."""
    s = statement.replace("\uf0b7", "–").strip().rstrip(" ;,")
    return s[:1].upper() + s[1:] if s else s


CORRECTIONS = {k: v for k, v in json.load(open(os.path.join(HERE, "corrections.json"), encoding="utf-8")).items()
               if not k.startswith("_")}


def corrected(source_id, row):
    """Les énoncés rétablis à la main (corrections.json), ou l'énoncé extrait s'il n'y en a pas."""
    statement = norm(row["statement"])
    for prefix, replacement in CORRECTIONS.get(source_id, {}).items():
        if statement.startswith(norm(prefix)):
            return [dict(row, statement=s) for s in replacement]
    return [row]


def rows_of(source, txt_dir):
    """Lignes « objectif » d'un texte, avec un chemin débarrassé des rubriques sans contenu."""
    if source.get("transcription"):
        data = json.load(open(os.path.join(HERE, "transcriptions", source["transcription"]), encoding="utf-8"))
        for domain, level, sub, statements in data["sections"]:
            path = [domain] + ([p.strip() for p in sub.split("›")] if sub else [])
            for s in statements:
                yield {"path": path, "levels": [level], "statement": s, "period": None,
                       "table": "Objectifs d’apprentissage", "columns": {}}
        return
    for row in extract(os.path.join(txt_dir, source["id"] + ".txt"), source.get("outline")):
        if row["kind"] != "objective":
            continue
        row["path"] = [p for p in row["path"] if not IGNORED.match(norm(p))]
        if source.get("drop_root"):
            row["path"] = row["path"][1:]
        yield from corrected(source["id"], row)


def build(txt_dir):
    today = EDITION + "T00:00:00"
    document_id = uid(DOCUMENT_KEY)
    doc_uri = {"title": "Programmes de l’école primaire", "identifier": document_id,
               "uri": f"urn:uuid:{document_id}"}
    items, associations, seen = [], [], {}
    children = {}                                   # parent → nombre d'enfants (pour les codes)
    type_ids = {t: uid("type", t) for t in ITEM_TYPES}

    def link(origin, destination, destination_title, seq):
        associations.append({
            "identifier": uid("isChildOf", origin, destination),
            "associationType": "isChildOf",
            "originNodeURI": {"identifier": origin, "uri": f"urn:uuid:{origin}"},
            "destinationNodeURI": {"identifier": destination, "uri": f"urn:uuid:{destination}",
                                   "title": destination_title},
            "sequenceNumber": seq,
            "lastChangeDateTime": today,
        })

    def node(key, statement, kind, parent, parent_title, code_prefix, levels, start=None, end=None, notes=None,
             abbreviated=None):
        """Crée l'énoncé s'il n'existe pas ; élargit ses niveaux et ses dates sinon."""
        if key in seen:
            item = seen[key]
            item["educationLevel"] = sorted(set(item["educationLevel"]) | set(levels), key=ORDER.index)
            if start and (not item.get("statusStartDate") or start < item["statusStartDate"]):
                item["statusStartDate"] = start
            if "statusEndDate" in item and (end is None or end > item["statusEndDate"]):
                item.pop("statusEndDate") if end is None else item.update(statusEndDate=end)
            return item
        children[parent] = children.get(parent, 0) + 1
        identifier = uid(*map(str, key))
        item = {
            "identifier": identifier,
            "uri": f"urn:uuid:{identifier}",
            "fullStatement": statement,
            "humanCodingScheme": f"{code_prefix}.{children[parent]}" if code_prefix else None,
            "CFItemType": kind,
            "CFItemTypeURI": {"identifier": type_ids[kind], "title": kind, "uri": f"urn:uuid:{type_ids[kind]}"},
            "educationLevel": sorted(set(levels), key=ORDER.index),
            "language": "fr",
            "lastChangeDateTime": today,
        }
        if abbreviated:
            item["abbreviatedStatement"] = abbreviated
        if start:
            item["statusStartDate"] = start
        if end:
            item["statusEndDate"] = end
        if notes:
            item["notes"] = notes
        if not item["humanCodingScheme"]:
            item.pop("humanCodingScheme")
        items.append(item)
        seen[key] = item
        link(identifier, parent, parent_title, children[parent])
        return item

    for cycle_code, (title, levels) in CYCLES.items():
        node(("cycle", cycle_code), title, "Cycle", document_id, doc_uri["title"], None, levels,
             abbreviated=f"Cycle {cycle_code[1]}")
        seen[("cycle", cycle_code)]["humanCodingScheme"] = cycle_code

    for source in SOURCES:
        for row in rows_of(source, txt_dir):
            declared = row.get("levels") or []
            if "keep" in source and row.get("table") != "Thème":
                declared = []                       # 2020 : attendus et compétences valent pour le cycle
            levels = [lv for lv in declared if lv in source["levels"]]
            if declared and not levels:            # 6e, collège : hors du référentiel
                continue
            path = list(row["path"])
            enseignement = source["enseignement"]
            if enseignement is None:                 # maternelle 2026, textes de 2020 : 1er titre
                if not path:
                    continue
                enseignement, path = path[0], path[1:]
            key_e = norm(enseignement)
            if key_e in ALIASES:
                original, enseignement = key_e, ALIASES[key_e]
            else:
                original = key_e
            if "keep" in source:
                if original not in source["keep"]:
                    continue
                levels = levels or source["keep"][original]
                levels = [lv for lv in levels if lv in source["keep"][original]]
            key_e = norm(enseignement)
            if not levels:
                continue
            windows = [source["levels"][lv] for lv in levels]
            start = min(w[0] for w in windows)
            ends = [w[1] for w in windows]
            end = None if None in ends or original in source.get("open_ended", ()) else max(ends)
            # Un énoncé par cycle : l'EMC et l'EVAR élémentaire couvrent deux cycles.
            by_cycle = {}
            for lv in levels:
                c = source["cycle"] or next(c for c, (_, lvs) in CYCLES.items() if lv in lvs)
                by_cycle.setdefault(c, []).append(lv)
            for cycle_code, lvs in by_cycle.items():
                cycle = seen[("cycle", cycle_code)]
                code = f"{cycle_code}.{CODES.get(key_e, key_e[:3].upper())}"
                parent = node(("ens", cycle_code, key_e), enseignement, "Enseignement", cycle["identifier"],
                              cycle["fullStatement"], None, lvs, start, end)
                parent.setdefault("humanCodingScheme", code)
                trail = ("ens", cycle_code, key_e)
                if end:
                    # Texte remplacé à la rentrée suivante : ses rubriques sont rangées à part,
                    # pour ne pas se mêler à celles du programme qui lui succède.
                    legacy = f"Programme de 2020 (jusqu’au {date.fromisoformat(end).strftime('%d/%m/%Y')})"
                    trail = trail + ("legacy", source["id"])
                    parent = node(trail, legacy, "Domaine", parent["identifier"], parent["fullStatement"],
                                  parent["humanCodingScheme"], lvs, start, end)
                for depth, title in enumerate(path):
                    trail = trail + (norm(title),)
                    kind = "Domaine" if depth == 0 else "Sous-domaine"
                    parent = node(trail, title, kind, parent["identifier"], parent["fullStatement"],
                                  parent["humanCodingScheme"], lvs, start, end)
                notes = [f"Source : {source['bo'] + ' — ' if source.get('bo') else ''}{source['url']}"]
                if row.get("period"):
                    notes.insert(0, f"Échéance : {row['period']}.")
                for name, values in (row.get("columns") or {}).items():
                    if values and name:
                        notes.insert(-1, f"{name} :\n" + "\n".join(f"- {v}" for v in values))
                statement = tidy(row["statement"])
                node(trail + (source["id"], ",".join(lvs), norm(statement)), statement, leaf_type(row.get("table")),
                     parent["identifier"], parent["fullStatement"], parent["humanCodingScheme"], lvs,
                     start, end, "\n\n".join(notes))

    document = {
        "identifier": document_id,
        "uri": doc_uri["uri"],
        "title": "Programmes de l’école primaire — cycles 1, 2 et 3",
        "creator": "Ministère de l’Éducation nationale",
        "publisher": "Open ENT",
        "description": ("Programmes d’enseignement de la petite section au CM2, d’après les textes officiels publiés "
                        "au Bulletin officiel. Chaque énoncé indique ses niveaux et ses dates d’application : les "
                        "textes en vigueur et ceux déjà publiés pour une rentrée à venir y figurent ensemble."),
        "officialSourceURL": "https://eduscol.education.gouv.fr/sites/default/files/document/calendriernouveauxprogrammes-129025.pdf",
        "language": "fr",
        "version": "2026-2027",
        "adoptionStatus": "Adopted",
        "statusStartDate": R[2026],
        "lastChangeDateTime": today,
        "subject": ["École primaire"],
        "notes": ("Énoncés repris mot pour mot des textes officiels ; seule la typographie des énumérations est "
                  "normalisée (initiale en capitale, « ; » final retiré). Le niveau scolaire est dans educationLevel, "
                  "l’échéance (« En fin de période 1 ») et les exemples de réussite dans notes."),
        "licenseURI": {"identifier": uid("license", "etalab-2.0"), "title": "Licence Ouverte 2.0",
                       "uri": f"urn:uuid:{uid('license', 'etalab-2.0')}"},
    }
    return {
        "CFDocument": document,
        "CFDefinitions": {
            "CFItemTypes": [{"identifier": type_ids[t], "title": t, "description": t, "hierarchyCode": str(i),
                             "lastChangeDateTime": today} for i, t in enumerate(ITEM_TYPES)],
            "CFLicenses": [{"identifier": uid("license", "etalab-2.0"), "title": "Licence Ouverte 2.0",
                            "licenseText": "https://www.etalab.gouv.fr/licence-ouverte-open-licence/",
                            "lastChangeDateTime": today}],
        },
        "CFItems": items,
        "CFAssociations": associations,
    }


ORDER = ["PS", "MS", "GS", "CP", "CE1", "CE2", "CM1", "CM2"]

if __name__ == "__main__":
    package = build(sys.argv[1])
    json.dump(package, sys.stdout, ensure_ascii=False, indent=1)
    from collections import Counter
    print(f"{len(package['CFItems'])} énoncés, {len(package['CFAssociations'])} liens ; par type : "
          f"{dict(Counter(i['CFItemType'] for i in package['CFItems']))}", file=sys.stderr)
