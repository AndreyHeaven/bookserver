# Fix-wave Task 03 — Execution Report

**Дата:** 2026-06-02
**Базовый commit:** `d83513b` (Task 03 — Spring Boot 4 core / Security / JWT / Liquibase / OpenAPI)
**Триаж:** `triage-task-03.md` (8 TP-now + 9 TP-deferred + 2 FP / No-action)
**Review-B:** `review-b-task-03.md` (18 findings)

---

## §1 Изменённые файлы

### Backend production code

| Path                                                                            | Change                                                                                            |
|---------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `backend/src/main/java/com/example/bookserver/web/GlobalExceptionHandler.java`  | +`@ExceptionHandler(DataIntegrityViolationException)` → 409; UTC timestamps                       |
| `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java` | +`AccountStatusUserDetailsChecker` after `loadUserByUsername`                                  |
| `backend/src/main/java/com/example/bookserver/auth/AuthService.java`            | +`AccountStatusUserDetailsChecker` in `refresh()`; pre-checks → `DataIntegrityViolationException` |
| `backend/src/main/java/com/example/bookserver/config/OpenApiConfig.java`        | Removed global `addSecurityItem(bearerAuth)`; updated javadoc                                     |
| `backend/src/main/java/com/example/bookserver/auth/AuthController.java`         | +`@SecurityRequirement(name="bearerAuth")` на `/me`                                               |
| `backend/src/main/java/com/example/bookserver/config/SecurityConfig.java`       | CORS origins из `@Value("${app.cors.allowed-origins:}")`; removed `setAllowCredentials(true)`; UTC ts |

### Backend resources

| Path                                              | Change                                                              |
|---------------------------------------------------|---------------------------------------------------------------------|
| `backend/src/main/resources/application.yml`      | +`app.cors.allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:}`           |
| `backend/src/main/resources/application-dev.yml`  | +`app.cors.allowed-origins: localhost:5173,localhost:3000`; YAML-комментарий про SQL TRACE |
| `backend/src/main/resources/application-prod.yml` | +`app.cors.allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}` (no default) |
| `backend/src/test/resources/application-test.yml` | +`app.cors.allowed-origins: http://localhost:5173`                  |
| `backend/src/main/resources/db/changelog/README.md` | +раздел «Auth & JWT (Task 03 follow-ups)»                          |

### Backend tests

| Path                                                                       | Change                                                                                  |
|----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `backend/src/test/java/com/example/bookserver/auth/AuthControllerIT.java`  | +2 теста: `register_duplicate_username_returns_409`, `login_with_disabled_user_returns_401` |

### Task documentation

| Path                                                            | Change                                                                                  |
|-----------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `.tasks/book-server-fullstack-tasks/task-03-result.md`          | +§8 «Known Limitations & Deferred Follow-ups» (9 пунктов)                              |
| `.tasks/book-server-fullstack-tasks/PLAN.md`                    | Task 03 checkbox → `[x]`, +блок follow-ups для Task 04/05/09/10/backlog                |
| `.tasks/book-server-fullstack-tasks/fix-wave-task-03.md`        | этот файл                                                                              |

---

## §2 TP-now fix snippets

### F2 / F17 — DataIntegrityViolationException → 409

`GlobalExceptionHandler.java`:
```java
@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                    HttpServletRequest req) {
    log.warn("Data integrity violation at {}: {}",
            req.getRequestURI(), ex.getMostSpecificCause().getMessage());
    ApiError body = build(HttpStatus.CONFLICT,
            "Resource conflict (unique constraint violation)", req, null);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
}
```

`AuthService.register` (для синхронизации pre-check и race-condition пути — оба возвращают 409):
```java
if (userRepository.existsByUsername(request.username())) {
    throw new DataIntegrityViolationException("Username already taken");
}
// ... аналогично для email
```

### F4 — AccountStatusUserDetailsChecker (JWT filter + refresh)

