# Task 03 — Execution report (Backend Core: Spring Boot 4 + Security + JWT + Liquibase + OpenAPI)

**Status:** ✅ Completed
**Commit:** `d83513b feat(backend): Spring Boot 4 core — Security/JWT/Liquibase/OpenAPI + /api/auth/* (Task 03)`
**Build:** `./gradlew :backend:build` → `BUILD SUCCESSFUL` (8 actionable tasks; 9 IT tests all green).

---

## 1. Files created / modified

### Created

```
backend/src/main/java/com/example/bookserver/
├── auth/
│   ├── AuthController.java
│   ├── AuthService.java
│   └── dto/
│       ├── LoginRequest.java
│       ├── MeResponse.java
│       ├── RefreshRequest.java
│       ├── RegisterRequest.java
│       └── TokenResponse.java
├── config/
│   ├── JpaConfig.java                 ← @EnableJpaAuditing + OffsetDateTime provider
│   ├── OpenApiConfig.java             ← OpenAPI bean (title, JWT bearerAuth scheme)
│   └── SecurityConfig.java            ← lambda DSL, CORS, JWT filter, entry points
├── domain/
│   ├── RoleEntity.java                ← maps to `roles` (Liquibase 003)
│   └── UserEntity.java                ← maps to `users` + `user_roles` (Liquibase 003)
├── repo/
│   ├── RoleRepository.java
│   └── UserRepository.java
├── security/
│   ├── CustomUserDetailsService.java
│   ├── JwtAuthenticationFilter.java   ← OncePerRequestFilter
│   ├── JwtService.java                ← jjwt 0.12.x, HS256, access/refresh, typ claim
│   └── UserPrincipal.java
└── web/
    ├── ApiError.java
    └── GlobalExceptionHandler.java

backend/src/main/resources/
├── application-dev.yml
└── application-prod.yml

backend/src/test/java/com/example/bookserver/
├── AbstractIntegrationTest.java       ← @Testcontainers PostgreSQLContainer 16-alpine
└── auth/AuthControllerIT.java         ← 9 MockMvc tests (register, login, me, refresh, health)

backend/src/test/resources/
└── application-test.yml
```

### Modified

| File                                                                | Change                                                                                                                  |
|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `settings.gradle.kts`                                               | Added `pluginManagement` + `dependencyResolutionManagement` blocks (Task 01 follow-up A-F2).                            |
| `backend/build.gradle.kts`                                          | Added Spring starters (validation/security/data-jpa/actuator/liquibase/webmvc-test), jjwt, springdoc, ZXing, testcontainers. |
| `backend/src/main/resources/application.yml`                        | Replaced minimal config with full base profile + JWT, Liquibase, JPA, actuator, springdoc settings.                     |
| `backend/src/main/java/com/example/bookserver/HealthController.java`| **Deleted.** Replaced by `spring-boot-starter-actuator` `/actuator/health` (Task 01 follow-up B-F9).                    |

---

## 2. Library versions

| Library                                  | Version  | Notes                                                                              |
|------------------------------------------|----------|------------------------------------------------------------------------------------|
| Spring Boot                              | 4.0.6    | inherited from Task 01                                                             |
| Spring Framework                         | 7.0.7    | transitive                                                                         |
| Spring Security                          | 7.0.5    | transitive (Spring Boot 4 starter)                                                 |
| Hibernate / JPA                          | 7.x      | transitive via `spring-boot-starter-data-jpa`                                      |
| Liquibase                                | (latest from Spring Boot 4 BOM) | brought in by `spring-boot-starter-liquibase` (replaces bare `liquibase-core`)     |
| PostgreSQL JDBC                          | 42.7.10  | runtime-only                                                                       |
| jjwt (api/impl/jackson)                  | 0.12.6   | latest 0.12.x                                                                      |
| springdoc-openapi-starter-webmvc-ui      | **3.0.3**| the dedicated Spring Boot 4 / Spring Framework 7 line (see §8 below)               |
| ZXing core + javase                      | 3.5.3    | pre-added for Task 08                                                              |
| Testcontainers (junit-jupiter, postgresql)| 1.21.4   | 2.0.x is core-only; PG module still ships on 1.21 line                             |
| Jackson                                  | 3.1.2    | **Spring Boot 4 ships Jackson 3.x** in package `tools.jackson.*`, not `com.fasterxml.*` |

---

## 3. `./gradlew :backend:build` output (last lines)

