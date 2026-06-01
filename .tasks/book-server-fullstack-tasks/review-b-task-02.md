# Review-B — Task 02 (PostgreSQL schema + Liquibase XML + seed genres)

**Commit:** `b2fae98`
**Reviewer focus:** edge cases, integration risks for Task 03 (Liquibase + JPA validate) and Task 04 (JPA entities), operational aspects, downstream readiness.

Артефакты, которые я смотрел:
- `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `backend/src/main/resources/db/changelog/changes/001-core-domain.xml`
- `backend/src/main/resources/db/changelog/changes/002-fts.xml`
- `backend/src/main/resources/db/changelog/changes/003-users-auth.xml`
- `backend/src/main/resources/db/changelog/changes/004-book-lists.xml`
- `backend/src/main/resources/db/changelog/changes/005-jobs.xml`
- `backend/src/main/resources/db/changelog/changes/006-seed-genres.xml`
- `backend/src/main/resources/db/changelog/seed/genres.csv`
- `backend/src/main/resources/db/changelog/README.md`
- `scripts/parse_genres.py` (untracked)
- `task-02-postgres-schema-liquibase-seed.md`, `task-02-result.md`

Чем я подкреплял findings:
- `wc -l` + `file` + `xxd` для CSV (273 строк, без BOM, без CRLF, чистый UTF-8).
- `awk -F,` для проверки структуры (5 базовых полей + 67 строк с quoted-полями).
- `run_inspections` на все XML файлы (только IDE-level XmlPathReference, см. F11).
- Прочитал task-файлы 03/04/06/08 — сверил downstream-ожидания со схемой.

---

## Findings

### Finding F1: FTS-триггер не обновляет `books.fts_tsv` при изменении имени автора в `persons`

- **Severity:** major
- **File:** `backend/src/main/resources/db/changelog/changes/002-fts.xml:144-159` (persons_fts_trigger), `:113-126` (trg_book_authors_fts)
- **Why:** В системе три триггера, влияющих на `books.fts_tsv`:
  - `trg_books_fts` BEFORE INSERT/UPDATE OF (title, keywords) ON books — пересчёт при изменении title/keywords;
  - `trg_book_authors_fts` AFTER INSERT/UPDATE/DELETE ON book_authors — пересчёт при изменении состава авторов;
  - `trg_persons_fts` BEFORE INSERT/UPDATE OF last/first/middle_name ON persons — пересчёт **только** `persons.fts_tsv`.

  При UPDATE имени автора (`persons.last_name`, `first_name`, `middle_name`) **никто** не пересчитывает `books.fts_tsv` для книг, ссылающихся на этого автора. В Task 06 импортеры будут делать upsert авторов и могут «нормализовать» имя или назначить `master_id` — после этого FTS-поиск по новому имени не найдёт книгу до тех пор, пока что-то снова не «дёрнет» `books.title/keywords` или `book_authors`.

  Симптом для Task 09 (frontend) — поиск по `q=Толстой` после нормализации имени автора возвращает 0 результатов, хотя книга в БД есть.
- **Suggestion:** добавить триггер AFTER UPDATE OF (last_name, first_name, middle_name) ON persons, который при изменении имени обновляет `books.fts_tsv` для всех книг через `book_authors`:
  ```sql
  CREATE OR REPLACE FUNCTION persons_propagate_fts_trigger() RETURNS trigger AS $$
  BEGIN
      UPDATE books b SET fts_tsv = books_fts_compute(
          b.title,
          b.keywords,
          (SELECT string_agg(coalesce(p.last_name,'')||' '||coalesce(p.first_name,'')||' '||coalesce(p.middle_name,''), ' ')
             FROM book_authors ba JOIN persons p ON p.id=ba.person_id WHERE ba.book_id=b.id))
      WHERE b.id IN (SELECT book_id FROM book_authors WHERE person_id = NEW.id);
      RETURN NEW;
  END;
  $$ LANGUAGE plpgsql;
  CREATE TRIGGER trg_persons_propagate_fts
      AFTER UPDATE OF last_name, first_name, middle_name ON persons
      FOR EACH ROW EXECUTE FUNCTION persons_propagate_fts_trigger();
  ```
  Альтернатива (если такой триггер дорогой для bulk-импорта): задокументировать как known limitation и обязать importer вызывать «recompute fts_tsv en masse» после batch-import.

---

### Finding F2: Bulk-import N авторов на одну книгу → N UPDATE на `books` (O(N²) при typical bulk-загрузке)

- **Severity:** major (performance, особо чувствительно для Task 06)
- **File:** `backend/src/main/resources/db/changelog/changes/002-fts.xml:113-126` (trg_book_authors_fts AFTER INSERT/UPDATE/DELETE FOR EACH ROW)
- **Why:** при импорте через INPX/FB2 для одной книги может быть несколько авторов, и importer вставляет их пачкой в `book_authors`. На каждую вставленную строку триггер делает:
  1. `SELECT string_agg(...)` по всем авторам этой книги;
  2. `SELECT title, keywords FROM books`;
  3. `UPDATE books SET fts_tsv = ... WHERE id = …`.

  То есть N авторов = 3·N запросов и N UPDATE одной и той же строки в `books`. Для одной книги с 10 авторами — 10 UPDATE на одну строку (writes amplification). Для bulk-импорта 100 000 книг с в среднем 1–3 авторами — ~300 000 лишних UPDATE на `books`, каждый из которых триггерит TOAST-rewrite и GIN-index update.

  Также `trg_book_authors_fts` срабатывает FOR EACH ROW — на `COPY book_authors FROM …` это N вызовов триггера.
- **Suggestion:**
  - Простой вариант: задокументировать в `README.md` рекомендуемый паттерн для импортеров — `SET session_replication_role = replica` (отключить триггер) на время batch-import + «recompute en masse» после: `UPDATE books SET fts_tsv = books_fts_compute(title, keywords, (SELECT string_agg(...) FROM book_authors ba ...))`.
  - Хирургический вариант: переписать `trg_book_authors_fts` как FOR EACH STATEMENT (использовать transition tables `REFERENCING NEW TABLE AS new_rows`) — тогда на bulk INSERT триггер вызовется 1 раз и обновит затронутые книги одним UPDATE с GROUP BY.
  - Минимум — отметить как known limitation и упомянуть, что Task 06 должен использовать deferred recompute.

---

### Finding F3: `trg_book_authors_fts` некорректно отрабатывает UPDATE с изменением `book_id`

- **Severity:** minor (теоретический сценарий, маловероятен в practice)
- **File:** `backend/src/main/resources/db/changelog/changes/002-fts.xml:83-87`
- **Why:** при `TG_OP='UPDATE'` функция использует только `NEW.book_id`. Если кто-то выполнит UPDATE, меняющий `book_id` строки в `book_authors` (например, при ручной правке или merge книг), то tsvector у **старой** книги останется со ссылкой на ушедшего автора. Аналогично при изменении `person_id`.

  В PG первичный ключ `(book_id, person_id)` теоретически можно обновить (FK CASCADE не запрещает). Importer-ы обычно используют DELETE+INSERT, поэтому продакшен-риск низкий, но это всё равно edge case.
- **Suggestion:** в ветке UPDATE пересчитать tsvector и для OLD.book_id, и для NEW.book_id, если они различаются:
  ```sql
  IF TG_OP = 'UPDATE' AND OLD.book_id <> NEW.book_id THEN
      PERFORM books_fts_recompute(OLD.book_id);
  END IF;
  ```
  Или явно задокументировать ограничение «book_id/person_id в book_authors не меняется, только DELETE+INSERT».

---

### Finding F4: `books.md5` без UNIQUE constraint — дедупликация целиком на сервисе

- **Severity:** minor (design decision, надо явно зафиксировать)
- **File:** `backend/src/main/resources/db/changelog/changes/001-core-domain.xml:127-129` (idx_books_md5 без unique)
- **Why:** Task 06 говорит «Дедупликация по md5 (если md5 есть и совпадает — обновлять, не создавать дубликат)». Сейчас на `md5` только B-tree индекс, не UNIQUE. Это даёт гибкость (можно иметь несколько строк с одним md5, например помеченных `deleted=true`), но переносит ответственность за дедуп целиком на сервисный слой Task 06. Если importer окажется не atomic — гонка двух конкурентных импортов вставит дубли, и detect-race потребует доп. логики (`SELECT … FOR UPDATE` + retry).

  Это не баг, а архитектурное решение, но downstream-агент Task 06 должен понимать, что DB не enforced'ит дедуп.
- **Suggestion:** одно из:
  1. Добавить `CREATE UNIQUE INDEX uk_books_md5 ON books (md5) WHERE md5 IS NOT NULL AND deleted = false;` (partial unique index — нативный PG паттерн, не ломает soft-deleted-копии).
  2. Явно отметить в `README.md`: «`md5` — non-unique index; importer обеспечивает дедупликацию через `SELECT … FOR UPDATE` + upsert».

---

### Finding F5: `TRUNCATE books|book_authors` не вызовет FTS-триггеры — известное ограничение для test-cleanup

- **Severity:** minor (документация)
- **File:** `backend/src/main/resources/db/changelog/changes/002-fts.xml` (общая особенность всех триггеров)
- **Why:** PostgreSQL row-level triggers (`FOR EACH ROW`) **не** срабатывают на `TRUNCATE`. Это нормально, но при использовании `TRUNCATE` в test-cleanup (Task 03/04 testcontainers) FTS-индекс не «очистится» автоматически. На пустых таблицах это не страшно — GIN индекс просто опустеет вместе с таблицей. Но если кто-то сделает TRUNCATE только на `book_authors`, оставив `books`, то `books.fts_tsv` останется со stale данными. В testcontainers обычно делают `TRUNCATE … CASCADE` на все таблицы, так что в практике риск минимальный.
- **Suggestion:** добавить в `README.md` секцию «Operational notes / test cleanup» с явным указанием: «for test isolation use `TRUNCATE … RESTART IDENTITY CASCADE`; row-level FTS triggers don't fire on TRUNCATE».

---

### Finding F6: `conversion_jobs.book_file_id` nullable, хотя FK имеет ON DELETE CASCADE

- **Severity:** minor
- **File:** `backend/src/main/resources/db/changelog/changes/005-jobs.xml:64`
- **Why:** колонка объявлена без `<constraints nullable="false"/>` (по умолчанию nullable). При этом FK задан с `onDelete="CASCADE"`. Эти два решения противоречивы: CASCADE удалит строку при удалении исходного book_file, поэтому SET NULL не понадобится — значит, и nullable не нужно. Семантически conversion job без исходного file_id бессмыслен.

  Это не блокирует Task 04/06/07, но Hibernate validate скажет, что колонка nullable, а entity скорее всего будет помечена `@NotNull` — может быть мисалайн.
- **Suggestion:** сделать `book_file_id` NOT NULL (`<constraints nullable="false"/>`). `output_book_file_id` логично оставить nullable (он SET NULL и заполняется только после успешной конвертации).

---

### Finding F7: 298 vs 272 жанра — обоснование code-agent корректно, criterion надо смягчить

- **Severity:** nit (согласен с code-agent)
- **File:** `task-02-postgres-schema-liquibase-seed.md:39, 84`, `PLAN.md:50`
- **Why:** Code-agent прав: в `sql/lib.libgenrelist.sql` есть 272 INSERT-кортежа, а `AUTO_INCREMENT=298` — это next-value, а не количество. В дампе есть пропуски GenreId. Я перепроверил по CSV: `awk -F, 'NR>1{print $1}' | sort -u | wc -l` = 272, причём `position` (бывший GenreId) идёт до 295, с пропусками.

  Поэтому acceptance criterion `«В genres ровно 298 строк»` некорректен и должен быть переформулирован: «matches the row count of `sql/lib.libgenrelist.sql` (272)». То же самое в `PLAN.md` сценарий S1.
- **Suggestion:** уточнить criterion в task-файле и S1 в `PLAN.md`. Code-agent уже предложил это в task-02-result.md (deviation #1) — принять.

---

### Finding F8: `scripts/parse_genres.py` остался untracked → потеря воспроизводимости

- **Severity:** minor (operational)
- **File:** `scripts/parse_genres.py` (140 строк, untracked в `git status`)
- **Why:** Code-agent сознательно не закоммитил скрипт, мотивируя scope-restriction («только `backend/src/main/resources/db/`»). Но: csv → 272 строки получены этим скриптом из `sql/lib.libgenrelist.sql`. Если seed-файл понадобится перегенерировать (например, при обновлении исходного дампа или fix формата для Liquibase loadData), то без скрипта нельзя воспроизвести процесс — придётся писать его заново. Это плохая reproducibility-практика для миграционного pipeline.
- **Suggestion:** закоммитить отдельным `chore(tools)`-коммитом `scripts/parse_genres.py` + минимальный `scripts/README.md` с инструкцией запуска. Альтернатива — переместить в `backend/scripts/` если хочется держать в backend-модуле.

---

### Finding F9: `<sql>` для CHECK constraints в 005 — rollback не отзывает constraint явно (полагается на dropTable)

- **Severity:** nit
- **File:** `backend/src/main/resources/db/changelog/changes/005-jobs.xml:44-47, 89-92`
- **Why:** Check-constraint добавляется через `<sql>ALTER TABLE … ADD CONSTRAINT …</sql>`, а rollback всего changeset'а — `<dropTable …/>`. Это работает (DROP TABLE сносит constraint), но если когда-нибудь захочется делать частичный rollback constraint'а (например, поменять enum-значения), то rollback-у constraint'а нет.

  Так же Liquibase поддерживает `<addCheckConstraint>` в новых версиях (`liquibase-core 4.27+`), что было бы более идиоматично и с собственным rollback.
- **Suggestion:** оставить как есть либо переписать на `<addCheckConstraint>` (опционально). Не блокер.

---

### Finding F10: `position` и `year` — слова, требующие осторожности в JPA-маппинге (Task 04)

- **Severity:** nit (предупреждение для Task 04)
- **File:** `001-core-domain.xml:26, 99, 152, 187, 218` (`position`), `001-core-domain.xml:99` (`year`), `004-book-lists.xml:47` (position)
- **Why:** `position` и `year` — зарезервированные/специальные слова в SQL (`position(substring IN string)`, `EXTRACT(YEAR …)`). PostgreSQL разрешает их как имена колонок без кавычек в большинстве случаев, и Liquibase их корректно создаёт. Но Hibernate при генерации SQL для запросов вроде `SELECT b.year FROM books b` может или не может квотить, в зависимости от диалекта и версии. В Hibernate 7.x по умолчанию `globally_quoted_identifiers=false`, и `year` обычно проходит, но это «недетерминизм» при подмене диалекта.

  Это не баг схемы — это потенциальная мина для Task 04. Хорошо бы Task 04 пометил такие поля `@Column(name = "\"year\"")` или включил global quoting.
- **Suggestion:** оставить как есть в схеме, но добавить в `README.md` примечание: «column names `year` and `position` are not reserved in PG but require `@Column(name = "…")` (and may need quoting) in Hibernate mapping».

---

### Finding F11: IDE-inspection ERRORы `Cannot resolve directory 'db'` в master + 006 — реально не падают, но смутят CI

- **Severity:** nit (известная проблема, code-agent уже описал)
- **File:** `db.changelog-master.xml:8-13`, `006-seed-genres.xml:17`
- **Why:** Я запустил `run_inspections` — получил 30 проблем, из которых 26 — `XmlPathReference: Cannot resolve directory 'db'/'changes'/'changelog'`. Это IDE-side check, который пытается резолвить `db/changelog/...` относительно файловой системы, не понимая, что Liquibase резолвит относительно classpath. Code-agent в отчёте отметил это (note #5 в task-02-result.md).

  Реальные значения этих путей корректны (`spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.xml` соберётся правильно). Однако:
  1. в CI могут гонять inspections и упасть на 26 ERROR'ах;
  2. IDE-плагин Liquibase (если установлен) обычно резолвит правильно, но «голая» IDEA — нет.
- **Suggestion:**
  - Лучший вариант — добавить файл `liquibase.properties` или `.idea/liquibase.properties` с `changeLogFile=backend/src/main/resources/db/changelog/db.changelog-master.xml` (опционально).
  - Минимум — оставить ссылку на это в `README.md` («IDE warnings are harmless; Liquibase resolves classpath:/ paths at runtime»). Code-agent уже это сделал в task-02-result.md.
  - Не блокирует Task 03 — Liquibase runtime разрешит пути корректно через classpath.

---

### Finding F12: `users.email` nullable — может расходиться с Task 03 валидацией

- **Severity:** nit
- **File:** `backend/src/main/resources/db/changelog/changes/003-users-auth.xml:20-22`
- **Why:** В схеме `email VARCHAR(255) UNIQUE` без `nullable=false`. В Task 03 `/api/auth/register` принимает «username+password» (email там не упоминается обязательным). Так что nullable — корректное решение. Но: UNIQUE на nullable колонке в PG означает «можно сколько угодно NULL'ов», что обычно ожидаемое поведение. Просто чтобы Task 04 не удивился, не делая `@Column(nullable=false)`.
- **Suggestion:** оставить как есть; убедиться, что в Task 04 поле `email` помечено nullable.

---

### Finding F13: `setweight` order отличается от формулировки в task-spec (минимально, скорее «улучшение»)

- **Severity:** nit
- **File:** `backend/src/main/resources/db/changelog/changes/002-fts.xml:24-27` vs task-spec `task-02-postgres-schema-liquibase-seed.md:60-62`
- **Why:** Task-spec формулирует формулу как `A=title || B=keywords` + «авторы через дополнительную функцию» (без явного веса). Реализация выбрала `A=title || B=authors || C=keywords`. README схемы (`db/changelog/README.md:64-68`) уже задокументировал этот выбор. На мой взгляд, такой выбор лучше — авторов искать важнее, чем keywords. Но technically есть мелкое расхождение с task-spec.
- **Suggestion:** не править реализацию; в `PLAN.md` (если потребуется) зафиксировать «A=title, B=authors, C=keywords» как окончательную схему весов.

---

### Finding F14: `<loadData>` файл-путь `db/changelog/seed/genres.csv` относительно classpath работает; CSV проходит проверки (BOM/CRLF/quoting/UTF-8)

- **Severity:** No finding — explicit positive verification.
- **Why:** проверил CSV:
  - `wc -l` = 273 (1 header + 272 data) ✓
  - `file` → `CSV text` (без BOM); первые 4 байта `63 6f 64 65` = `code` (не `EF BB BF`) ✓
  - CRLF lines: 0 ✓
  - Поля с запятыми внутри (например, `"Религия, духовность, эзотерика"`) обёрнуты в `"…"`, бэкслэшей нет (`grep -c '\\\\' = 0`); Liquibase 4.x `<loadData quotchar="&quot;" separator=",">` это поддерживает корректно ✓
  - Кириллица — валидный UTF-8 (двухбайтовые префиксы `D0 …`) ✓
  - `awk -F, '{print NF}'` показывает 5/6/7/8/9 полей — это нормально из-за quoted commas внутри полей; Liquibase парсит CSV правильно.

---

### Finding F15: Liquibase rerun + seed-roles — повторного запуска не будет (как и положено)

- **Severity:** No finding — explicit positive verification.
- **Why:** `<changeSet id="003-003-seed-roles" …>` без `runOnChange="true"`, без `runAlways="true"` — Liquibase помечает changeset как `EXECUTED` в `DATABASECHANGELOG` и при rerun пропускает. Никаких unique-violation на `ROLE_USER`/`ROLE_ADMIN` не будет. То же для seed-genres (006-001). ✓

---

### Finding F16: tsvector для `validate` (Task 03/04) — пройдёт, но требует осторожности в entity

- **Severity:** No finding (потенциальная проблема Task 04, не Task 02)
- **Why:** схема создаёт `fts_tsv TSVECTOR`. Hibernate `ddl-auto=validate` проверяет только колонки, которые упомянуты в entity. Если в `Book.java` и `Person.java` поле `fts_tsv` будет `@Transient`, Hibernate его не валидирует — всё ок. Если же entity захочет маппить `fts_tsv` как `@Column(insertable=false, updatable=false)`, потребуется либо `@JdbcTypeCode(SqlTypes.OTHER)` + кастомный конвертер, либо `columnDefinition="tsvector"` + `@Type` (Hibernate 7 поддерживает через `org.hibernate.usertype.UserType`). Это уже забота Task 04. Task 02 правильно создаёт схему.

---

## SUMMARY

- Схема в целом solid и соответствует требованиям Task 02 + downstream-задач (готовы поля для inpx-импорта, share-token, статусов job'ов, composite keys для М:N через `@IdClass`/`@EmbeddedId`).
- **2 major** проблемы в FTS-логике, обе про неполноту триггеров: **F1** — отсутствует propagation `persons → books.fts_tsv` (поиск по новому имени автора не найдёт книгу), **F2** — `trg_book_authors_fts FOR EACH ROW` создаёт write-amplification при bulk-импортах (критично для Task 06).
- **5 minor**: F3 (UPDATE с изменением `book_id` в book_authors), F4 (нет UNIQUE на md5 — design decision), F5 (TRUNCATE не пересчитывает FTS), F6 (`conversion_jobs.book_file_id` nullable), F8 (`scripts/parse_genres.py` untracked).
- **5 nit**: F7 (272 vs 298 — согласиться и смягчить criterion), F9 (CHECK через `<sql>` вместо `<addCheckConstraint>`), F10 (`position`/`year` — для Task 04), F11 (IDE inspections — known false-positive), F12 (`users.email` nullable), F13 (вес keywords стал C — соответствует README).
- **3 positive verification**: F14 (CSV корректный, UTF-8/no-BOM/no-CRLF/quoting), F15 (Liquibase rerun seed безопасен), F16 (tsvector validate — норма при правильном маппинге в Task 04).

**Рекомендую перед закрытием Task 02 минимум исправить F1 и F2** (или хотя бы зафиксировать F1 как known limitation в `README.md` + добавить деталь про deferred recompute для импортеров — F2). Остальные findings можно решать в фикс-вейве после Task 06.