`JwtAuthenticationFilter.doFilterInternal`:
```java
UserDetails userDetails = userDetailsService.loadUserByUsername(username);
try {
    accountStatusChecker.check(userDetails);
} catch (AccountStatusException ex) {
    log.debug("Account status check failed for {}: {}",
            userDetails.getUsername(), ex.getMessage());
    chain.doFilter(request, response);
    return;
}
// ... затем set Authentication
```

`AuthService.refresh`:
```java
UserDetails userDetails = userDetailsService.loadUserByUsername(username);
try {
    new AccountStatusUserDetailsChecker().check(userDetails);
} catch (AccountStatusException ex) {
    throw new BadCredentialsException("Invalid or expired refresh token", ex);
}
```

### F5 — Drop global bearerAuth

`OpenApiConfig.java`:
```java
return new OpenAPI()
        .info(new Info()...)
        .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
// no .addSecurityItem(...)
```

`AuthController.me()`:
```java
@GetMapping("/me")
@SecurityRequirement(name = "bearerAuth")
public ResponseEntity<MeResponse> me() { ... }
```

### F1 + CORS allow-credentials — extract origins, drop credentials

`SecurityConfig.java`:
```java
@Value("${app.cors.allowed-origins:}")
private String allowedOriginsCsv;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration conf = new CorsConfiguration();
    List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
            .map(String::trim).filter(s -> !s.isBlank()).toList();
    conf.setAllowedOrigins(origins);
    conf.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
    conf.setAllowedHeaders(List.of("*"));
    conf.setExposedHeaders(List.of("Authorization","Location"));
    // allowCredentials intentionally disabled (JWT in Authorization header).
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", conf);
    return source;
}
```

`application.yml`:
```yaml
app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:}
```
`application-dev.yml`:
```yaml
app:
  cors:
    allowed-origins: http://localhost:5173,http://localhost:3000
```
`application-prod.yml`:
```yaml
app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}
```
`application-test.yml`:
```yaml
app:
  cors:
    allowed-origins: http://localhost:5173
```

### F13 — UTC timestamps

`GlobalExceptionHandler.build`:
```java
OffsetDateTime.now(ZoneOffset.UTC)
```
`SecurityConfig.writeJsonError`:
```java
body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
```

### F8 — SQL TRACE warning

`application-dev.yml`:
```yaml
    # DEV ONLY. NEVER raise to TRACE and do NOT enable org.hibernate.orm.jdbc.bind=TRACE:
    # that would log JDBC parameter values (including password_hash and other PII) to stdout.
    org.hibernate.SQL: DEBUG
```

---

## §3 Новые IT-тесты (F16 partial)

### `register_duplicate_username_returns_409()`

Scenario: register `dup1`/`a@example.com` → 201; повторный register `dup1`/`b@example.com` → 409 Conflict.

Проверяет: `GlobalExceptionHandler.handleDataIntegrity` + `AuthService.register` pre-check переводят дубликат username в HTTP 409 (а не 500 как раньше, и не 400 как было после пред-проверки).

### `login_with_disabled_user_returns_401()`

Scenario: register `disabled1` → 201; через `UserRepository.findByUsername` → `setEnabled(false)` → `save`; login `disabled1` → 401.

Проверяет: `DaoAuthenticationProvider` через `AccountStatusUserDetailsChecker` отвергает disabled-юзера до bcrypt-сверки пароля.

> **Замечание**: тест для `me_with_disabled_user_returns_401()` (где валидный access-token уже выдан, потом юзер disabled, потом /me) отложен в F16-rest (Task 04 fix-wave). Текущий фикс в `JwtAuthenticationFilter` всё равно покрывает это поведение — но тест требует TRUNCATE-cleanup helper'а, который реализуем в Task 04.

---

## §4 Build & test verification

### `./gradlew :backend:build` (с тестами)

```
> Task :backend:compileJava UP-TO-DATE
> Task :backend:bootJar
> Task :backend:test
> Task :backend:check
> Task :backend:build

BUILD SUCCESSFUL in 11s
8 actionable tasks: 4 executed, 4 up-to-date
```

### `./gradlew :backend:test --tests 'com.example.bookserver.auth.AuthControllerIT'`

