# Triage — Task 01 (Gradle monorepo skeleton)

Date: 2026-06-01
Reviewed commit: `beb5c3c` ("chore: init gradle monorepo skeleton (Task 01)")
Inputs:
- Review-A (5 nit findings, build/Gradle/Spring Boot focus) — inlined in user prompt.
- Review-B (11 findings) — `.tasks/book-server-fullstack-tasks/review-b-task-01.md`.
- Task spec: `.tasks/book-server-fullstack-tasks/task-01-gradle-monorepo-skeleton.md`.
- Code-agent report: `.tasks/book-server-fullstack-tasks/task-01-result.md`.

---

## 0. Подтверждение фактов

| Проверка | Команда | Результат |
|---|---|---|
| Размер `sql/lib.b.annotations.sql` | `ls -lh sql/lib.b.annotations.sql` | `736 MiB` (771 791 309 bytes) ✅ blocker подтверждён |
| Файл реально в коммите | `git show --stat beb5c3c \| grep annotations` | `sql/lib.b.annotations.sql \| Bin 0 -> 771791309 bytes` ✅ |
| Размер `sql/` и `.git/` | `du -sh sql/ .git/` | `1.6G  sql/`, `667M .git/` ✅ |
| `.tasks/` отслежен? | `git ls-files .tasks/` | Да: PLAN.md, task-01..task-10, research/ask-01-02 — все в commit `beb5c3c`. `.tasks/` в `.gitignore` НЕ упомянут. |
| `task-01-result.md` отслежен? | `git status` | Нет, untracked — создан **после** Task 01 коммита. Аналогично `review-b-task-01.md`. |
| `.idea/vcs.xml` | `git status` | `Changes to be committed: new file: .idea/vcs.xml` — был добавлен в индекс уже после Task 01 коммита. |
| `.idea/misc.xml` содержимое | `read .idea/misc.xml` | `project-jdk-name="21 (2)" languageLevel="JDK_21"` ✅ confirmed mismatch с toolchain 25 |
| `.gitignore` секреты | grep | покрыт только `frontend/.env.local`; `.env`/`*.pem`/`*.key`/`*.jks` нет ✅ |
| `.gitignore` jar | `cat .gitignore` | `*.jar` + `!gradle/wrapper/gradle-wrapper.jar` (строки 9-10) ✅ |
| backend toolchain | `cat backend/build.gradle.kts` | `languageVersion.set(JavaLanguageVersion.of(25))`, vendor не задан ✅ |

Также в коммите находятся другие крупные SQL-файлы (>50 MB), которые тоже превышают рекомендации по размеру файла в git: `lib.a.annotations.sql` 120M, `lib.libbook.sql` 224M, `lib.librate.sql` 64M, `lib.reviews.sql` 411M. То есть проблема шире, чем один файл. **Все они подпадают под B-F1.**

---

## 1. Сводная таблица

| ID | Класс | Severity (исх.) | Block Task 02? | Out of scope Task 01 |
|---|---|---|---|---|
| A-F1 | FP | nit | no | no (стиль) |
| A-F2 | FP | nit | no | yes (Task 03) |
| A-F3 | FP | nit | no | yes (Task 10) |
| A-F4 | FP | nit | no | yes (Task 10) |
| A-F5 | FP | nit | no | no (cosmetic) |
| B-F1 | **TP** | blocker | **yes** | no |
| B-F2 | **TP** | critical | **yes** | no |
| B-F3 | **TP** | major | yes (cheap) | no |
| B-F4 | **TP** | major | yes | no |
| B-F5 | **TP** | major | yes | no |
| B-F6 | TP (low) | minor | no | no |
| B-F7 | FP | nit | no | yes (Task 03) |
| B-F8 | TP (low) | minor | no | no |
| B-F9 | FP | nit | no | yes (Task 03) |
| B-F10 | FP | nit | no | yes (Task 10) |

---

## 2. Подробный разбор

### A-F1 — `allprojects { group, version }` vs `subprojects`
- **Класс:** FP (nit/style)
- **Обоснование:** Версия на root проекте сама по себе ничего не ломает; Gradle принимает обе формы. Задание Task 01 не специфицирует. Это чисто стилистическая рекомендация; backend — единственный subproject, эффект нулевой.
- **Out of scope Task 01:** no (можно поправить в любой момент, но не блокер).

