#!/usr/bin/env python3
"""Parse sql/lib.libgenrelist.sql -> backend/src/main/resources/db/changelog/seed/genres.csv

Source columns (MariaDB INSERT VALUES): (GenreId, GenreCode, GenreDesc, GenreMeta)

In the source dump `GenreMeta` is NOT a real parent row, it is only a text label of
the top-level section (e.g. "Фантастика", "Проза"). This script promotes every distinct
`GenreMeta` value to a real parent `genres` row and wires each source genre to it via
`parent_id`, producing a proper two-level hierarchy.

Target CSV columns: id, code, parent_id, title, meta_section, position

Row layout (parents are emitted first so the self-FK is satisfiable on insert):
- Parent (section) rows:
  - `id`           <- 1..K (K = number of distinct GenreMeta values)
  - `code`         <- "meta_" + transliterated slug of the section title (unique)
  - `parent_id`    <- empty (top-level)
  - `title`        <- GenreMeta text
  - `meta_section` <- empty (the parent *is* the section)
  - `position`     <- 1..K in order of first appearance
- Leaf (genre) rows:
  - `id`           <- K + running index (source order)
  - `code`         <- GenreCode
  - `parent_id`    <- id of the parent section row
  - `title`        <- GenreDesc
  - `meta_section` <- GenreMeta (kept as a denormalized label for convenience)
  - `position`     <- original GenreId (preserves source ordering as best-effort)

Because ids are explicit, changeset 006 must reset the BIGSERIAL sequence after loadData.
"""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "sql" / "lib.libgenrelist.sql"
DST = ROOT / "backend" / "src" / "main" / "resources" / "db" / "changelog" / "seed" / "genres.csv"

META_CODE_PREFIX = "meta_"

# Cyrillic -> latin transliteration used to build readable, stable parent codes.
_TRANSLIT = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e",
    "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m",
    "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "h", "ц": "c", "ч": "ch", "ш": "sh", "щ": "sch",
    "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
}


def slugify(text: str) -> str:
    """Transliterate to a lowercase [a-z0-9_] slug (empty parts collapsed)."""
    out: list[str] = []
    for ch in text.lower():
        if ch in _TRANSLIT:
            out.append(_TRANSLIT[ch])
        elif ch.isascii() and (ch.isalnum()):
            out.append(ch)
        else:
            out.append("_")
    slug = re.sub(r"_+", "_", "".join(out)).strip("_")
    return slug


def make_meta_code(title: str, used: set[str]) -> str:
    """Build a unique parent code with the META_CODE_PREFIX, guarding collisions."""
    base = f"{META_CODE_PREFIX}{slugify(title) or 'section'}"
    code = base
    suffix = 2
    while code in used:
        code = f"{base}_{suffix}"
        suffix += 1
    used.add(code)
    return code


def extract_values_segment(sql_text: str) -> str:
    """Return everything after 'INSERT INTO `libgenrelist` VALUES ' up to the next ';' line break."""
    m = re.search(r"INSERT INTO `libgenrelist` VALUES\s+", sql_text)
    if not m:
        raise SystemExit("INSERT statement not found in source SQL")
    start = m.end()
    # Look for the closing ');' followed by newline OR the next statement marker.
    # In mysqldump it's typically a single statement terminated by ');'.
    end = sql_text.find("\n", start)
    if end == -1:
        end = len(sql_text)
    return sql_text[start:end]


