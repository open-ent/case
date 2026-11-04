#!/usr/bin/env bash
# Télécharge les textes officiels des programmes du collège et les convertit en texte.
#   ./fetch.sh <dossier>            → <dossier>/pdf/*.pdf et <dossier>/txt/*.txt
# Les PDF ne sont pas versionnés : ils se retéléchargent, et les liens figurent dans build.py.
#
# education.gouv.fr et eduscol.education.gouv.fr sont derrière Cloudflare : les fichiers statiques
# (/sites/default/files/…) passent, les pages HTML non.
set -euo pipefail
OUT="${1:?dossier de sortie}"
mkdir -p "$OUT/pdf" "$OUT/txt"
UA="Mozilla/5.0 (X11; Linux x86_64) Chrome/130"

HERE="$(cd "$(dirname "$0")" && pwd)" python3 -I -c '
import os, sys
sys.path.insert(0, os.environ["HERE"])
from build import SOURCES
extra = {}
for s in SOURCES:
    print(s["id"], extra.get(s["id"], s["url"]), sep="\t")
' > "$OUT/sources.tsv"

while IFS=$'\t' read -r id url; do
  curl -fsSL -A "$UA" -o "$OUT/pdf/$id.pdf" "$url"
  pdftotext -layout "$OUT/pdf/$id.pdf" "$OUT/txt/$id.txt"
  printf '%-26s %6s mots\n' "$id" "$(wc -w < "$OUT/txt/$id.txt")"
done < "$OUT/sources.tsv"