### A-F2 — нет `pluginManagement` / `dependencyResolutionManagement` в settings.gradle.kts
- **Класс:** FP (out of scope)
- **Обоснование:** Сейчас зависимостей кроме Spring Boot нет, mavenCentral объявлен в `backend/build.gradle.kts`. Сам Review-A явно пишет: «должно появиться в Task 03». Не блокер.
- **Out of scope Task 01:** yes (Task 03 при добавлении JWT/Liquibase/OpenAPI).

### A-F3 — README мимолётно упоминает Vue/Vuetify
- **Класс:** FP (out of scope)
- **Обоснование:** README будет полностью переписан в Task 10 (явно указано в самом README и в Task 01 spec). Текущая заглушка корректна, лёгкое опережение допустимо.
- **Out of scope Task 01:** yes (Task 10).

### A-F4 — toolchain без `vendor`
- **Класс:** FP (nit, follow-up)
- **Обоснование:** В локальной среде Gradle сам подтягивает JDK 25 через SDKMAN. Vendor становится важен для воспроизводимости и Docker-сборок, что относится к Task 10. Не блокер.
- **Out of scope Task 01:** yes (Task 10).

### A-F5 — нет `gradle/gradle-daemon-jvm.properties`
- **Класс:** FP (nit)
- **Обоснование:** Launcher JVM (Java 21) известен и работает локально, что подробно описано в `task-01-result.md`. Пиннинг через daemon-jvm.properties не критичен для Task 01 — выровнять можно вместе с CI/Docker в Task 10. Частично перекрывается B-F8 (auto-download/auto-detect).
- **Out of scope Task 01:** no (cosmetic, optional follow-up).

---

