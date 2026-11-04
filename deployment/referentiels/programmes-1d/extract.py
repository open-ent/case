#!/usr/bin/env python3
"""
Extraction des programmes officiels de l'école primaire, texte par texte.

Entrée : le texte d'un PDF officiel, converti par `pdftotext -layout` (cf. fetch.sh).
Sortie : la liste ordonnée de ses rubriques et de ses objectifs d'apprentissage, avec le chemin
qui les situe dans le texte (domaine, niveau, sous-domaine), l'échéance (« En fin de période 1 »)
et les colonnes voisines du tableau (exemples de réussite, attendus, repères).

Le texte officiel commande la structure : l'arborescence est lue dans le SOMMAIRE de chaque
programme, puis retrouvée dans le corps du texte. Rien n'est reformulé.

    python3 extract.py <texte.txt>            # résumé
    python3 extract.py <texte.txt> --json     # sortie complète
"""
import json
import re
import sys
import unicodedata

# ── Normalisation ────────────────────────────────────────────────────────────────────────────────


def norm(text):
    """Forme de comparaison : sans accents, apostrophes unifiées, espaces réduits, minuscules."""
    text = text.replace("’", "'").replace("‘", "'").replace("–", "-").replace("—", "-")
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", text).strip().lower()


def clean(text):
    """Forme publiée : espaces réduits, césures de fin de ligne recollées."""
    text = re.sub(r"(\w)- (\w)", lambda m: m.group(1) + "-" + m.group(2)
                  if m.group(2)[0].isupper() else m.group(1) + m.group(2), text)
    return re.sub(r"\s+", " ", text).strip()


# ── Niveaux ──────────────────────────────────────────────────────────────────────────────────────

LEVELS = [
    (r"^petite section\b|^avant 4 ans", ["PS"]),
    (r"^moyenne section\b|^a partir de 4 ans", ["MS"]),
    (r"^grande section\b|^a partir de 5 ans", ["GS"]),
    (r"^cours preparatoire\b|^cp\b", ["CP"]),
    (r"^cours elementaire premiere annee\b|^ce1\b", ["CE1"]),
    (r"^cours elementaire deuxieme annee\b|^ce2\b", ["CE2"]),
    (r"^cours moyen premiere annee\b|^cm1\b|^classe de cm1\b", ["CM1"]),
    (r"^cours moyen deuxieme annee\b|^cm2\b|^classe de cm2\b", ["CM2"]),
    (r"^cours moyen( premiere et deuxieme annees)?\b", ["CM1", "CM2"]),
    (r"^cours elementaire( premiere et deuxieme annees)?\b", ["CE1", "CE2"]),
    (r"^sixieme\b|^6e\b|^classe de (sixieme|6e)\b", ["6E"]),
    (r"^cinquieme\b|^5e\b|^classe de (cinquieme|5e)\b", ["5E"]),
    (r"^quatrieme\b|^4e\b|^classe de (quatrieme|4e)\b", ["4E"]),
    (r"^troisieme\b|^3e\b|^classe de (troisieme|3e)\b", ["3E"]),
    (r"^(seconde|terminale)\b|^classe de (seconde|premiere|terminale)"
     r"|^premiere (generale|technologique|professionnelle)\b|^premiere :", ["2D"]),
]

# Un titre qui n'est qu'un niveau, éventuellement suivi d'un intitulé : « CP : Se reconnaitre… ».
LEVEL_TITLE = re.compile(r"^(cours preparatoire|cours elementaire|cours moyen|sixieme|cinquieme|quatrieme|"
                         r"troisieme|seconde|terminale|cp|ce1|ce2|cm1|cm2)\b[^:]*?(:|$)")


def level_of(title):
    t = norm(title)
    for pattern, codes in LEVELS:
        if re.search(pattern, t):
            return codes
    return None


def pure_level(title):
    """Vrai si le titre n'est qu'un niveau, sans intitulé (« Cours moyen deuxième année »)."""
    t = norm(title)
    return bool(level_of(title)) and bool(re.match(r"^(classe de (cm1|cm2|sixieme|6e)|cours (preparatoire|elementaire|moyen)( (premiere|deuxieme)"
                                                    r"( et deuxieme)? annees?)?|sixieme|cinquieme|quatrieme|troisieme|cp|ce1|ce2|cm1|cm2)$", t))


# ── Sommaire ─────────────────────────────────────────────────────────────────────────────────────


def indent(line):
    return len(line) - len(line.lstrip(" "))


