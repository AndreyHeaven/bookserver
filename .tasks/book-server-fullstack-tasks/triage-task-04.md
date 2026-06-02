# Triage Task 04

**Дата:** 2026-06-02
**Базовый commit:** `24ed8c3` (Task 04 — JPA entities + repositories + BookSearch FTS)
**Источники:** `review-a-task-04.md` (11 findings) + `review-b-task-04.md` (19 findings) = 30 raw findings.

**Принцип triage:**
- **TP-now** — чинить в fix-wave-04 до начала Task 05, иначе ломает контракт `BookSearchRepository.search/facetCounts` или базовое test coverage, на котором Task 05 будет строить controller.
- **TP-deferred** — реальная проблема, но удобнее (или дешевле) делать вместе с Task 05/06/09. Должно быть зафиксировано в result.md / README как known follow-up.
- **FP** — ложноположительное, опровергнуто чтением кода.
- **Need more data** — недостаточно контекста для решения сейчас.

**Overlaps (одно и то же содержимое в обоих ревью):**
- A-F1 (multi-genre duplicates) — нет аналога в Review-B (B пропустил критический баг). Учитывается **только в A-F1**.
- A-F2 (Pageable.getSort ignored) — нет аналога в Review-B.
- A-F4 ≡ **B-F3** (soft-delete не фильтруется в search/aggregate/aggregateGenres) → объединяем в один TP-блок.
- A-F5 ≡ **B-F1** (нет `@Transactional(readOnly=true)` на fragment Impl) → один TP-блок.
- A-F9 ≡ **B-F2** ≡ B-F10 (test coverage: blank query, no-authors, deleted, multi-genre, delete-author, skip-self-filter, pagination, etc.) → один TP-блок с расширенным набором тестов.
- A-F10 ≡ **B-F6** (`AuthControllerIT.cleanUsers()` — dead code) → один TP-блок.
- A-F3 (LAZY на @OneToOne не работает) — нет аналога в Review-B.
- A-F6 (`Set<>` вместо ordered `List<>` для authors/translators/seriesMembers) — нет аналога в Review-B.

---

## Summary

- **TP-now:** **9** (A-F1, A-F2 partial-документация, A-F4∥B-F3, A-F5∥B-F1, A-F9∥B-F2∥B-F10, A-F10∥B-F6, A-F11, B-F4, B-F19+B-F17 docs)
- **TP-deferred:** **9** (A-F3, A-F6, A-F7, A-F8, B-F5, B-F8, B-F9, B-F11, B-F13)
- **FP:** **6** (B-F7, B-F12, B-F14, B-F15, B-F16, B-F18)
- **Need more data:** **0**

---

## Findings

### A-F1: COUNT and page rows duplicated when filtering by ≥2 matching `genreIds`
- **Verdict:** TP-now
- **Reasoning:** Подтверждено чтением `BookSearchRepositoryImpl.java:48-99`. SQL `JOIN book_genres bg ON bg.book_id = b.id WHERE bg.genre_id IN (:genreIds)` действительно не дедуплицирует. При фильтре `genreIds=[1,2]` и книге с обоими жанрами — она появится в page-выдаче 2 раза и в count тоже 2 раза. Тест в `BookRepositoryIT.java:99-104` использует только один `genre.getId()`, поэтому баг скрыт. Это контракт Task 04 (метод `search(... FacetFilter, Pageable)`), и Task 05 controller сразу попадёт в этот баг.
- **Fix direction:** В `BookSearchRepositoryImpl.search(...)`: заменить JOIN+IN на семи-джойн `WHERE EXISTS (SELECT 1 FROM book_genres bg WHERE bg.book_id = b.id AND bg.genre_id IN (:genreIds))`. Это убирает дублирование и в page query и в COUNT.
- **Effort:** small (~15 строк production + 1 тест в BookRepositoryIT).
- **Overlaps with:** —

