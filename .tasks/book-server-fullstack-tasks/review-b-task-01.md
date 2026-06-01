# Review-B — Task 01 (project hygiene, gitignore, готовность к Task 02–10)

Date: 2026-06-01
Commit reviewed: `beb5c3c` — "chore: init gradle monorepo skeleton (Task 01)"

## Findings

### Finding F1: В коммит попал бинарный SQL-дамп ~736 MB (`sql/lib.b.annotations.sql`)

- **Severity:** blocker
- **File:** `sql/lib.b.annotations.sql` (Bin 0 -> 771 791 309 bytes); см. также `sql/lib.b.annotations_pics.sql`
- **Why:**
  `git show --stat beb5c3c` показывает в коммите файл `sql/lib.b.annotations.sql | Bin 0 -> 771791309 bytes` (≈ 736 MiB). `du -sh sql/` = `1.6G`, `du -sh .git/` = `667M`. То есть первая же ревизия раздула репозиторий до 0.7 GB. Это нарушает практически все рекомендации (GitHub hard limit 100 MB на файл; `git push` упадёт; `git clone` будет неприемлемо долгим). Папка `sql/` явно перечислена в task-файле и в `project-state.md` как «исторический MySQL-дамп для референса», то есть это исходные данные для будущего импорта, а не код. Их место — Git LFS, отдельный bucket / volume или `/data` (который, кстати, уже добавлен в `.gitignore`). Самое плохое — это сделано в первом же коммите, поэтому история ещё *тривиально* переписывается без чужих pull'ов.
- **Suggestion:**
  1. Решить, как хранить эти дампы (LFS / внешний storage / локальная папка `/data/`).
  2. Если решение — не хранить в git: добавить правило `sql/*.sql` или `sql/lib.b.*.sql` в `.gitignore`, `git rm --cached sql/lib.b.annotations.sql sql/lib.b.annotations_pics.sql` (и/или всю `sql/`), и amend / переписать единственный существующий коммит (`git commit --amend` или `git reset --soft HEAD~1 && git commit ...`). Сейчас никто кроме инициатора в репозиторий ещё не пушит, переписать историю безопасно.
  3. Если решение — хранить через LFS: установить `git lfs track "sql/*.sql"`, ре-коммитнуть, переписать историю и убрать раздутый pack из `.git/objects` (`git gc --aggressive --prune=now`). Опять же — выполнимо именно сейчас, после Task 02+ будет уже сложнее.

---

### Finding F2: `.gitignore` не покрывает типовые секреты — высокий риск утечки

- **Severity:** critical
- **File:** `.gitignore`
- **Why:**
  Действующий `.gitignore` явно перечисляет только `frontend/.env.local`, но не покрывает:
  - универсальные `.env`, `.env.*` (особенно `.env.production`, `.env.development.local`, `.env.backup`);
  - бэкендовые `application-local.yml`, `application-*.local.yml`, `application-secrets.yml`;
  - ключи и сертификаты: `*.pem`, `*.key`, `*.crt`, `*.p12`, `*.jks`, `*.keystore`;
  - креды для облачных провайдеров: `.aws/`, `.gcp/`, `credentials.json`, `secrets.yml`.
  План включает Task 03 (security/JWT — там почти наверняка появятся signing-key материалы) и Task 10 (Docker compose — `.env` для compose). Запрет на «утечку секретов» — явное требование Review-B. Текущий `.gitignore` оставляет дверь нараспашку.
- **Suggestion:** добавить блок (формулировки на уровне whitelist-исключений уточняйте по ходу):
  ```gitignore
  # --- Secrets & local config ---
  .env
  .env.*
  !.env.example
  application-local.yml
  application-*.local.yml
  application-secrets.yml
  *.pem
  *.key
  *.crt
  *.p12
  *.jks
  *.keystore
  credentials.json
  secrets.yml
  ```
  Это out-of-scope конкретных Task 03 настроек, но in-scope «project hygiene и безопасность скелета» (R-A Review-B п.1 и п.9).

---

### Finding F3: `.gitignore` слишком агрессивен по `*.jar` — может затереть нужные бинарники в будущем

- **Severity:** major
- **File:** `.gitignore:9-10`
  ```gitignore
  *.jar
  !gradle/wrapper/gradle-wrapper.jar
  ```
