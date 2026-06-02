# Review-A — Task 04 (JPA entities + repositories + BookSearch FTS)

**Commit:** `24ed8c3`
**Focus:** correctness / behavior / architecture, JPA semantics, SQL correctness, soundness of relationships.
**Out of scope (Review-B):** style, naming, micro-formatting.
**Build/tests baseline:** `./gradlew :backend:test` → 12/12 green (`AuthControllerIT` 11 + `BookRepositoryIT` 1).
**Static analysis run:** `run_inspections` over `domain/`, `repo/`, `BookRepositoryIT`, `AbstractIntegrationTest`. 84 warnings, of which 81 are `JpaDataSourceORMInspection "Cannot resolve table/column"` — false positive (no IDE-side datasource is wired; entities are valid against Liquibase DDL and Hibernate `ddl-auto=validate` passes at boot). 1 real warning (`OneToOneWithLazy` on `Book.annotation`) is captured below.

---

## Findings

### Finding A-F1: COUNT and page rows duplicated when filtering by ≥2 matching `genreIds`
- **Severity:** Critical
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:48-99`
- **What:** `search(...)` joins `book_genres bg ON bg.book_id = b.id` and adds `bg.genre_id IN (:genreIds)` but never deduplicates. If a single book matches **more than one** of the requested `genreIds`, the JOIN multiplies that book's row in both the page query and the COUNT subquery.
  - Page rows: `SELECT b.id, b.title, b.year, b.lang, b.file_type, ... FROM books b JOIN book_genres bg ON bg.book_id = b.id WHERE bg.genre_id IN (1, 2)` — a book with genres `{1, 2}` appears **twice** in `List<BookSearchProjection>`.
  - Count: `SELECT COUNT(*) FROM (SELECT b.id FROM books b JOIN book_genres bg ON bg.book_id = b.id WHERE bg.genre_id IN (1, 2)) sub` — same book counted twice, `Page.getTotalElements()` is inflated.
- **Why it matters:** `BookRepositoryIT` only exercises a single genre/book (`genreIds = List.of(genre.getId())`), so the test happens to pass. As soon as any caller passes a multi-genre filter (the obvious "OR-of-genres" use case the facet UI implies — Task 05 will hit this immediately), the page becomes inconsistent: total != `content.size()` * pages, results contain duplicate books, and pagination skips items. This is a real bug in production, masked by the test.
- **Suggested fix:** Either (a) replace the JOIN with a semi-join: `WHERE EXISTS (SELECT 1 FROM book_genres bg WHERE bg.book_id = b.id AND bg.genre_id IN (:genreIds))` (no duplication, simpler count), or (b) keep the JOIN but add `SELECT DISTINCT b.id, b.title, …` to the page query and `SELECT COUNT(DISTINCT b.id)` for the count. Option (a) is preferred — fewer corner-cases with ORDER BY rank.

### Finding A-F2: `Pageable.getSort()` silently ignored
- **Severity:** High
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:71-81`
- **What:** `search(String, FacetFilter, Pageable)` consumes only `pageable.getPageSize()` and `pageable.getOffset()`. The `Sort` carried by `Pageable` is never read; the ORDER BY is hard-coded (`rank DESC, b.id ASC` when `q` non-blank, `title ASC, b.id ASC` otherwise).
- **Why it matters:** Task 05 will wire this fragment into a REST handler that almost certainly forwards `Pageable` from the client (Spring's `@PageableDefault` / `Pageable` argument resolver). A consumer that passes `PageRequest.of(0, 10, Sort.by("year").descending())` will get the hard-coded order back with no warning. That is a hidden contract violation of `Page<...> search(..., Pageable)`. Note that `Pageable` doc explicitly says: "if the Pageable carries Sort information, implementations MUST honor it".
- **Suggested fix:** Either (a) honor `pageable.getSort()` and translate to a whitelist of allowed columns (`title`, `year`, `lang`, `rank`) — anything outside the whitelist → 400 or fallback; (b) document explicitly in `BookSearchRepository#search` Javadoc that `Pageable.sort` is ignored and switch the parameter to `(query, filter, page, size)`; (c) accept `Pageable` but throw `IllegalArgumentException` when `pageable.getSort().isSorted()` is true to make the limitation explicit. Option (a) is correct; (b)/(c) are acceptable temporary workarounds.

### Finding A-F3: `Book.annotation` is effectively EAGER (LAZY on @OneToOne mappedBy doesn't work)
- **Severity:** Medium
- **Where:** `backend/src/main/java/com/example/bookserver/domain/Book.java:100-101`
- **What:** `@OneToOne(mappedBy = "book", cascade = ALL, orphanRemoval = true, fetch = LAZY)` on the non-owning side of `@OneToOne` is a known Hibernate limitation — without bytecode enhancement (`hibernate-enhance-maven-plugin` / Gradle equivalent), Hibernate cannot produce a lazy proxy for nullable @OneToOne and falls back to EAGER. IDE inspection `OneToOneWithLazy` flagged exactly this.
- **Why it matters:** Every `BookRepository.findById(...)`, every `findAll`, and crucially every page in the BookSearch flow (when Task 05 wires actual `Book` reads instead of the lightweight projection) will issue an extra `SELECT * FROM annotations WHERE book_id = ?` per book → classic N+1. For a page of 50 books that's 51 queries. Not blocking Task 04 (the projection doesn't touch `annotation`), but it is a latent foot-gun for the imminent /api/books endpoint.
- **Suggested fix:** Options: (a) use shared-PK owning side — drop the `Book.annotation` mappedBy, fetch annotation explicitly via `AnnotationRepository.findById(bookId)` when needed; (b) keep mappedBy but enable bytecode enhancement (`hibernate-enhance-maven-plugin` or `org.hibernate.orm.tooling.gradle.HibernatePlugin` with `lazyInitialization = true`); (c) accept EAGER and remove the misleading `fetch=LAZY`. Pick (a) or (b).

