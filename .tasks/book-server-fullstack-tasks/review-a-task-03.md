# Review-A Task 03 — Backend Core (Security/JWT/Liquibase/OpenAPI)

**Commit:** `d83513b feat(backend): Spring Boot 4 core — Security/JWT/Liquibase/OpenAPI + /api/auth/* (Task 03)`
**Reviewer focus:** Security DSL, JWT, JPA mapping `users`/`roles`, Liquibase, OpenAPI, `/api/auth/*` contract.

## Detailed verification

| Check item                                                               | Status   | Note |
|--------------------------------------------------------------------------|----------|------|
| Lambda DSL (no `.and()`)                                                 | ✅       | `SecurityConfig.java:42-61`. |
| `STATELESS` session                                                       | ✅       | `SecurityConfig.java:44`. |
| `permitAll` for `/api/auth/{login,register,refresh}`                     | ✅       | restricted to POST – stricter than spec, OK. |
| `permitAll` for `/actuator/health(+/**)`, `/actuator/info`               | ✅       | `SecurityConfig.java:51`. |
| `permitAll` for `/v3/api-docs(+/**)`, `/swagger-ui/**`, `/swagger-ui.html`| ✅       | `SecurityConfig.java:52-53`. |
| `permitAll` for `/api/public/**`                                         | ✅       | `SecurityConfig.java:54`. |
| `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` | ✅       | `SecurityConfig.java:61`. |
| `BCryptPasswordEncoder` bean                                             | ✅       | `SecurityConfig.java:66`. |
| CORS – dev origins (`localhost:5173`/`localhost`/`localhost:3000`)        | ✅       | `SecurityConfig.java:76-87`. |
| `authenticationEntryPoint` 401 JSON                                       | ✅       | `SecurityConfig.java:89-99`. |
| `@EnableMethodSecurity`                                                   | ✅       | `SecurityConfig.java:33`. |
| HS256 + jjwt 0.12 API (`.subject`/`.expiration`)                          | ✅       | `JwtService.java:84-94`. |
| Key length ≥ 32 bytes                                                     | ✅       | `JwtService.java:61-64`. |
| TTL: access 15m / refresh 30d                                             | ✅       | `application.yml:33-34`. |
| access/refresh typ claim                                                   | ✅       | `JwtService.java:33-36,89`. |
| Exception handling (ExpiredJwt, MalformedJwt, Signature → JwtException)   | ✅       | `JwtService.java:108-110`. |
| `OncePerRequestFilter`, doesn't overwrite SecurityContext                 | ✅       | `JwtAuthenticationFilter.java:29,48`. |
| `UsernameNotFoundException` in CUDS                                       | ✅       | `CustomUserDetailsService.java:24`. |
| register: unique username/email, BCrypt, ROLE_USER, MeResponse (no pwd)  | ✅       | `AuthService.java:54-77`. |
| login via `AuthenticationManager.authenticate`                            | ✅       | `AuthService.java:80-83`. |
| refresh checks `isRefreshToken`                                           | ✅       | `AuthService.java:94`. |
| `me` from `SecurityContextHolder`                                          | ✅       | `AuthService.java:106-114`. |
| DTOs records + `@Valid`                                                   | ✅       | `auth/dto/*.java`, `AuthController.java:27,33,38`. |
| `GlobalExceptionHandler`: all required handlers, no stacktrace leak      | ✅       | `GlobalExceptionHandler.java`. |
| UserEntity/RoleEntity: tables, columns, M:N JoinTable, auditing           | ✅       | `UserEntity.java`. |
| equals/hashCode (id-based, hashCode = class.hashCode())                   | ✅       | `UserEntity.java:113-129`. |
| `JpaConfig`: dateTimeProviderRef, EnableJpaRepositories, EntityScan (SB4) | ✅       | `JpaConfig.java`. |
| `ddl-auto=validate`, Liquibase change-log path, `open-in-view=false`     | ✅       | `application.yml:7-17`. |
| Dev JWT default ≥32 chars; prod no default                                | ✅       | dev secret = 55 chars; prod `${JWT_SECRET}` only. |
| `management.*` exposure & `show-details: when-authorized`                  | ✅       | `application.yml:22-29`. |
| 9 ITs, MockMvc, testcontainers PG `16-alpine`, static container           | ✅       | `AbstractIntegrationTest.java`, `AuthControllerIT.java`. |
| `HealthController.java` deleted                                           | ✅       | confirmed via search – не существует. |
| OpenAPI `bearerAuth` scheme                                                | ✅       | `OpenApiConfig.java`. |
| springdoc 3.0.3 + SB4                                                     | ✅       | `build.gradle.kts:19`. |

## Findings

