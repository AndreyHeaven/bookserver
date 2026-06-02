# Triage Task 03 — Review-B findings

**Дата:** 2026-06-02
**Базовый commit:** `d83513b` (Task 03 — Spring Boot 4 core / Security / JWT / Liquibase / OpenAPI)
**Источники:** `review-b-task-03.md` (Review-A не выполнялся — cancelled by user).

**Принцип triage:**
- **TP-now** = чинить в одной fix-wave до Task 04. Ломает безопасность/функциональность Task 04/05, ломает контракт фронта (Task 09), или это лёгкая документация/однострочный фикс, не требующий нового контекста.
- **TP-deferred** = реальная проблема, удобнее решать с контекстом Task 04/06/09; задокументировать как known follow-up в `task-03-result.md §7` / `db/changelog/README.md` / `PLAN.md`.
- **FP / No-action** = out-of-scope, by-spec, или verification-only.

**Дубликаты:** F2 ≡ F17 (одинаковый механизм, `DataIntegrityViolationException` → 500; решается одним хендлером).

---

## Сводная таблица

| ID | Severity | Triage | Action |
|----|----------|--------|--------|
| F1 | major | **TP-now** | Вынести CORS origins в `app.cors.allowed-origins` + профильная конфигурация |
| F2 ≡ F17 | major | **TP-now** | Добавить `@ExceptionHandler(DataIntegrityViolationException)` → 409 |
| F3 | major | **TP-deferred** | Задокументировать как known issue в result.md §7 + README.md + backlog |
| F4 | major | **TP-now** | `AccountStatusUserDetailsChecker` в `JwtAuthenticationFilter` и `AuthService.refresh` |
| F5 | major | **TP-now** | Убрать глобальный `addSecurityItem(bearerAuth)` из `OpenApiConfig` |
| F6 | minor | **FP** | Bean Validation by spec; current behavior acceptable |
| F7 | minor | **TP-deferred** | Документировать как item для Task 04 fix-wave |
| F8 | nit | **TP-now** | Добавить warning-комментарий в `application-dev.yml` |
| F9 | minor | **TP-deferred** | Документировать в result.md; убрать default в base — небольшая правка для Task 10 |
| F10 | nit | **TP-deferred** | Отложить до Task 09 (косметика Swagger) |
| F11 | minor | **TP-deferred** | Документировать trade-off в result.md §7 |
| F12 | nit | **TP-deferred** | Документировать; улучшить логирование в Task 09/10 (operational) |
| F13 | nit | **TP-now** | Заменить `OffsetDateTime.now()` → `OffsetDateTime.now(ZoneOffset.UTC)` в 2 местах |
| F14 | nit | **TP-deferred** | Документировать решение в javadoc `JwtService` |
| F15 | nit | **No-action** | Verification only; всё корректно |
| F16 | minor | **TP-now** (partial) | Добавить 2 теста для F2 и F4; refresh blank / unknown-user отложить в Task 04 |
| F17 | minor (dup F2) | **TP-now** | Решается фиксом F2 |
| F18 | nit | **TP-deferred** | Документировать как design-нюанс для Task 09 |
| CORS allow-credentials | nit | **TP-now** | Снять `setAllowCredentials(true)` (bundle с F1) |

---

## Per-finding triage

### F1 — CORS origins захардкожены, dev утекают в prod

```
[F1]: TP-now
  Обоснование: Hardcoded list смешивает dev-origins (`localhost:5173/3000`) и
    реальную prod-конфигурацию в одном профиле. Это (a) security risk: любой
    localhost-сервис на прод-хосте может вызвать API через браузер,
    (b) функциональный блок: реальный prod-домен не пускается. Review-B
    включает F1 в рекомендованный порядок фикса (F2 → F4 → F5 → F1 → F3),
    значит fix-wave перед Task 04. Фикс однострочный по сути (extract в
    @Value / @ConfigurationProperties).
  Fix direction:
    - SecurityConfig.java:75-83: убрать хардкод, читать через
      @Value("${app.cors.allowed-origins}") List<String> origins
      или @ConfigurationProperties("app.cors").
    - application-dev.yml: app.cors.allowed-origins: http://localhost:5173,http://localhost:3000
    - application-prod.yml: app.cors.allowed-origins: ${APP_CORS_ALLOWED_ORIGINS}
      (без default, fail-fast в prod).
    - application-test.yml: можно оставить пустую/dev-конфигурацию.
    - Заодно решить CORS allow-credentials (см. ниже).
```

