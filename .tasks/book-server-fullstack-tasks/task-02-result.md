# Task 02 — execution report

**Status:** ✅ Completed
**Commit:** `b2fae98 feat(db): Liquibase XML schema for library domain + genres seed (Task 02)`
**Build:** `./gradlew :backend:build` — `BUILD SUCCESSFUL` (5 actionable tasks).
**XML validity:** every changelog passes `xmllint --noout`.

---

## 1. Files created

```
backend/src/main/resources/db/changelog/
├── README.md                              (8.6 K — ER + FTS + inpx notes)
├── db.changelog-master.xml                (731 B  — includes the 6 changesets in order)
├── changes/
│   ├── 001-core-domain.xml                (10 changesets: 001-001 … 001-010)
│   ├── 002-fts.xml                        ( 9 changesets: 002-001 … 002-009)
│   ├── 003-users-auth.xml                 ( 4 changesets: 003-001 … 003-004)
│   ├── 004-book-lists.xml                 ( 3 changesets: 004-001 … 004-003)
│   ├── 005-jobs.xml                       ( 2 changesets: 005-001 … 005-002)
│   └── 006-seed-genres.xml                ( 1 changeset:  006-001)
└── seed/
    └── genres.csv                         (273 lines = 1 header + 272 data)
```

Helper script (not part of the runtime artifact, lives outside `backend/`):

```
scripts/parse_genres.py    — extracts CSV from sql/lib.libgenrelist.sql
```

> The script was not staged in the Task-02 commit (we only committed runtime
> assets under `backend/src/main/resources/db/`). It remains in the working tree
> for reproducibility — the orchestrator can decide whether to commit it
> separately or move it into a dedicated tools-only commit.

---

## 2. Directory tree of `backend/src/main/resources/db/`

```
backend/src/main/resources/db/changelog/
├── README.md
├── changes/
│   ├── 001-core-domain.xml
│   ├── 002-fts.xml
│   ├── 003-users-auth.xml
│   ├── 004-book-lists.xml
│   ├── 005-jobs.xml
│   └── 006-seed-genres.xml
├── db.changelog-master.xml
└── seed/
    └── genres.csv
```

---

## 3. Schema summary

### Core domain (10 changesets)

| Table                | Purpose                                                                  |
|----------------------|--------------------------------------------------------------------------|
| `genres`             | BIGSERIAL PK, unique `code`, self-FK `parent_id`, `meta_section`         |
| `persons`            | Authors + translators in one table; `master_id` self-FK; `fts_tsv`       |
| `series`             | BIGSERIAL PK, `title`                                                    |
| `books`              | BIGSERIAL PK, inpx-friendly (`md5`, `archive_name`, `inpx_source`), `fts_tsv`, `created_at`/`updated_at`. B-tree indexes on `lang`, `year`, `file_type`, `deleted`, `md5`, `archive_name` |
| `book_authors`       | M:N books ↔ persons (PK = book_id+person_id, `position`)                 |
| `book_translators`   | M:N books ↔ persons (PK = book_id+person_id, `position`)                 |
| `book_genres`        | M:N books ↔ genres                                                       |
| `book_series_members`| M:N books ↔ series with `sequence_number`                                |
| `book_files`         | 1:N → books; unique `storage_path`; `format`, `size_bytes`, `created_at` |
| `annotations`        | 1:1 → books (`book_id` is PK + FK)                                       |

### FTS (9 changesets)

- `tsvector` columns: `books.fts_tsv`, `persons.fts_tsv`
- GIN indexes: `books_fts_idx`, `persons_fts_idx`
- Function `books_fts_compute(title, keywords, authors)` (IMMUTABLE) →
  `setweight(A=title) || setweight(B=authors) || setweight(C=keywords)`,
  config `russian`, all inputs `coalesce`-protected