def read_summary(lines):
    """Rend (entrées, indice de la première ligne du corps). Une entrée : [retrait, titre]."""
    start = next(i for i, l in enumerate(lines) if norm(l) == "sommaire")
    entries = []
    i = start + 1
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        text = line.strip()
        # Le corps commence quand le premier titre du sommaire réapparaît.
        first = norm(entries[0][1]) if entries else ""
        if entries and len(entries) > 2 and indent(line) <= entries[0][0] and \
                (norm(text) == first or (len(norm(text)) >= 18 and first.startswith(norm(text)))):
            break
        # Ligne de continuation d'un titre trop long : minuscule, parenthèse, ou mot isolé.
        text = re.sub(r"\s*_{3,}.*$", "", text)
        if entries and (text[0].islower() or text.startswith("(") or text in ("heures)",)):
            entries[-1][1] += " " + text
        else:
            entries.append([indent(line), text])
        i += 1
    return entries, i


# ── Tableaux d'objectifs ─────────────────────────────────────────────────────────────────────────

HEADER = re.compile(r"Compétences travaillées\s+Domaines du socle|Attendus de fin de cycle\s*$|Notions abordées +Contenus|Objectifs? d.apprentissage(?!\s*:)|Notions et compétences")
PERIOD = re.compile(
    r"^(En (fin|début|milieu|cours)|Dès |Au (cours|début)|À (la fin|partir|l.issue)|Tout au long|Fin de|Début de|"
    r"Pendant |Période \d)", re.I)
LEVEL_LINE = re.compile(r"^(cours preparatoire|cours elementaire (premiere|deuxieme) annee|"
                        r"cours moyen (premiere|deuxieme) annee|sixieme|cp|ce1|ce2|cm1|cm2)$")
IGNORED = re.compile(r"^(principes?|points? de vigilance.*|preambule)$")
AGE = re.compile(r"^[•▪-]?\s*(?:À aborder )?(Avant 4 ans|avant 4 ans|À partir de 4 ans|À partir de 5 ans)\b")
# En-têtes des tableaux qu'on ne reprend pas : ils ferment le tableau qui les précède sans blanc.
TABLE_END = re.compile(r"^(Exemples de situations|Connaissances et compétences (associées|attendues)|Repères de progressivité|"
                       r"Croisements entre enseignements)")
# Numéros des domaines du socle (« 1, 3, 5 ») de la colonne voisine, glissés dans une compétence.
SOCLE = re.compile(r"\s+[1-5](?:\s*,\s*[1-5])*(?=\s|$)")
BULLET = re.compile(r"^\s*([-•–—▪\uf0a7\uf0b7]|\d+\.)\s+")


def columns_of(header_lines):
    """Positions de départ des colonnes, lues sur l'en-tête (blocs séparés de 3 espaces ou plus)."""
    starts = set()
    for h in header_lines:
        for m in re.finditer(r"\S+(?: {1,2}\S+)*", h):
            starts.add(m.start())
    starts = sorted(starts)
    # Deux départs proches appartiennent à la même colonne (en-tête centré sur deux lignes).
    merged = []
    for s in starts:
        if merged and s - merged[-1] < 12:
            continue
        merged.append(s)
    return merged


def split_line(line, starts):
    """Découpe une ligne aux frontières des colonnes, sur le blanc le plus proche."""
    cuts = [0]
    for b in starts[1:]:
        best = None
        # Une puce de la colonne de droite, collée au texte de gauche qui déborde : « …, à - L’élève ».
        for d in range(0, 11):
            for s in (b - d, b + d):
                if 1 < s < len(line) - 1 and line[s] in "-•" and line[s - 1] == " " and line[s + 1] == " ":
                    best = s
                    break
            if best is not None:
                break
        for d in range(0, 8):
            if best is not None:
                break
            for s in (b - d, b + d):
                if 1 < s < len(line) and line[s - 1] == " " and line[s] != " " and (s < 2 or line[s - 2] == " "):
                    best = s
                    break
            if best is not None:
                break
        if best is None and 0 < b < len(line) and line[b - 1] != " " and line[b] != " ":
            # Texte de gauche qui déborde, séparé par un seul blanc : on ne coupe pas un mot.
            best = next((s for d in range(1, 13) for s in (b + d, b - d)
                         if 1 < s < len(line) and line[s - 1] == " " and line[s] != " "), None)
        cuts.append(best if best is not None else b)
    cuts.append(max(len(line), cuts[-1]))
    return [line[cuts[i]:cuts[i + 1]] for i in range(len(cuts) - 1)]


# Mots qui ne terminent pas un énoncé : la ligne suivante le continue, même si elle prend une capitale.
DANGLING = {"et", "ou", "de", "à", "des", "les", "le", "la", "du", "en", "un", "une", "aux", "au", "par", "pour"}


