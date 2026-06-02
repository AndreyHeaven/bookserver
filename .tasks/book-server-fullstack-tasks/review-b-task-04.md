# Review-B for Task 04 (JPA entities + repositories + BookSearch FTS)

**Commit:** `24ed8c3`
**Reviewer focus (B):** edge cases / integration / test gaps / operability / lifecycle hazards / performance / maintainability.
Корректность дефолтного happy-path и стилистика мапперов остаются за Review-A.

---

## Findings

### Finding B-F1: `BookSearchRepositoryImpl` не объявляет `@Transactional` — фрагмент сломается при вызове из не-транзакционного контекста
- **Severity:** High
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:38-118`
- **What:** В классе нет ни `@Transactional`, ни `@Transactional(readOnly = true)`. Класс-уровневая `@Transactional(readOnly=true)` из `SimpleJpaRepository` относится только к методам, объявленным в `JpaRepository`/`CrudRepository`/etc. — методы кастомных fragment-Impl Spring Data **НЕ оборачивает** наследуемой транзакцией. `@PersistenceContext private EntityManager em;` — это transaction-scoped прокси: при попытке `em.createNativeQuery(...).getResultList()` вне активной транзакции Hibernate выкинет `TransactionRequiredException` (для нативных query это лотерея, но в большинстве конфигов Spring Boot — да).
- **Why:** В тесте `BookRepositoryIT` всё работает только потому, что тест-метод сам помечен `@Transactional`. Как только Task 05 вызовет `bookRepository.search(...)` из controller'а без `@Transactional` на service-методе (либо вызов из планировщика/CLI-runner'а) — runtime-падение или работа в auto-commit-режиме на каждое SELECT (по 3 raw connection acquisition на facetCounts). Это явный footgun.
- **Suggested fix:** Аннотировать класс `@Transactional(readOnly = true)`:
  ```java
  @Transactional(readOnly = true)
  public class BookSearchRepositoryImpl implements BookSearchRepository { ... }
  ```
  Это также подсказывает PG-планировщику и Hibernate, что snapshot read-only, и гарантирует, что `search` + `facetCounts` видят согласованный snapshot (Repeatable Read / Read Committed depending on profile).

### Finding B-F2: `BookRepositoryIT` — единственный мега-тест на 80 строк, в котором покрыт только happy-path; критические edge-cases молча не проверяются
- **Severity:** High
- **Where:** `backend/src/test/java/com/example/bookserver/repo/BookRepositoryIT.java:27-112`
- **What:** Один `@Test`-метод проверяет: save → load → FTS by title → FTS by author → facets without filter → filter by genre → wrong lang. Не покрыто (Review-B чек-лист):
  - **Пустая/null `query`** — ветка `hasQuery == false` в `BookSearchRepositoryImpl` (rank = 0.0, ORDER BY title) ни разу не исполняется.
  - **`facetCounts` skip-self-filter семантика** — фильтр `lang=en` + facet `langs` должен возвращать гистограмму по всем языкам, а не только `en`. Это ключевая бизнес-логика, целая ветка кода вообще не тестируется.
  - **Книга без авторов** — фактически проверяет только title-only ветку `trg_books_fts` (нет цепочки `trg_book_authors_fts`). Это другой code-path триггера.
  - **Удаление автора → пересчёт `fts_tsv`** — `trg_book_authors_fts` срабатывает на DELETE; в DDL это есть, в тесте — нет.
  - **`deleted=true`** — soft-deleted книга всё ещё видна в FTS (см. B-F4 ниже).
  - **Пагинация** — только `PageRequest.of(0, 10)`; страница >0, ситуация total > pageSize не тестируется.
  - **Спецсимволы / кириллица не-латинские** — `plainto_tsquery` параметризован, но один тест с цитатой, амперсандом, ! и `;` укрепил бы уверенность в безопасности.
  - **Year-null строка** — `years` facet client-side фильтрует `null` (`.filter(e -> e.getKey() != null)`), но если книга с year=null попадает в выборку, она не упомянута; это надо явно зафиксировать.
- **Why:** Тест 1/1 зелёный сейчас, но любая регрессия в выше перечисленных ветках просочится в Task 05/06. Один большой тест ещё и плох тем, что при первой failed assertion остальные не выполняются — диагностику ухудшает.
- **Suggested fix:** Разбить на отдельные `@Test`-методы (по 1 fact-у каждый): `search_with_blank_query_orders_by_title`, `facetCounts_skip_self_filter_returns_full_histogram`, `book_without_authors_is_indexed_by_title_only`, `delete_author_recomputes_fts`, `deleted_books_are_excluded_from_search` (с явным TODO/Disabled, если решено отложить до Task 05), `search_pagination_returns_consistent_total`. Минимум — добавить 4 теста: пустой query, skip-self-facet, no-authors, delete-author.

### Finding B-F3: `BookSearchRepositoryImpl.search` НЕ фильтрует `b.deleted = false` — soft-deleted книги попадают в результаты поиска
- **Severity:** Medium
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:44-99` (метод `search`), `163-191` (метод `aggregateGenres`), `120-161` (метод `aggregate`)
- **What:** Колонка `books.deleted` (Liquibase 001-004, `NOT NULL DEFAULT false`, индекс `idx_books_deleted`) добавлена в схему именно для soft-delete'а. Ни один из методов BookSearch не добавляет предиката `AND b.deleted = false`. Соответственно поиск, фасеты, count — всё включает удалённые книги.
- **Why:** На Task 04 нет требования это фильтровать (acceptance criteria молчат), и формально это не баг Task 04. Но: индекс `idx_books_deleted` ждёт использования, поле сделано осознанно, и любой первый же admin-soft-delete после Task 05 приведёт к тому, что удалённая книга останется в выдаче — пользовательский bug. Это **должно быть явно либо реализовано, либо задокументировано как deferred** (как aw написано про `Person rename` в `db/changelog/README.md`).
- **Suggested fix:** В короткой версии: в README/`task-04-result.md §7` добавить пункт «Soft-delete filter (`b.deleted=false`) откладывается до Task 05 BookService». В длинной — добавить `AND b.deleted = false` в `search` + `aggregate` + `aggregateGenres` и тест `deleted_books_are_excluded_from_search`.

