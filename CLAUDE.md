# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Дополняет `AGENTS.md` (стиль кода, коммиты) и `README.md` (функциональное описание, curl-примеры). Здесь — только то, что не выводится из чтения одного файла.

## Команды

```bash
docker compose up -d postgres                 # БД для локальной разработки (хост-порт 15432!)
./gradlew :backend:bootRun                    # backend :8080, профиль dev
./gradlew :backend:test                       # все backend-тесты (нужен Docker)
./gradlew :backend:test --tests 'com.example.bookserver.books.BookSearchControllerIT'
./gradlew :backend:test --tests '*Fb2*'       # по маске
./gradlew build                               # сборка всех модулей

npm --prefix frontend install
npm --prefix frontend run dev                 # Vite :5173, проксирует /api на :8080
npm --prefix frontend run build               # vue-tsc --noEmit + vite build (единственный регресс-гейт фронта)
npm --prefix frontend run lint                # ESLint
npm --prefix frontend run format              # Prettier
```

Тестового раннера у фронтенда нет: ошибки ловит строгая типизация в `run build`.

Локальный dev-датасорс — `jdbc:postgresql://localhost:15432/bookserver` (`application-dev.yml`), потому что compose пробрасывает `15432:5432`. README местами упоминает 5432 — это неточность.

## Версии, о которых легко ошибиться

- **Java 25**, Gradle toolchain с auto-download. **Spring Boot 4.0.x / Spring Framework 7**.
- Jackson 3: `ObjectMapper` импортируется из `tools.jackson.databind`, а не `com.fasterxml.jackson`.
- В Boot 4 автоконфигурация Liquibase вынесена в `spring-boot-starter-liquibase`, а MockMvc-слайс — в `spring-boot-starter-webmvc-test`.
- springdoc 3.0.x (линия под Boot 4).

## Архитектура: сквозные инварианты

**Схема БД — только Liquibase.** `ddl-auto=validate` во всех профилях; Hibernate никогда не выпускает DDL. Новая миграция = новый файл `changes/NNN-*.xml` + `<include>` в `db.changelog-master.xml`, id вида `NNN-001`, `author="bookserver"`, по возможности `<rollback>`. Подробная документация схемы, FTS-триггеров и известных ограничений — `backend/src/main/resources/db/changelog/README.md` (читать перед любым изменением схемы).

**FTS.** `books.fts_tsv` / `persons.fts_tsv` поддерживаются PG-триггерами (changeset 002), конфигурация `russian`, веса A=title, B=авторы, C=keywords. В JPA эти колонки помечены `insertable=false, updatable=false`. Поиск и фасеты написаны нативным SQL в `repo/BookSearchRepositoryImpl` (Criteria/JPQL не используется). Переименование автора и bulk-import оставляют `books.fts_tsv` устаревшим — recompute-запросы приведены в README changelog'а.

**Фасеты.** Каждый фасет (язык/год/жанр) считается отдельным GROUP BY, исключающим фильтр по собственному измерению, иначе гистограмма схлопывается в выбранное значение. Результаты кэшируются Caffeine (`CacheConfig`: `genresTree`, `bookFacets`, TTL 10 мин) — при изменении данных кэш нужно инвалидировать явно.

**Расширяемость через реестры бинов.** `ImporterRegistry` (по `BookImporter::type`) и `FormatConverterRegistry` (по паре форматов) собираются из всех Spring-бинов соответствующего интерфейса. Добавление импортёра/конвертера = новый бин, без правки существующего кода. Конвертации реально не реализованы: `NotImplementedFormatConverter` переводит задачу в `FAILED`.

**Асинхронные задачи.** `@EnableAsync` на `Application`; `ImportWorker` и `ConversionWorker` помечены `@Async` и пишут прогресс в `import_jobs` / `conversion_jobs`. Telegram long-polling крутится на собственном single-thread executor.

**Безопасность.** `SecurityConfig` — единственная точка правды по доступам. Особенности, которые ломаются при неаккуратной правке:
- `/opds/**` отдаёт `WWW-Authenticate: Basic` через отдельный `opdsAuthenticationEntryPoint` (иначе FBReader и прочие читалки не найдут креденшлы); остальные пути получают JSON-ошибку.
- Публичны: `/api/public/**`, `GET /api/books/*/cover`, `POST /api/telegram/webhook`, `GET /api/telegram/download/*`, auth-эндпоинты, actuator health/info, swagger.
- Глобального `bearerAuth` в OpenAPI нет — защищённые контроллеры помечать `@SecurityRequirement(name="bearerAuth")` вручную.
- `allowCredentials` отключён осознанно (JWT в заголовке, не в куках).

**Ошибки API** централизованы в `web/GlobalExceptionHandler` → `ApiError`. Новые доменные исключения добавлять туда, а не ловить в контроллерах.

**Хранилище файлов** абстрагировано `BookFileStorage` / `CoverStorage` с локальными реализациями (шардинг по md5). `sourcePath` импорта обязан лежать внутри `app.imports.base-dir` — проверка на path traversal. `app.imports.storage-mode`: `copy` (по умолчанию) или `in-place`.

## Тесты

Интеграционные тесты — суффикс `IT`, наследуют `AbstractIntegrationTest`: один статический контейнер PostgreSQL 16 на JVM, Liquibase накатывается на старте контекста, `@BeforeEach truncateAll()` чистит бизнес-таблицы одним `TRUNCATE ... RESTART IDENTITY CASCADE`. Список таблиц — константа `TABLES_TO_TRUNCATE`; **при добавлении новой таблицы её нужно дописать туда**. `roles` и `genres` (Liquibase-сиды) намеренно исключены. Чистые unit-тесты — суффикс `Test` (например `Fb2ParserTest`).

## Frontend

Axios-инстанс `src/api/http.ts` подшивает Bearer и выполняет однократный refresh при 401 с дедупликацией параллельных попыток (`refreshPromise`); при неудаче — `logout()` + редирект на `/login`. Запросы в обход axios (например `<v-img src="/api/books/{id}/cover">`) работают через dev-прокси Vite, а в проде — через `nginx.conf`. Слой API строго по доменам `src/api/*.ts`, типы — `src/types/*.ts`, алиас `@` → `src/`.

## Локальный контекст агентов

`.veai/memory/` содержит короткие заметки о неочевидных решениях (пагинация Vuetify, вложенная метаданные Spring `Page`, bootstrap админа, OPDS Basic-челлендж, токены Telegram). Индекс — `.veai/memory/MEMORY.md`; при работе над соответствующей областью читать заметку до правки. `.veai/rules/` задаёт стиль вывода и комментариев. `.tasks/book-server-fullstack-tasks/` — исторические task-пакеты и `PLAN.md`.
