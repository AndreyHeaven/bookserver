# BookServerFull

Full-stack приложение **«Библиотека книг»**: каталог книг с полнотекстовым
поиском, каталогами авторов и жанров, импортом из inpx/fb2, архитектурой
конвертации форматов, списками книг и публичной шарой через QR-код.

- **Backend:** Spring Boot 4 (Spring Framework 7) на **Java 25**, Gradle (Kotlin DSL),
  PostgreSQL + Liquibase (XML), full-text search на `tsvector` + GIN, Spring Security + JWT,
  OpenAPI/Swagger.
- **Frontend:** Vue 3.5 + Vuetify 3 + Vite + TypeScript (strict) + Pinia + Vue Router.

---

## Архитектура

```
┌──────────────┐      /api/*        ┌──────────────────────────┐        ┌──────────────┐
│  Frontend    │  ───────────────▶  │  Backend (Spring Boot 4) │  ────▶ │  PostgreSQL  │
│ Vue3+Vuetify │   nginx proxy      │  REST + Security(JWT)    │  JDBC  │  + Liquibase │
│  (nginx:80)  │  ◀───────────────  │  JPA / FTS / Importers / │        │  + FTS(GIN)  │
└──────────────┘      JSON          │  Conversion / Lists+QR   │        └──────────────┘
                                    └──────────────────────────┘
```

Домены backend:
- **Books / Authors / Genres** — read-only каталоги; поиск через PG FTS (`tsvector` + GIN,
  конфигурация `russian`), фасеты (язык/год/жанр), рекурсивная фильтрация по дереву жанров.
- **Security** — Spring Security + JWT (HS256): `register`, `login`, `refresh`, `me`.
- **Importers** — расширяемый `BookImporter` + реестр; реализации `InpxZipImporter`
  (inpx + zip) и `Fb2FolderImporter` (папка с `.fb2`). Запуск асинхронный (`ImportJob`).
- **Conversion** — архитектурный слот (`FormatConverter` + реестр по паре форматов).
  Реальной реализации нет: единственный бин `NotImplementedFormatConverter` помечает задачу
  `FAILED` с сообщением `Conversion not implemented yet`.
- **Lists + Share** — списки книг (owner-only CRUD), публичная шара через URL-safe
  share-token и QR-код (zxing). Публичный просмотр без авторизации под `/api/public/**`.

Хранение файлов книг: `BookFileStorage` + `LocalBookFileStorage` (директория из
`app.storage.books-dir`, sharding по md5).

---

## Требования

- **Docker** + Docker Compose (для быстрого старта).
- **Java 25** (JDK) — для локальной сборки/разработки backend. Toolchain уже настроен на 25;
  Gradle сам скачает совместимый JDK при необходимости.
- **Node.js 20+** — для фронтенда.
- Для integration-тестов backend нужен работающий Docker (Testcontainers поднимает PostgreSQL 16).

---

## Быстрый старт (Docker)

```bash
cp .env.example .env        # заполните секреты (как минимум JWT_SECRET >= 32 символов)
docker compose up --build
```

После старта:
- Frontend (SPA): <http://localhost>
- Backend API через nginx-proxy: <http://localhost/api/...>
- Backend напрямую: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

Liquibase накатит все changeset-ы автоматически; таблица `genres` будет засеяна
(~272 жанра из исторического дампа).

Остановить и удалить контейнеры (данные в volumes сохраняются):

```bash
docker compose down
```

Полный сброс, включая данные:

```bash
docker compose down -v
```

---

## Локальная разработка

1. **PostgreSQL в Docker:**

   ```bash
   docker compose up -d postgres
   ```

2. **Backend** (профиль `dev` по умолчанию, подключается к `localhost:5432`):

   ```bash
   ./gradlew :backend:bootRun
   ```

   API поднимется на <http://localhost:8080>.

3. **Frontend** (Vite dev-сервер с hot-reload на 5173):

   ```bash
   npm --prefix frontend install
   npm --prefix frontend run dev
   ```

   Dev-сервер: <http://localhost:5173> (`VITE_API_BASE_URL=http://localhost:8080/api`).

---

## Структура проекта