Finding F1: JwtAuthenticationFilter не проверяет account status (enabled/locked)
  Severity: minor
  File: `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java:50-57`
  Why: После `loadUserByUsername` фильтр создаёт `UsernamePasswordAuthenticationToken` напрямую, без вызова `AccountStatusUserDetailsChecker`. `UserPrincipal.isEnabled()` корректно отражает БД, но фильтр его игнорирует. В результате если у пользователя отозваны права (UPDATE users SET enabled=false) — он продолжит ходить с валидным access-токеном до его истечения (до 15 минут). На уровне `DaoAuthenticationProvider` (login) проверка есть, но JWT-flow её обходит. Цитата кода: `UserDetails userDetails = userDetailsService.loadUserByUsername(username); UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());` — после loadUserByUsername нет ни одного `if (!userDetails.isEnabled())` и ни одного `new AccountStatusUserDetailsChecker().check(userDetails)`.
  Suggestion: Перед `setAuthentication` вызвать `new AccountStatusUserDetailsChecker().check(userDetails)` (бросит `DisabledException`/`LockedException` — поймать и не аутентифицировать), либо явно `if (!userDetails.isEnabled()) return;`.

Finding F2: AuthService.refresh не проверяет enabled-флаг при выдаче новой пары токенов
  Severity: minor
  File: `backend/src/main/java/com/example/bookserver/auth/AuthService.java:91-103`
  Why: `refresh` после валидации refresh-токена делает `userDetailsService.loadUserByUsername(username)`, но не проверяет `principal.isEnabled()`. Деактивированный пользователь с непрошедшим истечения refresh-токеном (TTL 30d) может бесконечно ротировать пары access+refresh. Цитата: `UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username); return TokenResponse.bearer(jwtService.generateAccessToken(principal), jwtService.generateRefreshToken(principal));` — между загрузкой и выдачей токенов нет ни одного account-status check.
  Suggestion: Перед выдачей токенов: `if (!principal.isEnabled()) throw new BadCredentialsException("Account disabled");` (или `new AccountStatusUserDetailsChecker().check(principal)`).

Finding F3: Глобальный `SecurityRequirement` в OpenApiConfig помечает публичные эндпойнты как защищённые в Swagger UI
  Severity: nit
  File: `backend/src/main/java/com/example/bookserver/config/OpenApiConfig.java:28`
  Why: `.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))` применяет требование Bearer-auth ко **всем** операциям без исключения. В UI пункты `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`, `/actuator/health`, `/api/public/**` будут показываться с замком и попытаются отправлять `Authorization` хедер из «Authorize» диалога, хотя по факту permitAll. Это вводит фронт-разработчика в заблуждение и ломает «Try it out» без логина. Сами security-rules при этом корректны — проблема только в OpenAPI-документе. Цитата: `.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));`.
  Suggestion: Убрать глобальный `addSecurityItem` (оставить только определение схемы в `Components`) и пометить защищённые контроллеры/методы вручную через `@SecurityRequirement(name = "bearerAuth")` на классе/методе. Альтернатива — оставить глобальный, но добавить `@io.swagger.v3.oas.annotations.security.SecurityRequirements({})` (пустой) на `register/login/refresh` и `actuator`-эндпойнтах.

## REVIEW SUMMARY:
Issue 1:
- **Type:** Security
- **Severity:** Warning
- **Location:** `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java:50-57`
- **Description:** JWT-фильтр не вызывает `AccountStatusUserDetailsChecker` после `loadUserByUsername` — disabled-пользователь с действующим access-токеном продолжает иметь доступ до его истечения.

Issue 2:
- **Type:** Security
- **Severity:** Warning
- **Location:** `backend/src/main/java/com/example/bookserver/auth/AuthService.java:91-103`
- **Description:** `refresh` не проверяет `UserPrincipal.isEnabled()` перед выдачей новой пары токенов — деактивированный пользователь может бесконечно ротировать токены.

Issue 3:
- **Type:** Documentation
- **Severity:** Warning
- **Location:** `backend/src/main/java/com/example/bookserver/config/OpenApiConfig.java:28`
- **Description:** Глобальный `addSecurityItem` помечает все эндпойнты (включая `/api/auth/login`, `/actuator/health`, `/api/public/**`) как защищённые в OpenAPI/Swagger UI — UX-несоответствие реальным правилам безопасности.

## SUMMARY
- Реализация Task 03 в целом корректна: Security DSL (lambda, stateless, permitAll-листы), JWT-сервис (jjwt 0.12, HS256, key length validation, access/refresh typ-claim), JPA-маппинг `users`/`roles`/`user_roles` (соответствует Liquibase 003), `JpaConfig` (SB4 `EntityScan` пакет, `OffsetDateTime` provider), профили `application*.yml` (dev default ≥32, prod без default, `ddl-auto=validate`, `open-in-view=false`), `GlobalExceptionHandler` (все требуемые исключения, no stacktrace leak), Testcontainers с `postgres:16-alpine`, 9 IT-тестов закрывают register/login/me/refresh + edge cases, `HealthController.java` удалён, OpenAPI `bearerAuth` scheme присутствует, springdoc 3.0.3.
- Найдено 3 minor/nit замечания (см. F1–F3). Все они — security-defense-in-depth и UX OpenAPI, никаких blocker/critical багов нет.
- Можно передавать coding-агенту на доработку либо принимать как есть (контракт Task 03 удовлетворён).
