# BookServerFull — Database schema (Liquibase)

This directory holds **all** PostgreSQL schema migrations for the project. The
schema is managed **only** through Liquibase XML changelogs — no `ddl-auto=update`,
no `ddl-auto=create-drop`. JPA must be configured with `ddl-auto=validate`.

Target DBMS: **PostgreSQL ≥ 16**.

## Layout

```
db/changelog/
├── db.changelog-master.xml      ← entry-point, includes the 6 changesets in order
├── changes/
│   ├── 001-core-domain.xml      ← books / persons / genres / series / link tables
│   ├── 002-fts.xml              ← tsvector columns + GIN indexes + trigger functions
│   ├── 003-users-auth.xml       ← users / roles / user_roles + ROLE_USER, ROLE_ADMIN seed
│   ├── 004-book-lists.xml       ← book_lists / book_list_items / book_list_shares
│   ├── 005-jobs.xml             ← import_jobs / conversion_jobs (async pipelines)
│   └── 006-seed-genres.xml      ← <loadData> from seed/genres.csv
└── seed/
    └── genres.csv               ← 24 sections + 272 genres from sql/lib.libgenrelist.sql
```

All changesets have unique IDs (`001-001`, `001-002`, …) and `author="bookserver"`.
Each one declares a `<rollback>` block where it is meaningful (DROP TABLE / DROP
INDEX / DROP FUNCTION / DELETE).

## Entities and relationships

### Core domain (changeset 001)

| Table                | Purpose                                                                  |
|----------------------|--------------------------------------------------------------------------|
| `genres`             | Hierarchical genre dictionary. Self-FK `parent_id`. Unique `code`.       |
| `persons`            | Authors **and** translators in one table. `master_id` self-FK for dedup. |
| `series`             | Series / cycles.                                                         |
| `books`              | Central entity. inpx-friendly columns: `md5`, `archive_name`, `inpx_source`. |
| `book_authors`       | M:N books ↔ persons (author role). PK = (book_id, person_id), `position`.|
| `book_translators`   | M:N books ↔ persons (translator role).                                   |
| `book_genres`        | M:N books ↔ genres.                                                      |
| `book_series_members`| M:N books ↔ series with `sequence_number`.                               |
| `book_files`         | 1:N book → files (different formats). Unique `storage_path`.             |
| `annotations`        | 1:1 to book (`book_id` is PK).                                           |

ER (simplified):

```
genres ──┐                      persons ─┐                      series
         │                                │
         │ M:N                            │ M:N (author)         │ M:N
         │                                │                      │
         ▼                                ▼                      ▼
       books  ◄── book_authors           books ◄── book_series_members
       books  ◄── book_translators
       books  ◄── book_genres
       books  ──► book_files (1:N)
       books  ──► annotations (1:1)
```

### Full-text search (changeset 002)

- `books.fts_tsv` and `persons.fts_tsv` of PostgreSQL `tsvector` type.
- GIN indexes: `books_fts_idx`, `persons_fts_idx`.
- Configuration: **`russian`** (Russian-language analyser with stemming).
- Weights for `books.fts_tsv`:
  - **A** — `title`
  - **B** — concatenated authors (last/first/middle name through `book_authors`)
  - **C** — `keywords`
- Helper function `books_fts_compute(title, keywords, authors) → tsvector`
  is `IMMUTABLE` so it can be used in expressions / generated columns later.
- Two triggers keep `books.fts_tsv` in sync:
  - `trg_books_fts` (BEFORE INSERT / UPDATE OF title, keywords) — uses
    `books_fts_trigger()` which also picks up current `book_authors` rows;
  - `trg_book_authors_fts` (AFTER INSERT / UPDATE / DELETE on `book_authors`)
    — re-computes the parent book's tsvector when authorship changes.
- `persons.fts_tsv` is filled by `trg_persons_fts` (BEFORE INSERT/UPDATE) from
  last/first/middle name concatenation.

### Auth (changeset 003)

- `users (id, username UNIQUE, email UNIQUE, password_hash, enabled, created_at)`.
- `roles (id, name UNIQUE)` pre-seeded with `ROLE_USER` and `ROLE_ADMIN`.
- `user_roles (user_id, role_id)` M:N with FKs `ON DELETE CASCADE`.

### Book lists + public share (changeset 004)

- `book_lists` — user-owned lists. FK to `users.id` (CASCADE on delete).
- `book_list_items` — ordered membership; PK = (list_id, book_id), `position`.
- `book_list_shares` — public access tokens. `share_token` is UNIQUE; `expires_at`
  nullable. A list may have multiple share rows; "single active" semantics are
  enforced by the service layer, not the schema (so we can revoke + rotate).

### Async jobs (changeset 005)

`import_jobs` — tracks ingest of FB2 / INPX / ZIP sources:

