#!/usr/bin/env python3
"""
Référentiel CASE des programmes du collège, de la sixième à la troisième.

Même chaîne que le référentiel de l'école primaire (../programmes-1d) : mêmes règles d'extraction,
mêmes types d'énoncés, mêmes dates d'application par niveau. Seuls changent les textes, les niveaux
(`6E` `5E` `4E` `3E`, les codes d'exercizer) et l'espace de noms des identifiants.

    ./fetch.sh <dossier>
    python3 build.py <dossier>/txt > ../../../src/main/resources/referentiels/programmes-college.json

La sixième appartient au cycle 3 : ses énoncés viennent des mêmes textes que ceux du CM1 et du CM2,
mais ce référentiel les range sous son propre « Cycle 3 », pour qu'un collège n'ait qu'un programme
à ouvrir.
"""
import importlib.util
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("programmes_1d", os.path.join(HERE, "..", "programmes-1d", "build.py"))
engine = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(engine)
R, FIN, BO_2020, ITEM_TYPES = engine.R, engine.FIN, engine.BO_2020, engine.ITEM_TYPES
PRIMAIRE = {s["id"]: s for s in engine.SOURCES}

# Date d'édition, à avancer quand le contenu change (le module ne recharge qu'un fichier modifié).
EDITION = "2026-11-04"
# Espace de noms propre au collège : le « Cycle 3 » de ce référentiel n'est pas celui du primaire.
NS = uuid.UUID("7d0791d2-b983-48ab-b326-0ce77b63a224")

CYCLES = {
    "C3": ("Cycle 3 — Cycle de consolidation (sixième)", ["6E"]),
    "C4": ("Cycle 4 — Cycle des approfondissements (cinquième, quatrième, troisième)", ["5E", "4E", "3E"]),
}
ORDER = ["6E", "5E", "4E", "3E"]
EDU = "https://www.education.gouv.fr/sites/default/files/document/"

# Programme de 2020 du cycle 4 : intertitres absents de son sommaire.
SUBHEADINGS_C4_2020 = {
    "Histoire": 7, "Géographie": 7,
    "Organisation et transformations de la matière": 8, "Mouvement et interactions": 8,
    "L’énergie, ses transferts et ses conversions": 8, "Des signaux pour observer et communiquer": 8,
    "La planète Terre, l’environnement et l’action humaine": 8, "Le vivant et son évolution": 8,
    "Le corps humain et la santé": 8,
    "Thème A – Nombres et calculs": 8, "Thème B – Organisation et gestion de données, fonctions": 8,
    "Thème C – Grandeurs et mesures": 8, "Thème D - Espace et géométrie": 8,
    "Thème E – Algorithmique et programmation": 8,
}
C4_ALL = ["5E", "4E", "3E"]

