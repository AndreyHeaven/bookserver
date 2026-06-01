# Review-A Task 02 — корректность Liquibase XML, PG-схемы и FTS

**Commit:** `b2fae98`
**Scope:** `backend/src/main/resources/db/changelog/**` (7 файлов + 1 CSV + README)
**Метод:** статический анализ + IDE inspections (без запуска PG).

---

## Сводная таблица проверок

| Раздел | Статус | Комментарий |
|--------|--------|-------------|
| Liquibase XML структура | ✅ | namespace 4.27 OK, все `id`/`author` уникальны, rollback везде |
| `<include file>` пути | ✅ | classpath-relative `db/changelog/changes/*.xml` — стандарт Spring Boot |
| PG-типы | ✅ | `BIGSERIAL`, `TIMESTAMP WITH TIME ZONE`, `BOOLEAN`, `TSVECTOR` корректны |
| `defaultValueComputed="now()"` | ✅ | корректно работает на PG ≥ 16 |
| FK / `ON DELETE` действия | ⚠️ | один minor (см. F2) |
| Composite PK | ✅ | book_authors / book_translators / book_genres / book_series_members / user_roles / book_list_items |
| CHECK constraints | ✅ | синтаксис верный, добавлены через `<sql>` |
| UNIQUE constraints | ⚠️ | genres.code nullable (см. F3) |
| B-tree индексы | ✅ | все 6 на books созданы |
| GIN индексы (`USING GIN`) | ✅ | через raw `<sql>` (правильно: `<createIndex>` не поддерживает GIN) |
| `books_fts_compute` (IMMUTABLE) | ✅ | weights A=title, B=authors, C=keywords как в Review-A фокусе |
| Триггеры | ✅ | trg_books_fts (BEFORE), trg_book_authors_fts (AFTER) с OLD/NEW корректно |
| `<sql splitStatements="false" endDelimiter=";;">` | ✅ | корректно для `$$ ... $$` блоков |
| `<loadData>` для genres | ✅ | path, tableName, типы STRING/NUMERIC правильны |
| Seed: 272 vs 298 | ✅ | обоснованное расхождение, документировано в README и result.md |
| README.md | ✅ | ER, FTS, inpx-поля описаны |

---

## Findings

### Finding F1: `conversion_jobs.book_file_id` объявлен nullable, но семантически — обязателен

  Severity: **minor**
  File: `backend/src/main/resources/db/changelog/changes/005-jobs.xml:66`
  Why: Task spec явно помечает `output_book_file_id` как `nullable`, но **не** помечает `book_file_id`. Это подразумевает, что `book_file_id` — обязательный (без исходного файла конвертация не имеет смысла). Сейчас же:
  ```xml
  <column name="book_file_id" type="BIGINT"/>
  ```
  — атрибут `nullable` опущен → колонка nullable по умолчанию. FK с `onDelete="CASCADE"` ожидает не-NULL значение; nullable FK с CASCADE — это запах кода.
  Suggestion: добавить ограничение
  ```xml
  <column name="book_file_id" type="BIGINT">
      <constraints nullable="false"/>
  </column>
  ```

### Finding F2: `genres.code` nullable, хотя в исходных данных всегда не-NULL

  Severity: **minor**
  File: `backend/src/main/resources/db/changelog/changes/001-core-domain.xml:18-20`
  Why: исходная MariaDB-таблица имеет `GenreCode varchar(45) NOT NULL DEFAULT ''`. Все 272 строки seed имеют непустой `code` — он же бизнес-ключ. UNIQUE-ограничение есть, но nullable нет — теоретически можно вставить NULL (PG допускает несколько NULL в UNIQUE). Это ослабляет инвариант справочника.
  Suggestion:
  ```xml
  <column name="code" type="VARCHAR(64)">
      <constraints nullable="false" unique="true" uniqueConstraintName="uk_genres_code"/>
  </column>
  ```

### Finding F3: изменение имени `persons` не пересчитывает `books.fts_tsv` (stale FTS)

  Severity: **minor** (design gap, не нарушение спеки)
  File: `backend/src/main/resources/db/changelog/changes/002-fts.xml` (нет триггера)
  Why: триггер `trg_book_authors_fts` пересчитывает `books.fts_tsv` при изменении строк в `book_authors`, но **не** реагирует на `UPDATE persons SET last_name=... WHERE id=X`. После правки имени автора (типичный кейс — опечатка) FTS книги становится stale до тех пор, пока кто-то не тронет либо саму книгу (UPDATE title/keywords), либо строку в `book_authors`. Task spec этого явно не требует (только `persons.fts_tsv`), но это реальный корректностный gap, который аукнется в проде.
  Suggestion: добавить триггер `AFTER UPDATE OF last_name, first_name, middle_name ON persons` который проходит по `book_authors WHERE person_id = NEW.id` и пересчитывает `books.fts_tsv` для каждой связанной книги. Альтернатива — отложить в Task 05 и зафиксировать в TODO.

### Finding F4: redundant `separator=","` / `quotchar="&quot;"` в `<loadData>` (IDE warn)

  Severity: **nit**
  File: `backend/src/main/resources/db/changelog/changes/006-seed-genres.xml:19-20`
  Why: IDE inspection `XmlDefaultAttributeValue` отмечает, что `,` и `"` — значения по умолчанию для `<loadData>`. Безопасно убрать. Чисто косметика.
  Suggestion:
  ```xml
  <loadData file="db/changelog/seed/genres.csv"
            tableName="genres"
            encoding="UTF-8"
            usePreparedStatements="true">
  ```