| Column            | Notes                                                            |
|-------------------|------------------------------------------------------------------|
| `importer_type`   | e.g. `INPX`, `FB2_DIR`, `ZIP`                                    |
| `source_path`     | absolute path / URI of the source                                |
| `status`          | `PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED` (CHECK)     |
| `total_count`     | items planned to import                                          |
| `processed_count` | items already processed                                          |

`conversion_jobs` — format conversion pipeline (fb2 → epub/pdf/mobi):

| Column                | Notes                                                       |
|-----------------------|-------------------------------------------------------------|
| `book_file_id`        | source file (FK → `book_files`, CASCADE)                    |
| `target_format`       | `epub`, `pdf`, `mobi`, …                                    |
| `output_book_file_id` | populated after success (FK → `book_files`, SET NULL)       |
| `status`              | `PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED` (CHECK)|

### Genres seed (changeset 006)

- `seed/genres.csv` has 296 records (header + 296 data rows = 297 lines total):
  **24 parent section rows + 272 leaf genres**.
- Extracted from `sql/lib.libgenrelist.sql` (a MariaDB dump from LibRusEc /
  Flibusta — the canonical FB2 genre dictionary).
- The source had 4 columns `(GenreId, GenreCode, GenreDesc, GenreMeta)` and **no
  explicit parent_id**. `GenreMeta` ("Фантастика", "Проза", …) is just a text
  label of the section, not a real row. `parse_genres.py` **promotes each distinct
  `GenreMeta` to a real parent genre** and links every leaf to it via `parent_id`,
  producing a genuine two-level tree. Ids are explicit so the self-FK resolves:

| CSV column     | Parent (section) row      | Leaf (genre) row                        |
|----------------|---------------------------|-----------------------------------------|
| `id`           | `1..24`                   | `25..296` (source order)                |
| `code`         | `meta_<translit-slug>`    | `GenreCode`                             |
| `parent_id`    | empty (top-level)         | id of the parent section                |
| `title`        | `GenreMeta` text          | `GenreDesc`                             |
| `meta_section` | empty (it *is* the section)| `GenreMeta` (denormalized label)       |
| `position`     | `1..24`                   | original `GenreId`                      |

- Because explicit ids are inserted into a `BIGSERIAL` column, changeset
  `006-002-reset-genres-sequence` realigns the id sequence with
  `setval(pg_get_serial_sequence('genres','id'), MAX(id))` so subsequent inserts
  (fb2/inpx importers) don't collide.

> **Why 272 leaves and not 298?** The source MariaDB table had `AUTO_INCREMENT=298`
> (next value to be allocated), but only 272 actual rows survived in the dump:
> there are gaps in `GenreId` (e.g. some legacy codes were deleted). Leaf count
> therefore is **272** (plus 24 generated parents = 296 total).

## inpx-import specific fields

The `books` table carries three columns dedicated to inpx-style imports:

- `md5`           — HEX md5 of the source file (`varchar(32)`, taken from the
  inpx index / FB2 dump). Allows deduplication.
- `archive_name`  — name of the originating zip archive (e.g.
  `fb2-001-010.zip`), so the same logical book can be re-resolved against the
  storage layer.
- `inpx_source`   — identifier of the importing batch (e.g.
  `flibusta-2024-12`). Combined with `archive_name`, gives traceability across
  multiple import campaigns.

## Operational notes

- **Schema is managed only through Liquibase.** Spring Boot must run with
  `spring.jpa.hibernate.ddl-auto=validate`. Hibernate must never issue DDL.
- **Liquibase wiring (datasource / `liquibase-core` dependency) lives in
  Task 03**; this directory only contains the migration assets.
- The PostgreSQL `russian` config (used by FTS) ships with stock PG — no
  external dictionary downloads are required, but if you want better stemming
  consider attaching `unaccent` / `pg_trgm` later in a separate changeset.

## Known Limitations & Operational Notes

### FTS propagation

- **Person rename:** trigger `trg_persons_fts` обновляет только `persons.fts_tsv`, но НЕ пересчитывает `books.fts_tsv` для книг, ссылающихся на этого автора. После переименования автора (например, объединения дубликатов в Task 06) приложение обязано явно выполнить пересчёт:
  ```sql
  UPDATE books SET fts_tsv = books_fts_compute(
      title,
      keywords,
      (SELECT string_agg(coalesce(p.last_name,'') || ' ' || coalesce(p.first_name,'') || ' ' || coalesce(p.middle_name,''), ' ')
       FROM book_authors ba JOIN persons p ON p.id = ba.person_id
       WHERE ba.book_id = books.id)
  )
  WHERE id IN (SELECT book_id FROM book_authors WHERE person_id = :renamedPersonId);
  ```