### Finding A-F4: Soft-deleted books (`books.deleted = true`) are not excluded from search / facets
- **Severity:** Medium
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:51-69, 117-145, 156-180`
- **What:** Neither `search(...)` nor any of the three `aggregate*(...)` queries adds `AND b.deleted = false`. The DDL deliberately exposes the column (`books.deleted BOOLEAN NOT NULL DEFAULT false`, indexed by `idx_books_deleted` — see `001-core-domain.xml:103-109,128-130`), so soft-deletion is clearly intended.
- **Why it matters:** Once any code path sets `deleted=true`, the book will continue to surface in the FTS endpoint and inflate facet counts. Task 04 isn't responsible for the "delete book" flow, but it ships the read path that the soft-delete depends on. Better to fix the read path now than to track down phantom results later.
- **Suggested fix:** Add `where.append(" AND b.deleted = false ");` unconditionally in `search(...)`, `aggregate(...)`, and `aggregateGenres(...)`. Zero parameter, zero risk. Consider exposing an `includeDeleted` flag in `FacetFilter` only if there's an admin use case for it — otherwise hard-code `deleted = false`.

### Finding A-F5: `BookSearchRepositoryImpl` has no `@Transactional`; native queries depend on caller-provided transaction
- **Severity:** Medium
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:38-41`
- **What:** Spring Data's `SimpleJpaRepository` is `@Transactional(readOnly=true)` at the class level (modifying methods override). Custom fragment classes do NOT inherit this — they need their own annotation. `BookSearchRepositoryImpl` injects `EntityManager` via `@PersistenceContext` but has no `@Transactional` annotation. The only reason `BookRepositoryIT` passes is that the test method itself is `@Transactional`.
- **Why it matters:** When a future controller calls `bookRepository.search(...)` without going through a `@Transactional` service (e.g. a thin controller that delegates straight to the repository), the call will execute against a no-transaction `EntityManager` — depending on Hibernate version and connection pool, this may either silently auto-commit each statement (acceptable but wasteful), or in stricter modes throw `TransactionRequiredException` for entity-aware operations. The facet computation runs three more native queries, so the connection cost is non-trivial.
- **Suggested fix:** Add `@Transactional(readOnly = true)` to `BookSearchRepositoryImpl` (class-level) — uniform with `SimpleJpaRepository`'s default. Optionally also annotate the impl `@Repository` so Spring exception translation kicks in for `PSQLException`.