### F2 ≡ F17 — DataIntegrityViolationException → 500

```
[F2 ≡ F17]: TP-now
  Обоснование: Прямо ломает функциональность register (race condition →
    500 вместо 409). Влияет также на Task 06: importer будет пытаться
    вставлять книги по `books.md5` — любая race-вставка тоже даст 500.
    Это explicit example TP-now из задачи.
  Fix direction:
    - GlobalExceptionHandler.java: добавить
      @ExceptionHandler(DataIntegrityViolationException.class)
      public ResponseEntity<ApiError> handleDataIntegrity(
          DataIntegrityViolationException ex, HttpServletRequest req) {
          return ResponseEntity.status(CONFLICT).body(build(CONFLICT,
              "Resource conflict (unique constraint violation)", req, null));
      }
    - Логировать ex.getMostSpecificCause() на WARN без значений параметров.
    - Импортировать org.springframework.dao.DataIntegrityViolationException.
```

### F3 — refresh-token replay не защищён

```
[F3]: TP-deferred
  Обоснование: Реальная security gap, но полноценный фикс требует:
    (a) добавить `jti` claim в JWT;
    (b) Liquibase changeset на таблицу `refresh_tokens` (jti, user_id,
        issued_at, expires_at, used_at, revoked);
    (c) изменения в AuthService.refresh для whitelist-check и rotation;
    (d) cleanup-job для expired tokens.
    Это backlog-уровень scope, делать удобнее после Task 04 (JPA entities)
    в отдельной задаче. В Task 03 — **обязательно задокументировать**
    как known limitation, явно отметить статус «отложено», чтобы это не
    осталось «забытым» security risk.
  Document direction:
    - task-03-result.md §7 (Known issues): добавить пункт
      "10. Refresh-token replay protection — DEFERRED. Currently `refresh`
      rotates access/refresh pair but does NOT invalidate the previous
      refresh token (TTL 30d). Mitigation plan: separate backlog task
      adding `refresh_tokens` whitelist table + `jti` claim + rotation-
      invalidation. See db/changelog/README.md."
    - db/changelog/README.md: новый раздел "Known auth limitations":
      ссылка на отдельный changeset (например, 007-refresh-tokens.xml,
      to be added in backlog task). Поле jti UUID, user_id FK, used_at
      timestamptz, revoked boolean.
    - PLAN.md: дописать в Task 03 строку "Follow-up в backlog: refresh-
      token rotation invalidation (jti+blacklist)".
```

### F4 — JwtAuthenticationFilter не проверяет enabled/locked

```
[F4]: TP-now
  Обоснование: Security-критично: disabled user продолжает работать
    до exp access-token (15 мин). Когда Task 05 добавит /api/books с
    ACL, любая операция soft-delete пользователя становится бесполезной
    в окне до 15 минут. Это явный TP-now пример из задачи.
  Fix direction:
    - JwtAuthenticationFilter.java:50-58: после loadUserByUsername вызвать
      new AccountStatusUserDetailsChecker().check(userDetails);
      обернуть в try/catch на AccountStatusException
      (DisabledException/LockedException/AccountExpiredException),
      в catch — log.debug + не выставлять Authentication
      (тогда anyRequest().authenticated() автоматически даст 401).
    - AuthService.refresh: после loadUserByUsername сделать аналогичную
      проверку через AccountStatusUserDetailsChecker (одна строка) перед
      выдачей нового token pair.
    - Импортировать org.springframework.security.authentication.AccountStatusUserDetailsChecker.
```

### F5 — глобальный bearerAuth в OpenAPI

```
[F5]: TP-now
  Обоснование: Прямо ломает контракт для Task 09 (фронт-разработка):
    auto-generated client SDK будет требовать Bearer на /api/auth/login
    и /api/auth/refresh. Также вводит в заблуждение Swagger UI. Это
    explicit example TP-now из задачи.
  Fix direction:
    Вариант A (рекомендуется): убрать `.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))`
    из OpenApiConfig.java:28, оставить только `securityScheme`. На защищённые
    endpoints в Task 04+ ставить @SecurityRequirement(name="bearerAuth")
    на класс/метод. Task 04 fix-wave включит этот шаг для контроллеров
    книг/авторов/жанров.
    Вариант B: оставить глобальный, добавить @SecurityRequirements({})
    в AuthController на register/login/refresh. Хуже scalability.
    Выбираем A: убрать глобальный, явно проставить на BooksController/etc.
```