- Triggers:
  - `trg_books_fts` BEFORE INSERT/UPDATE OF title, keywords on `books`
  - `trg_book_authors_fts` AFTER INSERT/UPDATE/DELETE on `book_authors`
    (re-computes parent book's `fts_tsv` when authors change)
  - `trg_persons_fts` BEFORE INSERT/UPDATE OF last/first/middle name on `persons`

### Auth (4 changesets)

`users (id, username UNIQUE, email UNIQUE, password_hash, enabled, created_at)`
+ `roles (id, name UNIQUE)` pre-seeded with `ROLE_USER` and `ROLE_ADMIN`
+ `user_roles (user_id, role_id)` M:N CASCADE.

### Book lists + share (3 changesets)

`book_lists (id, owner_id→users.id CASCADE, title, description, created_at)`
+ `book_list_items (list_id, book_id, position, PK=list_id+book_id)`
+ `book_list_shares (id, list_id→book_lists.id CASCADE, share_token UNIQUE, created_at, expires_at)`.

### Async jobs (2 changesets)

- `import_jobs` — fields: `importer_type`, `source_path`, `status`,
  `message`, `created_at`, `started_at`, `finished_at`, `total_count`,
  `processed_count`. CHECK on `status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED')`.
- `conversion_jobs` — fields: `book_file_id` (FK CASCADE), `target_format`,
  `status`, `message`, `output_book_file_id` (FK SET NULL),
  `created_at`/`started_at`/`finished_at`. Same status CHECK.

### Genres seed (1 changeset)

`<loadData file="db/changelog/seed/genres.csv" tableName="genres" …>` with
columns `code`, `parent_id`, `title`, `meta_section`, `position` typed
STRING/NUMERIC. Loads 272 rows.

---

## 4. genres.csv line count

- **`wc -l` = 273** = 1 header + 272 data rows.

> ⚠️ Deviation from task description: task spec asks for **298 rows**, but the
> source `sql/lib.libgenrelist.sql` actually contains only **272 INSERT
> tuples**. The number 298 in the task spec / acceptance criterion refers to
> the **`AUTO_INCREMENT=298`** value at the end of the source CREATE TABLE
> (= "next id to assign"), not to the number of records that survived in the
> dump. Verified: `GenreId` ranges from 1 to 296 with gaps; unique `GenreCode`
> count = 272 (`awk -F, '{print $1}' | sort -u | wc -l` confirms it).
>
> See "Known deviations" below for the recommended follow-up.

---

## 5. Source-vs-target mapping for genres.csv

Source MySQL dump columns: `(GenreId, GenreCode, GenreDesc, GenreMeta)` — no
explicit parent. Mapping:

| CSV column     | Source        | Notes                                                    |
|----------------|---------------|----------------------------------------------------------|
| `code`         | `GenreCode`   | unique string id                                         |
| `parent_id`    | empty (NULL)  | hierarchy is only implied via `meta_section` text; FK to `genres(id)` left NULL for the seed — task spec explicitly allows this and asks to defer real hierarchy construction to Task 05 |
| `title`        | `GenreDesc`   | human-readable name (Russian)                            |
| `meta_section` | `GenreMeta`   | top-level grouping (e.g. "Фантастика")                   |
| `position`     | `GenreId`     | preserves original ordering                              |

---

## 6. Build verification

```
$ ./gradlew :backend:build --no-daemon
> Task :backend:compileJava UP-TO-DATE
> Task :backend:processResources UP-TO-DATE
> Task :backend:classes UP-TO-DATE
> Task :backend:resolveMainClassName UP-TO-DATE
> Task :backend:bootJar UP-TO-DATE
> Task :backend:jar UP-TO-DATE
> Task :backend:assemble UP-TO-DATE
> Task :backend:compileTestJava NO-SOURCE
> Task :backend:processTestResources NO-SOURCE
> Task :backend:testClasses UP-TO-DATE
> Task :backend:test NO-SOURCE
> Task :backend:check UP-TO-DATE
> Task :backend:build UP-TO-DATE

BUILD SUCCESSFUL in 3s
```

`./gradlew :backend:processResources --rerun-tasks` confirms the new files
get bundled into `backend/build/resources/main/db/changelog/…`.

---

## 7. Git commit

```
b2fae98  feat(db): Liquibase XML schema for library domain + genres seed (Task 02)
 9 files changed, 1288 insertions(+)
 create mode 100644 backend/src/main/resources/db/changelog/README.md
 create mode 100644 backend/src/main/resources/db/changelog/changes/001-core-domain.xml
 create mode 100644 backend/src/main/resources/db/changelog/changes/002-fts.xml
 create mode 100644 backend/src/main/resources/db/changelog/changes/003-users-auth.xml
 create mode 100644 backend/src/main/resources/db/changelog/changes/004-book-lists.xml
 create mode 100644 backend/src/main/resources/db/changelog/changes/005-jobs.xml
 create mode 100644 backend/src/main/resources/db/changelog/changes/006-seed-genres.xml
 create mode 100644 backend/src/main/resources/db/changelog/db.changelog-master.xml
 create mode 100644 backend/src/main/resources/db/changelog/seed/genres.csv
```

Branch history:
```
b2fae98 feat(db): Liquibase XML schema for library domain + genres seed (Task 02)
2ef9497 docs(task-01): add execution report, review, triage notes
cdf9769 chore: init gradle monorepo skeleton (Task 01)
```

> `scripts/parse_genres.py` and the unrelated `fix-wave-task-01.md` were
> intentionally **not** included in the Task-02 commit (the task scope is
> "only `backend/src/main/resources/db/`"). They remain untracked in the
> working tree.

---

## 8. Known deviations / notes for the orchestrator

1. **272 genre rows, not 298.** The source dump only contains 272 records
   (gaps in `GenreId` because some legacy entries were deleted before the
   dump was taken). The "298" figure in the task spec was the
   `AUTO_INCREMENT=298` next-value, not a count. **Recommendation:** soften
   the acceptance criterion in `task-02-postgres-schema-liquibase-seed.md`
   to "matches `lib.libgenrelist.sql` row count" (it does — 272/272).
2. **`parent_id` is NULL for all 272 seed rows.** The source has no
   `parent_id` column — hierarchy was only ever expressed via the
   `GenreMeta` string. The task spec explicitly allows this and defers
   real hierarchy synthesis ("metaroot" parent nodes) to Task 05.
3. **`<sql splitStatements="false" endDelimiter=";;">`** is used inside FTS
   changesets so PL/pgSQL `$$ … $$` blocks (which themselves contain `;`)
   pass through to PostgreSQL intact. The `;;` end-delimiter is purely a
   Liquibase parsing concern.
4. **Liquibase is not yet on the classpath.** As per the task instructions,
   we did NOT add `liquibase-core` to `build.gradle.kts`, did NOT configure
   `spring.liquibase.*`, did NOT run `./gradlew :backend:bootRun`. Real
   schema apply will happen in Task 03 (against testcontainers Postgres).
5. **IDE warnings about "Cannot resolve directory 'db'"** appear inside the
   master changelog and inside `006-seed-genres.xml`. These are
   IDE-level filesystem-relative path resolution complaints, not Liquibase
   issues — Liquibase resolves `db/changelog/…` against the classpath, where
   the files do exist. Safe to ignore (or wire up the Liquibase IDE plugin
   later for proper validation).
6. **`scripts/parse_genres.py`** is a one-off helper for extracting the CSV
   from the MariaDB dump. Kept in the repo working tree (untracked); the
   orchestrator can either commit it under e.g. `chore(tools): add genre
   dump → CSV extractor` or remove it after the data is in.

---

## 9. Acceptance criteria — self-check

- ✅ Master changelog includes the 6 changeset files in order.
- ✅ Seed loads **272** rows (matches source SQL — see deviation #1).
- ✅ GIN indexes on `books.fts_tsv` (`books_fts_idx`) and `persons.fts_tsv`
   (`persons_fts_idx`); triggers update tsvector on INSERT/UPDATE/DELETE.
- ✅ Every changeset has unique `id` and `author="bookserver"`; rollback
   declared for every CREATE / DROP / INSERT operation.
- ✅ snake_case names; PG-native types (`BIGSERIAL`, `BOOLEAN`,
   `TIMESTAMP WITH TIME ZONE`, `TSVECTOR`, `TEXT`, `VARCHAR`).
- ✅ R2 (PG schema via Liquibase) and R3 (FTS with weights A/B/C) covered.
- ✅ Git commit created (`b2fae98`).