SOURCES = [
    # ── Sixième (cycle 3) ────────────────────────────────────────────────────────────────────────
    dict(PRIMAIRE["c3-francais-2025"], levels={"6E": (R[2025], None)}),
    dict(PRIMAIRE["c3-maths-2025"], levels={"6E": (R[2025], None)}),
    dict(PRIMAIRE["c3-hg-2026"], levels={"6E": (R[2027], None)}),
    dict(PRIMAIRE["c3-eps-2026"], levels={"6E": (R[2027], None)}),
    # Le programme de 2020 vaut en sixième jusqu'à la rentrée 2027 pour l'histoire-géographie, les
    # sciences et l'EPS ; sans successeur pour les arts. Les langues vivantes de sixième ont un
    # nouveau programme depuis 2025 (BO n° 22 du 29 mai 2025), qui n'est pas encore repris ici.
    dict(PRIMAIRE["c3-2020-2023-consolide"],
         keep={"histoire et geographie": ["6E"], "sciences et technologie": ["6E"],
               "education physique et sportive": ["6E"], "arts plastiques": ["6E"],
               "education musicale": ["6E"], "histoire des arts": ["6E"]},
         levels={"6E": (R[2016], FIN[2027])}),
    # ── Tous niveaux ─────────────────────────────────────────────────────────────────────────────
    dict(PRIMAIRE["emc-2024"],
         levels={"6E": (R[2026], None), "5E": (R[2024], None), "4E": (R[2025], None), "3E": (R[2026], None)}),
    dict(id="college-evars", cycle=None, bo="BO n° 6 du 6 février 2025", outline="evars", drop_root=True,
         enseignement="Éducation à la vie affective et relationnelle, et à la sexualité",
         url=EDU + "Programme%20d%E2%80%99%C3%A9ducation%20%C3%A0%20la%20vie%20affective%20et%20relationnelle%2C%20et%20%C3%A0%20la%20sexualit%C3%A9%20au%20coll%C3%A8ge-405264.pdf",
         levels={lv: (R[2025], None) for lv in ORDER}),
    # ── Cycle 4 ──────────────────────────────────────────────────────────────────────────────────
    dict(id="c4-francais-2026", cycle="C4", enseignement="Français", bo="BO n° 10 du 5 mars 2026",
         themes_under="Culture littéraire et artistique",
         url=EDU + "Annexe%201%20%E2%80%93%20Programme%20de%20fran%C3%A7ais%20pour%20le%20cycle%204-480713.pdf",
         levels={"5E": (R[2026], None), "4E": (R[2027], None), "3E": (R[2028], None)}),
    dict(id="c4-maths-2026", cycle="C4", enseignement="Mathématiques", bo="BO n° 10 du 5 mars 2026",
         url=EDU + "Annexe%202%20%E2%80%93%20Programme%20de%20math%C3%A9matiques%20pour%20le%20cycle%204-480716.pdf",
         levels={"5E": (R[2026], None), "4E": (R[2027], None), "3E": (R[2028], None)}),
    dict(id="c4-techno-2024", cycle="C4", enseignement="Technologie", bo="BO n° 9 du 29 février 2024",
         outline="progression",
         url=EDU + "Annexe%20%E2%80%94%20Programme%20de%20technologie%20du%20cycle%204-368016.pdf",
         levels={"5E": (R[2024], None), "4E": (R[2025], None), "3E": (R[2026], None)}),
    # Programme de 2020 : ce qui n'a pas encore de successeur, et le français, les mathématiques et
    # les langues vivantes de quatrième et de troisième jusqu'à l'arrivée des nouveaux textes.
    dict(id="c4-2020-consolide", cycle="C4", enseignement=None, bo=BO_2020,
         subheadings=SUBHEADINGS_C4_2020,
         url="https://eduscol.education.gouv.fr/sites/default/files/document/programme-d-enseignement-du-cycle-4-67722.pdf",
         keep={"francais": ["4E", "3E"], "mathematiques": ["4E", "3E"],
               "langues vivantes (etrangeres ou regionales)": ["4E", "3E"],
               "arts plastiques": C4_ALL, "education musicale": C4_ALL, "histoire des arts": C4_ALL,
               "education physique et sportive": C4_ALL, "histoire et geographie": C4_ALL,
               "physique-chimie": C4_ALL, "sciences de la vie et de la terre": C4_ALL,
               "education aux medias et a l'information": C4_ALL},
         levels={"5E": (R[2016], FIN[2026]), "4E": (R[2016], FIN[2027]), "3E": (R[2016], FIN[2028])},
         open_ended={"arts plastiques", "education musicale", "histoire des arts", "education physique et sportive",
                     "histoire et geographie", "physique-chimie", "sciences de la vie et de la terre",
                     "education aux medias et a l'information"}),
]

COLLEGE = dict(
    key="open-ent:programmes-college", ns=NS, edition=EDITION, cycles=CYCLES, order=ORDER, sources=SOURCES,
    corrections=[os.path.join(HERE, "..", "programmes-1d", "corrections.json"), os.path.join(HERE, "corrections.json")],
    item_types=ITEM_TYPES + ["Repère de progressivité", "Repère annuel de programmation"],
    short_title="Programmes du collège",
    title="Programmes du collège — sixième (cycle 3) et cycle 4",
    description=("Programmes d’enseignement de la sixième à la troisième, d’après les textes officiels publiés au "
                 "Bulletin officiel. Chaque énoncé indique ses niveaux et ses dates d’application : les textes en "
                 "vigueur et ceux déjà publiés pour une rentrée à venir y figurent ensemble."),
    subject=["Collège"],
)

if __name__ == "__main__":
    engine.main(COLLEGE)