- **Why:**
  Whitelist для wrapper-jar реализован корректно — это +1, и `git ls-files` подтверждает, что `gradle/wrapper/gradle-wrapper.jar` коммитнут. Однако глобальный `*.jar` зацепит **любые** jar-файлы в проекте, включая будущие `libs/` (например, нестандартные fb2/inpx парсеры, если придётся подложить локальный jar в Task 06), а также `frontend/`-зависимости вроде `*.jar` от внешних шрифтов/инструментов. В реальных Gradle-репозиториях обычно ограничиваются игнорированием `build/`, `.gradle/` и `out/` (которые здесь уже есть), без `*.jar`. Кроме того, `build/` уже исключает `*.jar`, сгенерированные сборкой, поэтому глобальный `*.jar` избыточен и опасен.
- **Suggestion:** убрать строки `*.jar` и `!gradle/wrapper/gradle-wrapper.jar` (вторая больше не нужна), оставив `*.class`/`build/`/`.gradle/`/`out/`. Если параноидально хочется защититься, оставить более точечно: `**/build/libs/*.jar` (но и это излишне, т.к. `build/` уже под игнорированием).

---

### Finding F4: `.idea/misc.xml` закоммичен с машино-зависимым `project-jdk-name="21 (2)"`

- **Severity:** major
- **File:** `.idea/misc.xml:3`
  ```xml
  <component name="ProjectRootManager" version="2" languageLevel="JDK_21" default="true" project-jdk-name="21 (2)" project-jdk-type="JavaSDK">
  ```
- **Why:**
  `project-jdk-name="21 (2)"` — это имя SDK из конкретной IDE текущего разработчика (там, где уже был `21` и при подключении второго JDK 21 IDE назвала его `21 (2)`). У любого другого члена команды такого SDK-имени не будет — при открытии проекта IntelliJ покажет ошибку `Project SDK is not defined`. Аналогично уровень языка `JDK_21` противоречит фактическому Java 25 toolchain в `backend/build.gradle.kts`. Это прямо ломает onboarding следующего разработчика и противоречит цели «готовность скелета». В отчёте упомянуто, что переключение IDE — out of scope, но это не оправдывает коммит «битого» SDK-имени.
- **Suggestion:** либо
  - добавить `.idea/misc.xml` в `.gitignore` и `git rm --cached .idea/misc.xml`, либо
  - заменить значение на нейтральное `project-jdk-name="25"` и `languageLevel="JDK_25"` (и зафиксировать в `README` ожидаемое имя JDK SDK в IDE).
  Опционально: коммитить `.idea/codeStyles/`, `.idea/runConfigurations/` (как требует контекст ревью), но **не** коммитить `misc.xml` с локальным state.

---

### Finding F5: Файл `.idea/vcs.xml` оказался добавлен в индекс ПОСЛЕ task-коммита и не отражён в `task-01-result.md`

- **Severity:** major
- **File:** `.idea/vcs.xml` (стейджинг), отчёт `.tasks/book-server-fullstack-tasks/task-01-result.md`
- **Why:**
  `git status` показывает:
  ```
  Changes to be committed:
      new file:   .idea/vcs.xml
  Untracked files:
      .tasks/book-server-fullstack-tasks/task-01-result.md
  ```
  То есть состояние рабочего дерева на момент ревью **не соответствует** тому, что описано в `task-01-result.md`: туда не входит `.idea/vcs.xml`, а тут он уже в индексе. Это значит, что либо `git status` сделан после ручных правок, либо task-result не финализирован. Для project hygiene важно, чтобы коммит, помеченный «Task 01», точно соответствовал отчёту. Сейчас следующий разработчик не поймёт, чем закрывается Task 01.
- **Suggestion:** либо включить `.idea/vcs.xml` в Task 01 коммит (`git commit --amend`) и обновить `task-01-result.md`, либо отменить стейджинг (`git restore --staged .idea/vcs.xml`) и добавить файл явно в Task 10 (где будет финальный README/setup). И зафиксировать в репозитории `.tasks/book-server-fullstack-tasks/task-01-result.md` тоже (сейчас он Untracked).

---

### Finding F6: `.gitignore` не покрывает важные IntelliJ-артефакты и Gradle-артефакты, и в нём не оговорён white-list для нужных `.idea/*`