### A-F2: `Pageable.getSort()` silently ignored
- **Verdict:** TP-deferred (документировать сейчас, реализовать в Task 05)
- **Reasoning:** Подтверждено: `BookSearchRepositoryImpl.java:71-81` берёт только `pageSize`+`offset`. `Sort` действительно игнорируется. НО: Task 05 (`task-05-books-rest-api-fts.md`) будет добавлять controller с whitelisted `?sort=...`, поэтому контракт правильно проектировать вместе с REST-уровнем (не на repo). В Task 04 acceptance criteria о sort-параметре не сказано. Чтобы не копить долг и не маскировать misuse, в fix-wave-04 надо: (a) явно задокументировать в javadoc `BookSearchRepository.search` что `Pageable.sort` игнорируется, (b) если приходит `pageable.getSort().isSorted()` — логировать warn (опционально). Полная реализация whitelist sort — это Task 05.
- **Fix direction:** Javadoc на `BookSearchRepository.search` — «Pageable.sort is intentionally ignored; ordering is hard-coded to (rank DESC, id ASC) when query is non-blank, else (title ASC, id ASC). Sort whitelist support is added at the REST layer in Task 05.»
- **Effort:** small (~5 строк javadoc).
- **Overlaps with:** —

### A-F3: `Book.annotation` is effectively EAGER (LAZY on @OneToOne mappedBy doesn't work)
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено `Book.java:100-101`. Это известное ограничение Hibernate без bytecode-enhancement. Сейчас `BookSearchProjection` не читает `annotation`, поэтому в Task 04 это не вызывает N+1. Включить bytecode-enhancement plugin (Hibernate Gradle plugin) — это медиум-эффорт, и нужно прогнать full test-suite для подтверждения, что других регрессий нет. Логичнее сделать в Task 05 одновременно с добавлением `/api/books/{id}` (где `annotation` действительно читается). Задокументировать в result.md/README как known issue для Task 05.
- **Fix direction:** В Task 05: либо (a) переключиться на shared-PK owning side для `Annotation` и убрать `Book.annotation`-поле, либо (b) включить `org.hibernate.orm` Gradle plugin с `enableLazyInitialization`. Сейчас — задокументировать.
- **Effort:** medium (Task 05 — bytecode enhancement + переписать пару тестов).
- **Overlaps with:** —

### A-F4 ≡ B-F3: Soft-deleted books (`books.deleted = true`) are not excluded from search / facets
- **Verdict:** TP-now
- **Reasoning:** Подтверждено: ни `search`, ни `aggregate`, ни `aggregateGenres` (`BookSearchRepositoryImpl.java:51-99,117-145,156-180`) не добавляют `AND b.deleted = false`. DDL (`001-core-domain.xml:109,130-131`) деклассирует `books.deleted` NOT NULL DEFAULT false и индексирует `idx_books_deleted` — то есть это явно intended механизм. Это контракт read-path: как только Task 05/06 первый раз сделает `book.setDeleted(true)`, удалённая книга останется в поиске и в фасетах. Изменение тривиальное (3 строки SQL + 1 тест-кейс) и должно быть сделано сейчас, пока read-path трогается.
- **Fix direction:** Добавить `where.append(" AND b.deleted = false ");` безусловно в `search(...)`, `aggregate(...)`, `aggregateGenres(...)`. Без новых параметров (admin-флаг `includeDeleted` — отдельный follow-up в Task 05, если потребуется).
- **Effort:** small (3 строки production + 1 тест).
- **Overlaps with:** B-F3 (полный дубликат).

### A-F5 ≡ B-F1: `BookSearchRepositoryImpl` has no `@Transactional`
- **Verdict:** TP-now
- **Reasoning:** Подтверждено: `BookSearchRepositoryImpl` (39-41) — обычный класс без `@Transactional`. Spring Data `SimpleJpaRepository`-level `@Transactional(readOnly=true)` не распространяется на fragment-Impl методы (это документированное поведение Spring Data). Сейчас тест зелёный только потому, что `BookRepositoryIT.save_and_load_book_with_author_and_genre_and_search_by_fts()` сам помечен `@Transactional`. Task 05 controller будет вызывать `bookRepository.search(...)` через service, и если service-методу забудут добавить `@Transactional` (типичная ошибка), либо callsite — это планировщик/CLI runner — Hibernate либо швырнёт `TransactionRequiredException`, либо будет auto-commit на каждый из 4 SELECT'ов (search + 3 facet). Это footgun, который надо устранить на уровне fragment-Impl.
- **Fix direction:** На класс `BookSearchRepositoryImpl` добавить `@Transactional(readOnly = true)` (org.springframework.transaction.annotation). Это унифицирует поведение с `SimpleJpaRepository`. Опционально — `@Repository` для exception translation `PSQLException` → `DataAccessException`.
- **Effort:** small (1 строка + import).
- **Overlaps with:** B-F1 (полный дубликат).