def parse_tuples(values_segment: str):
    """Yield (genre_id:int, code:str, desc:str, meta:str) tuples."""
    # State machine: walk the segment, find each top-level (...) group, then split fields.
    i = 0
    n = len(values_segment)
    tuples = []
    while i < n:
        # Find next '('
        while i < n and values_segment[i] != "(":
            i += 1
        if i >= n:
            break
        # Parse the tuple's fields
        i += 1  # skip '('
        fields: list[str] = []
        current = []
        in_string = False
        # MySQL strings use single-quote delimiter, with '' escape for embedded quote and \' too.
        while i < n:
            ch = values_segment[i]
            if in_string:
                if ch == "\\" and i + 1 < n:
                    # backslash escape: keep next char verbatim
                    current.append(values_segment[i + 1])
                    i += 2
                    continue
                if ch == "'":
                    # Check for doubled '' escape
                    if i + 1 < n and values_segment[i + 1] == "'":
                        current.append("'")
                        i += 2
                        continue
                    # end of string
                    in_string = False
                    i += 1
                    continue
                current.append(ch)
                i += 1
            else:
                if ch == "'":
                    in_string = True
                    i += 1
                    continue
                if ch == ",":
                    fields.append("".join(current))
                    current = []
                    i += 1
                    continue
                if ch == ")":
                    fields.append("".join(current))
                    current = []
                    i += 1
                    # Skip an optional comma after the tuple
                    while i < n and values_segment[i] in ", \t\n":
                        i += 1
                    break
                # Unquoted token (numeric / NULL): accumulate
                current.append(ch)
                i += 1
        if len(fields) != 4:
            raise SystemExit(f"Expected 4 fields, got {len(fields)}: {fields!r}")
        try:
            gid = int(fields[0].strip())
        except ValueError as exc:
            raise SystemExit(f"Bad GenreId: {fields[0]!r}") from exc
        code = fields[1].strip()
        desc = fields[2].strip()
        meta = fields[3].strip()
        tuples.append((gid, code, desc, meta))
    return tuples


def build_rows(tuples):
    """Turn source tuples into ordered CSV rows (parents first, then leaves).

    Returns a list of [id, code, parent_id, title, meta_section, position] rows.
    """
    # 1. Collect distinct meta sections in order of first appearance.
    meta_order: list[str] = []
    seen_meta: set[str] = set()
    for _gid, _code, _desc, meta in tuples:
        if meta not in seen_meta:
            seen_meta.add(meta)
            meta_order.append(meta)

    # 2. Emit parent rows with explicit ids 1..K.
    used_codes: set[str] = set()
    meta_to_id: dict[str, int] = {}
    parent_rows: list[list[str]] = []
    for index, meta in enumerate(meta_order, start=1):
        meta_to_id[meta] = index
        parent_rows.append([str(index), make_meta_code(meta, used_codes), "", meta, "", str(index)])

    # 3. Emit leaf rows, ids continue after the parents, linked via parent_id.
    leaf_base = len(meta_order)
    leaf_rows: list[list[str]] = []
    for offset, (gid, code, desc, meta) in enumerate(tuples, start=1):
        leaf_id = leaf_base + offset
        leaf_rows.append([str(leaf_id), code, str(meta_to_id[meta]), desc, meta, str(gid)])

    return parent_rows + leaf_rows


def main() -> int:
    if not SRC.exists():
        raise SystemExit(f"Source file not found: {SRC}")
    sql_text = SRC.read_text(encoding="utf-8")
    segment = extract_values_segment(sql_text)
    tuples = parse_tuples(segment)
    if not tuples:
        raise SystemExit("No genre rows parsed")

    rows = build_rows(tuples)

    DST.parent.mkdir(parents=True, exist_ok=True)
    with DST.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh, quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
        writer.writerow(["id", "code", "parent_id", "title", "meta_section", "position"])
        writer.writerows(rows)

    parents = sum(1 for r in rows if not r[2])
    leaves = len(rows) - parents
    print(f"Wrote {len(rows)} rows to {DST} ({parents} parent sections + {leaves} genres)")

    # Sanity: code uniqueness
    codes = [r[1] for r in rows]
    if len(set(codes)) != len(codes):
        dup = [c for c in codes if codes.count(c) > 1]
        print(f"WARNING: duplicate genre codes: {sorted(set(dup))}", file=sys.stderr)
    # Sanity: id uniqueness
    ids = [r[0] for r in rows]
    if len(set(ids)) != len(ids):
        raise SystemExit("Generated non-unique ids")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