def indented_items(cells):
    """Compétences travaillées sans puces (sciences de 2020) : l'intitulé du groupe est en marge, les
    compétences en retrait, une par phrase ; une sous-liste à puces complète la phrase qu'elle suit
    (« Utiliser des outils numériques pour : - communiquer des résultats ; … »)."""
    items, group = [], None
    for raw in cells:
        text = raw.strip()
        if not text:
            continue
        if indent(raw) == 0 and not BULLET.match(raw):
            if group and items and items[-1]["group"] != group and text[0].islower():
                group += " " + text
            else:
                group = text
            continue
        if BULLET.match(raw) and items:
            items[-1]["text"] += " " + BULLET.sub("", raw, count=1).strip()
        elif items and items[-1]["group"] == group and (
                text[0].islower() or not items[-1]["text"].rstrip().endswith((".", "!", "?"))):
            items[-1]["text"] += " " + text
        else:
            items.append({"text": text, "period": None, "group": clean(group) if group else None})
    for it in items:
        it["text"] = clean(it["text"])
    return items


CEFR = re.compile(r"^Niveau (A1|A2|B1|B2)$")


def cefr_items(cells, single_column, groups):
    """Attendus des langues vivantes de 2020, rangés sous « Niveau A1 », « Niveau A2 » : le niveau
    passe en tête de chaque énoncé, comme dans les programmes récents (« (A1) Comprendre… »)."""
    out, level, chunk = [], None, []

    def flush():
        for it in items_of(chunk, single_column, groups):
            it["text"] = f"({level}) {it['text']}" if level else it["text"]
            out.append(it)

    for c in cells:
        m = CEFR.match(c.strip())
        if m:
            flush()
            level, chunk = m.group(1), []
        else:
            chunk.append(c)
    flush()
    return out


def items_of(cells, single_column, groups=False):
    """Regroupe les lignes d'une colonne en éléments ; les échéances étiquettent ceux qui suivent."""
    items, period, group, grouping = [], None, None, False
    has_bullets = any(BULLET.match(c) for c in cells)
    if any(CEFR.match(c.strip()) for c in cells):
        return cefr_items(cells, single_column, groups)
    if groups:
        margin = sum(1 for c in cells if c.strip() and indent(c) == 0 and not BULLET.match(c))
        indented = sum(1 for c in cells if c.strip() and indent(c) >= 2 and not BULLET.match(c))
        if margin and indented > 2 * sum(1 for c in cells if BULLET.match(c)):
            return indented_items(cells)
    for raw in cells:
        text = raw.strip()
        if not text:
            continue
        if groups and has_bullets and not BULLET.match(raw) and not PERIOD.match(text):
            # Intitulé de groupe entre deux listes (« Mettre en œuvre un projet artistique ») :
            # majuscule après un élément clos, ou suite minuscule d'un intitulé en cours.
            if grouping and text[0].islower():
                group += " " + text
                continue
            if text[0].isupper() and (not items or items[-1]["text"].rstrip().endswith((".", ";"))):
                group, grouping = text, True
                continue
        grouping = False
        if PERIOD.match(text) and len(text) < 60 and not BULLET.match(raw):
            period = clean(text.rstrip(" :"))
            continue
        if BULLET.match(raw):
            items.append({"text": BULLET.sub("", raw, count=1).strip(), "period": period,
                          # « Pratiquer des démarches scientifiques 4 » : le 4 est un domaine du socle.
                          "group": re.sub(r"\s+\d(\s*,\s*\d)*$", "", clean(group)) if group else None})
        elif (single_column and not has_bullets and not text[0].islower()
              and not (items and (items[-1]["text"].rstrip().endswith((",", "(", "’", ":", "-"))
                                  or items[-1]["text"].split()[-1] in DANGLING))):
            items.append({"text": text, "period": period})
        elif (not single_column and not has_bullets and not text[0].islower()
              and (not items or items[-1]["text"].rstrip().endswith((".", ";", "!", "?", "»", ")")))):
            # Colonne sans puces : un objectif par phrase, la majuscule après un point en ouvre un.
            items.append({"text": text, "period": period})
        elif items:
            items[-1]["text"] += " " + text
        else:
            items.append({"text": text, "period": period})
    for it in items:
        it["text"] = clean(it["text"])
    return [it for it in items if it["text"]]


