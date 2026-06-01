# Task 10: Docker-compose + README

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Подготовить `docker-compose.yml` для локального запуска (Postgres + backend + frontend) и полноценный README с инструкциями по разработке и запуску.

## Why This Task Exists
Чтобы любой разработчик мог поднять весь стек одной командой и приступить к работе/демонстрации.

## Spec Coverage
- Requirements: R8
- Scenarios: S1

## Required Inputs
- Backend из Tasks 01–08 (Spring Boot 4, Java 25, Liquibase, JWT, Importers, Conversion, Lists+QR).
- Frontend из Task 09 (Vue 3 + Vite TS).
- application.yml и профили из Task 03.

## Files/Areas
- `docker-compose.yml` — основной compose-файл.
- `docker-compose.dev.yml` — overrides для dev (опционально, если будет полезно).
- `.env.example` — пример переменных окружения (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `JWT_SECRET`, `APP_PUBLIC_BASE_URL`, `APP_STORAGE_BOOKS_DIR`, `APP_IMPORTS_BASE_DIR`).
- `backend/Dockerfile` (multi-stage: Gradle build → JRE 25 runtime).
- `frontend/Dockerfile` (multi-stage: node build → nginx static).
- `frontend/nginx.conf` — конфиг для SPA (history fallback на `index.html`, proxy на backend для `/api`).
- `README.md` — финальная версия с разделами:
  - Описание проекта.
  - Архитектура (бэк / фронт / БД / FTS / импортеры / конвертация / share).
  - Требования (Docker, Java 25, Node 20+).
  - Быстрый старт через `docker compose up`.
  - Локальная разработка (Postgres в docker, backend через `./gradlew :backend:bootRun`, frontend через `npm run dev`).
  - Структура проекта.
  - Импортёры (как запускать).
  - Конвертация (что architecture готова, реализации нет).
  - Списки и QR.
  - Тестирование (`./gradlew test`, `npm run test`).
  - Конфигурация (профили, env).
  - Ссылки на task-package в `.tasks/book-server-fullstack-tasks/`.

## Constraints / Non-Goals
- Не использовать Calibre.
- Не делать production-grade compose (HTTPS, secrets manager, swarm/k8s) — только локальная разработка/демо.
- Persistent data: `pgdata` volume + `books-data` volume + `imports-data` volume.

## Output Artifacts
- `docker-compose.yml`, `.env.example`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `README.md`.

## What to Do
1. `docker-compose.yml`:
   - **postgres**: image `postgres:16-alpine` (или `:17-alpine`), env из `.env`, volume `pgdata:/var/lib/postgresql/data`, port 5432:5432 (опционально).
   - **backend**: build `./backend`, depends_on postgres (healthcheck), env (`SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `JWT_SECRET`, `APP_*`), port 8080:8080, volumes `books-data:/data/books`, `imports-data:/data/imports`.
   - **frontend**: build `./frontend`, depends_on backend, port 80:80, env `VITE_API_BASE_URL=/api`.
   - networks default bridge.
2. `backend/Dockerfile`:
   - Stage 1: `gradle:jdk25` (или `eclipse-temurin:25-jdk` + копирование gradle wrapper), `gradle :backend:bootJar -x test`.
   - Stage 2: `eclipse-temurin:25-jre-alpine` (если на момент задачи доступен; иначе `eclipse-temurin:25-jre`), копировать jar, `ENTRYPOINT ["java","-jar","/app.jar"]`.
3. `frontend/Dockerfile`:
   - Stage 1: `node:20-alpine`, `npm ci && npm run build`.
   - Stage 2: `nginx:alpine`, копировать `dist` в `/usr/share/nginx/html`, копировать `nginx.conf`.
4. `frontend/nginx.conf`:
   - `location / { try_files $uri $uri/ /index.html; }`
   - `location /api/ { proxy_pass http://backend:8080/api/; ... }`
5. `.env.example` со всеми переменными и комментариями.
6. README:
   - Сценарий `docker compose up` (требуется заполнить `.env`).
   - Сценарий локальной разработки.
   - Описание архитектуры (короткие диаграммы в ASCII или текстом).
   - Пример запроса импорта через `curl`.
   - Пример создания списка и шары.
7. Проверить вручную (на машине разработчика) что `docker compose up --build` поднимает весь стек, доступно:
   - http://localhost — фронт.
   - http://localhost:8080/swagger-ui.html — Swagger.
   - после `register+login` UI работает, поиск возвращает результаты (если предварительно импортирована хотя бы fixture).

## Expected Output
- Один `docker compose up --build` поднимает весь стек.
- README отвечает на все базовые вопросы.

## Acceptance Criteria
- [ ] `docker compose up --build` поднимает postgres, backend, frontend.
- [ ] Postgres healthcheck зелёный; backend дождался postgres перед стартом.
- [ ] http://localhost открывает Vue SPA.
- [ ] http://localhost/api/auth/register работает через nginx proxy.
- [ ] Liquibase успешно накатил миграции, в `genres` 298 строк.
- [ ] `.env.example` содержит все переменные, README объясняет, как их заполнить.
- [ ] Calibre в compose отсутствует.
- [ ] Covered requirements and scenarios are satisfied (R8, S1).
- [ ] I've created a git commit for this task.
