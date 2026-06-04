# BookServerFull — Execution Plan

## Goal
Создать с нуля full-stack приложение «Библиотека книг»:
- **Backend:** Spring Boot 4, Java 25, Gradle (Kotlin DSL), PostgreSQL + Liquibase XML, полнотекстовый поиск через tsvector + GIN.
- **Frontend:** Vue 3 + Vuetify 3 + Vite + TypeScript + Pinia + Vue Router.
- **Особенности:** расширяемый импорт (inpx+zip, fb2), архитектура конвертации форматов без реальной реализации, списки книг с публичной шарой через share-token + QR-код.

## Scope

### In scope
- Gradle-монорепо `backend/` + `frontend/`, Java 25 toolchain, Gradle wrapper.
- PostgreSQL-схема, спроектированная на основе MySQL-дампов из `sql/`, поданная через Liquibase XML changelogs.
- PG full-text search (tsvector + GIN + триггеры) с конфигурацией `russian` (fallback `simple`).
- Multi-user приложение со Spring Security + JWT (login, refresh, me).
- REST API: книги, **каталог авторов (со списком и деталями)**, **каталог жанров с иерархией поджанров (дерево + детали)**, поиск с фасетами, импорт, конвертация (заглушка), списки, public share.
- Расширяемая архитектура импортеров (`BookImporter`) + готовые реализации `InpxZipImporter` и `Fb2FolderImporter`.
- Архитектура конвертации форматов (интерфейс `FormatConverter`, `ConversionJob`, очередь, REST). Реальной реализации нет — `NotImplementedFormatConverter` помечает задачу `FAILED` с понятным сообщением.
- Frontend Vue 3 + Vuetify 3 + Vite + TypeScript: страницы login/register, поиск, детали книги, **каталог авторов и детали автора, каталог жанров (дерево с поджанрами) и детали жанра**, мои списки, публичный просмотр списка, импорт, конвертация.
- Списки книг + публичная шара через share-token + QR-код (`com.google.zxing`).
- Docker-compose: Postgres + backend + frontend (nginx). Без Calibre.
- README, базовые юнит-тесты + integration-тесты на testcontainers.

### Out of scope
- Реальная реализация конвертации форматов (Calibre, kindlegen, своя).
- ETL ~875k записей из MySQL-дампов (только seed жанров и маппинг кодов).
- Production deployment, HTTPS, SSO, audit-логи, мониторинг.
- In-browser reader, рейтинги/отзывы/комментарии, мобильное приложение.

## Specification

### Requirements
- **R1.** Сборка через Gradle (Kotlin DSL, Java 25 toolchain): `./gradlew :backend:bootRun` поднимает backend, `npm --prefix frontend run dev` — frontend dev-server.
- **R2.** Liquibase XML управляет всеми изменениями схемы (master + changeset-файлы по доменам: books, users, lists, jobs), включая seed жанров.
- **R3.** PG FTS работает через tsvector-колонки на `books` и `persons` + GIN-индексы + триггеры (обновление tsvector через функции/триггеры).
- **R4.** Архитектура импорта расширяема: `BookImporter`-интерфейс + Spring-реестр. `InpxZipImporter` парсит `.inpx` и достаёт книги из `.zip`, `Fb2FolderImporter` парсит `.fb2` напрямую.
- **R5.** Архитектура конвертации работает end-to-end: REST → `ConversionJob` в БД → очередь → исполнитель вызывает `FormatConverter`. `NotImplementedFormatConverter` помечает задачу `FAILED` с сообщением `Conversion not implemented yet`. Добавление реального конвертера в будущем = новый Spring-бин + регистрация по `(sourceFormat, targetFormat)`.
- **R6.** Список книг можно расшарить публично: share-token + QR-код; публичный URL открывается без авторизации.
- **R7.** Frontend Vue 3 + Vuetify 3 покрывает: auth, поиск, детали книги, каталог авторов + детали автора, каталог жанров (дерево с поджанрами) + детали жанра, списки, импорт, конвертация (UI + сообщение о not implemented), public-list-view.
- **R8.** `docker compose up` поднимает Postgres + backend + frontend.
- **R9.** API и UI предоставляют отдельные каталоги авторов (список + детали со списком книг автора) и жанров (иерархическое дерево с поджанрами + детали жанра со списком книг и опциональным включением книг поджанров).