def read_table(lines, i, is_heading):
    """Lit le tableau dont l'en-tête est à la ligne i. Rend (colonnes, ligne suivante)."""
    header = [lines[i]]
    j = i + 1
    # En-tête sur deux lignes (« Attendus (connaissances et / compétences) »).
    if j < len(lines) and lines[j].strip() and not BULLET.match(lines[j]) and not PERIOD.match(lines[j].strip()) \
            and indent(lines[j]) > 10 and len(lines[j].strip()) < 60:
        header.append(lines[j])
        j += 1
    # En-tête de droite qui commence au-dessus de celui de gauche (EVAR : « Propositions de
    # démarches et d’activités pour / les séances… » encadre « Notions et compétences »).
    above = i - 1
    while above > i - 3 and above >= 0 and not lines[above].strip():
        above -= 1
    if above >= 0 and indent(lines[above]) > 30 and len(lines[above].strip()) < 70 and indent(lines[i]) < 30:
        header.insert(0, lines[above])
    hinted = columns_of(header)
    names = [clean(n) for n in re.split(r"\s{3,}", " ".join(h.strip() for h in header[:1]))]
    if len(header) > 1:
        names = [clean(" ".join(split_line(h, hinted)[k] for h in header)) for k in range(len(hinted))]
    rows = []
    blank = 0
    single = len(hinted) == 1
    while j < len(lines):
        line = lines[j]
        if not line.strip():
            blank += 1
            ahead = next((l for l in lines[j + 1:j + 4] if l.strip()), "")
            if (blank >= 2 or (single and rows)) and not CEFR.match(ahead.strip()):
                break
            j += 1
            continue
        stripped = line.strip()
        if HEADER.match(stripped) or AGE.match(stripped) or TABLE_END.match(stripped) or is_heading(line):
            break
        if blank and rows and indent(line) == 0 and not BULLET.match(line) and not PERIOD.match(stripped):
            break
        blank = 0
        rows.append(line)
        j += 1
    starts = infer_columns(rows, len(hinted)) if not single else [0]
    if len(starts) == 1 < len(hinted) and len(columns_of([lines[i]])) > 1:
        # Colonne de droite trop clairsemée pour se lire sur les lignes : on suit la ligne d'en-tête,
        # ramenée au retrait des lignes qui n'ont que la colonne de droite.
        right = [indent(r) for r in rows if indent(r) > 30]
        starts = columns_of([lines[i]])
        starts[-1] = min(right + [starts[-1]])
    cells = [[] for _ in starts]
    for line in rows:
        parts = split_line(line, starts) if len(starts) > 1 else [line]
        for k, part in enumerate(parts):
            cells[k].append(part)
    names = (names + [""] * len(starts))[:len(starts)]
    groups = norm(names[0]).startswith("competences travaillees")
    columns = [{"name": names[k], "items": items_of(cells[k], single, groups and k == 0)} for k in range(len(starts))]
    if groups:
        for item in columns[0]["items"]:
            item["text"] = clean(SOCLE.sub("", item["text"]))
    return columns, j


def infer_columns(rows, count):
    """Frontières des colonnes, lues sur les lignes du tableau : là où beaucoup de lignes
    reprennent après un double blanc, et où presque aucun mot ne chevauche."""
    width = max((len(r) for r in rows), default=0)
    scores = {}
    for b in range(15, max(16, width - 8)):
        starts = sum(1 for r in rows if b < len(r) and r[b] != " " and r[b - 1] == " " and r[b - 2] == " ")
        cross = sum(1 for r in rows if b < len(r) and r[b] != " " and r[b - 1] != " ")
        scores[b] = starts - 2 * cross
    chosen = [0]
    for _ in range(count - 1):
        candidates = [b for b in scores if all(abs(b - c) > 12 for c in chosen)]
        if not candidates:
            break
        best = max(candidates, key=lambda b: (scores[b], -b))
        if scores[best] <= 0:
            break
        chosen.append(best)
    return sorted(chosen)


# ── Parcours du texte ────────────────────────────────────────────────────────────────────────────


# Textes sans sommaire : le plan est donné ici, et le corps commence au premier repère de niveau.
EVAR_AXES = ["Se connaître, vivre et grandir avec son corps",
             "Rencontrer les autres et construire des relations, s’y épanouir",
             "Trouver sa place dans la société, y être libre et responsable"]
OUTLINES = {
    "evar": ("Éducation à la vie affective et relationnelle", EVAR_AXES,
             re.compile(r"^➜ (Cours préparatoire|À aborder avant 4 ans)")),
    "evars": ("Éducation à la vie affective et relationnelle, et à la sexualité", EVAR_AXES,
              re.compile(r"^➜ (Sixième|Seconde)$")),
}
THEME_2020 = re.compile(r"^Thème \d+ [-–] \S")
# Histoire-géographie de 2020, cycle 4 : « Classe de 5e » coiffe un tableau de repères annuels dont
# la colonne de gauche porte « Thème 1 », son intitulé, puis ses chapitres en liste.
ANNUAL = re.compile(r"^Classe de (5e|4e|3e)$")
ANNUAL_END = re.compile(r"^(Classe de (5e|4e|3e)|Croisements entre enseignements|Géographie|Histoire)$")
LEARNING_OBJECTIVE = re.compile(r"^Objectifs? d.apprentissage\s*:\s*(.+)")