### A-F6: `Book.authors` / `Book.translators` / `Book.seriesMembers` — `Set<>` без ordering
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено `Book.java:89-95` — `Set<BookAuthor> authors`, `Set<BookTranslator> translators`, `Set<BookSeriesMember> seriesMembers`. Колонка `position` (book_authors/book_translators) и `sequence_number` (book_series_members) есть в DDL (`001-core-domain.xml:147-152`). Pattern уже использован корректно в `BookList.items` (`BookList.java:48-49`: `List<BookListItem>` + `@OrderBy("position ASC")`). Это **контракт entity-маппинга** — на Task 04 формально TP-now. НО: в Task 04 нет ни одного места, где порядок авторов наблюдается (FTS-тригер `string_agg` без ORDER BY работает с любым порядком, projection не читает authors, REST появится в Task 05). Поэтому решение: переключить на `List<>` + `@OrderBy` лучше сделать в Task 05 одновременно с DTO-выдачей авторов в `/api/books/{id}`. Это маленький, но точечный change, который требует параллельной правки `book.getAuthors().add(...)` в `BookRepositoryIT` (HashSet → ArrayList).
- **Fix direction:** В Task 05 (или сейчас, если будет один логичный коммит): `Set<BookAuthor> authors` → `List<BookAuthor>` + `@OrderBy("position ASC")`. То же для `translators` и `seriesMembers` (по `sequenceNumber`). Обновить `BookRepositoryIT` (1 строка). Также удалить `import java.util.HashSet` если не используется.
- **Effort:** small-medium (~20 строк + регрессионный прогон тестов).
- **Overlaps with:** —

### A-F7: Years-facet round-trip `Integer → String → Integer`
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено `BookSearchRepositoryImpl.java:105-112`. `aggregate` возвращает `Map<String,Long>`, и для years-фасета каллер делает `Integer.parseInt(e.getKey())`. Сейчас не падает (year — INT в DDL), потеря типа лишь стилистическая. Чинить можно вместе с большей переборкой helper в Task 05 (когда добавится `Caffeine`-кэш фасетов — B-F11).
- **Fix direction:** В фасет-методе для years брать `Number` напрямую: `Integer yr = ((Number) row[0]).intValue();` и собирать `Map<Integer, Long>` без перехода через String. Альтернатива — параметризовать `aggregate` через `Function<Object,K>`.
- **Effort:** small (~10 строк).
- **Overlaps with:** —

### A-F8: `aggregate(...)` concatenates the `groupBy` column into SQL
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено `BookSearchRepositoryImpl.java:146-149`. Сейчас callsite'ы передают только литералы `"b.lang"` / `"b.year"` (см. lines 105-108) — реального SQL-injection нет. Но контракт «package-private метод принимает `String`» оставляет foot-gun. Делать enum-параметр стоит вместе с A-F7 (общий cleanup helper'а в Task 05).
- **Fix direction:** Завести `enum FacetDim { LANG("b.lang"), YEAR("b.year") }` и принимать его вместо `String groupBy`. Внутри метода — `groupBy.column()`.
- **Effort:** small (~15 строк).
- **Overlaps with:** —