### Non-Goals
- **NG1.** Реальная реализация конвертации форматов.
- **NG2.** Полная миграция исторических данных Flibusta/LibRusEc.
- **NG3.** Reader книг в браузере, социальные фичи.
- **NG4.** Production-grade deployment, HA, мониторинг, наблюдаемость.

### Acceptance Scenarios
- **S1.** После `docker compose up` Liquibase накатывает все changeset-ы, в таблице `genres` **~272 жанра** (соответствует реальному числу записей в `sql/lib.libgenrelist.sql`).
- **S2.** Пользователь регистрируется, логинится, получает JWT; защищённые endpoints отвергают запросы без токена.
- **S3.** `InpxZipImporter` принимает путь к папке с `.inpx` и `.zip`: книги, авторы, серии, жанры появляются в БД, tsvector-колонки заполнены, статус задачи виден в `/api/imports/{id}`.
- **S4.** `Fb2FolderImporter` принимает путь к папке с `.fb2`: метаданные парсятся, книга и `BookFile` сохраняются.
- **S5.** FTS-запрос по фрагменту названия/автора/аннотации возвращает релевантные книги с пагинацией и фасетами по языку/жанру/году.
- **S6.** Пользователь запускает конвертацию через UI: создаётся `ConversionJob`, переходит в статус `FAILED` с сообщением `Conversion not implemented yet`. UI корректно отображает статус.
- **S7.** Пользователь создаёт список книг, добавляет туда книги, расшаривает: получает share-URL и QR-код; неавторизованный пользователь видит публичный список.
- **S8.** Пользователь открывает страницу авторов, фильтрует по букве «Т», находит автора и переходит на его страницу — видит список его книг с пагинацией и фильтрами.
- **S9.** Пользователь открывает дерево жанров, раскрывает «Фантастика» → «Научная фантастика», переходит в детали жанра, включает toggle «Включать поджанры» и видит книги из всех поджанров; breadcrumbs корректно отражают путь.

## How to Use This Plan
1. Open the next unchecked task from the checklist below.
2. Read the corresponding task file completely.
3. Use the suggested agent and the provided inputs for that task.
4. Execute only the next unchecked task unless the user changes the plan.
5. Verify all acceptance criteria, including the git commit requirement.
6. Update the checklist after the task is completed.
7. If the plan becomes stale, update the relevant files before continuing.

## Task Checklist
- [x] `task-01-gradle-monorepo-skeleton.md`: Gradle monorepo skeleton — Suggested agent: Code — Covers: R1 — ✅ done (commits: `cdf9769` task-01 amended, `2ef9497` docs); fix-wave закрыл 7 TP findings (секреты в .gitignore, тяжёлые SQL вынесены из git, .idea/misc.xml вынесён, gradle auto-download).

    **Follow-up в Task 03**: заменить кастомный `HealthController` на `spring-boot-starter-actuator` (B-F9), добавить `pluginManagement` / `dependencyResolutionManagement` (A-F2), профили `application-{dev,prod,test}.yml` (B-F7).

    **Follow-up в Task 10**: полный README с требованиями JDK 25 (B-F10), `vendor` в toolchain (A-F4), Docker-related настройки.