### Finding A-F6: `Book.authors` / `Book.translators` use `Set<>` but the join tables carry `position` for ordering
- **Severity:** Medium
- **Where:** `backend/src/main/java/com/example/bookserver/domain/Book.java:89-95`
- **What:** `Set<BookAuthor> authors` / `Set<BookTranslator> translators` cannot preserve the `position INT NOT NULL` ordering that `book_authors` / `book_translators` carry (`001-core-domain.xml:147-152, 175-180`). The task spec explicitly demands authors ordered by position (FB2 import order, surname order on display, etc.), and `BookList.items` uses the correct pattern: `List<BookListItem>` + `@OrderBy("position ASC")` (`BookList.java:47-49`).
- **Why it matters:** As soon as Task 05 / Task 06 displays multiple co-authors, the order will be random (HashSet iteration is undefined). The trigger `book_authors_fts_trigger` aggregates with `string_agg(..., ' ')` (`002-fts.xml:42-50`) without `ORDER BY ba.position`, so FTS is unaffected — but the user-visible "Толстой Л. Н. и Достоевский Ф. М." may flip with each restart.
- **Suggested fix:** Change `Set<BookAuthor> authors` → `List<BookAuthor>` + `@OrderBy("position ASC")`; same for `translators` and `seriesMembers` (the last by `sequenceNumber`). Mirror the pattern already used in `BookList.items`.

### Finding A-F7: Years-facet round-trip `Integer → String → Integer`
- **Severity:** Low
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:105-112`
- **What:** `aggregate("b.year", …)` returns `Map<String, Long>` because the helper is typed as `Map<String, Long>`. The caller then runs `Integer.parseInt(e.getKey())` for every entry. Round-trip via `Number.toString()` (`aggregate` line 152: `row[0].toString()`) → `parseInt(...)` is fragile: if PG ever returns a non-INT type for `b.year` (it won't, but the type system shouldn't rely on it), this NPEs / NFEs at runtime.
- **Why it matters:** Not a runtime bug today; loses type-safety and obscures intent. Will bite if someone later groups by a non-numeric column via this helper.
- **Suggested fix:** Either parameterize the helper (`Map<K, Long> aggregate(Function<Object, K> keyMapper, ...)`), or duplicate the helper into `aggregateInt` / `aggregateString`. Cheapest: in the `years` builder, cast `row[0]` directly via `((Number) row[0]).intValue()` instead of `toString()` + `parseInt`.

### Finding A-F8: `aggregate(...)` concatenates the `groupBy` column into SQL
- **Severity:** Low (defense-in-depth)
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:146-149`
- **What:** `String sql = "SELECT " + groupBy + " AS g, ..."` — `groupBy` is a Java String concatenated unconditionally. At present the only call-sites pass the literals `"b.lang"` and `"b.year"`, so there is no live SQL-injection risk. But the helper is package-private with a `String` parameter; nothing in its signature stops a future call site from passing a request-derived value.
- **Why it matters:** Sharp edge that contradicts the rest of the impl (every other value uses `:named` bind parameters). It is also flagged by typical SAST tools as a tainted-flow false-positive, which adds noise.
- **Suggested fix:** Make `groupBy` an enum (`LANG`, `YEAR`) instead of `String`; the enum's `column()` getter returns the literal column. That removes the concatenation at the call site and documents the closed set.

### Finding A-F9: `BookRepositoryIT` does not pre-truncate AND does not exercise `b.deleted` / multi-genre paths
- **Severity:** Low
- **Where:** `backend/src/test/java/com/example/bookserver/repo/BookRepositoryIT.java:28-112`
- **What:** Two related test-coverage gaps that mask the bugs above:
  1. The happy-path scenario uses `List.of(genre.getId())` (one genre) — A-F1 is invisible because there's no multi-genre case.
  2. The test never sets `book.setDeleted(true)` and asserts the result is excluded — A-F4 is invisible.
- **Why it matters:** "12/12 green" looks comprehensive but is exercising one positive code path. The repository tests are the only line of defense before Task 05 starts wiring the controller, and they don't catch the actual bugs.
- **Suggested fix:** Extend the existing test (or add a sibling) to: (a) create two books, attach one to two genres, filter by both genres, assert `Page.totalElements == 1`; (b) create a deleted book that matches the FTS query, assert it is absent from `search` and `facetCounts`. Both extensions are 10–20 lines each and exercise both bugs.