---

## Не findings (пояснения для оркестратора)

### N1. «Cannot resolve directory 'db'» в IDE inspection
30 ERROR-уровневых сообщений `XmlPathReference` на `<include file=...>` и `<loadData file=...>` — это **известное ограничение IDE** (плагин Liquibase не подключён, путь резолвится как filesystem-relative). Liquibase резолвит эти пути по classpath, файлы там лежат: `backend/src/main/resources/db/changelog/changes/001-core-domain.xml` → classpath `db/changelog/changes/001-core-domain.xml`. Это **стандартная конвенция** Spring Boot + Liquibase. **Не баг.**

### N2. 272 строки вместо 298
- `wc -l genres.csv` = 273 (1 header + 272 data)
- `awk -F, 'NR>1{print $1}' | sort -u | wc -l` = 272 (все коды уникальны)
- `grep -oE "\([0-9]+,'" sql/lib.libgenrelist.sql | wc -l` = 272 (столько INSERT-кортежей в исходнике)
- В исходном SQL `AUTO_INCREMENT=298` — это **next-value sequence**, а не количество строк
- Документировано в `task-02-result.md` (deviation #1) и в `README.md` (раздел Genres seed)
- Соответствует фактическому содержимому `sql/lib.libgenrelist.sql` ✓
**Не баг.** Acceptance criterion в task-02-postgres-schema-liquibase-seed.md действительно стоит смягчить до «matches lib.libgenrelist.sql row count».

### N3. `BIGSERIAL` vs `BIGINT GENERATED BY DEFAULT AS IDENTITY`
Использован `BIGSERIAL` — старый pre-PG10 стиль. PG ≥ 16 поддерживает оба. `BIGSERIAL` работает корректно (создаёт sequence + DEFAULT). Совместимо с Liquibase. **Не баг**, но если хочется модернизировать — поменять в follow-up.

### N4. `books_fts_compute` помечен IMMUTABLE, хотя вызывает `to_tsvector('russian', …)`
Строго говоря, `to_tsvector(regconfig, text)` помечен в PG как STABLE (config может меняться). Но IMMUTABLE-маркер на этой функции — **широко распространённый паттерн в PG FTS-сообществе** (Postgres не возражает, и для целей оптимизатора это даёт выигрыш). **Не баг.**

### N5. CHECK-constraint без отдельного rollback'а в changeset'е
В 005-001 и 005-002 CHECK-constraint добавляется через `<sql>`, а rollback дропает только таблицу. Это OK: `DROP TABLE` каскадно удаляет constraint. **Не баг.**

### N6. `<column name="position">` / `<column name="year">` / `<column name="lang">`
Все non-reserved keyword'ы в PG — работают без кавычек. ✓

### N7. Отсутствие infinite-loop при триггерах
- `trg_books_fts` срабатывает только `OF title, keywords` → UPDATE `books.fts_tsv` его не вызывает
- `trg_book_authors_fts` обновляет только `books.fts_tsv`, не `title`/`keywords` → trg_books_fts не запускается рекурсивно ✓
- На INSERT книги: `book_authors` пуст (FK не позволяет вставить раньше книги), `v_authors=NULL`, потом trg_book_authors_fts добивает ✓

### N8. Порядок `<include>` корректен
001 (tables) → 002 (FTS использует books/persons/book_authors из 001) → 003 (users) → 004 (book_lists FK→users) → 005 (jobs FK→book_files) → 006 (seed genres). ✓

### N9. Полное соответствие task file
Все 15 таблиц из задания присутствуют, имена snake_case, PG-native типы. ✓

---

## SUMMARY

- **Критичных багов нет.** Liquibase XML структурно корректен, namespace 4.27 актуален, `id`/`author` уникальны, rollback присутствует везде, где разумно.
- **PG-типы** (`BIGSERIAL`, `TIMESTAMP WITH TIME ZONE`, `TSVECTOR`, `BOOLEAN`) применены правильно; `defaultValueComputed="now()"` работает на PG ≥ 16.
- **FTS-функции и триггеры** реализованы аккуратно: веса A/B/C соответствуют Review-A фокусу, `coalesce`-protected, `OLD.book_id` в DELETE-ветке используется верно, `splitStatements="false" endDelimiter=";;"` корректно обходит парсер Liquibase для `$$` блоков. Бесконечной рекурсии триггеров нет.
- **Seed:** 272 жанра соответствуют фактическому содержимому MariaDB-дампа; число 298 в спеке = AUTO_INCREMENT (next-value), а не количество строк — обоснованное расхождение, документировано.
- **Found 4 minor/nit findings:** `conversion_jobs.book_file_id` стоило бы пометить NOT NULL (F1), `genres.code` тоже (F2), отсутствие триггера на изменение имён персон (F3, design gap — допустимо отложить), и косметика loadData (F4).
- **30 ERROR-уровневых сообщений IDE inspection «Cannot resolve directory 'db'»** — это filesystem-resolve, не Liquibase; corretto.

Артефакт готов к Task 03 (подключение `liquibase-core` + datasource), реальная проверка миграций состоится там через testcontainers.