### A-F9 ≡ B-F2 ≡ B-F10: Test coverage gaps в `BookRepositoryIT`
- **Verdict:** TP-now (часть тестов) + TP-deferred (часть тестов)
- **Reasoning:** Подтверждено `BookRepositoryIT.java:27-112` — единственный мега-тест, покрывающий только happy-path. Базовые edge-cases, которые покрывают **именно** правки fix-wave-04 (A-F1 multi-genre, A-F4 deleted) — обязательны (иначе фикс никак не верифицируется). Дополнительные кейсы (blank query, no-authors, delete-author, skip-self-facet, multi-genre, pagination) — должны быть добавлены сейчас, пока контекст FTS-триггеров в памяти. Длинный список из B-F2 (спецсимволы, кириллица не-латинские, year-null) — TP-deferred в Task 05 (там появится BookService, легче добавлять там вместе с unit-тестами на service layer).
- **Fix direction:** Минимум для fix-wave-04 (разделить мега-тест на под-тесты или добавить отдельные `@Test`):
  1. `search_with_blank_query_orders_by_title` — проверяет ветку `hasQuery=false`.
  2. `search_with_multi_genre_filter_does_not_duplicate` — две книги, одна с двумя жанрами, фильтр по обоим → `totalElements=1` (covers A-F1).
  3. `deleted_books_are_excluded_from_search_and_facets` — book.setDeleted(true), assert page.empty && facets.langs пустой (covers A-F4).
  4. `facetCounts_skip_self_filter_returns_full_histogram` — две книги разными lang'ами, фильтр lang=ru → facet `langs` возвращает обе (covers B-F2 skip-self).
  5. `book_without_authors_is_indexed_by_title_only` — book без authors, search по title → 1 (covers B-F2 / B-F10).
  Оставить deferred (Task 05): delete-author trigger, спецсимволы, year-null bucket, pagination >0.
- **Effort:** medium (~80 строк тестового кода, 5 новых `@Test`).
- **Overlaps with:** B-F2, B-F10 (полные дубликаты; объединить).

### A-F10 ≡ B-F6: `AuthControllerIT.cleanUsers()` — dead code
- **Verdict:** TP-now (тривиальный cleanup, входит в логичную группу с B-F19/B-F17 = доводка test-инфраструктуры)
- **Reasoning:** Подтверждено `AuthControllerIT.java:37-39`. Базовый `AbstractIntegrationTest.cleanDb()` (line 73) уже TRUNCATE-ит `users`. JUnit Jupiter гарантирует super → sub @BeforeEach. `userRepository.deleteAll()` в children = always-empty-noop. Удалить вместе с unused `@Autowired UserRepository userRepository` ↦ нет, userRepository ещё нужен в `login_with_disabled_user_returns_401`. Чистый удаляемый код: метод `cleanUsers()` целиком.
- **Fix direction:** Удалить `@BeforeEach void cleanUsers() {...}` метод. `@Autowired UserRepository` оставить (используется в одном из тестов).
- **Effort:** small (~5 строк удалить).
- **Overlaps with:** B-F6 (полный дубликат).

### A-F11: `rank = 0.0` returned for browse (no query) — projection field misleading
- **Verdict:** TP-now
- **Reasoning:** Подтверждено `BookSearchRepositoryImpl.java:71-73,89` — для blank query `rankExpr = "0.0"`. API-консумер (Task 05) не сможет различить «нет query, rank бессмыслен» от «query был, rank=0». `BookSearchProjection.rank` — `Double` (boxed), поэтому `null` валидно. Семантический контракт лучше зафиксировать сейчас (входит в категорию «контракт repository», который потребляет Task 05 controller).
- **Fix direction:** В `search(...)` — `rankExpr = hasQuery ? "ts_rank_cd(...)" : "NULL::real";`. Helper `toDouble` уже возвращает `null` для `null`, ничего ломаться не должно. Добавить ассерт в новый тест `search_with_blank_query_orders_by_title`: `hit.rank()` is null.
- **Effort:** small (~3 строки + ассерт).
- **Overlaps with:** —

### B-F1 ≡ A-F5: `BookSearchRepositoryImpl` no `@Transactional`
- См. **A-F5** выше. Объединено.

### B-F2 ≡ A-F9: тест-покрытие
- См. **A-F9** выше. Объединено.

### B-F3 ≡ A-F4: soft-delete
- См. **A-F4** выше. Объединено.