### F6 — email blank → null силой Bean Validation

```
[F6]: FP
  Обоснование: Поведение `@Email` соответствует спецификации Bean
    Validation: null и empty string проходят, для строгой валидации
    нужен `@NotBlank` или `@Pattern`. Текущая нормализация в сервисе
    (`isBlank() → null`) приемлема для DTO contract. Контракт API
    остаётся стабильным: клиент может присылать null / отсутствующий
    email / пустую строку. Не блокирует, не нарушает security.
    Out-of-scope для Task 03 fix-wave.
```

### F7 — test cleanup через deleteAll, нет TRUNCATE

```
[F7]: TP-deferred
  Обоснование: Для текущих 9 тестов AuthControllerIT (только `users` +
    `user_roles`) `deleteAll()` работает корректно (FK cascade срабатывает).
    Реальная необходимость возникнет в Task 04 (когда появятся `books`,
    `book_authors`, FTS-триггеры): тогда нужен TRUNCATE ... RESTART
    IDENTITY CASCADE в shared helper'е. Это explicit example TP-deferred
    из задачи. Делать сейчас — преждевременно (нет таблиц с триггерами).
  Document direction:
    - task-03-result.md §7: добавить пункт
      "11. Test cleanup via userRepository.deleteAll() — Task 04 fix-wave
      MUST replace with shared `protected void truncateAll()` helper in
      AbstractIntegrationTest using EntityManager.createNativeQuery(
      'TRUNCATE TABLE users, user_roles, books, book_authors, ... RESTART
      IDENTITY CASCADE'). Reason: row-level FTS triggers (see Task 02 README
      §Known FTS limitations) DO NOT fire on TRUNCATE; tests that rely on
      tsvector recompute must reset state via TRUNCATE."
    - PLAN.md: дополнить Task 04 строкой "Implement TRUNCATE-based test
      cleanup helper in AbstractIntegrationTest (carry-over from Task 03)".
```

### F8 — warning о SQL TRACE в application-dev.yml

```
[F8]: TP-now
  Обоснование: Документация = всегда TP-now (по принципу из задачи).
    YAML-комментарий из 2 строк, не блокирует ничего, защищает от
    случайного включения TRACE с утечкой password_hash/PII.
  Fix direction:
    application-dev.yml:19: добавить блок-комментарий перед
    `org.hibernate.SQL: DEBUG`:
      # DEV ONLY. NEVER raise to TRACE and do NOT enable
      # org.hibernate.orm.jdbc.bind=TRACE: that would log SQL parameter
      # values, including password_hash (BCrypt) and PII (email, username)
      # on INSERT/UPDATE.
```

### F9 — JWT_SECRET пустой дефолт в base

```
[F9]: TP-deferred
  Обоснование: Реальное поведение fail-fast УЖЕ работает: prod-профиль
    fail-fast'ит на Spring placeholder, dev/test fail-fast'ит на
    @PostConstruct-проверке JwtService. Текущий код безопасен, просто
    "ortсsтороннее" поведение fail-fast (два разных механизма). Не блокирует
    Task 04/05. Лучше делать в Task 10 (Docker + .env design) когда вся
    конфигурация секретов будет в одном месте.
  Document direction:
    - task-03-result.md §7: добавить пункт
      "12. JWT secret base default — `application.yml:34` имеет
      `${JWT_SECRET:}` (пустой default). Fail-fast работает через два
      разных механизма (prod: Spring placeholder, dev/test: @PostConstruct).
      Cleanup для Task 10 docker-compose work: убрать default в base,
      каждый профиль обязан переопределить (prod через env,
      dev/test — long default in profile YAML)."
    - PLAN.md: дополнить Task 10 строкой "Unify JWT_SECRET fail-fast
      via single Spring placeholder (remove base default)".
```

### F10 — нет @Tag/@Operation в AuthController