```
> Task :backend:compileJava UP-TO-DATE
> Task :backend:processResources UP-TO-DATE
> Task :backend:classes UP-TO-DATE
> Task :backend:resolveMainClassName
> Task :backend:bootJar
> Task :backend:jar
> Task :backend:assemble
> Task :backend:compileTestJava UP-TO-DATE
> Task :backend:processTestResources UP-TO-DATE
> Task :backend:testClasses UP-TO-DATE
> Task :backend:test UP-TO-DATE
> Task :backend:check UP-TO-DATE
> Task :backend:build

BUILD SUCCESSFUL in 4s
8 actionable tasks: 3 executed, 5 up-to-date
```

---

## 4. `./gradlew :backend:test` — AuthControllerIT result

```
> Task :backend:test
BUILD SUCCESSFUL in 13s
```

Test report XML summary:
```
<testsuite name="com.example.bookserver.auth.AuthControllerIT" tests="9" skipped="0" failures="0" errors="0" time="1.728">
```

All 9 tests passed:

| Test                                                | Result |
|-----------------------------------------------------|--------|
| `register_returns_201_and_persists_user()`          | ✅      |
| `register_validation_returns_400()`                 | ✅      |
| `login_with_correct_password_returns_tokens()`      | ✅      |
| `login_with_wrong_password_returns_401()`           | ✅      |
| `me_without_token_returns_401()`                    | ✅      |
| `me_with_valid_access_token_returns_200()`          | ✅      |
| `refresh_with_valid_refresh_token_returns_new_tokens()` | ✅  |
| `refresh_with_access_token_is_rejected()`           | ✅      |
| `actuator_health_is_public()`                       | ✅      |

Tests run end-to-end against an ephemeral PostgreSQL 16 container via Testcontainers (Liquibase fully applied each run).

---

## 5. Manual `bootRun` + curl smoke test (with Docker PG)

`docker run -d --name task03-pg -p 15432:5432 -e POSTGRES_DB=bookserver -e POSTGRES_USER=bookserver -e POSTGRES_PASSWORD=bookserver postgres:16-alpine` → up.

Then `./gradlew :backend:bootRun --args="--spring.datasource.url=jdbc:postgresql://localhost:15432/bookserver"`. Liquibase ran all 6 changesets cleanly on first boot.

```
==== /actuator/health ====
{"groups":["liveness","readiness"],"status":"UP"}

==== POST /api/auth/register (valid) ====
HTTP 201
{"id":1,"username":"user1","email":"u1@example.com","roles":["ROLE_USER"]}

==== POST /api/auth/register (username "u1", too short) ====
HTTP 400
{"timestamp":"...","status":400,"error":"Bad Request","message":"Validation failed","path":"/api/auth/register",
 "fieldErrors":[{"field":"username","message":"размер должен находиться в диапазоне от 3 до 64"}]}

==== POST /api/auth/login (valid) ====
HTTP 200
{"accessToken":"eyJhbGciOiJIUzI1NiJ9...","refreshToken":"eyJhbGciOiJIUzI1NiJ9...","tokenType":"Bearer"}

==== GET /api/auth/me (no token) ====
HTTP 401
{"timestamp":"...","status":401,"error":"Unauthorized","message":"Full authentication is required to access this resource","path":"/api/auth/me"}

==== GET /api/auth/me (with valid bearer) ====
HTTP 200
{"id":1,"username":"user1","email":"u1@example.com","roles":["ROLE_USER"]}

==== POST /api/auth/refresh (valid refresh token) ====
HTTP 200
{"accessToken":"...new...","refreshToken":"...new...","tokenType":"Bearer"}

==== GET /v3/api-docs ====
HTTP 200
{"openapi":"3.1.0","info":{"title":"BookServerFull API",...}}

==== GET /swagger-ui/index.html ====
HTTP 200
```

JWT is HS256, with the `typ` claim distinguishing access (`access`) from refresh (`refresh`) tokens; access TTL 15m, refresh TTL 30d (overridable via `app.security.jwt.access-token-ttl`/`refresh-token-ttl`).

---

## 6. Git commit

```
d83513b5a23314b56426dea9aca87c637d2d8e0c
feat(backend): Spring Boot 4 core — Security/JWT/Liquibase/OpenAPI + /api/auth/* (Task 03)
```

Included paths: `backend/**`, `settings.gradle.kts`. (Pre-existing PLAN.md edits from a previous task remain unstaged.)

---

## 7. Known issues / deviations

None of these are blockers; all current acceptance criteria are met.

