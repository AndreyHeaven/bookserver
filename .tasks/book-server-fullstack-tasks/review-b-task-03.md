# Review-B for Task 03 (Backend Core: Security/JWT/Liquibase/OpenAPI)

Commit: `d83513b`
Reviewer focus: edge cases, security risks (JWT, password handling, race conditions), test gaps, operational concerns, готовность к Task 04+.

---

## Findings

### Finding F1: CORS-конфигурация одинакова для dev и prod, dev-origins утекают в prod
- **Severity:** major
- **File:** `backend/src/main/java/com/example/bookserver/config/SecurityConfig.java:75-83`
- **Why:** `CorsConfigurationSource` зашит хардкодом, без `@Profile`/конфиг-плейсхолдеров: `http://localhost:5173`, `http://localhost`, `http://localhost:3000` будут активны и в `prod`. Это (а) увеличивает attack surface (любой `localhost`-сервис на машине, где работает прод-backend, сможет дёргать API из браузера) и (б) не пускает реальный production-домен, который понадобится в Task 09/10. Review-B-задача прямо спрашивает: «Не лишние ли там dev-origins в prod-профиле?» — да, лишние.
- **Suggestion:** Вынести origin-лист в `app.cors.allowed-origins` (List<String>), читать через `@Value` / `@ConfigurationProperties`. В `application-dev.yml` оставить `localhost:5173,localhost:3000`, в `application-prod.yml` сделать обязательным (без дефолта), задавать через env `APP_CORS_ALLOWED_ORIGINS`.

### Finding F2: race condition на параллельный `register` → 500 вместо 4xx
- **Severity:** major
- **File:** `backend/src/main/java/com/example/bookserver/web/GlobalExceptionHandler.java:21-67`
- **Why:** В `AuthService.register` есть проверки `existsByUsername` / `existsByEmail` до `save`, но между двумя параллельными запросами окно есть. При коллизии Hibernate выкидывает `org.springframework.dao.DataIntegrityViolationException` (обёртка над PG-`unique_violation` 23505). В `GlobalExceptionHandler` нет хендлера для этого исключения → попадает в `handleGeneric` и возвращает `500 Internal Server Error`. Review-B явно спрашивает про этот сценарий.
- **Suggestion:** Добавить:
  ```java
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
      ApiError body = build(HttpStatus.CONFLICT, "Resource conflict (unique constraint violation)", req, null);
      return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }
  ```
  Опционально логировать `ex.getMostSpecificCause()` без значений колонок.