```
[F10]: TP-deferred
  Обоснование: Swagger UI работает, endpoints видны. Описания нужны
    для UX Task 09 (Vue frontend), но клиент SDK работает без них.
    Review-B сам рекомендует "отложить до Task 09 как косметику".
    Делать сейчас — преждевременно (нет привязки к фронт-схемам).
  Document direction:
    - task-03-result.md §7: добавить пункт
      "13. OpenAPI annotation polish — AuthController endpoints currently
      lack @Tag/@Operation/@ApiResponse. Will be added in Task 09 along
      with all other controllers (Books/Authors/Genres/Lists) for
      consistent Swagger UI presentation and SDK generation."
    - PLAN.md: дополнить Task 09 строкой "Add @Tag/@Operation
      annotations to all REST controllers".
```

### F11 — UserDetails из БД на каждый запрос

```
[F11]: TP-deferred
  Обоснование: Выбран осознанный trade-off (мгновенная ревокация ролей
    vs +2 SQL per request). Не блокирует Task 04/05; реальный bottleneck
    появится только при нагрузочных тестах /api/books (Task 05+).
    Документировать сейчас как trade-off, при необходимости оптимизировать
    в Task 05 с Caffeine TTL 30s.
  Document direction:
    - task-03-result.md §7: добавить пункт
      "14. UserDetails per-request fetch — JwtAuthenticationFilter
      reloads UserDetails from DB on every authenticated request
      (+2 SQL: users + user_roles). Trade-off: immediate role revocation
      vs throughput. Optimization deferred to Task 05 (Caffeine cache,
      TTL ~30s); revisit if profiling shows hotspot."
```

### F12 — silent DEBUG на невалидных токенах

```
[F12]: TP-deferred
  Обоснование: Operational concern, не security gap. В DEBUG-логах вся
    информация уже есть (просто не различается expired/signature/malformed).
    Real-world prod-логи обычно на INFO/WARN — лучше уточнить, когда
    Task 10 будет настраивать production-логирование.
  Document direction:
    - task-03-result.md §7: добавить пункт
      "15. JWT validation logging granularity — JwtService.parseAndValidate
      currently swallows all exceptions into Optional.empty() with single
      log.debug. Production observability would benefit from differentiated
      log.warn for SignatureException (potential attack) vs log.debug for
      ExpiredJwtException (normal). Address in Task 10 along with overall
      logging configuration."
```

### F13 — OffsetDateTime.now() без UTC

```
[F13]: TP-now
  Обоснование: Тривиальный 1-line фикс в 2 местах, улучшает консистентность
    timestamps между БД (UTC через JpaConfig.offsetDateTimeProvider) и API
    responses (ApiError.timestamp). Frontend (Task 09) будет форматировать
    эти timestamps; разница TZ ломает упорядочивание/сравнение. Бандлится
    с другими fixes без overhead.
  Fix direction:
    - GlobalExceptionHandler.java:78 (метод build):
      OffsetDateTime.now() → OffsetDateTime.now(ZoneOffset.UTC)
    - SecurityConfig.java:120 (writeJsonError):
      body.put("timestamp", OffsetDateTime.now().toString())
      → body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString())
    - Импорт `java.time.ZoneOffset` в оба файла.
```

### F14 — roles claim не используется фильтром

```
[F14]: TP-deferred
  Обоснование: Архитектурное решение «всегда свежие роли через БД» уже
    принято (см. также F11). claim в payload — небольшой overhead (~30B
    per role), не security risk. Удаление claim'а — clean-up, документирование
    выбора — javadoc. Можно сделать в Task 05 fix-wave, когда станет ясно,
    нужен ли claim для каких-нибудь нестандартных flow.
  Document direction:
    - task-03-result.md §7: добавить пункт
      "16. JwtService.CLAIM_ROLES is currently a payload-side dead weight
      — JwtAuthenticationFilter does NOT read it; authorities come from
      UserDetails.getAuthorities(). Trade-off decision: keep claim for
      future stateless-fast-path optimization OR remove it. Defer to Task
      05 (REST API with ACL) when access patterns are clearer."
    - JwtService.java: добавить javadoc на CLAIM_ROLES со ссылкой на
      решение в result.md.
```

### F15 — verification, no action

```
[F15]: No-action
  Обоснование: Positive verification: `/actuator/health` show-details=
    when-authorized + анонимный запрос → детали скрыты. Подтверждено
    в task-03-result.md §5 (curl output). Зафиксировано в Review-B для
    полноты, не требует никаких изменений.
```

### F16 — пропуски в тестах

