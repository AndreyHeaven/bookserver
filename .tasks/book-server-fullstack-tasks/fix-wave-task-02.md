# Fix-wave Task 02 — Execution Report

**Date:** 2026-06-01
**Base commit:** `b2fae98` (feat(db): Liquibase XML schema for library domain + genres seed (Task 02))
**Triage source:** `.tasks/book-server-fullstack-tasks/triage-task-02.md`

## Scope

Tightened TP-now schema constraints, documented TP-deferred findings as known
limitations in `db/changelog/README.md`, aligned Task 02 spec wording with the
actual implementation (272 genres / FTS weights A=title, B=authors, C=keywords),
and committed `scripts/parse_genres.py` + a small README so the seed CSV remains
regeneratable.

## Commits

1. **`6db845d` — fix(db): tighten Task 02 schema constraints + document FTS known limitations**
   - `conversion_jobs.book_file_id` → NOT NULL (closes A-F1 ≡ B-F6).
   - `genres.code` → NOT NULL (closes A-F2).
   - `006-seed-genres.xml` → drop default `separator=","` and `quotchar="&quot;"`
     attributes (closes A-F4).
   - `db/changelog/README.md` → new **Known Limitations & Operational Notes**
     section covering A-F3/B-F1, B-F2, B-F3, B-F4, B-F5, B-F10 + tooling
     reference + IDE-warning note.
2. **`f8dd1f4` — chore(tools): add genre dump → CSV extractor for reproducibility**
   - `scripts/parse_genres.py` (untouched, committed as-is per task scope).
   - `scripts/README.md` with usage instructions and behaviour summary.
3. **(current HEAD) — docs(plan): align Task 02 spec with implementation (272 genres, FTS weights ABC)**
   - `PLAN.md` S1: `298 записей` → `~272 жанра`.
   - `task-02-postgres-schema-liquibase-seed.md`:
     - Required Inputs: 298 → 272 + AUTO_INCREMENT clarification.
     - What to Do #3 (FTS): explicit weights `A=title, B=authors, C=keywords`.
     - What to Do #7, Expected Output, Acceptance Criteria: 298 → ~272.
   - Task folder review/triage/result files added to git.

## Diffs (schema/xml)

### `001-core-domain.xml`

```diff
-            <column name="code" type="VARCHAR(64)">
-                <constraints unique="true" uniqueConstraintName="uk_genres_code"/>
-            </column>
+            <column name="code" type="VARCHAR(64)">
+                <constraints nullable="false" unique="true" uniqueConstraintName="uk_genres_code"/>
+            </column>
```

### `005-jobs.xml`

```diff
-            <column name="book_file_id" type="BIGINT"/>
+            <column name="book_file_id" type="BIGINT">
+                <constraints nullable="false"/>
+            </column>
```

### `006-seed-genres.xml`

```diff
         <loadData file="db/changelog/seed/genres.csv"
                   tableName="genres"
-                  separator=","
-                  quotchar="&quot;"
                   encoding="UTF-8"
                   usePreparedStatements="true">
```

## README — added section structure

```
## Known Limitations & Operational Notes
  ### FTS propagation
    - Person rename (deferred A-F3/B-F1)
    - Bulk-import write amplification (deferred B-F2)
    - TRUNCATE (deferred B-F5)
  ### Join-table immutability (deferred B-F3)
  ### Deduplication (deferred B-F4)
  ### JPA mapping (deferred B-F10 — for Task 04)
  ### Tooling (scripts/parse_genres.py reference)
  ### IDE warnings (B-F11 — already documented elsewhere, re-stated)
```

## Verification

- `xmllint --noout backend/src/main/resources/db/changelog/db.changelog-master.xml backend/src/main/resources/db/changelog/changes/*.xml` → **OK** (no errors, no output).
- `wc -l backend/src/main/resources/db/changelog/seed/genres.csv` → **273** (272 + header) — unchanged.
- `./gradlew :backend:build` → **BUILD SUCCESSFUL** (5 actionable tasks: 4 executed, 1 up-to-date).
- `git status` after all commits: clean (the only untracked file is `fix-wave-task-01.md` carried over from Task 01 fix-wave, not in scope here).
- `git log --oneline`:
  ```
  <HEAD>  docs(plan): align Task 02 spec with implementation (272 genres, FTS weights ABC)
  f8dd1f4 chore(tools): add genre dump → CSV extractor for reproducibility
  6db845d fix(db): tighten Task 02 schema constraints + document FTS known limitations
  b2fae98 feat(db): Liquibase XML schema for library domain + genres seed (Task 02)
  2ef9497 docs(task-01): add execution report, review, triage notes
  cdf9769 chore: init gradle monorepo skeleton (Task 01)
  ```

## Out of scope / not done

- Did NOT add a partial unique index on `books.md5` (B-F4 — documented as deferred).
- Did NOT rewrite `trg_book_authors_fts` to STATEMENT-level (B-F2 — documented as deferred).
- Did NOT add a persons→books FTS-propagation trigger (A-F3/B-F1 — documented as deferred for Task 06).
- Did NOT touch `scripts/parse_genres.py` (per explicit task constraint).
- Did NOT wire Liquibase into the backend build (`liquibase-core` — Task 03).
- Did NOT run Postgres/Liquibase locally (no migrations have ever been applied; safe to edit existing changesets).

## Findings handled

| ID | Type | Action |
|----|------|--------|
| A-F1 ≡ B-F6 | TP-now | NOT NULL on `conversion_jobs.book_file_id` |
| A-F2 | TP-now | NOT NULL on `genres.code` |
| A-F4 | TP-now | dropped redundant `separator`/`quotchar` |
| A-F3 ≡ B-F1 | TP-deferred | documented in README (FTS propagation) |
| B-F2 | TP-deferred | documented in README (bulk-import) |
| B-F3 | TP-deferred | documented in README (join-table immutability) |
| B-F4 | TP-deferred | documented in README (dedup) |
| B-F5 | TP-deferred | documented in README (TRUNCATE) |
| B-F7 | TP-now | spec aligned: 298 → ~272 |
| B-F8 | tooling | `scripts/parse_genres.py` + `scripts/README.md` committed |
| B-F10 | TP-deferred | documented in README (JPA mapping) |
| B-F13 | TP-now | spec aligned: FTS weights A/B/C |
| B-F9, B-F11, B-F12, B-F14, B-F15, B-F16 | FP / no-action | not actioned |