### B-F4: Pageable.pageSize не имеет верхней границы — потенциальная OOM / DoS
- **Verdict:** TP-now (через config-only)
- **Reasoning:** Подтверждено `BookSearchRepositoryImpl.java:80-81` — `pageable.getPageSize()` передаётся как есть. Без cap'а каллер из Task 05 (controller с `Pageable` arg-resolver) рискует пустить запрос на миллион строк. Самый простой и универсальный фикс — config-уровень: `spring.data.web.pageable.max-page-size: 100` в `application.yml`. Это не трогает production-код и закрывает entry-point до Task 05, на Task 04 защищая repo. Альтернатива (cap внутри `search()` через `Math.min`) — тоже OK, но менее общее решение.
- **Fix direction:** Добавить в `backend/src/main/resources/application.yml`:
  ```yaml
  spring:
    data:
      web:
        pageable:
          max-page-size: 100
          default-page-size: 20
  ```
- **Effort:** small (4 строки config).
- **Overlaps with:** —

### B-F5: `truncateAll()` использует hardcoded список таблиц
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено `AbstractIntegrationTest.java:54-70`. Список из 16 таблиц — действительно fragile. Но: добавление новой таблицы — это всегда явный CR через Liquibase, и тогда же логично добавить её в `TABLES_TO_TRUNCATE`. Dynamic discovery через `information_schema.tables` — реальное улучшение, но (a) требует исключения `roles`, `databasechangelog`, `databasechangeloglock`, `genres` (B-F17, B-F19); (b) Task 05 будет добавлять минимум 1-2 таблицы и без полного rewrite этой инфраструктуры всё равно придётся обновлять список. Логичнее сделать в Task 05/06 одним коммитом, когда станет ясно, не появятся ли `seed`-таблицы (например, conversion job-statuses).
- **Fix direction:** В Task 05/06 — заменить hardcoded на dynamic discovery с явным blacklist (`roles`, `databasechangelog*`, `genres`). Кэшировать после первого вызова.
- **Effort:** small (~30 строк).
- **Overlaps with:** —

### B-F6 ≡ A-F10: `AuthControllerIT.cleanUsers()` dead code
- См. **A-F10** выше. Объединено.

### B-F7: `if (jdbcTemplate == null) return;` — мёртвая защитная ветка
- **Verdict:** FP
- **Reasoning:** Подтверждено `AbstractIntegrationTest.java:82-84`. Защита формально мёртвая, но: (a) `truncateAll()` — `protected` метод, может быть вызван в условиях, где `JdbcTemplate` не сконфигурён (slice-test, не наследующий `@SpringBootTest`). (b) Удаление этой ветки требует параллельной проверки, что ни один существующий child-класс не нарушает контракт. (c) Это не баг, это лёгкое over-engineering. На fix-wave-04 не тратим — оставляем как есть, в Task 05 при добавлении slice-тестов решим.
- **Fix direction:** —
- **Effort:** —
- **Overlaps with:** —

### B-F8: В `BookSearchRepositoryImpl` нет логирования
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено: нет `Logger` field. Operability gap реальный, но: (a) Task 04 acceptance не требует logging, (b) instrument-логи имеют смысл вместе с Caffeine-кэшем фасетов (B-F11) и metrics (Task 09/10), (c) без production deployment (Task 10) ROI логов нулевой. Задокументировать как known follow-up в result.md.
- **Fix direction:** В Task 05/09 — добавить `Logger`, slow-query warn (>500ms) с параметрами и stack-trace на failure.
- **Effort:** small.
- **Overlaps with:** —

### B-F9: `BookRepository.findByMd5(null)` возвращает 0 строк
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено `BookRepository.java:10`. Spring Data действительно сгенерирует `WHERE md5 = ?` с NULL → 0 строк (PG `NULL = NULL` is false). Это semantic-quirk Spring Data, и Task 06 (importer) ещё не написан — он сам решит, как обрабатывать `md5 IS NULL` (например, отдельный query `findFirstByMd5IsNull`). Javadoc-фикс уместен, но не блокер для Task 04. Задокументировать как note в result.md.
- **Fix direction:** В Task 06 — добавить либо отдельный method `findFirstByMd5IsNull(...)`, либо service-уровень с явным null-check'ом перед вызовом. Javadoc сейчас — опционально.
- **Effort:** small (Task 06).
- **Overlaps with:** —

