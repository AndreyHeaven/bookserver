# Task 04 — Execution report (JPA entities + repositories + BookSearch FTS)

**Status:** ✅ Completed
**Commit:** `24ed8c3 feat(backend): JPA entities + repositories + BookSearch FTS (Task 04)`
**Build:** `./gradlew :backend:compileJava` → SUCCESS. `./gradlew :backend:test` → SUCCESS (12 tests, 0 failures).

---

## 1. Created / modified files

### Domain (entities + embeddable IDs + enums) — `backend/src/main/java/com/example/bookserver/domain/`

| File | Notes |
|---|---|
| `Book.java` | Maps `books`; `@ManyToMany` → `Genre` via `book_genres`; `@OneToMany` to `BookAuthor` / `BookTranslator` / `BookSeriesMember` / `BookFile` (cascade ALL); `@OneToOne` to `Annotation`. `fts_tsv` → `@Transient` (PG trigger owns it). Audited via `AuditingEntityListener` (`created_at` / `updated_at`). |
| `Person.java` | Authors + translators; self-FK `master_id`. `fts_tsv` → `@Transient`. |
| `Series.java` | Maps `series` (id + title). |
| `Genre.java` | Self-FK `parent_id`, unique `code`, `meta_section`, `position`, `title`. |
| `BookFile.java` | Unique `storage_path`; `@CreatedDate` for `created_at`. |
| `Annotation.java` | 1:1 with Book via shared PK (`@MapsId`). |
| `BookAuthor.java` + `BookAuthorId.java` | `@EmbeddedId` (`bookId`, `personId`), `@MapsId`, `position`. |
| `BookTranslator.java` + `BookTranslatorId.java` | analogous to `BookAuthor`. |
| `BookSeriesMember.java` + `BookSeriesMemberId.java` | `@EmbeddedId` + extra column `sequence_number`. |
| `BookList.java` | `owner` → `UserEntity` (ManyToOne); items ordered by `position`. |
| `BookListItem.java` + `BookListItemId.java` | embedded id `{listId, bookId}` + `position`. |
| `BookListShare.java` | unique `share_token`, optional `expires_at`. |
| `ImportJob.java` + `ImportStatus.java` | enum statuses via `@Enumerated(EnumType.STRING)`. |
| `ConversionJob.java` + `ConversionStatus.java` | enum statuses, FK `book_file_id` (NOT NULL) + nullable `output_book_file_id`. |

### Repositories — `backend/src/main/java/com/example/bookserver/repo/`

`BookRepository` (extends `JpaRepository` + `BookSearchRepository`, `findByMd5`), `PersonRepository`, `SeriesRepository`, `GenreRepository` (+ `findByCode`), `BookFileRepository` (+ `findByStoragePath`), `AnnotationRepository`, `BookListRepository` (+ `findByOwnerIdOrderByCreatedAtDesc`), `BookListItemRepository`, `BookListShareRepository` (+ `findByShareToken`), `ImportJobRepository`, `ConversionJobRepository`.

### Custom FTS — same package

- `BookSearchRepository.java` — interface fragment (`search(query, FacetFilter, Pageable)` + `facetCounts(query, FacetFilter)`).
- `BookSearchRepositoryImpl.java` — native SQL impl (see §4).
- `BookSearchProjection.java` — record `(id, title, year, lang, fileType, rank)`.
- `FacetFilter.java` — record `(lang, year, genreIds)` + `empty()` helper.
- `FacetCounts.java` — record `(Map<String,Long> langs, Map<Integer,Long> years, Map<Long,Long> genres)`.

### Tests

- `backend/src/test/java/com/example/bookserver/AbstractIntegrationTest.java` — extended with `JdbcTemplate`-driven `truncateAll()` via `TRUNCATE … RESTART IDENTITY CASCADE`, wired to a `@BeforeEach cleanDb()`. Excludes `roles` to preserve the Liquibase seed.
- `backend/src/test/java/com/example/bookserver/repo/BookRepositoryIT.java` — new IT.

