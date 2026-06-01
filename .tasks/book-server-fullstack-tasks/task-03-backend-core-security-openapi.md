# Task 03: Backend Core (Spring Boot 4 + Security + JWT + OpenAPI)

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Настроить ядро backend: зависимости, профили, Spring Security + JWT, Liquibase, OpenAPI, GlobalExceptionHandler, testcontainers-профиль.

## Why This Task Exists
Все последующие задачи (entities, REST API, importers, конвертация, lists) стоят на этом ядре. Без security, OpenAPI и Liquibase-подключения backend не сможет двигаться дальше.

## Spec Coverage
- Requirements: R1, R2
- Scenarios: S2

## Required Inputs
- Скелет из Task 01: `backend/build.gradle.kts`, `Application.java`, `application.yml`.
- Liquibase master + changeset-ы из Task 02 в `backend/src/main/resources/db/changelog/`.
- PostgreSQL ≥ 16 (доступ — `jdbc:postgresql://localhost:5432/bookserver`, пользователь `bookserver`/пароль `bookserver` — задаётся через env).

## Files/Areas
- `backend/build.gradle.kts` — добавить зависимости.
- `backend/src/main/resources/application.yml` — base config.
- `backend/src/main/resources/application-dev.yml` — dev-profile.
- `backend/src/main/resources/application-prod.yml` — prod-profile.
- `backend/src/test/resources/application-test.yml` — test-profile (testcontainers).
- `backend/src/main/java/com/example/bookserver/config/SecurityConfig.java`
- `backend/src/main/java/com/example/bookserver/config/OpenApiConfig.java`
- `backend/src/main/java/com/example/bookserver/security/JwtService.java`
- `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/example/bookserver/security/CustomUserDetailsService.java`
- `backend/src/main/java/com/example/bookserver/auth/AuthController.java` (`POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `GET /api/auth/me`)
- `backend/src/main/java/com/example/bookserver/auth/dto/*.java` (Register/Login/Token/Me DTO + records)
- `backend/src/main/java/com/example/bookserver/web/GlobalExceptionHandler.java`
- `backend/src/test/java/com/example/bookserver/AbstractIntegrationTest.java` (testcontainers-postgres)

## Constraints / Non-Goals
- Не реализовывать JPA entities/repositories — это Task 04.
- Не делать книжные REST-endpoints — это Task 05.
- Не использовать deprecated security DSL (Spring Security 6.x+ lambda-style configuration only).
- JWT — HS256, секрет ≥ 32 символов через `${JWT_SECRET}` env (дефолт только для dev-профиля).
- Stateless session policy.

## Output Artifacts
- Все файлы из Files/Areas + рабочий `/api/auth/*` flow.

## What to Do
1. Расширить `backend/build.gradle.kts`:
   - `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`;
   - `org.liquibase:liquibase-core`;
   - `org.postgresql:postgresql`;
   - `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (последняя совместимая версия);
   - `org.springdoc:springdoc-openapi-starter-webmvc-ui` (для Spring Boot 4 совместимая версия);
   - `com.google.zxing:core` + `javase` (для Task 08; добавить заранее);
   - test: `spring-boot-starter-test`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`.
2. `application.yml` (base): `spring.application.name`, `spring.profiles.active=dev`, `server.port=8080`, `spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.xml`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, JWT-секция (`app.security.jwt.secret`, `app.security.jwt.access-token-ttl=15m`, `app.security.jwt.refresh-token-ttl=30d`).
3. `application-dev.yml`: datasource (`jdbc:postgresql://localhost:5432/bookserver`), `JWT_SECRET` дефолт для dev (с явной пометкой "change in prod").
4. `application-prod.yml`: datasource через env, JWT_SECRET обязательно через env, `logging.level.root=INFO`.
5. `application-test.yml`: testcontainers, `spring.liquibase.enabled=true`.
6. `SecurityConfig`: stateless, разрешить `POST /api/auth/login`, `POST /api/auth/register`, `GET /actuator/health`, `GET /v3/api-docs/**`, `GET /swagger-ui/**`, `GET /api/public/**`. Всё остальное — `authenticated()`. CORS — разрешить `http://localhost:5173` (dev) и `http://localhost` (prod).
7. `JwtService`: генерация access/refresh, валидация, извлечение claims (subject = username, role).
8. `JwtAuthenticationFilter` (`OncePerRequestFilter`): извлечение `Authorization: Bearer ...`, аутентификация через `CustomUserDetailsService`.
9. `CustomUserDetailsService` обращается к `UsersRepository` (заглушка на этапе Task 03; финальный — после Task 04). Допустимо временно сделать in-memory user provider, который заменится в Task 04. Рекомендуется завести заранее JPA-проекцию `UserPrincipalView` чтобы не блокировать Task 04.
10. `AuthController`: register (валидация, хэширование bcrypt, сохранение в `users`), login (генерация tokens), refresh, me.
11. `OpenApiConfig`: title/description/version, securityScheme JWT.
12. `GlobalExceptionHandler` (`@RestControllerAdvice`): обработка `MethodArgumentNotValidException`, `EntityNotFoundException`, `AccessDeniedException`, общий 500.
13. `AbstractIntegrationTest`: подключение testcontainers PostgreSQL ≥ 16, прогон Liquibase.
14. Написать smoke-тест на register+login+me, который должен пройти после Task 04 (на Task 03 — допустимо помечать `@Disabled` или использовать in-memory заглушку и пометить TODO для замены в Task 04).

## Expected Output
- `./gradlew :backend:bootRun` поднимает приложение, подключается к Postgres, Liquibase накатывает миграции.
- Swagger UI доступен по `http://localhost:8080/swagger-ui.html`.
- `/api/auth/register` + `/api/auth/login` работают, `/api/auth/me` возвращает 401 без токена и 200 с валидным.

## Acceptance Criteria
- [ ] При старте backend Liquibase успешно накатывает все changeset-ы из Task 02.
- [ ] `/api/auth/register` принимает username+password, создаёт пользователя.
- [ ] `/api/auth/login` возвращает JWT (access + refresh).
- [ ] `/api/auth/me` возвращает 401 без токена и `{username,...}` с валидным токеном.
- [ ] Swagger UI открывается и показывает auth-endpoints с поддержкой Bearer auth.
- [ ] `AbstractIntegrationTest` поднимает testcontainers PG и Liquibase отрабатывает в тестах.
- [ ] Covered requirements and scenarios are satisfied (R1, R2, S2).
- [ ] I've created a git commit for this task.
