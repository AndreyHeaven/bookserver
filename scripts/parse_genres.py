#!/usr/bin/env python3
"""Parse sql/lib.libgenrelist.sql -> backend/src/main/resources/db/changelog/seed/genres.csv

Source columns (MariaDB INSERT VALUES): (GenreId, GenreCode, GenreDesc, GenreMeta)
Target CSV columns: code, parent_id, title, meta_section, position

- `code`         <- GenreCode
- `parent_id`    <- empty (NULL) because source has no parent_id column;
                  hierarchy is expressed only via meta_section text (handled by service layer later).
- `title`        <- GenreDesc
- `meta_section` <- GenreMeta
- `position`     <- original GenreId (preserves source ordering as best-effort).
"""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "sql" / "lib.libgenrelist.sql"
DST = ROOT / "backend" / "src" / "main" / "resources" / "db" / "changelog" / "seed" / "genres.csv"


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


def main() -> int:
    if not SRC.exists():
        raise SystemExit(f"Source file not found: {SRC}")
    sql_text = SRC.read_text(encoding="utf-8")
    segment = extract_values_segment(sql_text)
    rows = parse_tuples(segment)
    if not rows:
        raise SystemExit("No genre rows parsed")

    DST.parent.mkdir(parents=True, exist_ok=True)
    with DST.open("w", encoding="utf-8", newline="") as fh:
        writer = csv.writer(fh, quoting=csv.QUOTE_MINIMAL, lineterminator="\n")
        writer.writerow(["code", "parent_id", "title", "meta_section", "position"])
        for gid, code, desc, meta in rows:
            writer.writerow([code, "", desc, meta, str(gid)])

    print(f"Wrote {len(rows)} rows to {DST}")
    # Sanity: code uniqueness
    codes = [r[1] for r in rows]
    if len(set(codes)) != len(codes):
        dup = [c for c in codes if codes.count(c) > 1]
        print(f"WARNING: duplicate genre codes: {sorted(set(dup))}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