JUnit XML report:
```
<testsuite name="com.example.bookserver.auth.AuthControllerIT" tests="11" skipped="0" failures="0" errors="0" timestamp="2026-06-02T04:35:17.407Z" hostname="MacBook-Pro-Andrej.local" time="2.004">
```

Все 11 тестов зелёные:

| Test                                              | Result |
|---------------------------------------------------|--------|
| `register_returns_201_and_persists_user`          | ✅     |
| `register_validation_returns_400`                 | ✅     |
| `login_with_correct_password_returns_tokens`      | ✅     |
| `login_with_wrong_password_returns_401`           | ✅     |
| `me_without_token_returns_401`                    | ✅     |
| `me_with_valid_access_token_returns_200`          | ✅     |
| `refresh_with_valid_refresh_token_returns_new_tokens` | ✅ |
| `refresh_with_access_token_is_rejected`           | ✅     |
| `actuator_health_is_public`                       | ✅     |
| **`register_duplicate_username_returns_409`** (new) | ✅   |
| **`login_with_disabled_user_returns_401`** (new)  | ✅     |

---

## §5 Documentation additions

### `.tasks/book-server-fullstack-tasks/task-03-result.md`

Добавлен раздел `## §8 Known Limitations & Deferred Follow-ups` со ссылками на 9 TP-deferred findings и кросс-ссылками на planned tasks: F3 (backlog), F7/F16-rest (Task 04), F11/F14 (Task 05), F10/F18 (Task 09), F9/F12 (Task 10).

### `backend/src/main/resources/db/changelog/README.md`

В разделе `## Known Limitations & Operational Notes` добавлен подраздел `### Auth & JWT (Task 03 follow-ups)` с пунктами:
- Refresh-token replay → backlog (Task 09 production-readiness)
- Disabled-user grace period → ✅ fixed in fix-wave
- CORS → ✅ extracted to `app.cors.allowed-origins`, allowCredentials отключён
- OpenAPI security → ✅ global bearerAuth dropped, per-method `@SecurityRequirement`

### `.tasks/book-server-fullstack-tasks/PLAN.md`

Task 03 checkbox обновлён `[ ]` → `[x]` с детальным блоком follow-ups для Task 04/05/09/10/backlog.

---

## §6 Deviations / Known issues

### `AuthService.register` pre-check semantics

В исходной реализации pre-check `existsByUsername`/`existsByEmail` бросал `IllegalArgumentException`, который через `GlobalExceptionHandler` транслировался в HTTP **400 Bad Request**. После фикс-вэйва pre-check бросает `DataIntegrityViolationException` → HTTP **409 Conflict**.

**Обоснование**: семантически дубликат username/email — это conflict, а не клиентская ошибка валидации (валидация — `@Size`/`@Email`). Кроме того, новая семантика согласуется с race-condition путём, где DB-constraint всё равно бросает `DataIntegrityViolationException` → 409. Один и тот же ответ для duplicate username — последовательное API-поведение.

**Тесты, которые могли сломаться**: проверил — единственный существующий тест `register_validation_returns_400` использует `@Size`/`@Email`-нарушения (не duplicate), поэтому 400 для него остаётся. Все 11 тестов зелёные.

### Не изменено (по триажу)

- `AbstractIntegrationTest.deleteAll` cleanup — оставлен, TRUNCATE-helper отложен в Task 04 (F7).
- `JwtService.CLAIM_ROLES` claim — оставлен, решение об удалении/использовании отложено в Task 05 (F14).
- `JwtService.parseAndValidate` DEBUG logging — оставлен, перевод на WARN отложен в Task 10 (F12).
- `@Tag`/`@Operation` аннотации на AuthController — отложены в Task 09 (F10).
- `refresh_tokens` таблица + jti claim — отложены в backlog (F3).
- `refresh_with_blank_token_returns_400` / `me_with_unknown_user_token_returns_401` тесты — отложены в Task 04 fix-wave (F16-rest).

### Open questions / risks

Нет блокеров. Docker доступен на host, testcontainers поднимает PG 16 без проблем.

---

## §7 Git commit

См. `git log` после commit.