### B-F10 ≡ A-F9 (часть): Триггер flush-order — тест зависит от cascade ordering
- **Verdict:** TP-now (включается в A-F9 fix-wave)
- **Reasoning:** Подтверждено: `BookRepositoryIT.java:54-72` сохраняет book с populated `authors` set'ом, потом `em.flush()` — оба INSERT в одной flush-операции. Текущее поведение работает только потому, что cascade-флайш порядок такой: parent (book) → child (book_authors). Если кто-то изменит cascade или сделает detached-save, регрессия проскочит. Это часть test coverage gap — добавить тест «save book without authors → flush → assert fts_tsv по title only; затем `book.getAuthors().add(...)` → flush → assert fts_tsv содержит автора» одновременно с другими тестами из A-F9.
- **Fix direction:** В рамках A-F9 fix-wave: новый тест `fts_tsv_recomputed_when_author_added_separately` — закрывает контракт двух триггеров.
- **Effort:** small (~25 строк теста, часть пакета тестов из A-F9).
- **Overlaps with:** A-F9 (включено).

### B-F11: facetCounts — 3 отдельных roundtrip — trade-off не задокументирован
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено `BookSearchRepositoryImpl.java:104-118`. Три отдельных SQL — осознанный design (быстрее GIN-friendly), и Task 04 result.md §4 это объясняет. Но в `db/changelog/README.md` нет note'а. Maintainability follow-up: добавить заметку плюс рекомендация Caffeine-кэш в Task 05. Не блокер для Task 04.
- **Fix direction:** В Task 05 — добавить Caffeine TTL ~30s на facetCounts. Сейчас — отметить в README/result.
- **Effort:** small (документация сейчас, кэш в Task 05).
- **Overlaps with:** —

### B-F12: нет верификации, что `pageable` не unpaged
- **Verdict:** FP
- **Reasoning:** Подтверждено `BookSearchRepositoryImpl.java:80-81`. `Pageable.unpaged()` действительно бросит `UnsupportedOperationException` при `getOffset()`. Но: (a) это поведение Spring Data API — корректная защита уже есть на уровне Spring Boot Pageable arg-resolver (никто из контроллеров не передаст unpaged по умолчанию), (b) если future admin-endpoint захочет export-all — он явно выберет либо stream, либо `Pageable.unpaged()` — тогда репо должно нормально работать (бросать IAE — это излишнее ограничение). Лишний guard не нужен. Если разработчик случайно передаст unpaged — exception будет понятный.
- **Fix direction:** —
- **Effort:** —
- **Overlaps with:** —

### B-F13: Embeddable id-classes — мутабельные POJO вместо Java records
- **Verdict:** TP-deferred
- **Reasoning:** Подтверждено: `BookAuthorId`, `BookTranslatorId`, `BookListItemId`, `BookSeriesMemberId` — все мутабельные классы с setter'ами. Hibernate 6.4+ поддерживает records as `@Embeddable`. Текущие классы корректны и работают, тест зелёный. Переписать — modernization follow-up, не блокер Task 04 и не имеет risk-смысла перед Task 05/06 (которые ID не используют напрямую). Делать в общем cleanup-проходе, не сейчас.
- **Fix direction:** В Task 09 (frontend) или отдельный cleanup-task: записать `BookAuthorId` как record.
- **Effort:** medium (~80 строк, 4 файла + регрессионные тесты).
- **Overlaps with:** —