### B-F1 — 736 MiB SQL-дамп в `beb5c3c`
- **Класс:** **TP (BLOCKER)**
- **Обоснование:** Подтверждено фактически: `sql/lib.b.annotations.sql` = 771 791 309 bytes (≈ 736 MiB), `.git/` уже 667M. Файл превышает GitHub's hard limit 100 MB → `git push` к GitHub упадёт `remote: error: File ... is XXX MB; this exceeds GitHub's file size limit of 100.00 MB`. Помимо одного файла, в индексе ещё 4 SQL > 50 MB (`lib.reviews.sql` 411M, `lib.libbook.sql` 224M, `lib.a.annotations.sql` 120M, `lib.librate.sql` 64M). Task 01 spec **не** перечислял `sql/` в Output Artifacts → агент должен был исключить `sql/` из коммита через `.gitignore`. Поскольку push'а ещё не было, переписать историю безопасно.
- **Fix direction (минимальный):**
  1. Добавить в `.gitignore`: `sql/*.sql` (или весь `sql/`, поскольку папка — read-only reference для импорта в Task 06 и хранится локально).
  2. `git rm --cached sql/*.sql` (одной командой, не трогая физические файлы на диске).
  3. Amend единственного коммита: `git commit --amend --no-edit` (либо `git reset --soft HEAD~1 && git add -A && git commit -m "chore: init gradle monorepo skeleton (Task 01)"`).
  4. (Опционально) `git gc --aggressive --prune=now`, чтобы уменьшить `.git/` до разумного размера.
  5. Обновить `task-01-result.md`: упомянуть, что `sql/` — локальная reference-папка, не в git; commit hash станет новым.
  6. В Task 06 (importers) — спроектировать так, что путь к локальной `sql/` папке (или к /data/sql) задаётся переменной окружения. Альтернатива: Git LFS (`git lfs track "sql/*.sql"`), но это лишний tooling — для read-only reference дампов проще не хранить в git.
- **Out of scope Task 01:** no.

### B-F2 — `.gitignore` не покрывает секреты
- **Класс:** **TP (critical)**
- **Обоснование:** В Task 03 появятся JWT signing keys (`${JWT_SECRET}`), в Task 10 — docker-compose `.env`. Сейчас `.gitignore` ловит только `frontend/.env.local`. Любой случайно созданный `application-local.yml`, `.env`, `app.key` уйдёт в git. Это базовая security-гигиена, должна быть до Task 03.
- **Fix direction (минимальный):**
  Добавить блок в `.gitignore`:
  ```gitignore
  # --- Secrets / local config ---
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
- **Out of scope Task 01:** no (project hygiene в scope Task 01).

### B-F3 — глобальный `*.jar` в `.gitignore`
- **Класс:** **TP (major)**
- **Обоснование:** `build/` уже игнорирует все сборочные jar-ы; глобальный `*.jar` избыточен и потенциально опасен (затрёт будущие `libs/`/ресурсные jar-ы). Whitelist `!gradle/wrapper/gradle-wrapper.jar` нужен только пока есть `*.jar`. Стоимость fix — 1 строка.
- **Fix direction:** Удалить из `.gitignore` строки `*.jar` и `!gradle/wrapper/gradle-wrapper.jar`. `gradle/wrapper/gradle-wrapper.jar` останется отслеженным (уже коммитнут), `build/libs/*.jar` останется игнорируемым через `build/`.
- **Out of scope Task 01:** no.

### B-F4 — `.idea/misc.xml` с `project-jdk-name="21 (2)"`
- **Класс:** **TP (major)**
- **Обоснование:** Конкретное имя `21 (2)` — артефакт текущей машины разработчика. У любого другого члена команды IntelliJ покажет `Project SDK is not defined`. `languageLevel="JDK_21"` напрямую противоречит фактическому toolchain 25 в `backend/build.gradle.kts`. Task 01 spec говорит «не игнорировать `.idea/` целиком, чтобы сохранить runConfigurations, codeStyles» — но это **не** требует коммитить `misc.xml` с локальным state. Аргумент code-agent'а «переключение IDE на JDK 25 вынесено за scope» — слабый: либо файл нужно нормализовать, либо вообще не отслеживать.
- **Fix direction (минимальный, выбрать одно):**
  - **Вариант A (предпочтительно):** добавить `.idea/misc.xml` в `.gitignore` (рядом с `.idea/workspace.xml`), `git rm --cached .idea/misc.xml`.
  - **Вариант B:** нормализовать содержимое — `languageLevel="JDK_25"`, `project-jdk-name="25"`, в README.md (когда дойдёт до Task 10) зафиксировать ожидаемое имя SDK.
  - Вариант A проще и меньше шансов на дрейф между машинами разработчиков.
- **Out of scope Task 01:** no (Task 01 в scope: `.gitignore` и `.idea/`-стратегия).

### B-F5 — рассинхрон между `git status` и `task-01-result.md`
- **Класс:** **TP (major)**
- **Обоснование:** Подтверждено: `.idea/vcs.xml` staged (не в Task 01 коммите); `task-01-result.md` и `review-b-task-01.md` Untracked. `.tasks/` сам по себе **не** игнорируется (`.gitignore` не содержит `.tasks`), а частично отслежен (`PLAN.md`, `task-01..10`, `research/` — в коммите `beb5c3c`). То есть оставшиеся untracked — это новые файлы, созданные после Task 01 коммита, а не намеренный exclude. Их состояние нужно зафиксировать до Task 02, иначе следующий разработчик не поймёт, какой код относится к какому Task.
- **Fix direction (минимальный):**
  - Решить, попадает ли `.idea/vcs.xml` в Task 01 или в Task 10 setup. По умолчанию рекомендую включить в Task 01 amend (содержит просто `VcsDirectoryMapping`, безопасно).
  - Очередной fix-wave коммит должен покрыть и `task-01-result.md` + `review-b-task-01.md` + `triage-task-01.md` (этот файл) под отдельным коммитом, например `chore: add task-01 result + review-b + triage`. Либо `git add .tasks/ && git commit --amend` если решили смержить с Task 01.
  - Рекомендация: оставить Task 01 amend только для фикса blocker'а и hygiene .gitignore; артефакты `.tasks/...result.md/review-b/triage.md` положить отдельным «task-package» коммитом.
- **Out of scope Task 01:** no.

### B-F6 — расширить `.gitignore`
- **Класс:** TP (minor, low priority)
- **Обоснование:** Все перечисленные паттерны полезны, особенно `*.tsbuildinfo`, `frontend/coverage/`, `.idea/httpRequests/`, `.idea/jarRepositories.xml`, `.idea/libraries-with-intellij-classes.xml`. Стоимость fix — ~10 строк в `.gitignore`. Можно включить в тот же hygiene-коммит, что и B-F2/B-F3.
- **Fix direction:**
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

  # Frontend extras (Task 09)
  frontend/coverage/
  *.tsbuildinfo
  frontend/.eslintcache

  # Misc temp/backup
  *.tmp
  *.bak
  ```
- **Out of scope Task 01:** no.

### B-F7 — `application.yml` без профилей
- **Класс:** FP (out of scope)
- **Обоснование:** Сам Review-B пишет «не править в рамках Task 01». Профили — Task 03. Сейчас файл полностью соответствует спеке Task 01 (`server.port: 8080`, `spring.application.name: bookserver`).
- **Out of scope Task 01:** yes (Task 03).

### B-F8 — `gradle.properties` без `auto-detect`/`auto-download`
- **Класс:** TP (minor, low priority)
- **Обоснование:** Дёшево (2 строки), снимает риск, что чужая машина без JDK 25 не сможет собрать проект Task 02+. Не блокер сейчас (на машине разработчика SDKMAN автоматически подхватывает), но имеет смысл закрыть в hygiene-волне.
- **Fix direction:** Добавить в `gradle.properties`:
  ```properties
  org.gradle.java.installations.auto-detect=true
  org.gradle.java.installations.auto-download=true
  ```
- **Out of scope Task 01:** no.

### B-F9 — `HealthController` vs `/actuator/health`
- **Класс:** FP (out of scope)
- **Обоснование:** Спека Task 01 явно требует endpoint `/health` возвращающий `{"status":"UP"}`. Кастомный контроллер — корректная реализация. Конфликт с actuator проявится только при добавлении `spring-boot-starter-actuator` в Task 03; там же и решается. Сейчас править нечего.
- **Out of scope Task 01:** yes (Task 03 — заменить на actuator или сменить mapping).

### B-F10 — README не упоминает JDK 25
- **Класс:** FP (out of scope)
- **Обоснование:** README — полностью переписывается в Task 10 (это явно зафиксировано и в Task 01 spec, и в самом README: «Подробное README — Task 10»). Текущее содержимое — корректная заглушка. Менять под Task 01 — преждевременно.
- **Out of scope Task 01:** yes (Task 10).

---

## 3. Need more data

Нет findings, требующих дополнительного расследования. Все факты подтверждены `git show --stat`, `git status`, `du`, `ls`, чтением файлов.

---

## 4. Агрегированная рекомендация

### Fix wave перед Task 02 (обязательно)
**Один атомарный коммит** «chore: post-task-01 hygiene fixes» (после amend Task 01):

1. **B-F1** (blocker) — переписать Task 01 коммит:
   - `git rm --cached sql/*.sql`
   - Добавить в `.gitignore` запись `sql/*.sql` (или `sql/` целиком — рекомендую папку целиком, т.к. это локальный reference для Task 06 импортера).
   - `git commit --amend --no-edit` (либо amend сообщения, если хочется упомянуть).
   - Опционально `git gc --aggressive --prune=now`.
   - Обновить `task-01-result.md`: новый commit hash, явный пункт «sql/ — локальная reference-папка, не в git».
2. **B-F4** — добавить `.idea/misc.xml` в `.gitignore` + `git rm --cached .idea/misc.xml` (вариант A).
3. **B-F3** — удалить `*.jar` + `!gradle/wrapper/gradle-wrapper.jar` из `.gitignore`.
4. **B-F2** — добавить блок секретов (`.env`/`.env.*`/`*.pem`/`*.key`/`*.jks`/...).
5. **B-F6** — расширить `.gitignore` (IntelliJ extras + Gradle extras + frontend extras + misc).
6. **B-F8** — добавить `org.gradle.java.installations.auto-detect=true` + `auto-download=true` в `gradle.properties`.
7. **B-F5** — определиться с `.idea/vcs.xml` и зафиксировать `task-01-result.md` + `review-b-task-01.md` + `triage-task-01.md`. Рекомендую отдельным коммитом «docs: task-01 result, review, triage».

Все эти изменения — `~30` строк правок в трёх файлах (`.gitignore`, `gradle.properties`, опционально `.idea/misc.xml`) и пара `git rm --cached`. Никаких сборочных правок.

### Можно отложить (follow-up)
- **A-F1** — стилистическая правка `allprojects → subprojects`, при желании в Task 03 или позже.
- **A-F2** — `pluginManagement`/`dependencyResolutionManagement` — Task 03.
- **A-F3, A-F4, B-F10** — README + toolchain vendor — Task 10.
- **A-F5** — `gradle-daemon-jvm.properties` — Task 10 (вместе с CI/Docker сетапом), опционально.
- **B-F7** — профили `application.yml` — Task 03.
- **B-F9** — `HealthController` → actuator — Task 03.

### Что НЕ делать сейчас
- Не трогать Java toolchain (25 корректно).
- Не менять `Application.java`/`HealthController.java`.
- Не добавлять Spring зависимости.
- Не переименовывать пакеты.
- Не править `application.yml`.
- Не править README по содержанию (только в Task 10).