1. **Jackson 3 + Spring Boot 4 (important for Task 04+).** Spring Boot 4 brings Jackson 3.x where the `ObjectMapper` lives in `tools.jackson.databind.ObjectMapper` (NOT `com.fasterxml.jackson.databind.ObjectMapper`). Anywhere downstream code touches `ObjectMapper` it must use the new package. Annotation-only code that uses `@JsonInclude`, `@JsonIgnore`, `@JsonProperty` from `com.fasterxml.jackson.annotation` **still works** (Jackson 3 keeps the annotations module under the `com.fasterxml.jackson.core:jackson-annotations` coordinate for compatibility); we use this in `ApiError`. **Action for Task 04+**: any custom Jackson configuration / Module / Mixin must target `tools.jackson.*` types.

2. **MockMvc test slice moved.** In Spring Boot 4 `@AutoConfigureMockMvc` is no longer pulled in by `spring-boot-starter-test`. We added `spring-boot-starter-webmvc-test` (this brings `spring-boot-webmvc-test` + `MockMvcAutoConfiguration`). The annotation now lives at `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`.

3. **`EntityScan` moved.** In Spring Boot 4 `org.springframework.boot.autoconfigure.domain.EntityScan` is gone; the annotation is now `org.springframework.boot.persistence.autoconfigure.EntityScan` (used in `JpaConfig`).

4. **JPA Auditing requires explicit `DateTimeProvider` for `OffsetDateTime`.** Spring Data JPA's default `DateTimeProvider` returns `LocalDateTime`, which Hibernate cannot map to `OffsetDateTime` columns (and we use `OffsetDateTime` throughout). `JpaConfig` declares a bean `offsetDateTimeProvider` and `@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")`. Future entities with `@CreatedDate OffsetDateTime` / `@LastModifiedDate OffsetDateTime` will work out of the box.

5. **Liquibase starter.** In Spring Boot 4 the Liquibase auto-configuration was moved out of `spring-boot-autoconfigure` into its own `spring-boot-liquibase` module. We use `spring-boot-starter-liquibase` (which pulls in `liquibase-core` transitively) so that `spring.liquibase.*` properties bind correctly.

6. **CORS allow-credentials + wildcard headers.** `SecurityConfig` allows credentials and `*` headers explicitly. If future endpoints need to expose response headers beyond `Authorization` / `Location` the list must be extended.

7. **Roles seed dependence.** `AuthService.register` resolves the `ROLE_USER` role from the DB (Liquibase changeset `003-003-seed-roles` provides it). If the seed is ever removed, registration fails fast with a clear message — `"Default role ROLE_USER is missing — Liquibase seed not applied?"`.

8. **Open question — testcontainers 2.0.x.** We pinned testcontainers to **1.21.4**: the new 2.0.x line has the core artifact but no PostgreSQL/JUnit-Jupiter modules at the time of writing (only 6 modules released). Re-evaluate in Task 05/06 when more 2.x modules ship.

9. **Russian locale messages from Hibernate Validator.** The `register_validation_returns_400` integration test response carries Russian error text ("размер должен находиться в диапазоне от 3 до 64") because the test JVM picks up the host locale (`ru_RU`). This is benign for the API contract — clients should rely on the `status`/`field` fields, not on the localized `message`. If we want stable English error text we can pin `-Duser.language=en` in the test JVM args; left as a Task 09 polish item.

---

## 8. springdoc-openapi & Spring Boot 4 — special note

The task file flagged uncertainty whether the springdoc artefact is compatible with Spring Boot 4 at the time of execution. **Resolution:** springdoc-openapi has shipped a dedicated 3.0.x line for Spring Boot 4 / Spring Framework 7.

- **Chosen version:** `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3`.
- **Verification:** the artefact's POM declares `spring-boot-starter-webmvc` 4.0.6 as a (compile/runtime) parent dependency line — fully aligned. At runtime the application boots cleanly, `/v3/api-docs` returns a valid OpenAPI 3.1.0 document, and `/swagger-ui/index.html` serves the UI.
- **No fallback was needed.**

(The 2.x series remained the Spring Boot 3.x line and would have required runtime work-arounds; we did not pull it.)

---

## 9. Acceptance criteria checklist (per task file)