### B-F14: `book_list_shares.created_at` — DDL `defaultValueComputed="now()"` + `@CreatedDate`
- **Verdict:** FP
- **Reasoning:** Подтверждено `004-book-lists.xml:84` + `BookListShare.java`. Двойная семантика реально присутствует, но: (a) это **уже** зафиксированный pattern Task 02 (`created_at` у всех таблиц), Liquibase changesets уже committed, менять их в fix-wave недопустимо (по правилам orchestrator). (b) Для Java-flow `@CreatedDate` всегда выигрывает (Hibernate insertable=true передаёт `OffsetDateTime.now()`). DDL-default срабатывает только для не-JPA INSERT (Liquibase data, manual SQL). Это feature, а не баг. Удалять `defaultValueComputed` из DDL = changeset modification, запрещено. Документация на этот pattern уже есть косвенно через task-02-result.
- **Fix direction:** —
- **Effort:** —
- **Overlaps with:** —

### B-F15: `book_list_shares` — нет partial-index на `expires_at`
- **Verdict:** FP (out-of-scope Task 04)
- **Reasoning:** Подтверждено: indexes в DDL `004-book-lists.xml` не содержат partial-index. Это forward-looking performance для Task 08 (`task-08-book-lists-public-share-qr.md`). На Task 04 — out of scope; реально нужен только когда `book_list_shares` накопит >10K записей. Reviewer сам говорит «не блокер Task 04».
- **Fix direction:** —
- **Effort:** —
- **Overlaps with:** —

### B-F16: `BookSearchRepository`-fragment проверяется только косвенно
- **Verdict:** FP
- **Reasoning:** Convention-based wiring (`{Name}Impl`) — стандартный Spring Data механизм. Если рефактор переименует `BookSearchRepositoryImpl` без обновления, `bookRepository.search(...)` бросит `AbstractMethodError` — но это покажется немедленно в любом IT (`BookRepositoryIT` упадёт). Добавлять отдельный `ApplicationContext.getBean(BookSearchRepository.class)`-тест — over-engineering. Реальной защиты текущий `BookRepositoryIT` достаточно (если падает search — fragment не подцепился).
- **Fix direction:** —
- **Effort:** —
- **Overlaps with:** —

### B-F17: `databasechangelog`/`databasechangeloglock` в `TABLES_TO_TRUNCATE` не упомянуты
- **Verdict:** TP-now (часть doc-bundle с B-F19)
- **Reasoning:** Подтверждено `AbstractIntegrationTest.java:50-53`. Комментарий объясняет только исключение `roles`. Liquibase-таблицы фактически безопасны (их нет в `TABLES_TO_TRUNCATE`), но без явной заметки maintainer может «улучшить» список и сломать suite. Делается за 5 строк javadoc, входит в логичную группу с B-F19 (документация test-инфраструктуры).
- **Fix direction:** Расширить javadoc у `TABLES_TO_TRUNCATE` строкой про Liquibase metadata tables и `genres` (seeded 272 жанра).
- **Effort:** small (~5 строк javadoc).
- **Overlaps with:** B-F19 (логически).

### B-F18: `@Transactional` на тестовом методе — concept-only nit
- **Verdict:** FP
- **Reasoning:** Подтверждено `BookRepositoryIT.java:28`. Reviewer сам помечает «not a bug, educational». Никакого фикса не предложено, только javadoc-комментарий. Это noise, не finding. Скипаем.
- **Fix direction:** —
- **Effort:** —
- **Overlaps with:** —

### B-F19: README.md (Task 02 follow-ups) не упоминает Task 04 — список deferred устарел
- **Verdict:** TP-now
- **Reasoning:** Подтверждено `db/changelog/README.md:177-187`. В README есть SQL-сниппет с `TRUNCATE TABLE book_authors, ..., genres RESTART IDENTITY CASCADE` и фраза «для test cleanup использовать ...». Это рассинхрон с реальным состоянием: (a) helper уже реализован в `AbstractIntegrationTest.truncateAll()`, (b) `genres` в helper'е НЕ truncate-ится (он seeded). Если кто-то прочитает README и добавит `genres` в `TABLES_TO_TRUNCATE` — 272 жанра потеряются и тесты, опирающиеся на них (когда появятся в Task 05/06), упадут. Это documentation drift — нужно актуализировать, входит в группу с A-F10/B-F6/B-F17 (cleanup test-инфраструктуры).
- **Fix direction:** Обновить `db/changelog/README.md` раздел `## Known Limitations & Operational Notes` → `### FTS propagation` → подсекция `**TRUNCATE:**`:
  - Заменить SQL-сниппет: убрать `genres` (он seeded), оставить только business-таблицы из `TABLES_TO_TRUNCATE`.
  - Добавить ссылку: «Test cleanup helper реализован в `AbstractIntegrationTest.truncateAll()` (Task 04). Список таблиц — `TABLES_TO_TRUNCATE`; `roles` и `genres` исключены, т.к. seeded из Liquibase changeset.»