# En-têtes et pieds de page répétés à chaque page des programmes consolidés.
RUNNING = re.compile(r"^\s*(©|D.après le BOEN|Programme consolidé publié|Bulletin officiel n° |.*www\.(education|eduscol)\.)"
                     r"|^\s+no \d+ du \d+ \w+ \d{4}$")   # suite du « D'après le BOEN no 31… et le BOEN »


def read_annual(lines, i):
    """Tableau de repères annuels (histoire-géographie de 2020, cycle 4) à partir de la ligne i,
    juste après « Classe de 5e ». Rend ([(thème, [chapitres])], ligne suivante)."""
    j, rows = i, []
    while j < len(lines) and not (ANNUAL_END.match(lines[j].strip()) and indent(lines[j]) < 60 and rows):
        rows.append(lines[j])
        j += 1
    body = [r for r in rows if not re.match(r"^\s*(Repères annuels|Démarches et contenus|programmation)", r.strip())]
    cut = infer_columns([r for r in body if r.strip()], 2)
    themes = []
    edge = cut[1] if len(cut) > 1 else 10 ** 6
    for r in body:
        # La colonne de gauche ne contient jamais deux blancs de suite : elle s'arrête au premier.
        gap = re.search(r"\S( {2,})\S", r)
        if gap and gap.start(1) <= edge + 4:
            left = r[:gap.start(1)]
        else:
            # Colonnes collées par un seul blanc : on coupe au dernier blanc avant la colonne de droite.
            space = r.rfind(" ", 0, edge + 1)
            left = r if len(r.rstrip()) <= edge else r[:space] if space > 0 else r[:edge]
        text = left.strip()
        if not text:
            continue
        if re.match(r"^Thème \d+$", text):
            themes.append([text, "", []])
        elif not themes:
            continue
        elif BULLET.match(left):
            themes[-1][2].append(BULLET.sub("", left, count=1).strip())
        elif themes[-1][2]:
            themes[-1][2][-1] += " " + text
        else:
            themes[-1][1] += " " + text
    def tidy(text):
        # Exposants en ligne à part : « e Le XVIII siècle » → « Le XVIIIe siècle ».
        return re.sub(r"\be ((?:Le |au |et )?)([IVXL]+) (siècles?)", r"\1\2e \3", clean(text)).rstrip(" :")

    return [(f"{n} : {tidy(title)}", [tidy(c) for c in chapters]) for n, title, chapters in themes], j


PROGRESSION = re.compile(r"^Repères de progressivité$")
TECHNO_THEMES = ["Les objets et les systèmes techniques : leurs usages et leurs interactions à découvrir et à analyser",
                 "Structure, fonctionnement, comportement : des objets et des systèmes techniques à comprendre",
                 "Création, conception, réalisation, innovations : des objets à concevoir et à réaliser"]


def progression_items(cells):
    """Une colonne d'un tableau de repères de progressivité : un repère par capitale initiale,
    sauf quand la ligne précédente reste ouverte (« pour », « , », parenthèse)."""
    items = []
    for raw in cells:
        text = raw.strip()
        if not text:
            continue
        if items and (text[0].islower() or text[0] in "(«" or items[-1].rstrip().endswith((",", "(", "’", "'"))
                      or items[-1].split()[-1] in DANGLING or re.search(r"[’'](un|une)$", items[-1])
                      or items[-1].count("(") > items[-1].count(")")):
            items[-1] += " " + text
        else:
            items.append(text)
    return [clean(t) for t in items]