- [x] At startup, Liquibase applies all changesets from Task 02 against the configured PostgreSQL — confirmed via bootRun + via every Testcontainers run.
- [x] `/api/auth/register` accepts `username` + `password` (+ optional `email`) and creates a user — verified by IT and manual curl.
- [x] `/api/auth/login` returns access + refresh JWT — verified.
- [x] `/api/auth/me` returns 401 without token and `{id, username, email, roles}` with a valid token — verified.
- [x] Swagger UI is accessible and shows the auth endpoints with Bearer auth — verified at `http://localhost:8080/swagger-ui/index.html`.
- [x] `AbstractIntegrationTest` boots a PostgreSQL 16 container and Liquibase runs in tests — verified (all 9 ITs green).
- [x] R1, R2, S2 covered.
- [x] Git commit created (`d83513b`).

---

## §8 Known Limitations & Deferred Follow-ups

Fix-wave (after Review-B / triage) closed 8 TP-now findings (F1, F2/F17, F4, F5, F8, F13, F16-partial, CORS allow-credentials).
The remaining 9 items below are documented here and tracked in `PLAN.md` against later tasks.

### F3 — Refresh-token replay protection
Старый refresh-token остаётся валидным до своего exp (30 дней) даже после rotation. Нет jti+blacklist хранилища.
- **Follow-up**: добавить jti UUID в claims, таблицу `refresh_tokens` (новый Liquibase changeset), проверку jti в whitelist + помечание использованным в `AuthService.refresh`.
- **Plan reference**: backlog (отдельная задача до Task 09 или в production-readiness phase).

### F7 — Test data cleanup uses deleteAll() instead of TRUNCATE
`AuthControllerIT` использует `userRepository.deleteAll()`. Это не сбрасывает sequence и не учитывает FTS-триггеры из Task 02 README. Для Task 04+ (books/persons/book_authors с FTS) этот подход не сработает.
- **Follow-up**: Task 04 — добавить в `AbstractIntegrationTest` метод `protected void truncateAll()` через `TRUNCATE TABLE ... RESTART IDENTITY CASCADE` (см. `db/changelog/README.md § Test cleanup notes`).

### F9 — JWT secret has empty default in base application.yml
`${JWT_SECRET:}` в base даёт пустой дефолт. Fail-fast зависит от `@PostConstruct`-проверки в `JwtService`, а не от Spring placeholder.
- **Follow-up**: Task 10 — убрать дефолт из base; dev-профиль переопределяет на dev-default, prod-профиль на env. Тогда любой не-настроенный профиль падает на старте Spring без bean instantiation.

### F10 — OpenAPI @Tag / @Operation annotations missing
`AuthController` отображается в Swagger без описаний.
- **Follow-up**: Task 09 — добавить `@Tag(name="Auth", description=...)`, `@Operation(summary=..., responses={...})` на все контроллеры (вместе с AuthorsController/BooksController/GenresController).

### F11 — UserDetails loaded from DB on every authenticated request
`JwtAuthenticationFilter` дёргает `userRepository.findByUsername` + EAGER-fetch ролей при каждом запросе с Bearer. Trade-off: мгновенная ревокация ролей vs +2 SQL/request.
- **Follow-up**: Task 05 — измерить latency на `/api/books`. Если > 10ms/request, добавить `Caffeine`/`@Cacheable` с TTL 30s.

### F12 — Invalid tokens logged only at DEBUG
`JwtService.parseAndValidate` логирует причины invalid token на DEBUG. В prod, где DEBUG обычно выключен, невозможно увидеть signature-mismatch / expired / malformed — что плохо для security alerting.
- **Follow-up**: Task 10 — поднять до WARN с различением `ExpiredJwtException` / `SignatureException` / `MalformedJwtException`.

### F14 — Unused `roles` claim in access token
`JwtService.generateAccessToken` добавляет `roles` claim, но `JwtAuthenticationFilter` его не использует (перезагружает из БД).
- **Follow-up**: Task 05 — решить: либо использовать claim для построения authorities (быстрее, риск устаревших ролей), либо убрать claim (экономия ~30 байт на роль в токене). Документировать в javadoc `JwtService`.

### F16 (rest) — Additional test gaps
Не покрыты в `AuthControllerIT`:
- `refresh_with_blank_token_returns_400` — через `@Valid`-валидацию `RefreshRequest`.
- `me_with_unknown_user_token_returns_401` — создать user → login → удалить user → me.
- **Follow-up**: Task 04 fix-wave — добавить вместе с TRUNCATE-helper.

### F18 — Refresh doesn't distinguish expired vs invalid
`AuthService.refresh` возвращает один и тот же 401 "Invalid or expired refresh token". UI не может показать "session expired" отдельно.
- **Follow-up**: Task 09 — если UX требует, добавить error-code в `ApiError` (например `code: "refresh_expired"`).