### NOT touched (Task 03 scope, per task spec)

`UserEntity`, `RoleEntity`, `UserRepository`, `RoleRepository`, `CustomUserDetailsService`, `JpaConfig` (already has `@EnableJpaAuditing`), `application.yml` (already `ddl-auto: validate`).

---

## 2. Test summary

```
<testsuite name="com.example.bookserver.auth.AuthControllerIT" tests="11" skipped="0" failures="0" errors="0" time="2.275">
<testsuite name="com.example.bookserver.repo.BookRepositoryIT"   tests="1"  skipped="0" failures="0" errors="0" time="0.091">
```

**12 / 12 green.**

`./gradlew :backend:test` tail:
```
> Task :backend:compileTestJava
> Task :backend:processTestResources UP-TO-DATE
> Task :backend:testClasses
> Task :backend:test

BUILD SUCCESSFUL in 14s
5 actionable tasks: 2 executed, 3 up-to-date
```

Old AuthControllerIT (11 tests, including duplicate-register-409, login-disabled-401) continues to pass with the new `TRUNCATE` cleanup replacing the previous `userRepository.deleteAll()` — verified by Gradle output. With `ddl-auto: validate` Hibernate validated all entity mappings against the Liquibase-managed schema (no startup failure across both ITs).

---

## 3. `./gradlew :backend:compileJava` and `:backend:check`

```
> Task :backend:compileJava

BUILD SUCCESSFUL in 5s
1 actionable task: 1 executed
```

```
> Task :backend:compileJava UP-TO-DATE
> Task :backend:processResources UP-TO-DATE
> Task :backend:classes UP-TO-DATE
> Task :backend:compileTestJava UP-TO-DATE
> Task :backend:processTestResources UP-TO-DATE
> Task :backend:testClasses UP-TO-DATE
> Task :backend:test UP-TO-DATE
> Task :backend:check UP-TO-DATE

BUILD SUCCESSFUL in 777ms
5 actionable tasks: 5 up-to-date
```

---

## 4. `BookSearchRepositoryImpl` — implementation notes

**`search(query, filter, pageable)`** — single page query + a count query.

Generated SQL (when `q` is non-blank):

```sql
SELECT b.id, b.title, b.year, b.lang, b.file_type,
       ts_rank_cd(b.fts_tsv, plainto_tsquery('russian', :q)) AS rank
  FROM books b [JOIN book_genres bg ON bg.book_id = b.id]
 WHERE 1=1
   AND b.fts_tsv @@ plainto_tsquery('russian', :q)
   [AND b.lang = :lang]
   [AND b.year = :year]
   [AND bg.genre_id IN (:genreIds)]
 ORDER BY rank DESC, b.id ASC
 LIMIT :limit OFFSET :offset
```

When `q` is blank/null the FTS predicate is skipped, rank is hard-coded to `0.0`, and ORDER BY falls back to `title ASC`. Count uses `SELECT COUNT(*) FROM (… same predicate …) sub`.

Row mapping is manual: `em.createNativeQuery(sql).getResultList()` returns `List<Object[]>`, and we build `BookSearchProjection` records via type-safe helpers (`toLong`, `toInteger`, `toDouble`). No `@SqlResultSetMapping` is needed and no Hibernate-specific `ResultTransformer` is used — keeps the impl portable.