### Finding F3: refresh-token replay не защищён (нет jti+blacklist, нет rotation-invalidation)
- **Severity:** major (если рассматривать как security, иначе minor с пометкой «отложено»)
- **File:** `backend/src/main/java/com/example/bookserver/auth/AuthService.java:91-103`, `backend/src/main/java/com/example/bookserver/security/JwtService.java:84-93`
- **Why:** `refresh` валидирует `typ=refresh` и выдаёт новый access+refresh, но **старый refresh-token остаётся валидным до своего exp (30 дней)**. Token, утёкший один раз, может быть использован параллельно много раз. В Task 03 нет ни `jti`-claim'а, ни blacklist/whitelist хранилища, ни ревокации при rotation. Это явно отсутствует в коде, при этом в задаче спрашивают «есть или явно отложено?» — в коде/README/`task-03-result.md §7` это **не задокументировано как отложено**.
- **Suggestion:** Минимально: явно зафиксировать в `task-03-result.md §7 Known issues` и в `db/changelog/README.md` как известное ограничение, со ссылкой на follow-up (отдельная задача в backlog'е до Task 09). Для зрелого решения — добавить `jti` UUID в claims, таблицу `refresh_tokens` (Liquibase changeset 003 или новый 007), при `refresh` проверять `jti` в whitelist и помечать использованным.

### Finding F4: `JwtAuthenticationFilter` не проверяет `isEnabled`/`isAccountNonLocked` — disabled-юзер продолжает работать по access-token
- **Severity:** major
- **File:** `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java:50-58`
- **Why:** После `loadUserByUsername` фильтр сразу собирает `UsernamePasswordAuthenticationToken` без вызова `UserDetailsChecker`/`AccountStatusUserDetailsChecker`. Соответственно `UserPrincipal.isEnabled()==false` игнорируется до тех пор, пока access-token не истечёт (15 минут). Для login через `AuthenticationManager` `DaoAuthenticationProvider` сам проверит `isEnabled()`, а вот в JWT-pipeline проверка отсутствует. Это типичная дырка «soft delete не действует мгновенно».
- **Suggestion:** В `JwtAuthenticationFilter.doFilterInternal` после `loadUserByUsername` добавить:
  ```java
  new AccountStatusUserDetailsChecker().check(userDetails);
  ```
  и поймать `DisabledException`/`LockedException` — отправлять 401 через `authenticationEntryPoint` или просто `log.debug` + не выставлять Authentication (тогда `anyRequest().authenticated()` сработает на 401). То же относится к `AuthService.refresh` (там сейчас тоже нет проверки enabled).

### Finding F5: OpenAPI глобально требует `bearerAuth` — `/api/auth/login`/`register`/`refresh` помечены как защищённые
- **Severity:** major (для UX Swagger и для генерации client SDK), minor для самой безопасности
- **File:** `backend/src/main/java/com/example/bookserver/config/OpenApiConfig.java:28`
- **Why:** `.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))` — это **глобальное** требование, оно применяется к каждой операции, включая публичные `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`, `/actuator/health`. В сгенерированном `/v3/api-docs` эти endpoints будут отображены как «требуют Bearer», что (а) вводит в заблуждение фронт-разработчиков и (б) сломает автоматически сгенерированных клиентов (axios interceptor будет требовать токен на login). Review-B прямо спрашивает: «`bearerAuth` отображается на `/api/auth/*`?» — да, отображается **на всех**.
- **Suggestion:** Убрать глобальный `addSecurityItem`, оставить только `securityScheme`. В каждом защищённом контроллере/методе (Task 04+) использовать `@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")`. Альтернатива: оставить глобальный, но в `AuthController` явно поставить `@SecurityRequirements({})` на `register`/`login`/`refresh`.

### Finding F6: `email = ""` (пустая строка) превращается в `null` молча — `@Email` это пропускает
- **Severity:** minor
- **File:** `backend/src/main/java/com/example/bookserver/auth/dto/RegisterRequest.java:9`, `backend/src/main/java/com/example/bookserver/auth/AuthService.java:58`
- **Why:** `@Email` пропускает пустую строку (по спецификации Bean Validation null/empty валидны). В сервисе `request.email().isBlank()` конвертирует в null. С точки зрения работы — OK. Но `@Email` без `@Size(min=...)` пропустит явно «не-email» вида `"   "` (пробелы → `isBlank()==true` → null) или мусор `"a"` (НЕ blank, но `@Email` на одиночном символе тоже даст false). Это не блокер, но контракт API становится мутным.
- **Suggestion:** Либо принимать только `null`/валидный email (отдельный normalize в DTO/MapStruct: пустая строка через `@JsonSetter(nulls = Nulls.SKIP)` или `String email = email == null ? null : email.trim().isBlank() ? null : email.trim()`). Не критично для Task 03.

### Finding F7: тесты используют `userRepository.deleteAll()` вместо `TRUNCATE ... RESTART IDENTITY CASCADE`
- **Severity:** minor (для текущего AuthControllerIT — OK, для Task 04+ — критично)
- **File:** `backend/src/test/java/com/example/bookserver/auth/AuthControllerIT.java:36-38`
- **Why:** `db/changelog/README.md` § «TRUNCATE» прямо указывает: row-triggers не срабатывают на TRUNCATE, и для очистки между тестами нужно использовать `TRUNCATE ... RESTART IDENTITY CASCADE`. В `AuthControllerIT` сейчас просто `deleteAll()` на UserRepository — это не сбрасывает `users_id_seq`, не трогает `user_roles` напрямую (хотя CASCADE FK сработает), и в Task 04 (когда добавится `books`, `book_authors` с FTS-триггерами) подход уже не сработает. Также: shared static container — `@BeforeEach deleteAll()` для каждого теста создаёт несколько SELECT+DELETE, не оптимально.
- **Suggestion:** Завести в `AbstractIntegrationTest` метод `protected void truncateAll()` через `EntityManager.createNativeQuery("TRUNCATE TABLE users, user_roles ... RESTART IDENTITY CASCADE").executeUpdate()` (с `@Transactional` или JdbcTemplate). Сейчас оставить как есть, но **обязательно зафиксировать в `task-03-result.md §7` как item для Task 04 fix-wave**.

### Finding F8: `org.hibernate.SQL: DEBUG` в dev — допустимо, но опасно при ужесточении до TRACE
- **Severity:** minor (nit)
- **File:** `backend/src/main/resources/application-dev.yml:19`
- **Why:** Сейчас включён `org.hibernate.SQL: DEBUG` — пишет SQL без bind-значений, что безопасно. Но если кто-то поднимет до `TRACE` или добавит `org.hibernate.orm.jdbc.bind: TRACE`, в логи попадут значения `password_hash` (BCrypt-хеш, не plain) и любые PII (email, username) при INSERT/UPDATE. Это нужно явно зафиксировать.
- **Suggestion:** Добавить комментарий рядом с `org.hibernate.SQL: DEBUG`:
  ```yaml
  # DEV ONLY. Не повышать до TRACE и не добавлять org.hibernate.orm.jdbc.bind=TRACE:
  # это писало бы значения параметров (включая password_hash, email) в лог.
  ```

### Finding F9: `app.security.jwt.secret` в base `application.yml` имеет пустой дефолт `${JWT_SECRET:}`, prod-фейл-фаст зависит от двух разных механизмов
- **Severity:** minor
- **File:** `backend/src/main/resources/application.yml:34`, `backend/src/main/resources/application-prod.yml:12`, `backend/src/main/java/com/example/bookserver/security/JwtService.java:55-60`
- **Why:** В base `secret: ${JWT_SECRET:}` (пустой дефолт). В prod профиль перегружает на `${JWT_SECRET}` без default → Spring placeholder resolution **выкинет `IllegalArgumentException` на старте**, что хорошо. Но в test/dev-сценариях, где переменная не задана и base-конфиг применяется без переопределения профилем, мы попадаем в `JwtService.@PostConstruct` с blank-значением и там фейлимся. Два разных пути fail-fast — рабочее, но запутанное. Также: base-default `""` означает, что в profile, который случайно забудет переопределить `app.security.jwt.secret`, поведение зависит исключительно от `@PostConstruct`-проверки.
- **Suggestion:** Убрать дефолт в base: `secret: ${JWT_SECRET}`. Dev-профиль переопределяет на длинный dev-default. Тест-профиль — на test-default. Тогда prod и любой не-настроенный профиль одинаково падают на Spring placeholder, ещё до создания бина — раньше и понятнее.

### Finding F10: `AuthController` не имеет `@Tag` / OpenAPI-аннотаций — Swagger покажет endpoint без описания
- **Severity:** nit
- **File:** `backend/src/main/java/com/example/bookserver/auth/AuthController.java:17-19`
- **Why:** Сейчас `/api/auth/*` появятся в Swagger UI с дефолтным контроллер-неймингом и без описаний параметров/ответов. Это не блокер, но Task 09 (Vue front) будет читать схему — пустые описания неудобны.
- **Suggestion:** Добавить `@Tag(name="Auth", description="...")`, `@Operation(summary=..., responses={...})`. Можно отложить до Task 09 как косметику.

### Finding F11: `JwtAuthenticationFilter` загружает UserDetails из БД **на каждый запрос** — отсутствие кэша
- **Severity:** minor (готовность к Task 05+ под нагрузкой)
- **File:** `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java:52-58`
- **Why:** Каждый запрос с Bearer-token дёргает `userRepository.findByUsername` + EAGER-fetch на `user_roles`. На /api/books, который Task 05 будет дёргать с фронта много раз, это +2 SQL на каждый GET. С другой стороны это даёт мгновенную ревокацию ролей. Trade-off, но **в коде не задокументирован**.
- **Suggestion:** Зафиксировать в `task-03-result.md §7` как known performance trade-off; если нужна оптимизация — использовать `Caffeine`/`@Cacheable` с TTL 30s. Не делать сейчас, но отметить.

### Finding F12: `JwtAuthenticationFilter` молча игнорирует `UsernameNotFoundException` и невалидные токены — невозможно отличить «нет токена» от «токен испорчен» в логах
- **Severity:** nit
- **File:** `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java:58-60`
- **Why:** `log.debug(...)` пишет только subject from token, но не саму причину; невалидные токены вообще логируются на DEBUG в `JwtService.parseAndValidate`. В production логирование DEBUG обычно выключено → невозможно увидеть, что приходят битые токены (sign by wrong secret и т.д.). Полезно для отладки и для security alerting.
- **Suggestion:** Сделать `log.warn` (или INFO) на signature-mismatch / expired (различать `ExpiredJwtException` vs `SignatureException` vs `MalformedJwtException`). Не блокер.

### Finding F13: `OffsetDateTime.now()` в `GlobalExceptionHandler.build` использует системную TZ, в `JpaConfig.offsetDateTimeProvider` — UTC; в `ApiError.timestamp` будут локальные смещения
- **Severity:** nit
- **File:** `backend/src/main/java/com/example/bookserver/web/GlobalExceptionHandler.java:78`
- **Why:** Несогласованность: записи в БД (`created_at`) идут в UTC, а ответы API — в local TZ хоста. Для агрегаторов логов и фронта это ломает упорядочивание.
- **Suggestion:** `OffsetDateTime.now(ZoneOffset.UTC)`. Аналогично в `SecurityConfig.writeJsonError` (строка `body.put("timestamp", OffsetDateTime.now().toString())`).

### Finding F14: `roles` claim хранится в access-token, но фильтр его не использует
- **Severity:** nit
- **File:** `backend/src/main/java/com/example/bookserver/security/JwtService.java:88`, `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java:52-58`
- **Why:** `roles` claim добавляется в payload, но фильтр игнорирует его, перезагружая `UserDetails` из БД. То есть claim-данные = лишний баласт в каждом ответе login (увеличивает токен на ~30 байт на роль). С плюсом: всегда свежие роли. Решение валидное, но claim тогда смысла не имеет.
- **Suggestion:** Либо убрать `CLAIM_ROLES` из payload (мертвая нагрузка), либо использовать claim'ы для построения authorities без обращения к БД (быстрее, но риск устаревших ролей). Документировать выбор в `JwtService` javadoc.

### Finding F15: `/actuator/health` показывает `groups: [liveness, readiness]`, `show-details=when-authorized` — но текущая конфигурация считает анонимный запрос "не авторизованным", деталей быть не должно, и их и нет — ОК
- **Severity:** nit (verification)
- **File:** `backend/src/main/resources/application.yml:27-29`
- **Why:** Проверил: `management.endpoint.health.show-details=when-authorized`. Анонимный запрос (без JWT) деталей не получает (curl output из result.md показывает только `{"groups":...,"status":"UP"}`). Это безопасно.
- **Suggestion:** Никаких действий не требуется. Зафиксировано здесь для полноты Review-B-чеклиста (item 12).

### Finding F16: тестов нет на (a) повторный register того же username, (b) login с `enabled=false`, (c) refresh с null/blank `refreshToken`
- **Severity:** minor (test gap)
- **File:** `backend/src/test/java/com/example/bookserver/auth/AuthControllerIT.java` (весь)
- **Why:** Review-B список из 11 пунктов прямо спрашивает: «Покрыты ли: refresh с access-токеном (должен 401), повторный register (unique violation), login с disabled=false?» — текущие 9 тестов:
  1. `register_returns_201_and_persists_user`
  2. `register_validation_returns_400`
  3. `login_with_correct_password_returns_tokens`
  4. `login_with_wrong_password_returns_401`
  5. `me_without_token_returns_401`
  6. `me_with_valid_access_token_returns_200`
  7. `refresh_with_valid_refresh_token_returns_new_tokens`
  8. `refresh_with_access_token_is_rejected` (✓ покрыт пункт)
  9. `actuator_health_is_public`

  **Не покрыты:** двойной register (важно из-за F2), `enabled=false` login, refresh с null/blank/мусором (валидация `@NotBlank` покроет, но IT нет), `me` с просроченным access-token (filter молча проигнорирует → 401, но без теста). Также нет теста timing-safe-эквивалентности ответа для «unknown user» vs «wrong password» — оба возвращают 401 (хорошо), но без явного теста.
- **Suggestion:** Добавить 4 теста в Task 04 fix-wave (когда уже точно стабилизируется JPA-слой):
  - `register_duplicate_username_returns_409` (после F2)
  - `login_with_disabled_user_returns_401` (после F4)
  - `refresh_with_blank_token_returns_400` (через `@Valid`-валидацию `RefreshRequest`)
  - `me_with_unknown_user_token_returns_401` (создать user → login → удалить user → me)

### Finding F17: `RegisterRequest.email` принимает дубликат через race: между `existsByEmail` и `save` второй register c тем же email → 500 (та же причина, что F2)
- **Severity:** minor (дубликат F2 по механизму, но отдельный сценарий)
- **File:** `backend/src/main/java/com/example/bookserver/auth/AuthService.java:59-61`
- **Why:** Review-B п.5 явно спрашивает: «`register` с существующим username, новым email → 4xx, не 500». В одиночной транзакции (sequential) `existsByUsername` отловит. Но при параллельных запросах либо username, либо email-уникальность стрельнёт через `DataIntegrityViolationException` → 500.
- **Suggestion:** Решается фикс F2.

### Finding F18: `JwtService.parseAndValidate(...)` в `AuthService.refresh` не делает разницу между «expired» и «invalid»
- **Severity:** nit
- **File:** `backend/src/main/java/com/example/bookserver/security/JwtService.java:104-112`, `backend/src/main/java/com/example/bookserver/auth/AuthService.java:92-94`
- **Why:** Любое исключение jjwt сваливается в `Optional.empty()` → `BadCredentialsException("Invalid or expired refresh token")`. Это безопасно (не leak'аем причину), но при разработке фронта неудобно: 401 без указания «токен просрочен» → пользователю придётся снова login'иться, тогда как UI мог бы показать «session expired» отдельно. Это design-нюанс.
- **Suggestion:** Не блокер. Если фронту нужно — добавить error-code в `ApiError` (например, `code: "refresh_expired"`). Документировать как minor для Task 09.

---

## Готовность к Task 04 (JPA entities)

✅ **`JpaConfig`** готов принимать остальные сущности: `@EntityScan basePackages="com.example.bookserver.domain"`, `@EnableJpaRepositories basePackages="com.example.bookserver.repo"`, `@EnableJpaAuditing` с кастомным `OffsetDateTime` provider — будет работать с `@CreatedDate`/`@LastModifiedDate` из коробки.
✅ **Audit** централизован (один `DateTimeProvider`-бин).
✅ **`AbstractIntegrationTest`** переиспользуем в `BookRepositoryIT`/etc.
⚠️ **TRUNCATE-cleanup** (F7) не реализован — Task 04 обязан добавить shared helper.
⚠️ **F4 (disabled-user check)** должен быть исправлен до Task 05, иначе ACL на /api/books обходится disabled-юзерами по access-token.

## Готовность к Task 06 (importers)

✅ Liquibase подключён, все 6 changesets применяются (testcontainers green).
⚠️ Race-condition handler (F2) необходим до начала bulk-import — иначе любая дубль-вставка по `books.md5` будет 500.
ℹ️ Логи hibernate без bind-values (F8) — OK для bulk-импорта.

## Hidden git/file issues

✅ `.gitignore` (см. `.gitignore:54-67`) корректно исключает `.env`, `.env.*` (кроме `.env.example`), `application-local.yml`, `application-*.local.yml`, `application-secrets.yml`, `*.pem/*.key/*.jks/*.keystore`, `credentials.json`, `secrets.yml`.
✅ В репозитории нет файлов `.env` / `application-local.yml` / секрет-ключей.
✅ testcontainers (`postgresql` + `junit-jupiter`) в `testImplementation` (см. `backend/build.gradle.kts:54-55`).

## Operational notes

- **JWT secret length** — валидируется в `JwtService.init()` (`< 32 bytes → IllegalStateException`), HS256 фиксирован, UTF-8 кодировка детерминирована (`getBytes(StandardCharsets.UTF_8)`). ✓
- **Алгоритм фиксирован** (`Jwts.SIG.HS256` при подписи; jjwt 0.12.x по умолчанию НЕ принимает `alg=none`). ✓
- **BCrypt rounds** — default 10 (`BCryptPasswordEncoder()` без аргумента) → ОК.
- **Timing-safe 401**: и «unknown user», и «wrong password» оба идут через `AuthenticationManager.authenticate(...)` → `BadCredentialsException`. Сообщение в ответе одинаковое (`"Invalid username or password"`). ✓
- **`@Size(min=8)` на password** — есть (`RegisterRequest`). ✓
- **CORS `setAllowCredentials(true)` + JWT в Authorization** — корректно, но `allow-credentials` для JWT в `Authorization`-header не требуется (нужен только для cookies). Снять `setAllowCredentials(true)` безопасно и упрощает CORS (можно использовать `setAllowedOriginPatterns` для wildcard).
- **Swagger UI**: `/swagger-ui.html` редиректит на `/swagger-ui/index.html` — оба доступны (см. `SecurityConfig.java:51-53`).
- **springdoc 3.0.3** — это релизная версия для Spring Boot 4, не snapshot (см. `task-03-result.md §8`).

---

## REVIEW SUMMARY

Issue 1:
- **Type:** Security
- **Severity:** Major
- **Location:** `backend/src/main/java/com/example/bookserver/config/SecurityConfig.java:75-83`
- **Description:** CORS-origins захардкожены и применяются ко всем профилям, dev-origins утекают в prod, реальный prod-домен не пускается. См. F1.

Issue 2:
- **Type:** Bug
- **Severity:** Major
- **Location:** `backend/src/main/java/com/example/bookserver/web/GlobalExceptionHandler.java:21-67`
- **Description:** Нет `@ExceptionHandler(DataIntegrityViolationException.class)` → параллельный register с тем же username/email возвращает 500 вместо 409. См. F2/F17.

Issue 3:
- **Type:** Security
- **Severity:** Major
- **Location:** `backend/src/main/java/com/example/bookserver/auth/AuthService.java:91-103`
- **Description:** Token replay не защищён: refresh-token остаётся валидным после rotation; нет jti+blacklist. В Known Issues явно не задокументировано. См. F3.

Issue 4:
- **Type:** Security
- **Severity:** Major
- **Location:** `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java:50-58`
- **Description:** Filter не проверяет `isEnabled`/`isAccountNonLocked` через `AccountStatusUserDetailsChecker` — disabled-юзер продолжает работать по access-token до его exp. Та же проблема в `AuthService.refresh`. См. F4.

Issue 5:
- **Type:** Documentation / BestPractice
- **Severity:** Major
- **Location:** `backend/src/main/java/com/example/bookserver/config/OpenApiConfig.java:28`
- **Description:** Глобальный `addSecurityItem(bearerAuth)` помечает в спецификации все endpoints, включая `/api/auth/login|register|refresh` и `/actuator/health`, как требующие Bearer — вводит в заблуждение и ломает авто-генерированных клиентов. См. F5.

Issue 6:
- **Type:** BestPractice
- **Severity:** Minor
- **Location:** `backend/src/main/resources/application.yml:34`
- **Description:** `${JWT_SECRET:}` в base даёт пустой default, fail-fast зависит от `@PostConstruct`-проверки. Лучше убрать default из base. См. F9.

Issue 7:
- **Type:** Test
- **Severity:** Minor
- **Location:** `backend/src/test/java/com/example/bookserver/auth/AuthControllerIT.java`
- **Description:** Не покрыты: повторный register (unique violation), login с `enabled=false`, refresh с blank/null, me с удалённым user. См. F16.

Issue 8:
- **Type:** Test / Maintainability
- **Severity:** Minor
- **Location:** `backend/src/test/java/com/example/bookserver/auth/AuthControllerIT.java:36-38`
- **Description:** Cleanup через `userRepository.deleteAll()` вместо TRUNCATE...RESTART IDENTITY CASCADE; не сработает для FTS-таблиц в Task 04+. См. F7.

Issue 9:
- **Type:** Performance / Documentation
- **Severity:** Minor
- **Location:** `backend/src/main/java/com/example/bookserver/security/JwtAuthenticationFilter.java:52-58`
- **Description:** UserDetails загружается из БД на каждый запрос; trade-off не задокументирован. См. F11.

Issue 10:
- **Type:** Maintainability
- **Severity:** Minor
- **Location:** `backend/src/main/java/com/example/bookserver/security/JwtService.java:88`
- **Description:** `roles` claim добавляется, но не используется фильтром — мёртвая нагрузка. См. F14.

Issue 11:
- **Type:** BestPractice
- **Severity:** Nit
- **Location:** `backend/src/main/java/com/example/bookserver/web/GlobalExceptionHandler.java:78`, `backend/src/main/java/com/example/bookserver/config/SecurityConfig.java:120`
- **Description:** `OffsetDateTime.now()` без UTC даёт локальные смещения в `timestamp`; БД пишет UTC — рассогласование. См. F13.

Issue 12:
- **Type:** Security
- **Severity:** Nit
- **Location:** `backend/src/main/resources/application-dev.yml:19`
- **Description:** Добавить комментарий-предупреждение «не повышать SQL до TRACE / не добавлять `org.hibernate.orm.jdbc.bind=TRACE`». См. F8.

Issue 13:
- **Type:** Documentation
- **Severity:** Nit
- **Location:** `backend/src/main/java/com/example/bookserver/auth/AuthController.java:17-19`
- **Description:** Нет `@Tag`/`@Operation` аннотаций — Swagger UI без описаний. Можно отложить до Task 09. См. F10.

## SUMMARY

- **5 major findings:** CORS-конфиг (F1), отсутствие handler'а на `DataIntegrityViolationException` (F2/F17), token replay не защищён и не задокументирован как отложенный (F3), `JwtAuthenticationFilter` не проверяет статус аккаунта (F4), OpenAPI глобально требует `bearerAuth` на публичных endpoints (F5).
- **6 minor:** JWT secret default в base (F9), test-cleanup без TRUNCATE (F7), trade-off DB-fetch на каждый запрос (F11), мёртвый `roles` claim (F14), пропуски в тестах (F16), CORS allow-credentials не нужен для JWT в Authorization.
- **5 nit'ов:** email blank/empty семантика (F6), UTC timestamps (F13), различение expired/invalid (F18), warn-логирование причин невалидных токенов (F12), Swagger-аннотации (F10), warning-комментарий про SQL TRACE (F8).
- **Готовность к Task 04/06:** базово готово, но F2 (handler) и F4 (disabled check) желательно поправить до Task 05, иначе security-сценарии для `/api/books` будут протекать. F7 (TRUNCATE) — обязательно в Task 04 fix-wave.
- **Hidden git/file issues:** не обнаружено — `.gitignore` правильно покрывает env/local/keys, в коммите ничего лишнего нет.
- **Эту ревью можно передать coding-agent'у; рекомендуемая последовательность фиксов:** F2 → F4 → F5 → F1 → F3 (хотя бы документация в task-03-result.md и плане) → остальные.
