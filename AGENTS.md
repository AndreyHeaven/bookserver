# AGENTS.md

Руководство для контрибьютеров (и агентов) репозитория **BookServerFull** —
full-stack приложения «Библиотека книг». Подробное функциональное описание — в
[`README.md`](README.md).

## Структура проекта

Gradle-монорепо (Kotlin DSL) из двух модулей:

- `backend/` — Spring Boot 4 / Java 25.
  - `src/main/java/com/example/bookserver/` — код, сгруппированный по доменам:
    `auth`, `security`, `books`, `authors`, `genres`, `imports`, `conversion`,
    `lists`, `opds`, `storage`, `domain` (JPA-сущности), `repo` (репозитории),
    `config`, `web`. Каждый домен обычно содержит `*Controller`, `*Service` и
    пакет `dto/`.
  - `src/main/resources/db/changelog/` — миграции Liquibase (XML).
  - `src/test/java/.../bookserver/` — интеграционные тесты (суффикс `IT`).
- `frontend/` — Vue 3 + Vuetify 3 + Vite + TypeScript.
  - `src/{views,components,api,stores,router,types,plugins,utils}/`.
- `sql/` — исторические MySQL-дампы (референс схемы), `.tasks/` — план и задачи.

## Команды сборки, тестирования и разработки

```bash
docker compose up --build            # весь стек (frontend + backend + postgres)
docker compose up -d postgres        # только БД для локальной разработки

./gradlew :backend:bootRun           # backend на :8080 (профиль dev)
./gradlew :backend:test              # backend-тесты (нужен Docker для Testcontainers)
./gradlew build                      # сборка всех модулей

npm --prefix frontend install        # зависимости фронта
npm --prefix frontend run dev        # Vite dev-сервер на :5173 (hot-reload)
npm --prefix frontend run build      # прод-сборка + строгая проверка типов
npm --prefix frontend run lint       # ESLint
```

## Стиль кода и именование

- Отступы заданы в `.editorconfig`: **4 пробела** для Java/Kotlin, **2 пробела**
  для Vue/TS/JSON/YAML. Кодировка UTF-8, LF, финальный перевод строки.
- Backend: пакеты по доменам; классы — `PascalCase`, контроллеры/сервисы с
  суффиксами `Controller`/`Service`, JPA-сущности в `domain/`, DTO — record-и в
  `dto/`. Все секреты в `prod` обязательны (fail-fast).
- Frontend: компоненты и views — `PascalCase.vue`, TypeScript strict-режим,
  форматирование через Prettier (`npm --prefix frontend run format`).

## Тестирование

- Backend: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16).
  Интеграционные тесты именуются с суффиксом `IT` (напр. `AuthControllerIT`) и
  наследуют `AbstractIntegrationTest`. Требуется запущенный Docker.
- Frontend: отдельного test-раннера нет; регресс ловится строгой типизацией в
  `run build`.

## Коммиты и пулл-реквесты

- Формат коммитов — **Conventional Commits** с областью:
  `type(scope): summary`. Примеры из истории:
  `feat(backend): ...`, `feat(frontend): ...`, `fix(db): ...`,
  `chore(docker): ...`, `docs(readme): ...`.
  Типичные `type`: `feat`, `fix`, `chore`, `docs`; `scope`:
  `backend`, `frontend`, `db`, `docker`, `readme`.
- Задачи из `.tasks/` помечаются в конце сообщения (напр. `(Task 08)`).
- PR должны: содержать понятное описание и связанную задачу/issue, проходить
  `./gradlew :backend:test` и `npm --prefix frontend run build`, а для изменений
  в UI — прикладывать скриншоты.

## Конфигурация и безопасность

- Профили Spring: `dev` (дефолт, `localhost:5432`), `prod` (все секреты через
  env, fail-fast), `test` (Testcontainers).
- Секреты — только через переменные окружения (см. `.env.example`); минимум
  `JWT_SECRET` ≥ 32 символов. Не коммитьте `.env`.
- `sourcePath` импорта обязан лежать внутри `app.imports.base-dir` (защита от
  path traversal).