- **Effort:** small (~10 строк README).
- **Overlaps with:** B-F17 (логически — один bundle про test-инфраструктуру).

---

## Recommended fix-wave-04 scope

Группы — логически сгруппированные TP-now, чтобы каждая образовывала один commit:

1. **BookSearch correctness (production read-path)** — A-F1, A-F4 ≡ B-F3, A-F11 → один commit в `BookSearchRepositoryImpl.java`:
   - A-F1: JOIN+IN → `WHERE EXISTS (SELECT 1 FROM book_genres ...)` для `search(...)`.
   - A-F4 ≡ B-F3: `AND b.deleted = false` в `search`, `aggregate`, `aggregateGenres`.
   - A-F11: rank `0.0` → `NULL::real` когда `!hasQuery`.

2. **`@Transactional` + javadoc on search contract** — A-F5 ≡ B-F1, A-F2 → один commit:
   - A-F5 ≡ B-F1: `@Transactional(readOnly = true)` на класс `BookSearchRepositoryImpl`.
   - A-F2: javadoc на `BookSearchRepository.search` — «Pageable.sort ignored; whitelist support in Task 05».

3. **Page-size cap (config-level guard)** — B-F4 → один commit:
   - `spring.data.web.pageable.max-page-size: 100`, `default-page-size: 20` в `application.yml`.

4. **Test coverage** — A-F9 ≡ B-F2 ≡ B-F10 → один commit в `BookRepositoryIT.java`:
   - Разделить мега-тест на отдельные `@Test` (опционально оставить как один + добавить новые).
   - Новые тесты: `search_with_blank_query_orders_by_title` (rank null + ORDER BY title), `search_with_multi_genre_filter_does_not_duplicate` (covers A-F1), `deleted_books_are_excluded_from_search_and_facets` (covers A-F4), `facetCounts_skip_self_filter_returns_full_histogram` (covers B-F2), `book_without_authors_is_indexed_by_title_only` + `fts_tsv_recomputed_when_author_added_separately` (covers B-F2/B-F10).

5. **Test infrastructure cleanup + documentation drift** — A-F10 ≡ B-F6, B-F17, B-F19 → один commit:
   - Удалить `AuthControllerIT.cleanUsers()` (но оставить `@Autowired UserRepository`).
   - Расширить javadoc `TABLES_TO_TRUNCATE` в `AbstractIntegrationTest`: упомянуть Liquibase metadata-tables и `genres`-seed.
   - Обновить `db/changelog/README.md` TRUNCATE-сниппет: убрать `genres`, добавить ссылку на `AbstractIntegrationTest.truncateAll()`.

**Итого в fix-wave-04:** 5 logical commits, 9 TP-now findings, ~150-200 LOC изменений (small/medium).

**Отложено в Task 05 (TP-deferred):** A-F3 (annotation N+1 / bytecode-enhance), A-F6 (`Set→List` + @OrderBy), A-F7+A-F8 (aggregate helper refactor), B-F5 (dynamic truncate discovery), B-F8 (logging in BookSearch), B-F9 (findByMd5(null) javadoc/method-split), B-F11 (Caffeine cache for facetCounts), B-F13 (records for embeddable IDs). Все они задокументировать в `task-04-result.md §7 follow-ups` и `db/changelog/README.md`.

**FP (ничего не делаем):** B-F7 (dead defensive code, mais OK), B-F12 (unpaged-guard over-engineering), B-F14 (двойной default — committed pattern Task 02), B-F15 (out-of-scope Task 04), B-F16 (smoke-test fragment — over-engineering), B-F18 (concept-only nit).