```
[F16]: TP-now (partial) + TP-deferred (partial)
  Обоснование: Тесты для F2 (duplicate register → 409) и F4 (disabled-user
    login → 401, disabled-user /me with valid token → 401) ОБЯЗАТЕЛЬНЫ
    в текущей fix-wave для верификации фиксов. Без них фикс F2/F4
    не доказан. Это делает их TP-now.
    Тесты для `refresh с blank/null` (валидация @NotBlank — уже работает
    de-facto через @Valid, IT не критичен) и `me с удалённым user`
    (edge case) — менее приоритетны, могут пойти в Task 04 fix-wave.
  Fix direction (TP-now часть):
    - AuthControllerIT.java: добавить 2 теста:
      * register_duplicate_username_returns_409() — сделать 2 register с
        одинаковым username, ожидать 409 от GlobalExceptionHandler.
      * login_with_disabled_user_returns_401() — register user, set
        enabled=false через прямой UPDATE / UserRepository, login —
        ожидать 401.
      * (опционально) me_with_disabled_user_returns_401() — login,
        затем UPDATE enabled=false, /me с access-токеном — ожидать 401
        (проверка F4 в filter pipeline).
  Document direction (TP-deferred часть):
    - task-03-result.md §7: "Дополнительные IT (refresh blank/null,
      me with deleted user) — добавить в Task 04 fix-wave вместе с
      TRUNCATE-cleanup helper'ом."
```

### F17 — дубликат F2 по email

```
[F17]: TP-now (решается фиксом F2)
  Обоснование: Тот же механизм (race condition между existsByEmail и
    save → DataIntegrityViolationException). Фикс из F2 (универсальный
    @ExceptionHandler) покрывает оба случая (duplicate username +
    duplicate email). Отдельной правки не требует.
```

### F18 — expired vs invalid в refresh

```
[F18]: TP-deferred
  Обоснование: Design-нюанс UX фронта (показывать «session expired» vs
    «invalid token»). Не блокер, не security gap (current behavior
    безопасно не leak'ает причину). Делать удобнее в Task 09 при
    дизайне error-handling на фронте.
  Document direction:
    - task-03-result.md §7: "17. Refresh error granularity — current
      `refresh` returns 401 with generic 'Invalid or expired refresh
      token'. UX improvement: distinguish ExpiredJwtException → code
      'refresh_expired' (UI may show 'Session expired, please log in
      again') vs other → code 'refresh_invalid'. Add error code in
      ApiError when frontend (Task 09) wires session-expiry UX."
    - PLAN.md: дополнить Task 09 строкой "Wire refresh error codes
      from backend into UI session-expiry flow".
```

### CORS allow-credentials

```
[CORS allow-credentials]: TP-now (bundle с F1)
  Обоснование: Не нужен для JWT в Authorization header (нужен только для
    cookie-based auth). Снятие упрощает CORS-конфигурацию: позволяет
    использовать setAllowedOriginPatterns для wildcard origin, что
    удобно для Task 09 dev (Vite preview ports varies). Bundling
    с F1 — естественно, обе правки в CorsConfigurationSource.
  Fix direction:
    - SecurityConfig.java: убрать configuration.setAllowCredentials(true).
    - Опционально перейти на setAllowedOriginPatterns (вместо setAllowedOrigins)
      если требуется wildcard в dev. Для prod — экзakt origins.
```

---

## Готовность к Task 04 после fix-wave

После предложенных TP-now фиксов:

- ✅ `GlobalExceptionHandler` корректно ловит unique-violation (важно для
  Task 06 importers, где `books.md5` будет дедуплицироваться).
- ✅ `JwtAuthenticationFilter` корректно отказывает disabled-юзерам
  (важно для Task 05 ACL на /api/books, /api/users/me/lists).
- ✅ OpenAPI без глобального bearerAuth — Task 04+ контроллеры явно
  ставят `@SecurityRequirement` на защищённые методы.
- ✅ CORS вынесен в конфигурацию — Task 09 фронт корректно работает
  на любом порту через env override.
- ✅ Timestamps API в UTC — Task 09 frontend сортирует/сравнивает
  с timestamp'ами от БД без TZ-математики.
- ✅ Documentation в `task-03-result.md §7` отражает оба исправленных
  и отложенные limitation'ы (F3, F7, F9, F10, F11, F12, F14, F18, F16-partial).