### Finding A-F10: `AuthControllerIT.cleanUsers()` redundant after base-class truncate
- **Severity:** Nit
- **Where:** `backend/src/test/java/com/example/bookserver/auth/AuthControllerIT.java:37-40` (relative to base class `AbstractIntegrationTest.java:73-90`)
- **What:** `AbstractIntegrationTest.cleanDb()` now truncates `users` (in `TABLES_TO_TRUNCATE`). JUnit 5 runs the superclass `@BeforeEach` before the subclass `@BeforeEach`, so `userRepository.deleteAll()` always operates on an already-empty table. The Task-04 result file (§7.1) explicitly notes this is left to avoid Task-03 scope creep.
- **Why it matters:** No functional bug, but the duplicated cleanup is noise and obscures the new base-class contract.
- **Suggested fix:** Delete `AuthControllerIT.cleanUsers()` and its `UserRepository` autowire in a Task-05 polish pass. Not blocking.

### Finding A-F11: `rank = 0.0` returned for browse (no query) — projection field is misleading
- **Severity:** Nit
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:71-73,89`
- **What:** When `query` is blank, `rankExpr = "0.0"`. Every projection in the page has `rank() == 0.0`. The field is non-null, but the value carries no meaning.
- **Why it matters:** API consumers cannot distinguish "no query, rank meaningless" from "valid FTS hit with rank=0". A `null` would express the semantics correctly (`Double rank` is boxed already).
- **Suggested fix:** Use `NULL::real` instead of `0.0` in the SELECT when `!hasQuery`. The `toDouble(...)` helper already returns `null` for `null` inputs, so `BookSearchProjection.rank()` will be `null` in browse mode.

---

## Verification log (positive checks — no issue)

The following items were verified against DDL / spec and are correct:

1. **All table & column names match DDL.** 81 `JpaDataSourceORMInspection` warnings are IDE false positives (no datasource attached). Hibernate `ddl-auto=validate` runs at boot of every IT and the contexts succeed → mappings ARE validated against the live PG schema.
2. **`fts_tsv` mapping.** `Book.ftsTsv` and `Person.ftsTsv` are `@Transient`. Hibernate validate ignores transient fields and accepts extra DB columns. No `ddl-auto=validate` conflict. Matches PLAN.md guidance ("`Person.fts_tsv` и `Book.fts_tsv` — `@Transient`").
3. **Embedded IDs + `@MapsId`.** `BookAuthor`/`BookTranslator`/`BookSeriesMember`/`BookListItem` use `@EmbeddedId` + `@MapsId("...")` on both `@ManyToOne` legs. No duplicate FK columns are generated; the EmbeddedId fields share the JoinColumn.
4. **`Annotation` shared PK.** `@Id @Column(name="book_id")` + `@OneToOne @MapsId @JoinColumn(name="book_id")` is the canonical shared-PK pattern. Hibernate reuses the column.
5. **Enums match the CHECK constraint.** `ImportStatus` and `ConversionStatus` both have exactly `{PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED}`, matching `005-jobs.xml:46-48, 92-94`. `@Enumerated(EnumType.STRING)` is present on both fields (`ImportJob.java:34`, `ConversionJob.java:38`).
6. **FK nullability / ON DELETE.** `book_files.book_id` NOT NULL + CASCADE → `BookFile.book` is `optional=false` + parent has `cascade=ALL, orphanRemoval=true`. `book_authors`/`book_translators`/`book_genres`/`book_series_members` CASCADE — all parent collections cascade-ALL+orphanRemoval. `conversion_jobs.book_file_id` NOT NULL + CASCADE; `output_book_file_id` nullable + SET NULL — JPA mapping has correct nullability (`@JoinColumn(name="output_book_file_id")` without `nullable=false`). `persons.master_id` SET NULL + nullable — JPA `@ManyToOne` without `optional=false`. `book_lists.owner_id` CASCADE matches.
7. **Auditing.** All entities with `created_at`/`updated_at` columns are annotated `@EntityListeners(AuditingEntityListener.class)` and have `@CreatedDate`/`@LastModifiedDate` on `OffsetDateTime` fields. `JpaConfig.offsetDateTimeProvider` (Task 03) provides the correct type. `RoleEntity`, `Series`, `Genre`, `Person`, `Annotation`, embeddable-id entities — all correctly skip auditing (no `*_at` columns in DDL).
8. **No duplicate `UserEntity` / `RoleEntity`.** `domain.UserEntity` and `domain.RoleEntity` are from Task 03 and unchanged in Task 04 (per result file §1; verified by reading both files — no Task-04 modifications visible).
9. **`BookSearchProjection` as a record + manual cast.** Acceptable approach for native SQL; avoids `@SqlResultSetMapping` boilerplate and Hibernate-only `ResultTransformer`. `toLong`/`toInteger`/`toDouble` helpers correctly handle PG's `BIGINT→Long`, `INT→Integer`, `REAL→Float→Double` via the common `Number` superinterface.
10. **Fragment-pattern wiring.** `BookRepository extends JpaRepository<Book, Long>, BookSearchRepository` and `BookSearchRepositoryImpl implements BookSearchRepository` are in the same `com.example.bookserver.repo` package — Spring Data fragment discovery matches by name `{Repo}Impl` and picks it up automatically. Confirmed by green `BookRepositoryIT` (auto-wired and exercised).
11. **`JpaConfig` (Task 03) already has `@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")`.** No conflict with Task 04. Confirmed in `JpaConfig.java:15`.
12. **Skip-self-filter rule in facets.** `aggregate(b.lang, ..., skipLang=true, skipYear=false, skipGenre=false)` and `aggregate(b.year, ..., skipLang=false, skipYear=true, skipGenre=false)` correctly skip their own dimension. `aggregateGenres` skips the genre filter entirely (correct for the "skip self" rule, since the genres facet's filter is "which genres this book has", and that's what we're aggregating). All three use `COUNT(DISTINCT b.id)`, so even with the multi-row JOIN they return correct per-bucket counts.
13. **`plainto_tsquery('russian', :q)` parameterization.** No double parsing — the function is called once in the WHERE and once in the SELECT (rank), both with `:q`. Param is bound, not concatenated. No SQL injection. Russian morphology is correct per PLAN.md (russian dictionary).
14. **`ts_rank_cd(b.fts_tsv, plainto_tsquery('russian', :q))`.** Argument order is `(tsvector, tsquery)` which matches PG signature. GIN index `books_fts_idx` covers the `@@` operator (not rank — rank can't use GIN by design, but the predicate that filters rows does).
15. **`AbstractIntegrationTest.truncateAll()` table order.** Single `TRUNCATE … RESTART IDENTITY CASCADE` — order is irrelevant due to CASCADE; sequence reset is correct; `roles` deliberately excluded so the Liquibase 003-003 seed (`ROLE_USER`/`ROLE_ADMIN`) survives. Auth registration that relies on `roleRepository.findByName("ROLE_USER")` continues to work. Confirmed across 12 passing tests.
16. **Trigger timing in `BookRepositoryIT`.** `em.flush()` issues `INSERT INTO books ... ; INSERT INTO persons ... ; INSERT INTO book_authors ...` synchronously. PG triggers fire per-row at statement execution (not at commit) — so the subsequent `SELECT fts_tsv` within the same transaction sees the populated value. The `em.clear()` after flush forces a re-read from DB. Logic is correct.

---

## Summary

| Severity | Count | IDs |
|---|---|---|
| **Critical** | 1 | A-F1 |
| **High** | 1 | A-F2 |
| **Medium** | 4 | A-F3, A-F4, A-F5, A-F6 |
| **Low** | 3 | A-F7, A-F8, A-F9 |
| **Nit** | 2 | A-F10, A-F11 |
| **Total** | **11** | |

**Bottom line.** Entity mapping is solid — every column, FK, cascade, nullability, audit annotation, and enum value matches the Liquibase DDL, and `ddl-auto=validate` boots cleanly. The `@MapsId` + `@EmbeddedId` pattern is implemented correctly for all five link tables. Auditing is wired through Task 03's `JpaConfig` with the right `OffsetDateTime` provider. No duplicated `UserEntity`/`RoleEntity`.

**However, the `BookSearchRepositoryImpl` has two real correctness bugs masked by an under-strength test:** (A-F1) multi-genre filter inflates both rows and total count due to missing DISTINCT/EXISTS; (A-F2) `Pageable.getSort()` is silently dropped on the floor. These will hit production the moment Task 05 wires the controller, and `BookRepositoryIT` does not cover them. Add the suggested fixes + the two extra test cases (A-F9) before merging the FTS into the REST surface.

The Medium-severity items (A-F3 N+1 risk on `Book.annotation`, A-F4 soft-delete leak, A-F5 missing `@Transactional`, A-F6 `Set` instead of ordered `List` for authors/translators) are not blockers for Task 04 acceptance but should each be tracked as a Task-05 prerequisite — they all touch the read path the upcoming controller will use.