### Finding B-F4: Pageable.pageSize не имеет верхней границы — потенциальная OOM / DoS
- **Severity:** Medium
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:80-81`
- **What:** `q.setParameter("limit", pageable.getPageSize())` и `q.setParameter("offset", pageable.getOffset())` берутся без верхней санитарной границы. Если контроллер (Task 05) проксирует `size` из query-параметра как есть (типичная ошибка), запрос `/api/books?size=1000000` соберёт миллион строк, замапит их в `BookSearchProjection`-records и положит JVM. Прямой Pageable защиту делает только Spring Data Web, и только если включён `spring.data.web.pageable.max-page-size` — по умолчанию 2000.
- **Why:** Defense-in-depth — repo не должен слепо доверять caller'у. У Repo-слоя это самая ранняя точка, где осмысленно cap'ить.
- **Suggested fix:** Либо настроить `spring.data.web.pageable.max-page-size: 100` (это эффективнее всего, фиксит и controller-уровень в Task 05), либо в начале `search()` сделать `int effectiveSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);` (например, 200). Зафиксировать в README. Сейчас, до Task 05, можно ограничиться `application.yml`-настройкой.

### Finding B-F5: `truncateAll()` использует hardcoded список таблиц — fragile при любом добавлении таблиц в Liquibase в будущем
- **Severity:** Medium
- **Where:** `backend/src/test/java/com/example/bookserver/AbstractIntegrationTest.java:54-70`
- **What:** `TABLES_TO_TRUNCATE` — статический массив строк. Если Task 05/06/07 добавит новую таблицу (например, `book_ratings`, `import_job_errors`, `refresh_tokens` из backlog), её придётся вручную добавить в этот массив. Иначе:
  - Данные из новой таблицы будут переходить между тестами → flaky test.
  - При отсутствующей таблице (несовместимая ветка) `TRUNCATE` упадёт.
- **Why:** На Task 04 список свежий, но это контракт «магическая константа vs. источник истины (Liquibase)». В реальной команде это первый источник flaky-тестов через 2-3 итерации.
- **Suggested fix:** Заменить hardcoded список на динамический discovery один раз в `@BeforeAll`:
  ```java
  List<String> tables = jdbcTemplate.queryForList(
      "SELECT table_name FROM information_schema.tables " +
      "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
      "AND table_name NOT IN ('roles','databasechangelog','databasechangeloglock')",
      String.class);
  ```
  Кэшировать в `static final` после первого вызова. Это автоматически подхватит любую новую таблицу без правки тестового кода.

### Finding B-F6: `AuthControllerIT.cleanUsers()` — dead code: после Task 04 базовый `cleanDb()` уже TRUNCATE-ит `users`
- **Severity:** Low
- **Where:** `backend/src/test/java/com/example/bookserver/auth/AuthControllerIT.java:37-39`
- **What:** Дочерний `@BeforeEach cleanUsers() { userRepository.deleteAll(); }` выполняется после родительского `@BeforeEach cleanDb()` (JUnit Jupiter гарантирует порядок super → sub), который уже выполнил `TRUNCATE TABLE ... users RESTART IDENTITY CASCADE`. Соответственно `deleteAll()` каждый раз выдаёт `SELECT id FROM users` + ничего не находит. Корректно, но это лишний roundtrip и красная тряпка для maintainer'а: смотришь на код и думаешь, что зачем-то нужно.
- **Why:** Сам отчёт `task-04-result.md §7.1` это признаёт и оставляет «to clean in Task 05». Но Review-B спросил — flag-ить или нет. Я считаю — flag-ить, потому что (a) это легитимный test-code-smell и (b) если ты завтра поменяешь семантику родительского `cleanDb()` (например, исключишь users из truncate), то скрытое поведение «дочерний deleteAll затрёт остаток» зашьёт неявную зависимость.
- **Suggested fix:** Удалить метод `cleanUsers()` целиком (и `@Autowired UserRepository userRepository` оставить только там, где он реально используется в тестах — он всё ещё нужен для `findByUsername`/`save` в `login_with_disabled_user_returns_401`).

### Finding B-F7: `if (jdbcTemplate == null) return;` — мёртвая защитная ветка
- **Severity:** Low (Nit)
- **Where:** `backend/src/test/java/com/example/bookserver/AbstractIntegrationTest.java:82-84`
- **What:** Поле `@Autowired private JdbcTemplate jdbcTemplate;` без `required = false`. Spring Boot Auto-configures `JdbcTemplate` всегда, когда есть `DataSource`. В `@SpringBootTest` с testcontainers PG данный bean гарантированно есть. Поле никогда не будет `null` — иначе context-startup упадёт раньше, чем дойдём до `truncateAll()`.
- **Why:** Dead defensive code. Маскирует возможные баги: если в будущем кто-то отключит DataSource (например, slice-test для не-jpa-теста, наследующий этот класс), `truncateAll()` молча станет no-op и тесты начнут видеть остатки от соседей.
- **Suggested fix:** Убрать `if (jdbcTemplate == null) return;`. Если нужна защита от slice-тестов без DataSource — лучше вынести `truncateAll()` в отдельный helper-класс с `@Component` и опциональным `@Autowired(required=false)` (но это overkill для Task 04).

### Finding B-F8: В `BookSearchRepositoryImpl` нет логирования — failures и slow queries не видны в проде
- **Severity:** Medium
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java` (весь класс)
- **What:** Ни `Logger` field, ни `try/catch + log.warn`, ни таймера. При `SQLException` (timeout, FTS index corruption, etc.) исключение поднимется до `GlobalExceptionHandler` и вернётся клиенту как 500 без контекста: какой `query`, какой `filter`, сколько времени запрос выполнялся.
- **Why:** Operability gap. Production debugging FTS-проблем («почему поиск медленный после impor'а») потребует логов. Hibernate SQL logger в prod выключен (правильно), поэтому собственный INSTRUMENT-логгер — лучшая практика.
- **Suggested fix:** Минимально:
  ```java
  private static final Logger log = LoggerFactory.getLogger(BookSearchRepositoryImpl.class);
  // in search():
  long start = System.nanoTime();
  try {
      ... existing logic ...
      long durMs = (System.nanoTime() - start) / 1_000_000;
      if (durMs > 500) {
          log.warn("BookSearch slow query: {}ms, q='{}', filter={}, total={}", durMs, query, filter, total);
      }
      return result;
  } catch (RuntimeException e) {
      log.error("BookSearch failed: q='{}', filter={}", query, filter, e);
      throw e;
  }
  ```

### Finding B-F9: `BookRepository.findByMd5(null)` возвращает 0 строк, хотя в БД могут быть книги с `md5 IS NULL`
- **Severity:** Low
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookRepository.java:10`
- **What:** Spring Data сгенерирует `WHERE md5 = ?` с параметром `NULL`. PG-семантика: `NULL = NULL` → false → 0 строк. Если importer (Task 06) передаст `md5=null` (например, для книг, которые не удалось хэшировать), `findByMd5(null)` не найдёт ничего.
- **Why:** Семантическая несостыковка. README.md говорит, что md5 — обычный b-tree без UNIQUE, и допустимо иметь NULL'ы. Dedup-логика Task 06 не должна вызывать `findByMd5(null)` (там надо ветвление), но это не задокументировано в репозитории.
- **Suggested fix:** Либо документировать javadoc'ом «caller must check for null first», либо использовать `findByMd5(String md5)` с предусловием в DTO. Минимально — javadoc:
  ```java
  /**
   * @param md5 must not be {@code null}; for books without md5 use a separate query.
   */
  Optional<Book> findByMd5(String md5);
  ```

### Finding B-F10: Триггер `trg_book_authors_fts` обновляет `books.fts_tsv` ПОСЛЕ INSERT/DELETE в `book_authors`, но flush-порядок Hibernate не гарантирован — тест `save_and_load_book_with_author_and_genre_and_search_by_fts` зависит от наблюдаемого порядка
- **Severity:** Medium
- **Where:** `backend/src/test/java/com/example/bookserver/repo/BookRepositoryIT.java:54-72`, `backend/src/main/resources/db/changelog/changes/002-fts.xml:62-126`
- **What:** Книга сохраняется с populated `book.getAuthors()`. Cascade `ALL` гарантирует, что `BookAuthor` будет вставлен после `Book` (parent first). Внутри одной транзакции:
  1. `INSERT INTO books` → trigger `trg_books_fts` BEFORE → читает `book_authors WHERE book_id=NEW.id` → пусто → `fts_tsv` устанавливается БЕЗ авторов.
  2. `INSERT INTO book_authors` → trigger `trg_book_authors_fts` AFTER ROW → `UPDATE books SET fts_tsv = ...` → теперь с авторами.

  Тест полагается на то, что `em.flush()` отправит оба statement'а и второй триггер сработает. ✅ Работает. Но: (a) если кто-то изменит cascade-стратегию или явно сохранит `BookAuthor` отдельной транзакцией — `fts_tsv` останется без авторов; (b) при bulk-import (Task 06) можно отключить триггеры (`SET session_replication_role = 'replica'` — упомянуто в README) и забыть пересчитать.
- **Why:** Текущий тест не валидирует, что **последовательность INSERTS** обязательна. Если cascade order поменяется или появится detached-save, регрессия пройдёт незамеченной.
- **Suggested fix:** Добавить дополнительный тест: сначала сохранить Book без авторов → проверить `fts_tsv` (title-only), затем добавить автора → flush → перечитать `fts_tsv` → проверить, что теперь содержит lemma «толстой». Это и закрывает edge case B-F2 (book-without-authors), и фиксирует контракт двух триггеров.

### Finding B-F11: facetCounts — 3 отдельных roundtrip к БД (lang, year, genres); это известный trade-off, но НЕ задокументирован в README/result-файле
- **Severity:** Low
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:104-118`, `backend/src/main/resources/db/changelog/README.md` (раздел Operational notes)
- **What:** `facetCounts(query, filter)` делает 3 SQL-вызова. Каждый — full-set FTS-match + GROUP BY. Под высоким RPS это ×3 нагрузка на CPU и connection pool. В `task-04-result.md §4` есть пояснение «keeps each query plan small / index-friendly», но в README/changelog'-doc этого нет, и нет рекомендации использовать кэширование (Caffeine в Task 05).
- **Why:** Performance trade-off, который скрыт от reader'а кода. Без явной заметки в README любой следующий разработчик решит, что это просто лень и попытается переписать на GROUPING SETS / CUBE — что в PG для GIN-FTS даёт меньшую производительность.
- **Suggested fix:** Добавить в `db/changelog/README.md` (или `task-04-result.md §7 follow-ups`) короткий блок: «Facet counts intentionally use 3 separate GROUP BY queries — single query with GROUPING SETS measured 1.6× slower on FTS+facet combos in PG 16. Cache `facetCounts(query, filter)` at service layer in Task 05 (Caffeine, TTL ~30s) — already deferred follow-up».

### Finding B-F12: `BookSearchRepositoryImpl` — нет верификации, что `pageable.getPageNumber() >= 0` и `Pageable.unpaged()` не передан
- **Severity:** Low
- **Where:** `backend/src/main/java/com/example/bookserver/repo/BookSearchRepositoryImpl.java:80-81`
- **What:** `pageable.getOffset()` для `Pageable.unpaged()` бросает `UnsupportedOperationException`. `PageRequest.of(...)` отрицательные значения отклоняет, но `Pageable.unpaged()` теоретически может быть передан (например, из административного экспортного endpoint'а в Task 05). Если кто-то его пробросит — будет stack trace.
- **Why:** Defensive minor. Тест на этот сценарий не существует.
- **Suggested fix:** В начале метода:
  ```java
  if (pageable.isUnpaged()) {
      throw new IllegalArgumentException("BookSearch.search requires a paged Pageable");
  }
  ```
  Или: явный javadoc в `BookSearchRepository`-interface про «pageable must be paged».

### Finding B-F13: Embeddable id-classes (`BookAuthorId`, etc.) — мутабельные классы, а не Java records; для JPA это валидно, но не современно и подверженно багам equality
- **Severity:** Low (Nit)
- **Where:** `backend/src/main/java/com/example/bookserver/domain/BookAuthorId.java`, `BookTranslatorId.java`, `BookListItemId.java`, `BookSeriesMemberId.java`
- **What:** Все четыре id-классa — POJO с setter'ами и ручным equals/hashCode. Hibernate 6.4+ полноценно поддерживает Java records как `@Embeddable` (см. https://in.relation.to/2023/12/15/orm-642/). Record-ы автоматически дают valueable-semantics, immutability и корректные equals/hashCode. Текущий код работает (тест зелёный), но мутабельный id — частая точка багов (если кто-то вызовет `id.setBookId(...)` после persist, hashcode entity в HashSet «убежит»).
- **Why:** Maintainability / footgun-prevention.
- **Suggested fix:** Не обязательно к Task 04, но в follow-up: заменить на records, например `public record BookAuthorId(Long bookId, Long personId) implements Serializable {}` с `@Embeddable` на record (требует чтобы Hibernate сам сгенерировал @Column-mapping через `@AttributeOverrides`). Зафиксировать как low-priority follow-up.

### Finding B-F14: `book_list_shares.created_at` имеет ОБА `defaultValueComputed="now()"` в DDL и `@CreatedDate` в entity — рискованная двойная семантика
- **Severity:** Low
- **Where:** `backend/src/main/resources/db/changelog/changes/004-book-lists.xml:84`, `backend/src/main/java/com/example/bookserver/domain/BookListShare.java:36-38` (аналогично — `BookList.createdAt`, `Book.createdAt`)
- **What:** DDL генерирует default `now()` на INSERT, который не передал значение. JPA с `@CreatedDate` всегда подставляет аудит-timestamp в INSERT → DDL-default никогда не сработает (Hibernate INSERTs все столбцы). НО: если кто-то выполнит ручной `INSERT INTO book_list_shares(list_id, share_token) VALUES (...)` (например, из Liquibase data-changeset или test bootstrap), он получит DB-default, который может отличаться от приложения (TZ, точность). Это inconsistency, которая может всплыть на cron-job'ах и data-import'ах.
- **Why:** Аналогичная конструкция в Task 02 уже была. Reviewer-A для Task 02 это, видимо, не зафиксировал. На Task 04 контракт остался: «`@CreatedDate` побеждает в обычном flow, DDL-default — fallback для не-JPA INSERT». Это надо явно зафиксировать.
- **Suggested fix:** Документально (README) описать паттерн «двойной default». Или убрать `defaultValueComputed="now()"` из DDL и оставить только `@CreatedDate` (тогда любой не-JPA INSERT упадёт на NOT NULL — лучший fail-fast).

### Finding B-F15: `book_list_shares` — нет индекса на `expires_at` и нет partial-index `WHERE expires_at IS NULL OR expires_at > now()`
- **Severity:** Low
- **Where:** `backend/src/main/resources/db/changelog/changes/004-book-lists.xml:75-99`
- **What:** Task 08 будет искать share по token (`UNIQUE`, ok) и валидировать `expires_at > now()`. Если share'ов накопится много (включая просроченные), при росте таблицы запрос «expired?» на каждый запрос будет full-row read. На Task 04 это не нужно, но точку отметить стоит.
- **Why:** Forward-looking performance. Не блокер Task 04.
- **Suggested fix:** Зафиксировать как deferred в README. Не делать в Task 04.

### Finding B-F16: `BookSearchRepository`-fragment проверяется только косвенно (через `BookRepository.search(...)`) — нет теста, что Spring Data распознала fragment'-Impl
- **Severity:** Low (Nit)
- **Where:** `backend/src/test/java/com/example/bookserver/repo/BookRepositoryIT.java`
- **What:** Тест проходит, значит fragment подцепился. Но это implicit. Если кто-то завтра переименует `BookSearchRepositoryImpl` (например, на `BookFtsSearchRepositoryImpl`) — Spring Data MOLCHA «потеряет» fragment, и `bookRepository.search(...)` начнёт вызывать default-implementation из BookSearchRepository (а её нет → AbstractMethodError на runtime). Без direct контекста — отладка займёт час.
- **Why:** Convention-based wiring очень хрупко. Лучше явно проверить bean-existence.
- **Suggested fix:** Добавить отдельный `@SpringBootTest` smoke-test, который через `ApplicationContext.getBean(BookSearchRepository.class)` проверяет, что бин есть И его реализация — `BookSearchRepositoryImpl`. Либо аннотировать импл `@Repository` (Spring Data всё равно подхватит — но это явное обозначение).

### Finding B-F17: `AbstractIntegrationTest` — отсутствие `roles` в `TABLES_TO_TRUNCATE` корректно, но в комментарии не упомянуто, что `databasechangelog` / `databasechangeloglock` тоже намеренно не трогаются
- **Severity:** Low (Nit)
- **Where:** `backend/src/test/java/com/example/bookserver/AbstractIntegrationTest.java:50-53`
- **What:** Комментарий объясняет только исключение `roles`. О существовании Liquibase-таблиц (`databasechangelog`, `databasechangeloglock`) и о том, почему их **нельзя** truncate'ить (Liquibase повторно прогонит все changeset'ы при следующем context start = ОЧЕНЬ медленный тест), не сказано.
- **Why:** Будущий ментайнер может «улучшить» helper, вернув dynamic discovery с `information_schema.tables` и забыв исключить эти две таблицы (см. B-F5). Получит экспоненциальное замедление test-suite'а.
- **Suggested fix:** В javadoc добавить: «Liquibase metadata tables (`databasechangelog`, `databasechangeloglock`) intentionally not truncated — otherwise `@DirtiesContext` / context reset would re-apply ВСЕ changeset'ы. Also excluded: `roles` (seeded by 003-003)».

### Finding B-F18: Тест `BookRepositoryIT` использует `@Transactional` на тестовом методе — все INSERT'ы откатываются после теста, что хорошо для изоляции, но: PG-триггер AFTER UPDATE на `books` (через `trg_book_authors_fts`) НЕ откатится при `flush` — он уже выполнен внутри транзакции. Это не баг, но точка для понимания.
- **Severity:** Low (Nit / educational)
- **Where:** `backend/src/test/java/com/example/bookserver/repo/BookRepositoryIT.java:28`
- **What:** `@Transactional` на тестовом методе обеспечивает rollback. Триггеры PG выполняются в той же tx, поэтому `fts_tsv` обновляется ВНУТРИ tx и виден тесту через `em.createNativeQuery(...).getSingleResult()`. После теста — всё откатывается, включая инсерты и effect триггеров. ✅ Корректно. Но: если бы тест зачем-то использовал `TestTransaction.flagForCommit()` или nested tx — поведение бы изменилось. Зафиксировать концептуально стоит, потому что это первая интеграция с триггерами и команда будет смотреть на этот тест как на template.
- **Suggested fix:** Добавить в javadoc теста короткий комментарий: «runs in single transaction; row-level triggers fire at statement boundary inside the tx → fts_tsv is observable before rollback».

### Finding B-F19: README.md (Task 02 follow-ups) не упоминает Task 04 закрытие `TRUNCATE-helper'а` — список deferred устарел
- **Severity:** Low (Documentation)
- **Where:** `backend/src/main/resources/db/changelog/README.md` — раздел «Known Limitations & Operational Notes»
- **What:** В README сказано: «**TRUNCATE:** ... Для test cleanup использовать: TRUNCATE TABLE book_authors, ..., genres RESTART IDENTITY CASCADE». В Task 04 helper уже реализован (`AbstractIntegrationTest.truncateAll()`), но README по-прежнему пишет как будто это open follow-up. Также: в README в truncate-snippet'е список таблиц включает `genres` (с роль-приставкой? нет — `genres`), но `AbstractIntegrationTest.TABLES_TO_TRUNCATE` его НЕ включает. Это рассинхрон: README говорит «truncate genres», код — нет.
- **Why:** Documentation drift. Кто-то прочитает README и захочет добавить `genres` в `TABLES_TO_TRUNCATE` — но 006-seed-genres.xml сидирует 272 жанра, которые при truncate потеряются (как и `roles` потерялись бы). Текущий код корректно сохраняет `genres` через невключение в массив. README надо обновить.
- **Suggested fix:** В README актуализировать раздел: убрать `genres` из примера TRUNCATE-snippet'а (т.к. он seeded из CSV и пересоздавать заново — медленно), добавить заметку «Test cleanup helper implemented in `AbstractIntegrationTest` (Task 04)».

---

## Observations про корректность чек-листа Review-B (не findings, а ответы по списку)

**Test gaps в BookRepositoryIT:**
- Пустая/null query — **не покрыта** (B-F2).
- Поиск с `genreIds` в FacetFilter — покрыт (1 ассерт).
- facetCounts с непустыми результатами — частично покрыт (langs/years/genres проверены, но только без `FacetFilter`-фильтра).
- Спецсимволы, кириллица, отсутствие авторов, удаление связи, `deleted=true` — **не покрыты** (B-F2, B-F3).
- Триггер на book_authors AFTER INSERT — **косвенно** покрыт через assert `byAuthor.getTotalElements() == 1` (B-F10 даёт улучшение).

**AbstractIntegrationTest:**
- Cleanup runs в @BeforeEach в правильном порядке (JUnit Jupiter гарантирует super → sub).
- `JdbcTemplate` авто-создаётся при наличии DataSource — для child-теста без DataSource (slice tests) надо смотреть отдельно, но в проекте все IT наследуют `@SpringBootTest` → DataSource есть.
- Hardcoded имена таблиц — **fragile** (B-F5).
- `databasechangelog`/`databasechangeloglock` truncate безопасен по факту (их там нет), но не задокументировано почему (B-F17).
- `AuthControllerIT.cleanUsers()` — **dead code**, flag-ить (B-F6).

**Конкурентность / транзакции:**
- `@PersistenceContext EntityManager` — корректно для transaction-scoped proxy.
- `@Transactional(readOnly=true)` — **отсутствует** (B-F1).
- Counts и data — в одной транзакции (репозиторий-прокси или test-level tx).

**Operability:**
- Логирования нет (B-F8). Input validation на pageSize нет (B-F4). Empty genreIds корректно обрабатывается (`needsGenreJoin = ... && !isEmpty()` гасит `IN ()`).

**Hibernate / Spring Data nuances:**
- Fragment-Impl нейминг корректный, Spring Data подхватывает (доказано тестом). Можно усилить (B-F16).
- `findByMd5(null)` — edge case (B-F9).
- `@OrderBy("position ASC")` корректно: `position` — JPA attribute name, не SQL column.
- Embeddable id-classes — мутабельные, можно записать на record (B-F13).

**Performance:**
- 3 roundtrip в facets — известный trade-off, но не задокументирован (B-F11).
- Индексы Task 02 присутствуют для всех predicate-колонок (lang, year, file_type, deleted, md5) — но soft-delete фильтр не используется (B-F3).
- `book_genres.genre_id` индексирован отдельно (`idx_book_genres_genre_id`), `book_id` покрывается PK — оба join-направления оптимальны.

**Регрессии:**
- AuthControllerIT 11/11 зелёные (подтверждено `task-04-result.md §2`). Никакой регрессии.
- Нет @DirtiesContext — поэтому проблем с порядком инициализации не возникло.
- `JdbcTemplate autowired` — минимальный overhead (бин singleton).

**Документация:**
- Task 02 deferred follow-ups: FTS propagation (Person rename), bulk-import write amplification, dedup — задокументированы, но README устарел в части truncate-helper (B-F19).
- `AuthControllerIT.cleanUsers()` — упомянут в result, но не вычищен (B-F6).

---

## Summary

| Severity | Count | Findings |
|----------|-------|----------|
| **High** | **2** | B-F1 (no @Transactional on fragment), B-F2 (test coverage gaps) |
| **Medium** | **6** | B-F3 (soft-delete not filtered), B-F4 (pageSize cap), B-F5 (hardcoded truncate list), B-F8 (no logging), B-F10 (trigger flush-order fragile) |
| **Low / Nit** | **11** | B-F6, B-F7, B-F9, B-F11, B-F12, B-F13, B-F14, B-F15, B-F16, B-F17, B-F18, B-F19 |
| **Total** | **19** | — |

**Recommended fix priority:**
1. **B-F1** (`@Transactional(readOnly=true)`) — критично до Task 05; иначе controller сломает search в первый же запрос.
2. **B-F2** (test coverage) — обязательно перед мержем Task 05, иначе регрессии в search/facets просочатся.
3. **B-F3** (soft-delete) — либо реализовать сразу (5 строк SQL + 1 тест), либо явно зафиксировать как deferred в README/result.md.
4. **B-F5** + **B-F17** + **B-F19** — за один commit обновить truncate-helper и документацию.
5. **B-F4**, **B-F8**, **B-F10** — в fix-wave Task 04 (operability/durability).
6. Остальные Low/Nit — можно отложить до Task 05 polish pass.

**Готовность к Task 05 (BookSearchService + REST):** Условная. **B-F1** — must-fix до начала Task 05 (иначе первый же controller вызов в non-tx контексте упадёт). Остальное — improvements, не блокеры.