- **Severity:** minor
- **File:** `.gitignore`
- **Why:**
  Task требует «не игнорировать `.idea/` целиком, чтобы сохранить runConfigurations, codeStyles». Сейчас в `.gitignore` перечислены только частные изменчивые файлы IntelliJ (`workspace.xml`, `tasks.xml`, `usage.statistics.xml`, `shelf`, `dictionaries`, `dataSources`, `sqlDataSources`, `uiDesigner.xml`). Это рабочая стратегия, но в ней отсутствуют:
  - `.idea/httpRequests/` (см. `.idea/.gitignore`, который добавляет это уже на уровне самой папки);
  - `.idea/inspectionProfiles/Profile_Default.xml` (для проф. использования имеет смысл whitelist'ить только Project_Default.xml);
  - `*.iws` (устарел, но всё ещё генерируется в некоторых сценариях);
  - `.idea/caches/`, `.idea/libraries-with-intellij-classes.xml`, `.idea/jarRepositories.xml` (machine-specific paths).

  Также для Gradle нет `gradle-app.setting`, `!gradle-wrapper.properties`, `.gradletasknamecache`. Для будущего фронта стоит явно добавить `frontend/.vuepress/dist`, `frontend/coverage/`, `*.tsbuildinfo`. И полезно положить общий `*.log` (он есть) и `*.tmp`, `*.bak`.
- **Suggestion:** расширить `.gitignore`, например:
  ```gitignore
  # IntelliJ extras
  .idea/httpRequests/
  .idea/caches/
  .idea/jarRepositories.xml
  .idea/libraries-with-intellij-classes.xml
  .idea/inspectionProfiles/Profile_Default.xml
  *.iws

  # Gradle extras
  gradle-app.setting
  .gradletasknamecache

  # Frontend extras (для Task 09)
  frontend/coverage/
  *.tsbuildinfo
  frontend/.eslintcache

  # Misc
  *.tmp
  *.bak
  ```

---

### Finding F7: `application.yml` корректен по содержанию, но имени профиля и наследования не задано — придётся править в Task 03

- **Severity:** nit
- **File:** `backend/src/main/resources/application.yml`
- **Why:**
  Текущее содержимое минимально и корректно. Однако задача Review-B спрашивает «готов к расширению профилями в Task 03». Сейчас нет ни одного якоря: ни `spring.profiles.active`, ни `spring.config.import`, ни управляемого `management.endpoint`. Это не блокер, просто отмечу: при первом же коммите Task 03 файл будет полностью переписан. Главное — что преждевременных настроек (datasource/jpa/security) тут нет, и это соответствует требованию.
- **Suggestion:** не править в рамках Task 01, но в Task 03 — добавить `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` сверху и завести `application-dev.yml`, `application-prod.yml` рядом.

---

### Finding F8: `gradle.properties` не фиксирует `org.gradle.java.installations.auto-detect` / `auto-download`

- **Severity:** minor
- **File:** `gradle.properties`
- **Why:**
  Текущее содержимое:
  ```properties
  org.gradle.parallel=true
  org.gradle.caching=true
  org.gradle.jvmargs=-Xmx2g
  ```
  В отчёте явно сказано, что launcher работает на Java 21, а Java 25 подтягивается toolchain'ом. Если на CI/чужой машине нет ни SDKMAN, ни ручной установки JDK 25, сборка упадёт. Самое аккуратное решение — явно разрешить auto-download:
  ```properties
  org.gradle.java.installations.auto-download=true
  org.gradle.java.installations.auto-detect=true
  ```
  Это снимает риск, что Task 02+ упрутся в «нет JDK 25 в системе».
- **Suggestion:** добавить две строки. Это не зависимость и не configuration override, а просто Gradle toolchain settings.

---

### Finding F9: HealthController всегда возвращает hard-coded `UP` — будущая инфраструктура (Task 02 БД, Task 06 импортеры) этим пользоваться не сможет

- **Severity:** nit
- **File:** `backend/src/main/java/com/example/bookserver/HealthController.java`
- **Why:**
  Хардкод `Map.of("status", "UP")` — корректная заглушка для acceptance criterion Task 01. С точки зрения безопасности он не раскрывает чувствительных данных (просто статичная строка) — это плюс. Минус: в Spring Boot есть готовый `/actuator/health` (`spring-boot-starter-actuator`), который и предполагается использовать в Task 03+. Сейчас контроллер занимает path `/health`, который актуатор тоже использует по умолчанию — будет конфликт mapping'а либо потребуется переименование, либо удаление кастомного контроллера. Это можно зафиксировать как known follow-up.
- **Suggestion:** оставить как есть для Task 01, но в Task 03 — заменить кастомный `HealthController` на `spring-boot-starter-actuator` с `management.endpoints.web.exposure.include=health` и сменить базовый путь (`management.endpoints.web.base-path=/`) либо оставить `/actuator/health`. В README / task-01-result добавить «known follow-up: replace with actuator in Task 03».

---

### Finding F10: README.md упоминает Task 09/Task 10, но не упоминает требование к JDK 25 и Docker

- **Severity:** nit
- **File:** `README.md`
- **Why:**
  README хорош как заглушка, явно говорит «Подробное README — Task 10», описывает структуру. Однако «Быстрый старт» предполагает, что у читателя уже есть JDK 25 + Docker, но нигде нет требований к окружению. Если кто-то склонирует репозиторий сейчас, `./gradlew :backend:bootRun` упадёт без JDK 25 (см. F8). Это нарушение принципа «минимально полезный README».
- **Suggestion:** добавить блок:
  ```markdown
  ## Требования
  - JDK 25 (Gradle toolchain auto-download, либо локально через SDKMAN/asdf)
  - Gradle wrapper (включён, запускать через `./gradlew`)
  ```
  Не более 3 строк, без overengineering'а.

---

### Finding F11: `frontend/.gitkeep` ОК; готовность к Task 09 подтверждена

- **Severity:** —
- **File:** `frontend/.gitkeep` (0 байт)
- **Why:** Файл реально пустой (0 байт), `frontend/` других файлов не содержит. К замене на Vite-проект в Task 09 препятствий нет. Это позитивный результат, оставлено в списке для полноты.
- **Suggestion:** в Task 09 — `git rm frontend/.gitkeep` и инициализация Vite. Сейчас правок не требуется.

---

## Сводка

- **Блокер**: F1 (≈ 736 MB бинарного SQL в первом коммите) — починить до начала Task 02, переписать первый и единственный коммит.
- **Критично/Безопасность**: F2 — добавить `.env`, `*.pem`, `*.key`, `application-*.local.yml` в `.gitignore` до Task 03 (security/JWT).
- **Major**: F3 (слишком жадный `*.jar`), F4 (`misc.xml` с локальным `21 (2)`), F5 (`.idea/vcs.xml` вне task-коммита, `task-01-result.md` Untracked).
- **Minor/Nit**: F6 (расширить .gitignore), F7 (профили — на Task 03), F8 (gradle toolchain auto-download), F9 (HealthController vs actuator — follow-up), F10 (README — требование JDK 25).

Положительные стороны (специально для baseline):
- `.editorconfig` покрывает Java/Kotlin/Vue/TS/JS/JSON/YAML/HTML/CSS/SCSS/Makefile, charset/EOL/final newline корректны.
- `.gitignore` правильно делает whitelist для `gradle/wrapper/gradle-wrapper.jar`.
- `application.yml` — минимальный, без преждевременных datasource/jpa/security.
- `Application.java`, `HealthController.java`, `settings.gradle.kts`, `backend/build.gradle.kts` — без лишних зависимостей и не противоречат Task 02–10.
- Структура `com.example.bookserver` подходит как корневой пакет для будущих `auth/`, `books/`, `imports/`, `conversion/`, `lists/`, `domain/`, `repo/`, `config/`, `security/`, `web/`, `storage/` подпакетов.
- `git ls-files` подтверждает, что `build/`, `.gradle/`, `.idea/workspace.xml`, `.idea/BookServerFull.iml` НЕ закоммичены — игнор работает.
- `frontend/.gitkeep` действительно пустой и готов к Vite.
- Сообщение коммита `chore: init gradle monorepo skeleton (Task 01)` — осмысленное.

**Приоритет действий**: сначала F1 + F2 + F4 + F5 (всё ломает hygiene «прямо сейчас»), затем F3, F6, F8, F10 в одном hygiene-коммите перед Task 02. F7 и F9 — заметки в backlog, исправляются в Task 03.