**`facetCounts(query, filter)`** — three small GROUP-BY queries:
- `langs`: `SELECT b.lang, COUNT(DISTINCT b.id) … GROUP BY b.lang` (skips the lang filter so the histogram doesn't collapse to the selected value).
- `years`: same pattern, skips the year filter; null years are pruned client-side.
- `genres`: `SELECT bg.genre_id, COUNT(DISTINCT b.id) FROM books b JOIN book_genres bg … GROUP BY bg.genre_id`. The genres facet always joins through `book_genres` and applies lang/year filters.

Why three separate queries rather than one with `GROUPING SETS`: keeps each query plan small / index-friendly, and the per-facet "skip self filter" rule is the standard pattern for facet UIs.

---

## 5. `AbstractIntegrationTest` — TRUNCATE helper (Task 03 F7 follow-up)

```java
@Autowired private JdbcTemplate jdbcTemplate;
private static final String[] TABLES_TO_TRUNCATE = {
        "annotations", "book_authors", "book_translators", "book_genres",
        "book_series_members", "book_list_items", "book_list_shares", "book_lists",
        "conversion_jobs", "import_jobs", "book_files", "books", "persons",
        "series", "user_roles", "users"
};

@BeforeEach void cleanDb() { truncateAll(); }

protected void truncateAll() {
    if (jdbcTemplate == null) return;
    String tables = String.join(", ", TABLES_TO_TRUNCATE);
    jdbcTemplate.execute("TRUNCATE TABLE " + tables + " RESTART IDENTITY CASCADE");
}
```

- Single `TRUNCATE … RESTART IDENTITY CASCADE` resets all sequences and avoids FK ordering issues.
- `roles` is intentionally NOT in the list — preserves the Liquibase seed (`ROLE_USER`/`ROLE_ADMIN`) required by `AuthService.register()`.
- `AuthControllerIT` still uses `userRepository.deleteAll()` inside its own `@BeforeEach cleanUsers()`. The new base-class `cleanDb()` runs first; this remains compatible (truncate already removed the user, the explicit deleteAll is a no-op). All 11 AuthControllerIT tests stay green.

---

## 6. Git commit

```
24ed8c3cae88dd6d2781d62763c6f51b7aa0e754 feat(backend): JPA entities + repositories + BookSearch FTS (Task 04)
```

`git show --stat HEAD` highlights:

- 38 files changed, 1647 insertions(+), 2 deletions(-)
- 14 domain entities + 5 embeddable-id classes + 2 enums
- 11 repository interfaces (`BookRepository`, `PersonRepository`, …, `BookListShareRepository`)
- 5 search-infra files (`BookSearchRepository[Impl]`, `BookSearchProjection`, `FacetFilter`, `FacetCounts`)
- `AbstractIntegrationTest.java` modified (+55 / -2)
- new `BookRepositoryIT.java`

Branch history:

```
24ed8c3 feat(backend): JPA entities + repositories + BookSearch FTS (Task 04)
d83513b feat(backend): Spring Boot 4 core — Security/JWT/Liquibase/OpenAPI + /api/auth/* (Task 03)
b2fae98 feat(db): Liquibase XML schema for library domain + genres seed (Task 02)
…
```

---

## 7. Known deviations / open questions

1. **`AuthControllerIT.cleanUsers()` is now redundant.** The new base-class `@BeforeEach cleanDb()` already wipes the `users` table. Left in place to avoid touching Task 03 test code beyond the strict scope of Task 04 — can be removed in a Task 05 polish pass.
2. **`BookSearchRepository.facetCounts` signature** — task file lists two overloads (`facetCounts(query)` vs `facetCounts(query, filter)`). I implemented the two-arg version per the orchestrator's directive (`Реализуй Task 04 строго по спецификации` block specifies `facetCounts(String query, FacetFilter filter)`); a one-arg overload is trivial to add when callers need it.
3. **Years facet** drops `null` year rows during the cast to `Integer`. Acceptable: the books-without-year bucket would just be "unknown" and is not asked for in any acceptance criterion.
4. **No `domain/converter/*` directory.** Task spec explicitly states converters are not needed since `@Enumerated(EnumType.STRING)` is sufficient — confirmed: status columns are `VARCHAR(16)` with a CHECK constraint, Hibernate `validate` passes.
5. **Person/`book_translators` mapping symmetry.** A Person is referenced via `BookAuthor` and `BookTranslator` separately; there is no `Person.authorOf` / `Person.translatorOf` inverse side (kept the mapping unidirectional from the link entity for simplicity — adding inverse `@OneToMany` later is non-breaking).
6. **Acceptance criterion "`/api/auth/me` after login returns username from БД"** — already satisfied by Task 03's `AuthService.me()` (it loads via `UserRepository.findByUsername`); no change needed in Task 04 per the orchestrator's explicit instruction "in-memory заглушка уже заменена".

---

## 8. Fix-wave (review remediation)

**Status:** ✅ Completed in a single commit on top of `24ed8c3`.
**Commit:** `1aa53c3 fix(backend): Task 04 review fixes — BookSearch correctness, @Transactional, tests, docs` (HEAD of branch after fix-wave-04; the result.md committed inside that commit references the previous amend sha `7a85d44` because of the obvious amend-cycle — the orchestrator can pin whichever sha is final after PLAN.md update)
**Inputs:** `review-a-task-04.md` (11 findings) + `review-b-task-04.md` (19 findings) → `triage-task-04.md` distilled them into **9 TP-now** × 5 logical groups, **9 TP-deferred**, **6 FP**.
**Test outcome:** 17/17 green (11 AuthControllerIT + 6 BookRepositoryIT). `./gradlew :backend:test` and `:backend:check` both BUILD SUCCESSFUL.

### Group 1 — BookSearch correctness (`BookSearchRepositoryImpl.java`)

| Finding | Change |
|---|---|
| **A-F1** — multi-genre filter duplicated rows | Replaced `JOIN book_genres bg … WHERE bg.genre_id IN (…)` with `WHERE EXISTS (SELECT 1 FROM book_genres bg WHERE bg.book_id = b.id AND bg.genre_id IN (:genreIds))`. Page query, COUNT subquery and the `aggregate(…)` facet helper now all use the same semi-join. |
| **A-F4 ≡ B-F3** — soft-deleted books polluted search and facets | Every WHERE clause in `search`, `aggregate` (lang/year) and `aggregateGenres` now starts with `WHERE b.deleted = false`. No new parameter (the `includeDeleted` admin flag stays a Task 05 follow-up). |
| **A-F11** — rank reported as `0.0` for blank query | `rankExpr` is now `NULL::real` when `hasQuery == false`. `BookSearchProjection.rank` is `Double` (boxed); `toDouble(null)` already returns `null`. |

### Group 2 — `@Transactional` + javadoc contract

- **A-F5 ≡ B-F1:** `@Transactional(readOnly = true)` (`org.springframework.transaction.annotation`) added on the **class** `BookSearchRepositoryImpl`, so all fragment methods inherit a read-only TX even when callers forget to annotate the service layer.
- **A-F2:** Javadoc on `BookSearchRepository.search(String, FacetFilter, Pageable)` now states explicitly that `Pageable.getSort()` is intentionally ignored — ordering is `(rank DESC, id ASC)` when the query is non-blank, `(title ASC, id ASC)` otherwise. Whitelist-based sort support is deferred to the REST layer in Task 05.

### Group 3 — Page-size cap (`application.yml`)

Added a new sub-section under existing `spring:` block (no duplicate root):

```yaml
spring:
  data:
    web:
      pageable:
        max-page-size: 100
        default-page-size: 20
```

Protects every future `Pageable` argument-resolver (Task 05 controller) against unbounded page sizes / DoS.

### Group 4 — Test coverage (+5 tests in `BookRepositoryIT`)

Existing happy-path test untouched. Five new `@Test` methods added, each `@Transactional` for cleaner txn scope and using `EntityManager.flush() + clear()` to make sure BEFORE/AFTER row triggers run and the L1 cache cannot mask stale state:

1. `search_with_blank_query_orders_by_title()` — 3 books `Бета / Альфа / Гамма`, `query=null` (and `""`) → results ordered `Альфа → Бета → Гамма`, every `hit.rank()` is `null`. **Covers A-F11.**
2. `search_with_multi_genre_filter_does_not_duplicate()` — book `b1` in two genres, `b2` in one; filter on both → `totalElements == 2`, distinct IDs == content size. **Covers A-F1.**
3. `deleted_books_are_excluded_from_search_and_facets()` — two books with one `deleted=true` → search returns only the live one; `facetCounts` returns only the live row's lang/year/genre. **Covers A-F4 ≡ B-F3.**
4. `facet_counts_apply_skip_self_filter()` — lang filter `ru` → `langs` facet still contains both `ru→1` and `en→1` (skip-self), while `years` facet does apply the lang filter (documented contract). **Covers B-F2.**
5. `book_without_authors_is_indexed_by_title_only_and_recomputed_when_author_added()` — book saved without authors has non-empty `fts_tsv` containing the russian-stemmed title (`произведен`); attaching a `BookAuthor` afterwards re-runs `trg_book_authors_fts` and the tsvector then also contains the author surname stem (`достоевск`). **Covers B-F2 / B-F10.**

Helpers `newBook / createBook / createGenre` (with `UUID`-randomized code) added at the bottom of the file.

### Group 5 — Test infrastructure cleanup + docs

- **A-F10 ≡ B-F6 — `AuthControllerIT.cleanUsers()` removed.** Base-class `cleanDb()` already TRUNCATEs `users`, so `userRepository.deleteAll()` was a no-op. `@Autowired UserRepository userRepository` stays (still used by `login_with_disabled_user_returns_401`). The now-unused `import org.junit.jupiter.api.BeforeEach` is also dropped.
- **B-F17 — `TABLES_TO_TRUNCATE` javadoc extended.** Explicitly enumerates intentionally-excluded tables: `roles` (Liquibase seed for `AuthService.register`), `genres` (272-row Liquibase seed, changeset 006-001), and Liquibase metadata tables (`databasechangelog`, `databasechangeloglock`).
- **B-F19 — `db/changelog/README.md` TRUNCATE block updated.** `genres` removed from the snippet (it's seeded), and a follow-up paragraph points readers at `AbstractIntegrationTest.truncateAll()` / `TABLES_TO_TRUNCATE` as the canonical implementation.

### TP-deferred (carried over to Task 05+)

Not fixed in this fix-wave because the work is cheaper to bundle with the upcoming controller / service / cleanup pass:

- **A-F3** — `Book.annotation` LAZY-mode is effectively EAGER without bytecode-enhancement (Task 05 `/api/books/{id}` consumer).
- **A-F6** — `Set<BookAuthor> / Set<BookTranslator> / Set<BookSeriesMember>` should become `List<>` + `@OrderBy("position ASC")` (Task 05 DTO).
- **A-F7** + **A-F8** — `aggregate(…)` helper refactor (typed `FacetDim` enum + `Integer` year-key without round-trip via `String`).
- **B-F5** — dynamic `TABLES_TO_TRUNCATE` discovery via `information_schema.tables`.
- **B-F8** — `Logger` + slow-query warn in `BookSearchRepositoryImpl` (Task 05/09).
- **B-F9** — `BookRepository.findByMd5(null)` returns 0 rows; either javadoc or `findFirstByMd5IsNull` overload (Task 06 importer).
- **B-F11** — Caffeine TTL cache around `facetCounts` (Task 05).
- **B-F13** — Embeddable ID classes as Java records (general modernization pass).

All TP-deferred items are also catalogued in `triage-task-04.md` § *Отложено в Task 05 (TP-deferred)* so they are visible to the Task 05 reviewer.

### False positives confirmed

`B-F7`, `B-F12`, `B-F14`, `B-F15`, `B-F16`, `B-F18` — see verdicts in `triage-task-04.md` for the reasoning. No production change.