```
BookServerFull/
├─ backend/                 # Spring Boot 4 (Java 25, Gradle Kotlin DSL)
│  ├─ src/main/java/com/example/bookserver/
│  │  ├─ auth/ security/     # JWT, Spring Security
│  │  ├─ books/ authors/ genres/  # каталоги + FTS
│  │  ├─ imports/            # BookImporter, inpx/fb2
│  │  ├─ conversion/         # FormatConverter (slot, без реализации)
│  │  ├─ lists/              # списки + share + QR
│  │  ├─ storage/            # BookFileStorage
│  │  └─ domain/ repo/       # JPA-сущности и репозитории
│  ├─ src/main/resources/db/changelog/   # Liquibase XML
│  └─ Dockerfile
├─ frontend/                # Vue 3 + Vuetify 3 SPA
│  ├─ src/{views,components,api,stores,router,types}/
│  ├─ nginx.conf
│  └─ Dockerfile
├─ sql/                     # исторические MySQL-дампы (референс схемы)
├─ docker-compose.yml
├─ .env.example
└─ .tasks/book-server-fullstack-tasks/   # план и task-package
```

---

## Импортёры

Запуск асинхронного импорта (Bearer-токен обязателен):

```bash
# inpx + zip: sourcePath — папка с *.inpx и *.zip
curl -X POST http://localhost:8080/api/imports \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"inpx-zip","sourcePath":"/data/imports/lib1"}'

# fb2: sourcePath — папка с *.fb2
curl -X POST http://localhost:8080/api/imports \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"fb2-folder","sourcePath":"/data/imports/fb2"}'

# статус задачи
curl http://localhost:8080/api/imports/1 -H "Authorization: Bearer $TOKEN"
```

`sourcePath` должен находиться внутри `app.imports.base-dir` (по умолчанию `/data/imports`
в контейнере) — пути вне базовой директории отвергаются (защита от path traversal).
Дедупликация книг по `md5`.

Формат `.inp` описан в `backend/src/main/resources/META-INF/inpx-format.md`.

---

## Конвертация форматов

Архитектура готова end-to-end (REST → `ConversionJob` → очередь → `FormatConverter`),
но **реальной реализации нет** (по требованию — без Calibre/kindlegen).

```bash
curl -X POST http://localhost:8080/api/conversions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"bookFileId":1,"targetFormat":"epub"}'
# → задача создаётся и переходит в FAILED с сообщением "Conversion not implemented yet"
```

Как добавить реальный конвертер — см. `backend/src/main/resources/META-INF/conversion-architecture.md`
(достаточно нового Spring-бина `FormatConverter`, без изменения существующего кода).

---

## Списки книг и QR

```bash
# создать список
curl -X POST http://localhost:8080/api/lists \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"Моя подборка"}'

# добавить книгу
curl -X POST http://localhost:8080/api/lists/1/items \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"bookId":42}'

# расшарить: вернёт {token, publicUrl, qrPngBase64}
curl -X POST http://localhost:8080/api/lists/1/share -H "Authorization: Bearer $TOKEN"

# публичный просмотр (без авторизации)
curl http://localhost:8080/api/public/lists/<token>
# QR-код как PNG
curl http://localhost:8080/api/public/lists/<token>/qr.png -o qr.png
```

После `DELETE /api/lists/1/share` публичные endpoints начинают возвращать 404.

---

## Тестирование

```bash
# Backend (JUnit 5 + Testcontainers PostgreSQL — требуется Docker)
./gradlew :backend:test

# Frontend (сборка + strict type-check)
npm --prefix frontend run build
```

---

## Конфигурация

Профили Spring (`SPRING_PROFILES_ACTIVE`, по умолчанию `dev`):
- **dev** — локальная разработка, коннект к `localhost:5432`, dev-дефолт JWT-секрета.
- **prod** — все секреты обязательны через переменные окружения (fail-fast на старте).
- **test** — используется integration-тестами (Testcontainers).

Основные переменные окружения (см. `.env.example`):

| Переменная | Назначение |
|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | параметры PostgreSQL |
| `JWT_SECRET` | секрет подписи JWT (HS256), **≥ 32 символов** |
| `APP_CORS_ALLOWED_ORIGINS` | разрешённые CORS-origin (CSV) |
| `APP_PUBLIC_BASE_URL` | базовый URL для share-ссылок и QR |
| `APP_STORAGE_BOOKS_DIR` | директория хранения файлов книг |
| `APP_IMPORTS_BASE_DIR` | базовая директория импортов |

---

## План и документация

Пошаговый план реализации и описания задач: `.tasks/book-server-fullstack-tasks/PLAN.md`
и соответствующие `task-*.md`.