- [x] `task-02-postgres-schema-liquibase-seed.md`: PostgreSQL schema + Liquibase XML + seed — Suggested agent: Code — Covers: R2, R3, S1 — ✅ done (commits: `b2fae98` initial, `6db845d` fix tighten, `f8dd1f4` chore tools, `b5b271f` docs align); 6 changesets, 272 genres seeded; fix-wave закрыл 5 TP-now (NOT NULL constraints, redundant attrs, spec alignment) и задокументировал 6 TP-deferred (FTS propagation, bulk-import write amplification, TRUNCATE, dedup) в README.

    **Схема будет фактически применена в Task 03** (при подключении Liquibase + Spring Boot, testcontainers PG); Task 04 проверит маппинг через `ddl-auto=validate`.

    **Follow-up в Task 06** (importers): реализовать persons-rename FTS recompute pass; использовать `SET session_replication_role='replica'` для bulk-import и явный пересчёт `books.fts_tsv`; дедуп по md5 через `SELECT FOR UPDATE` + upsert.
- [x] `task-03-backend-core-security-openapi.md`: Backend core (Spring Boot 4, Security, JWT, OpenAPI) — Suggested agent: Code — Covers: R1, R2, S2 — ✅ done (commits: `d83513b` initial, `20f39c5` fix-wave); 9 IT тестов + 2 новых (duplicate-register-409, disabled-user-401) зелёные; fix-wave закрыл 8 TP-now (F1/F2/F4/F5/F8/F13/F16-partial + CORS credentials) и задокументировал 9 TP-deferred.

    **Follow-up в Task 04**: TRUNCATE-cleanup helper в `AbstractIntegrationTest` (F7); тесты на refresh с blank token + me с удалённым user (F16-rest).

    **Follow-up в Task 05**: измерить latency UserDetails per-request, при необходимости — Caffeine кэш (F11); решить судьбу `roles` claim (F14).

    **Follow-up в Task 09**: `@Tag`/`@Operation` аннотации на AuthController + других контроллерах (F10); error-code `refresh_expired` для UX session-expiry (F18).

    **Follow-up в Task 10**: убрать дефолт JWT_SECRET в base application.yml (F9); поднять логирование invalid tokens до WARN (F12).

    **Follow-up в backlog (security hardening)**: refresh-token replay protection через jti + `refresh_tokens` таблицу (F3).
- [x] `task-04-jpa-entities-repositories.md`: JPA entities + repositories — Suggested agent: Code — Covers: R2, R3 — ✅ done (commits: `24ed8c3` initial, `574579f` fix-wave); 17 IT тестов зелёные (11 AuthControllerIT + 6 BookRepositoryIT); fix-wave закрыл 9 TP-now (BookSearch multi-genre EXISTS, soft-delete фильтр, rank=NULL для blank-query, @Transactional(readOnly), Pageable.sort javadoc, page-size cap, +5 тестов, AuthControllerIT.cleanUsers удалён, TRUNCATE-docs).

    **Follow-up в Task 05** (TP-deferred): bytecode-enhance для LAZY `Book.annotation` (A-F3); `Set→List` + `@OrderBy(position ASC)` для `authors`/`translators`/`seriesMembers` (A-F6); aggregate helper refactor — `Integer→String→Integer` round-trip + `groupBy` enum (A-F7/A-F8); whitelist sort на REST-уровне (A-F2 явно отложен); logging в BookSearchRepositoryImpl (B-F8); Caffeine TTL-кэш для facetCounts (B-F11); dynamic truncate discovery в `AbstractIntegrationTest` (B-F5).

    **Follow-up в Task 06**: `findFirstByMd5IsNull` / explicit null-check в importers (B-F9).

    **Follow-up в cleanup-task / Task 09**: records для `@EmbeddedId`-классов (B-F13).