def extract_progression(lines):
    """Programme de technologie du cycle 4 (2024), sans sommaire : thème › compétence de fin de cycle ›
    sous-thème, puis un tableau « Repères de progressivité » à trois colonnes (5e, 4e, 3e)."""
    out, theme, competence, competences = [], None, None, []
    i = 0
    while i < len(lines):
        line, stripped = lines[i], lines[i].strip()
        t = norm(stripped)
        hit = next((th for th in TECHNO_THEMES if indent(line) == 0 and t and norm(th).startswith(t)
                    and len(t) >= 25), None)
        if hit and not (i + 1 < len(lines) and lines[i + 1].startswith("Repères")):
            theme, competence = hit, None
            i += 2 if norm(hit) != t else 1
            continue
        if stripped == "Compétences de fin de cycle":
            # Les compétences du thème, en liste : retrait, suite en retrait, puis un paragraphe.
            # Chaque compétence est une puce () ; ses lignes de suite sont en retrait ; un paragraphe
            # sans puce ni retrait les sépare.
            i += 1
            while i < len(lines):
                text = lines[i].strip()
                if text.startswith("\uf0b7"):
                    competences.append([text.lstrip("\uf0b7 "), i])
                elif text and competences and competences[-1][1] == i - 1 and indent(lines[i]) >= 3:
                    competences[-1][0] += " " + text
                    competences[-1][1] = i
                elif text and not norm(text).startswith(("progressivite",)) and indent(lines[i]) == 0 \
                        and competences and i - competences[-1][1] > 40:
                    break
                i += 1
                if text.startswith("Progressivité pour atteindre"):
                    break
            continue
        known = next((c for c, _ in competences if indent(line) == 0 and len(t) >= 25 and norm(c).startswith(t)), None)
        if known and theme:
            competence = clean(known)
            while i + 1 < len(lines) and norm(" ".join([stripped, lines[i + 1].strip()])) and \
                    norm(known).startswith(norm(stripped + " " + lines[i + 1].strip())):
                i += 1
                stripped = stripped + " " + lines[i].strip()
            i += 1
            continue
        if PROGRESSION.match(stripped) and theme and competence:
            topic = clean(lines[i - 1].strip())
            j = i + 1
            while j < len(lines) and not lines[j].strip():
                j += 1
            levels = [level_of(w) for w in lines[j].split()]
            j += 1
            rows = []
            while j < len(lines) and not norm(lines[j]).startswith("connaissances") and \
                    not (not lines[j].strip() and j + 1 < len(lines) and not lines[j + 1].strip() and rows):
                if lines[j].strip() and not RUNNING.match(lines[j]) and not lines[j].strip().startswith("Bulletin officiel"):
                    rows.append(lines[j])
                j += 1
            starts = infer_columns(rows, len(levels))
            cells = [[] for _ in starts]
            for r in rows:
                for k, part in enumerate(split_line(r, starts)):
                    cells[k].append(part)
            for k, column in enumerate(cells):
                for item in progression_items(column):
                    out.append({"kind": "objective", "path": [theme, competence, topic], "levels": levels[k],
                                "period": None, "table": "Repères de progressivité", "statement": item, "columns": {}})
            i = j
            continue
        i += 1
    return out


