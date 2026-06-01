# Triage Task 02 — Review-A + Review-B findings

**Дата:** 2026-06-01
**Базовый commit:** `b2fae98` (Task 02 — Liquibase XML schema + genres seed)
**Источники:** `review-a-task-02.md`, `review-b-task-02.md`
**Принцип triage:**
- **TP-now** = чинить в одной fix-wave перед закрытием Task 02 (ломает Task 03 при подключении Liquibase / Hibernate `validate`, или ломает Task 04 при маппинге JPA, или меняет acceptance criterion).
- **TP-deferred** = реальная проблема, но имеет смысл решать в Task 04/05/06 с большим контекстом; в Task 02 фиксируется как known limitation в `backend/src/main/resources/db/changelog/README.md`.
- **FP / nit** = out-of-scope или несостоятельная (cosmetic / уже задокументированная / positive verification).

**Дубликаты:**
- A-F3 ≡ B-F1 (persons → books FTS propagation).
- A-F1 ≡ B-F6 (`conversion_jobs.book_file_id` NOT NULL).

---

## Сводная таблица

| ID | Severity (review) | Triage | Action |
|----|-------------------|--------|--------|
| A-F1 ≡ B-F6 | minor | **TP-now** | NOT NULL на `conversion_jobs.book_file_id` |
| A-F2 | minor | **TP-now** | NOT NULL на `genres.code` |
| A-F3 ≡ B-F1 | minor / major | **TP-deferred** | Документировать как known limitation в `db/changelog/README.md`; решать в Task 06 одновременно с B-F2 |
| A-F4 | nit | **TP-now** (easy win) | Убрать redundant `separator=","`/`quotchar="&quot;"` в `<loadData>` |
| B-F2 | major | **TP-deferred** | Документировать write-amplification + рекомендуемый паттерн для importer (Task 06) |
| B-F3 | minor | **TP-deferred** | Документировать immutability `book_id`/`person_id` в `book_authors` |
| B-F4 | minor | **TP-deferred** | Документировать политику dedup; partial unique index — решение Task 06 |
| B-F5 | minor | **TP-deferred** | Документировать TRUNCATE-ограничение FTS-триггеров |
| B-F7 | nit | **TP-now** | Скорректировать `298 → 272` в `task-02-...md` и в `PLAN.md` (S1) |
| B-F8 | minor | **TP-now** | Закоммитить `scripts/parse_genres.py` (+ `scripts/README.md`) отдельным `chore(tools)` |
| B-F9 | nit | **FP** | Косметика, не блокер; rollback через `dropTable` корректен |
| B-F10 | nit | **TP-deferred** | Документировать `year`/`position` как note для Task 04 (JPA mapping) |
| B-F11 | nit | **FP** | Уже задокументировано в `task-02-result.md` (note #5) и в Review-A (N1) |
| B-F12 | nit | **FP** | Positive verification: `users.email` nullable — корректно для Task 03 |
| B-F13 | nit | **TP-now** | Скорректировать spec: формулу `A=title, B=authors, C=keywords` зафиксировать в `task-02-...md` (реализация — better-than-spec, принимаем её) |

---

## Per-finding triage

### A-F1 ≡ B-F6 — `conversion_jobs.book_file_id` NOT NULL

```
[A-F1 ≡ B-F6]: TP-now
  Обоснование: nullable FK с ON DELETE CASCADE — семантически противоречиво
    (CASCADE подразумевает, что родитель всегда существует). В Task 04 entity
    скорее всего получит `@JoinColumn(nullable=false)` — будет mismatch
    schema↔entity. Лучше зафиксировать инвариант сейчас, до того как JPA-маппинг
    зацементируется. Влияет также на Hibernate logical inferences для bulk-fetch.
  Fix direction:
    backend/src/main/resources/db/changelog/changes/005-jobs.xml:66
    <column name="book_file_id" type="BIGINT">
        <constraints nullable="false"/>
    </column>
    output_book_file_id оставить nullable (SET NULL — корректный сценарий).
```

### A-F2 — `genres.code` NOT NULL

```
[A-F2]: TP-now
  Обоснование: UNIQUE без NOT NULL допускает множественные NULL в справочнике;
    исходный MariaDB-источник имеет NOT NULL DEFAULT ''; все 272 строки seed —
    непустые. Безопасный фикс (никакие существующие данные не нарушают
    invariant). Лучше зафиксировать сейчас, пока спека жанров не разъехалась
    с реальной DDL.
  Fix direction:
    backend/src/main/resources/db/changelog/changes/001-core-domain.xml:18-20
    <column name="code" type="VARCHAR(64)">
        <constraints nullable="false" unique="true"
                     uniqueConstraintName="uk_genres_code"/>
    </column>
```

### A-F3 ≡ B-F1 — persons → books FTS propagation

```
[A-F3 ≡ B-F1]: TP-deferred
  Обоснование: реальный correctness gap (UPDATE persons.last_name оставит
    stale tsvector у всех связанных книг). НЕ ломает Task 03 (validate),
    НЕ ломает Task 04 (entities). Влияет на корректность FTS-поиска (Task 05)
    и на массовый upsert авторов в Task 06. Решение тесно связано с B-F2
    (write amplification): простой row-level trigger создаст квадратичный
    cost при normalization-passах. Правильная реализация — либо
    statement-level trigger с REFERENCING NEW TABLE (PG ≥ 10), либо явный
    `recompute fts_tsv en masse` в importer-е после batch person-rename.
    Принимать решение разумно в Task 06 с реальным контекстом.
  Document direction:
    backend/src/main/resources/db/changelog/README.md (новый раздел
    "Known FTS limitations"):
    1. "Renaming a person (UPDATE persons.last_name / first_name / middle_name)
        does NOT propagate into books.fts_tsv. Callers performing bulk author
        normalization must invoke a recompute query:
        UPDATE books b SET fts_tsv = books_fts_compute(b.title, b.keywords,
            (SELECT string_agg(...) FROM book_authors ba JOIN persons p ...))
        WHERE b.id IN (SELECT book_id FROM book_authors WHERE person_id IN (...));
       Trigger-based propagation deferred to Task 06 (см. B-F2)."
```

### A-F4 — redundant `separator` / `quotchar` в `<loadData>`

```
[A-F4]: TP-now (cosmetic, easy win)
  Обоснование: pure nit, но фиксится в одной строке и убирает IDE warning
    `XmlDefaultAttributeValue`. Безопасно (значения = defaults). Делается
    в одной fix-wave с другими TP-now правками; никакого риска регрессии.
  Fix direction:
    backend/src/main/resources/db/changelog/changes/006-seed-genres.xml:19-20
    Убрать атрибуты separator="," и quotchar="&quot;" (оставить только
    file, tableName, encoding, usePreparedStatements + columns).
```

### B-F2 — write amplification на bulk INSERT в `book_authors`

```
[B-F2]: TP-deferred
  Обоснование: реальный performance issue (FOR EACH ROW на каждого автора
    → N UPDATE на одну строку books + N GIN-index updates). НЕ ломает
    Task 03/04 — миграции применяются, validate проходит. Боль начинается
    в Task 06 (массовый импорт через InpxZipImporter). Хирургическое
    решение — statement-level trigger с transition tables — требует
    переписывания PL/pgSQL функции и тесно связано с A-F3/B-F1.
    Обе проблемы лучше решать вместе и в контексте реального импорта.
  Document direction:
    backend/src/main/resources/db/changelog/README.md:
    "Bulk-import note: trg_book_authors_fts is a FOR EACH ROW trigger →
     writes amplification on bulk inserts. Recommended pattern for
     importers (Task 06):
       1. SET session_replication_role = 'replica';  -- disable triggers
       2. bulk INSERT INTO books, book_authors
       3. SET session_replication_role = 'origin';
       4. UPDATE books SET fts_tsv = books_fts_compute(...)
            WHERE id IN (... affected book ids ...);
     A future migration may rewrite the trigger as statement-level via
     REFERENCING NEW TABLE AS new_rows."
```

### B-F3 — UPDATE `book_id` в `book_authors` оставит stale OLD.book_id

```
[B-F3]: TP-deferred
  Обоснование: edge case (импортеры делают DELETE+INSERT, не UPDATE
    book_id). Реальный downstream-риск минимален. Документирование
    в README достаточно. Фактический фикс — пара строк в
    book_authors_fts_trigger — можно сделать заодно с B-F2 рерайтом.
  Document direction:
    backend/src/main/resources/db/changelog/README.md (в разделе
    "Known FTS limitations"):
    "book_authors.(book_id, person_id) are treated as IMMUTABLE — to
     re-associate an author with a different book, perform DELETE + INSERT.
     A direct UPDATE will leave the OLD.book_id row's tsvector stale."
```

### B-F4 — `books.md5` без UNIQUE

```
[B-F4]: TP-deferred
  Обоснование: architectural decision. Liquibase schema корректно
    создаёт b-tree (не unique) индекс — это даёт гибкость (несколько
    soft-deleted версий с одним md5). Дедупликация лежит на importer-е
    (Task 06). Если в Task 06 окажется, что service-level dedup
    подвержен race-condition'ам, можно добавить partial unique index
    отдельным changeset'ом 007-*.xml (`UNIQUE (md5) WHERE md5 IS NOT
    NULL AND deleted = false`). Сейчас Task 02 ничего не ломает.
  Document direction:
    backend/src/main/resources/db/changelog/README.md:
    "books.md5 has a non-unique b-tree index. Deduplication is the
     responsibility of the import service (Task 06). If race-conditions
     between concurrent imports become a problem, add a partial unique
     index:
       CREATE UNIQUE INDEX uk_books_md5 ON books(md5)
       WHERE md5 IS NOT NULL AND deleted = false;
     in a follow-up Task 06 changeset."
```

### B-F5 — TRUNCATE не вызывает FTS-триггеры

```
[B-F5]: TP-deferred
  Обоснование: known PG limitation; в test-cleanup обычно используется
    TRUNCATE … CASCADE на все таблицы, FTS-данные исчезают вместе с
    rows. Реальный риск — только если кто-то делает выборочный
    TRUNCATE book_authors, оставляя books. Документация в README
    достаточна; нет смысла усложнять триггеры под edge case.
  Document direction:
    backend/src/main/resources/db/changelog/README.md (раздел "Test cleanup
    notes"):
    "Row-level triggers do NOT fire on TRUNCATE. For test isolation use:
       TRUNCATE persons, books, book_authors RESTART IDENTITY CASCADE;
     This wipes fts_tsv together with rows. Partial TRUNCATE (only
     book_authors, leaving books) will leave books.fts_tsv stale —
     in such case run the explicit recompute UPDATE listed in
     'Known FTS limitations'."
```

### B-F7 — 298 vs 272 жанров (acceptance criterion)

```
[B-F7]: TP-now
  Обоснование: acceptance criterion в task-02 сейчас формулирован как
    "ровно 298 строк", но фактический источник `sql/lib.libgenrelist.sql`
    имеет 272 INSERT-кортежа (`AUTO_INCREMENT=298` — это next-value).
    Code-agent корректно реализовал seed = 272 строки; spec надо
    поправить, чтобы quality loop не воспринимал это как баг.
  Fix direction:
    1. .tasks/book-server-fullstack-tasks/task-02-postgres-schema-liquibase-seed.md:
       - Required Inputs:    "298 жанров" → "272 жанра (272 INSERT row из
                              298 next-id значения AUTO_INCREMENT)"
       - Expected Output:    "В genres появляется 298 строк" → "В genres
                              появляется 272 строки (соответствует кол-ву
                              INSERT-кортежей в sql/lib.libgenrelist.sql)"
       - Acceptance Criteria: "ровно 298" → "ровно 272 (matches row count
                               of sql/lib.libgenrelist.sql)"
    2. .tasks/book-server-fullstack-tasks/PLAN.md:
       - S1: "298 записей" → "272 записи"
```

### B-F8 — `scripts/parse_genres.py` untracked

```
[B-F8]: TP-now
  Обоснование: воспроизводимость seed-pipeline. CSV (272 строки) был
    сгенерирован этим скриптом из исходного MySQL-дампа; без скрипта
    регенерация (например, при обновлении дампа) потребует переписывания
    парсера. Низкий риск, простой фикс — отдельный `chore(tools)` коммит.
  Fix direction:
    1. Создать scripts/README.md с инструкцией запуска:
         python3 scripts/parse_genres.py \
             sql/lib.libgenrelist.sql \
             backend/src/main/resources/db/changelog/seed/genres.csv
    2. git add scripts/parse_genres.py scripts/README.md
    3. git commit -m "chore(tools): add genre dump → CSV extractor used in Task 02"
```

### B-F9 — CHECK constraints через `<sql>` без явного rollback

```
[B-F9]: FP
  Обоснование: rollback через <dropTable> каскадно удаляет constraint —
    Liquibase корректно отрабатывает откат. Переписывание на
    <addCheckConstraint> — чисто идиоматика, не баг. Не блокирует
    Task 03/04. Делать в фикс-вейве не нужно.
```

### B-F10 — `year` / `position` semi-reserved в Hibernate

```
[B-F10]: TP-deferred
  Обоснование: не баг схемы (PG принимает оба имени без кавычек),
    но потенциальная мина для Task 04 — Hibernate-диалект может
    решить квотить по-разному в зависимости от версии и конфигурации.
    Лучше документировать сейчас, чтобы Task 04 не наступил.
  Document direction:
    backend/src/main/resources/db/changelog/README.md (раздел "JPA mapping
    notes"):
    "Column names `year` (books) and `position` (book_authors,
     book_translators, book_genres, book_series_members, book_list_items,
     genres) are accepted by PG without quoting but are semi-reserved
     in some SQL dialects. JPA entities (Task 04) should map them
     explicitly via @Column(name = \"year\") / @Column(name = \"position\")
     to avoid Hibernate dialect-dependent quoting surprises."
```

### B-F11 — IDE `Cannot resolve directory 'db'` warnings

```
[B-F11]: FP
  Обоснование: IDE filesystem-resolve false-positive; Liquibase резолвит
    classpath:/db/changelog/... корректно при runtime. Уже задокументировано
    в task-02-result.md (note #5) и в Review-A (N1). Никакого действия
    не требуется.
```

### B-F12 — `users.email` nullable

```
[B-F12]: FP
  Обоснование: positive verification от reviewer-а. Task 03 регистрация
    использует только username+password; email опционален. Решение
    корректное.
```

### B-F13 — Weight C для keywords (vs spec говорит про B)

```
[B-F13]: TP-now
  Обоснование: реализация выбрала `A=title || B=authors || C=keywords` —
    это лучшая семантика (авторов искать важнее keywords). README
    `db/changelog/README.md` уже документирует этот выбор. Spec task-02
    формулирует только `A=title || B=keywords` (авторы — "через
    дополнительную функцию"). Чтобы spec не противоречил реализации
    и downstream-задачи могли опираться на финальную формулу — поправить
    spec под реальную реализацию.
  Fix direction:
    .tasks/book-server-fullstack-tasks/task-02-postgres-schema-liquibase-seed.md:
       раздел "What to Do" пункт 3 (FTS):
       заменить формулу на:
       "tsvector = setweight(to_tsvector('russian', title), 'A')
                 || setweight(to_tsvector('russian', authors_concat), 'B')
                 || setweight(to_tsvector('russian', coalesce(keywords,'')), 'C')"
    PLAN.md:
       при следующем апдейте PLAN-а — зафиксировать формулу в Key
       Decisions ("FTS weights: A=title, B=authors, C=keywords").
```

---

## Aggregated recommendation

### Одна fix-wave перед закрытием Task 02 (TP-now)

**Schema fixes (1 commit, `fix(db): tighten Task 02 schema constraints`):**
1. **A-F1 / B-F6** — `005-jobs.xml`: `book_file_id` → NOT NULL.
2. **A-F2** — `001-core-domain.xml`: `genres.code` → NOT NULL.
3. **A-F4** — `006-seed-genres.xml`: убрать redundant `separator`/`quotchar`.

**Documentation / README fixes (тот же commit):**
4. **A-F3/B-F1, B-F2, B-F3, B-F4, B-F5, B-F10** — добавить в
   `backend/src/main/resources/db/changelog/README.md` разделы:
   - **Known FTS limitations** (A-F3/B-F1, B-F3, B-F5).
   - **Bulk-import note** (B-F2).
   - **Deduplication policy** (B-F4).
   - **JPA mapping notes** (B-F10).
   - **Test cleanup notes** (B-F5 — см. также Known FTS limitations).

**Spec fixes (отдельный commit, `docs(plan): align Task 02 spec with reality`):**
5. **B-F7** — `task-02-...md` и `PLAN.md` (S1): `298 → 272`.
6. **B-F13** — `task-02-...md` What-to-Do #3: явная формула `A=title, B=authors, C=keywords`.

**Tooling commit (отдельный, `chore(tools): add genre dump → CSV extractor`):**
7. **B-F8** — `scripts/parse_genres.py` + `scripts/README.md`.

### Deferred (фиксируется в `db/changelog/README.md` как known follow-up)

| Finding | Когда решать | Где доделать |
|---------|--------------|--------------|
| A-F3 ≡ B-F1 | Task 06 (одновременно с B-F2) | новый changeset 007-*.xml — statement-level trigger или explicit recompute pattern в importer |
| B-F2 | Task 06 | переписать `trg_book_authors_fts` на `FOR EACH STATEMENT` с transition tables, либо явно отключать триггер в importer-е |
| B-F3 | Task 06 (по необходимости) | пара строк в `book_authors_fts_trigger` (OLD.book_id ≠ NEW.book_id) |
| B-F4 | Task 06 | partial unique index на `books.md5`, если service-level dedup окажется race-prone |
| B-F5 | Task 03/04 (testcontainers conventions) | вписать `TRUNCATE … CASCADE` в `AbstractIntegrationTest` cleanup |
| B-F10 | Task 04 | `@Column(name = "year")` / `@Column(name = "position")` в JPA entities |

### FP / no action

- **B-F9** — CHECK constraints через `<sql>` корректны, rollback покрыт.
- **B-F11** — IDE warning, уже задокументирован.
- **B-F12** — users.email nullable — корректно.

---

## Что в итоге уйдёт в fix-wave-task-02.md

Three small commits:
1. `fix(db): tighten Task 02 schema constraints + README known-limitations`
   — A-F1, A-F2, A-F4, плюс README с TP-deferred (A-F3, B-F2..B-F5, B-F10).
2. `docs(plan): align Task 02 spec with implementation (genres=272, FTS weights)`
   — B-F7, B-F13.
3. `chore(tools): add scripts/parse_genres.py + scripts/README.md`
   — B-F8.

No-action для B-F9, B-F11, B-F12.