- [x] `task-05-books-rest-api-fts.md`: Books / Authors / Genres REST API + full-text search — Suggested agent: Code — Covers: R3, R9, S5, S8, S9 — ✅ done; Books/Auth/Genres read-only API, recursive genre filters/tree counts, author FTS, facets, OpenAPI annotations; 23 backend tests green (`./gradlew :backend:test`).
- [ ] `task-06-importers-inpx-fb2.md`: Importers (Inpx+ZIP, fb2) — Suggested agent: Code — Covers: R4, S3, S4
- [ ] `task-07-conversion-architecture.md`: Conversion architecture (без реализации) — Suggested agent: Code — Covers: R5, S6
- [ ] `task-08-book-lists-public-share-qr.md`: Book lists + public share + QR — Suggested agent: Code — Covers: R6, S7
- [ ] `task-09-frontend-vue-vuetify.md`: Frontend Vue 3 + Vuetify 3 — Suggested agent: Code — Covers: R7, R9, S2, S5, S6, S7, S8, S9
- [ ] `task-10-docker-compose-readme.md`: Docker-compose + README — Suggested agent: Code — Covers: R8, S1

## Shared Context

### Key Decisions
- Gradle Kotlin DSL, Java 25 toolchain, Spring Boot 4.x.
- Liquibase XML (master + changeset-файлы по доменам), не Flyway.
- PostgreSQL ≥ 16, full-text search через tsvector + GIN + триггеры.
- Spring Security + JWT (HS256, секрет в `application.yml` через `${JWT_SECRET}`).
- Frontend: Vue 3 + Vuetify 3 + Vite + TypeScript + Pinia + Vue Router 4.
- Хранение файлов книг: интерфейс `BookFileStorage` с FS-реализацией `LocalBookFileStorage` (директория задаётся через `app.storage.books-dir`).
- Архитектура конвертации форматов реализуется, но единственный бин — `NotImplementedFormatConverter`, всегда возвращающий ошибку. Это даёт slot для будущей интеграции.
- Авторы и переводчики — одна сущность `Person` с двумя связями к `Book`.
- Калькуляция/обновление tsvector — через PG-триггеры, JPA не управляет этим полем напрямую.
- Docker-compose поднимает только Postgres + backend + frontend (без Calibre).

### Constraints
- Не использовать Calibre, kindlegen и другие внешние конвертеры — оставить slot для будущей интеграции.
- Не делать ETL исторических данных из MySQL-дампов (875k книг). Использовать только seed жанров (`lib.libgenrelist.sql`, 298 записей) и опционально маппинг устаревших кодов жанров (`lib.libgenretranslate.sql`).
- Все DDL только через Liquibase XML. Не использовать `spring.jpa.hibernate.ddl-auto=update/create` в dev/prod.
- Не подключать deprecated/неподдерживаемые версии библиотек: Spring Boot 4 (Spring Framework 7), Vuetify 3, Vue 3.5+, Vite 5+.

### Risks / Open Questions
- **OQ1 (resolved).** Конвертация: `NotImplementedFormatConverter` со статусом `FAILED` (выбран дефолт).
- **OQ2 (resolved).** Storage файлов: интерфейс `BookFileStorage` + `LocalBookFileStorage` (FS).
- **OQ3 (resolved).** Дефолты: multi-user + JWT, только seed жанров, Kotlin DSL, TypeScript.
- **R1.** Spring Boot 4 GA: проверить актуальную минорную версию на момент Task 03; при необходимости поправить совместимость с Java 25.
- **R2.** Vuetify 3 + Vue 3 + Vite 5 — проверить совместимые мажорные версии в момент Task 09.
- **R3.** Тестовые ресурсы для импортеров (один маленький `.inpx`+`.zip` и одна `.fb2`) — добавить в `backend/src/test/resources/fixtures/`, файлы должны быть синтетическими, не из реальных дампов.

## Research Artifacts
- `research/ask-01-db-schema/db-schema.md` — полный разбор содержимого `sql/`: 18 MySQL-дампов, схема таблиц, отсутствие FTS, частичная поддержка inpx-полей, seed-данные жанров, ER-обзор, рекомендации по миграции на PG + JPA.
- `research/ask-02-project-state/project-state.md` — текущее состояние корня проекта: только `sql/` и `.idea/`, ни Gradle, ни git, ни src; нужно создавать с нуля.