def extract(path, outline=None, subheadings=None):
    """`subheadings` : intertitres absents du sommaire, {titre: profondeur} (thèmes des sciences de 2020)."""
    lines = open(path, encoding="utf-8").read().replace("\f", "\n").split("\n")
    lines = ["" if RUNNING.match(l) else l for l in lines]
    if outline == "progression":
        return extract_progression(lines)
    if outline:
        root, children, first = OUTLINES[outline]
        entries = [[0, root]] + [[2, c] for c in children]
        body = next(i for i, l in enumerate(lines) if first.match(l.strip()))
    else:
        entries, body = read_summary(lines)
    # Profondeur = rang du retrait ; deux retraits voisins d'un espace sont le même rang (la mise
    # en page du sommaire de l'EPS hésite entre 2 et 3 espaces pour des entrées sœurs).
    # Mais deux retraits qui se suivent comme parent et enfant (maternelle : 1 puis 2) restent distincts.
    nested = {(a[0], b[0]) for a, b in zip(entries, entries[1:]) if b[0] == a[0] + 1}
    ranks = []
    for value in sorted({e[0] for e in entries}):
        if ranks and value - ranks[-1][-1] <= 1 and (ranks[-1][-1], value) not in nested:
            ranks[-1].append(value)
        else:
            ranks.append([value])
    titles = [(next(r for r, group in enumerate(ranks) if e[0] in group), e[1]) for e in entries]

    pointer = 1 if outline else 0
    # Programmes de 2020 : les volets 1 et 2 (spécificités du cycle, contributions au socle) citent
    # chaque enseignement en intertitre ; les contenus ne commencent qu'au volet 3.
    volet3 = next((k for k, t in enumerate(titles) if norm(t[1]).startswith("volet 3")), None)
    if volet3 is not None:
        body = next(j for j in range(body, len(lines)) if norm(lines[j]).startswith("volet 3"))
        pointer = volet3
    if not outline and volet3 is None and len(titles) > 1 and IGNORED.match(norm(titles[0][1])):
        # Sommaire ouvert par des « Principes » : leurs intertitres reprennent les noms des domaines
        # (« La pensée informatique » en mathématiques du cycle 4) ; le contenu commence au premier domaine.
        first = norm(titles[1][1])
        start = next((j for j in range(body, len(lines)) if norm(lines[j]) == first and indent(lines[j]) <= 2), None)
        if start is not None:
            body, pointer = start, 1
    trail = [(0, entries[0][1], 0)] if outline else []   # [(profondeur, titre, indice dans le sommaire)]
    level = None                    # le niveau courant n'est pas un étage : il étiquette
    out = []

    def match_entry(text):
        """Indice de l'entrée du sommaire que la ligne ouvre, en avançant seulement."""
        t = norm(text)
        if not t or len(t) > 200:
            return None
        shallowest = None
        for k in range(pointer, len(titles)):
            # On ne saute jamais un titre plus haut que celui qu'on reconnaît : un intertitre des
            # « Principes » homonyme d'un sous-domaine ferait sinon passer domaine et niveau.
            title = norm(titles[k][1])
            matches = t == title or (len(t) >= 18 and title.startswith(t)) or (len(title) >= 18 and t.startswith(title))
            if matches and (shallowest is None or shallowest >= titles[k][0]):
                return k
            shallowest = titles[k][0] if shallowest is None else min(shallowest, titles[k][0])
        return None

    def heading_at(line):
        """Entrée du sommaire que la ligne ouvre. Dans les programmes de 2020, un enseignement
        s'ouvre en début de ligne : son nom en retrait est une case de tableau (« Technologie »
        dans la colonne des disciplines croisées de l'histoire des arts), pas un titre ; en minuscule
        ou suivi d'un point, c'est la fin d'une phrase (« …et l'enseignement moral et civique. »)."""
        text = line.strip()
        k = match_entry(text)
        if k is not None and volet3 is not None and titles[k][0] == 0 and \
                (indent(line) > 10 or not text[0].isupper() or text.endswith(".")):
            return None
        return k

    def is_heading(line):
        return heading_at(line) is not None

    marks = {norm(t).rstrip(" ."): (d, t) for t, d in (subheadings or {}).items()}
    age_level = None                # maternelle : « • À partir de 5 ans… » précède le tableau
    i = body
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped.startswith("➜"):
            # EVAR : « ➜ Cours préparatoire », « ➜ À aborder avant 4 ans » valent jusqu'au suivant.
            stripped = stripped.lstrip("➜ ")
            age = AGE.match(stripped)
            if age and trail:
                level, age_level = level_of(age.group(1)), None
                trail, pointer = trail[:1], trail[0][2] + 1
                i += 1
                continue
        age = AGE.match(stripped)
        if age:
            age_level = level_of(age.group(1))
            i += 1
            continue
        k = heading_at(lines[i]) if stripped else None
        if k is not None:
            age_level = None
            depth, title = titles[k]
            # Un titre long peut se poursuivre sur la ligne suivante.
            if norm(title) != norm(stripped) and norm(title).startswith(norm(stripped)) and i + 1 < len(lines):
                i += 1
            pointer = k + 1
            if level_of(title) and LEVEL_TITLE.match(norm(title)):
                level = level_of(title)
            if pure_level(title):
                out.append({"kind": "level", "levels": level, "title": clean(title)})
            else:
                trail = [t for t in trail if t[0] < depth] + [(depth, title, k)]
                out.append({"kind": "heading", "depth": depth, "title": clean(title),
                            "path": [clean(t[1]) for t in trail], "levels": level})
            i += 1
            continue
        if stripped and pure_level(stripped) and trail:
            # Niveau absent du sommaire (français du cycle 3). Deux ordres coexistent dans le même
            # texte : « niveau › sous-domaines » (Lecture) et « sous-domaine › niveaux » (Écriture).
            # On tranche en regardant ce qui vient d'abord : un sous-domaine ou un tableau.
            domain = trail[0]
            saved = pointer
            pointer = domain[2] + 1
            level_first = False
            for j in range(i + 1, min(i + 400, len(lines))):
                ahead = lines[j].strip()
                if HEADER.match(ahead):
                    break
                if ahead and match_entry(ahead) is not None:
                    level_first = True
                    break
            level = level_of(stripped)
            if level_first:
                trail = [domain]
            else:
                pointer = saved
            i += 1
            continue
        mark = marks.get(norm(stripped).rstrip(" .")) if indent(lines[i]) == 0 and trail else None
        if mark:
            depth, title = mark
            trail = [t for t in trail if t[0] < depth] + [(depth, title, trail[-1][2])]
            out.append({"kind": "heading", "depth": depth, "title": title,
                        "path": [clean(t[1]) for t in trail], "levels": level})
            i += 1
            continue
        annual = ANNUAL.match(stripped)
        if annual and trail:
            level = level_of(stripped)
            themes, i = read_annual(lines, i + 1)
            for title, chapters in themes:
                path = [clean(t[1]) for t in trail] + [title]
                for chapter in chapters or [title]:
                    out.append({"kind": "objective", "path": path, "levels": level, "period": None,
                                "table": "Repères annuels de programmation", "statement": chapter, "columns": {}})
            continue
        theme = THEME_2020.match(stripped)
        if theme and trail:
            # Histoire et géographie de 2020 : « Classe de CM2 » puis « Thème 1 - Le temps de la République ».
            out.append({"kind": "objective", "path": [clean(t[1]) for t in trail], "levels": level,
                        "period": None, "table": "Thème", "statement": clean(stripped), "columns": {}})
            i += 1
            continue
        learning = LEARNING_OBJECTIVE.match(stripped)
        if learning and trail:
            # EVAR : l'objectif d'apprentissage coiffe ses « notions et compétences ».
            text = learning.group(1)
            while i + 1 < len(lines) and lines[i + 1].strip() and not HEADER.search(lines[i + 1]) \
                    and not text.rstrip().endswith("."):
                i += 1
                text += " " + lines[i].strip()
            trail = [t for t in trail if t[0] < 9] + [(9, clean(text), trail[-1][2])]
            out.append({"kind": "heading", "depth": 9, "title": clean(text),
                        "path": [clean(t[1]) for t in trail], "levels": age_level or level})
            i += 1
            continue
        if stripped.startswith("Attendus de fin de cycle"):
            # Programmes de 2020 : l'intertitre qui précède les attendus nomme le sous-domaine
            # (« Produire une performance optimale, mesurable à une échéance donnée »).
            back = i - 1
            while back > i - 4 and not lines[back].strip():
                back -= 1
            title = lines[back].strip()
            if title and len(title) < 110 and not title.endswith((".", ":", ";", ",")) and not BULLET.match(lines[back]) \
                    and (back == 0 or not lines[back - 1].strip()) and trail and norm(title) != norm(trail[-1][1]) \
                    and title[0].isupper() and "Exemples de situations" not in title:
                trail = [t for t in trail if t[0] < 8] + [(8, title, trail[-1][2])]
                out.append({"kind": "heading", "depth": 8, "title": clean(title),
                            "path": [clean(t[1]) for t in trail], "levels": level})
        if HEADER.match(stripped):
            columns, i = read_table(lines, i, is_heading)
            # EMC : « Notions abordées | Contenus d'enseignement | Démarches » — les contenus
            # d'enseignement tiennent lieu d'objectifs, les notions les étiquettent.
            main = 1 if len(columns) > 1 and norm(columns[0]["name"]).startswith("notions abordees") else 0
            first, others = columns[main], columns[:main] + columns[main + 1:]
            for item in first["items"]:
                out.append({
                    "kind": "objective",
                    "path": [clean(t[1]) for t in trail] + ([item["group"]] if item.get("group") else []),
                    "levels": age_level or level,
                    "period": item["period"],
                    "table": clean(first["name"]),
                    "statement": item["text"],
                    "columns": {col["name"]: [it["text"] for it in col["items"]
                                              if it["period"] == item["period"] or not item["period"]]
                                for col in others if col["items"]},
                })
            continue
        i += 1
    return out


def summary(rows):
    from collections import Counter
    objectives = [r for r in rows if r["kind"] == "objective"]
    print(f"{len([r for r in rows if r['kind'] == 'heading'])} rubriques, {len(objectives)} objectifs")
    print("par niveau :", dict(Counter("+".join(r["levels"] or ["?"]) for r in objectives)))
    seen = None
    for r in objectives:
        key = " › ".join(r["path"])
        if key != seen:
            print(f"  [{'+'.join(r['levels'] or ['?'])}] {key}")
            seen = key
        print(f"      - {r['statement'][:110]}" + (f"  ⟨{r['period']}⟩" if r["period"] else ""))


if __name__ == "__main__":
    outline = next((a.split("=", 1)[1] for a in sys.argv if a.startswith("--outline=")), None)
    rows = extract(sys.argv[1], outline, json.loads(next((a.split("=", 1)[1] for a in sys.argv
                                                        if a.startswith("--subheadings=")), "null")))
    if "--json" in sys.argv:
        json.dump(rows, sys.stdout, ensure_ascii=False, indent=1)
    else:
        summary(rows)