- **Bulk-import write amplification:** `trg_book_authors_fts` срабатывает FOR EACH ROW. Для книги с N авторами bulk INSERT выполняет N UPDATE на `books` + N обновлений GIN-индекса. Рекомендации для Task 06:
  - Использовать `SET session_replication_role = 'replica'` для отключения триггеров на время bulk-insert;
  - После загрузки явно пересчитать `books.fts_tsv` одним UPDATE-запросом по затронутым `book_id`-ам;
  - В будущем (отдельный follow-up) можно переписать триггер на STATEMENT-level с `REFERENCING NEW TABLE`.
- **TRUNCATE:** PostgreSQL row-level триггеры не срабатывают на `TRUNCATE`. Для test cleanup использовать:
  ```sql
  TRUNCATE TABLE book_authors, book_translators, book_genres, book_series_members,
                 annotations, book_files, book_list_items, book_list_shares,
                 book_lists, conversion_jobs, import_jobs, books,
                 persons, series, user_roles, users
  RESTART IDENTITY CASCADE;
  ```
  Частичный TRUNCATE (например, только `book_authors`) оставит `books.fts_tsv` stale — выполнить recompute UPDATE вручную.

  **Test cleanup helper:** реализован в `AbstractIntegrationTest.truncateAll()`
  (Task 04). Использует `JdbcTemplate.execute("TRUNCATE TABLE ... RESTART IDENTITY CASCADE")`.
  Список таблиц — константа `TABLES_TO_TRUNCATE`. Из truncate-набора исключены `roles`
  и `genres` (seeded из Liquibase) и Liquibase metadata-таблицы (`databasechangelog`,
  `databasechangeloglock`).

### Join-table immutability

- PK таблиц `book_authors`, `book_translators` (`book_id, person_id`) считаются неизменяемыми. Для пере-ассоциации использовать DELETE+INSERT, а не UPDATE этих колонок. Текущий триггер `trg_book_authors_fts` корректно обрабатывает INSERT/UPDATE/DELETE для пересчёта tsvector, но при UPDATE book_id оставит stale tsvector у OLD.book_id (поскольку OLD/NEW принадлежат разным книгам).

### Deduplication

- `books.md5` — обычный b-tree index, **без UNIQUE constraint**. Дедупликация при импорте (Task 06) — обязанность сервиса: `SELECT ... FOR UPDATE` по md5 + upsert логика. Если в будущем потребуется database-level guarantee, добавить partial unique index:
  ```sql
  CREATE UNIQUE INDEX books_md5_unique_active ON books(md5)
  WHERE md5 IS NOT NULL AND deleted = false;
  ```
  Это решение отложено, поскольку реальный inpx содержит дубликаты с одинаковым md5 (исторические артефакты Flibusta).

### JPA mapping (для Task 04)

- `books.year` и `book_list_items.position`, `book_authors.position`, `book_translators.position` — semi-reserved в Hibernate. В JPA-сущностях обязательно указывать `@Column(name = "year")`/`@Column(name = "position")` чтобы избежать диалект-зависимого квотирования.
- `books.fts_tsv` и `persons.fts_tsv` — `tsvector`-колонки, управляемые триггерами. В JPA пометить как `@Column(insertable = false, updatable = false, columnDefinition = "tsvector")` либо `@Transient`. `ddl-auto=validate` должен пройти.

### Tooling

- `scripts/parse_genres.py` — скрипт для генерации `seed/genres.csv` из `sql/lib.libgenrelist.sql`. См. `scripts/README.md`.

### IDE warnings

- IntelliJ может выдавать `Cannot resolve directory 'db'` для `<include file="db/changelog/changes/*.xml">` в master changelog и `<loadData file="db/changelog/seed/genres.csv">` в seed changeset. Это false-positive — Liquibase runtime разрешает classpath-relative пути корректно. Игнорировать (либо подавить через `// noinspection XmlPathReference` где это уместно).

### Auth & JWT (Task 03 follow-ups)

- **Refresh-token replay**: при rotation старый refresh-token остаётся валидным до exp. Нет jti+blacklist. Follow-up: добавить таблицу `refresh_tokens` и jti-claim в Task 09 production-readiness.
- **Disabled-user grace period**: исправлено в Task 03 fix-wave (`JwtAuthenticationFilter` + `AuthService.refresh` теперь вызывают `AccountStatusUserDetailsChecker`). Соответственно `enabled=false` отвергается немедленно для login/refresh и для следующего запроса с access-token; текущий access-token истечёт сам через TTL.
- **CORS**: origins вынесены в `app.cors.allowed-origins`. Dev-default: `http://localhost:5173,http://localhost:3000`. Prod-задание обязательно через env `APP_CORS_ALLOWED_ORIGINS`. `allowCredentials` отключён (не требуется для JWT в Authorization header).
- **OpenAPI security**: глобальный `bearerAuth`-requirement убран. Защищённые endpoints обязаны помечаться `@SecurityRequirement(name="bearerAuth")` на классе или методе. Это нужно учесть при добавлении контроллеров в Task 04+/05.
