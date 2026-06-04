# Fix-Wave — Task 01 (Gradle monorepo skeleton)

Date: 2026-06-01
Inputs: `triage-task-01.md` (TP: B-F1, B-F2, B-F3, B-F4, B-F5, B-F6, B-F8).

---

## 1. Тяжёлые SQL-файлы (>50 MB), исключённые из git

| Файл | Размер |
|---|---|
| `sql/lib.b.annotations.sql` | 736M |
| `sql/lib.reviews.sql`       | 411M |
| `sql/lib.libbook.sql`       | 224M |
| `sql/lib.a.annotations.sql` | 120M |
| `sql/lib.librate.sql`       |  64M |

Все остальные SQL остаются в git, включая `sql/lib.libgenrelist.sql` (28K, нужен в Task 02 для seed 298 жанров) и `sql/lib.libgenre.sql` (25M, ниже порога 50M).

Примечание по `sql/lib.b.annotations_pics.sql` (2.3M) — оставлен в git (не подпадает под порог 50M).

---

## 2. `.gitignore` — diff vs предыдущая версия

**Удалено:**
```
*.jar
!gradle/wrapper/gradle-wrapper.jar
```
(B-F3). `gradle/wrapper/gradle-wrapper.jar` уже в индексе, останется отслеживаемым.

**Добавлено / упорядочено:**

```gitignore
# --- Gradle extras ---
gradle-app.setting
.gradletasknamecache

# --- IntelliJ IDEA --- (расширено)
*.iws
.idea/misc.xml
.idea/httpRequests/
.idea/jarRepositories.xml
.idea/libraries-with-intellij-classes.xml
.idea/caches/
.idea/inspectionProfiles/Profile_Default.xml

# --- Frontend extras ---
frontend/coverage/
frontend/.eslintcache
*.tsbuildinfo

# --- Local data / runtime artifacts --- (добавлено)
*.tmp
*.bak

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

# --- Heavy SQL dumps (>50 MB, kept locally) ---
sql/lib.a.annotations.sql
sql/lib.b.annotations.sql
sql/lib.libbook.sql
sql/lib.librate.sql
sql/lib.reviews.sql
```

Сохранены все рабочие правила (`.gradle/`, `build/`, `out/`, `*.class`, `*.log`, frontend node_modules/dist/.vite/.env.local, `*.swp`, `.DS_Store`, Thumbs.db, `/data/`, `*.local`, IntelliJ workspace/tasks/usage.statistics/dictionaries/shelf/dataSources/sqlDataSources/uiDesigner, `*.iml`).

---

## 3. `gradle.properties` — diff

```diff
 org.gradle.parallel=true
 org.gradle.caching=true
 org.gradle.jvmargs=-Xmx2g
+org.gradle.java.installations.auto-detect=true
+org.gradle.java.installations.auto-download=true
```

---

## 4. Удалено из индекса (`git rm --cached`)

- `sql/lib.a.annotations.sql`
- `sql/lib.b.annotations.sql`
- `sql/lib.libbook.sql`
- `sql/lib.librate.sql`
- `sql/lib.reviews.sql`
- `.idea/misc.xml`

(Физически файлы остаются на диске.)

---

## 5. Новые commit hashes

| # | Hash    | Сообщение |
|---|---------|-----------|
| 1 | `cdf9769` | chore: init gradle monorepo skeleton (Task 01) — **amended** (prev `beb5c3c`) |
| 2 | `2ef9497` | docs(task-01): add execution report, review, triage notes |

---

## 6. `du -sh .git/` до и после

| Состояние | Размер |
|---|---|
| До fix-wave | 667M |
| После amend (без gc) | 667M (packfiles ещё не очищены) |

`.git/` физически не уменьшилось, потому что `git gc --aggressive --prune=now` намеренно не запускалось (оставлено пользователю, см. раздел «Опционально» ниже). По индексу (`git ls-files`) тяжёлые SQL отсутствуют, что и важно для будущего push'а после `git gc`.

`du -sh sql/` = **1.6G**, без изменений (файлы на диске сохранены).

---

## 7. `git status` после всех операций

```
On branch master
nothing to commit, working tree clean
```

---

## 8. `./gradlew :backend:build` — финальный вывод

```
> Task :backend:compileJava UP-TO-DATE
> Task :backend:processResources UP-TO-DATE
> Task :backend:classes UP-TO-DATE
> Task :backend:resolveMainClassName UP-TO-DATE
> Task :backend:bootJar UP-TO-DATE
> Task :backend:jar UP-TO-DATE
> Task :backend:assemble UP-TO-DATE
> Task :backend:compileTestJava NO-SOURCE
> Task :backend:processTestResources NO-SOURCE
> Task :backend:testClasses UP-TO-DATE
> Task :backend:test NO-SOURCE
> Task :backend:check UP-TO-DATE
> Task :backend:build UP-TO-DATE

BUILD SUCCESSFUL in 3s
5 actionable tasks: 5 up-to-date
```

Build зелёный, ничего не сломано.

---

## 9. Подтверждения (verification)

| Проверка | Результат |
|---|---|
| `git log --oneline` | 2 коммита: `2ef9497`, `cdf9769` ✅ |
| `git ls-files \| grep "sql/"` | 13 файлов, тяжёлых нет; `lib.libgenrelist.sql` присутствует ✅ |
| `git ls-files \| grep ".idea/misc.xml"` | пусто ✅ |
| `git ls-files \| grep gradle-wrapper.jar` | `gradle/wrapper/gradle-wrapper.jar` ✅ |
| `git status` | clean ✅ |
| `./gradlew :backend:build` | BUILD SUCCESSFUL ✅ |

---

## 10. Опциональные дальнейшие шаги (пользователю)

- `git gc --aggressive --prune=now` — физически уменьшит `.git/` (≈ 667M → <50M) после того как тяжёлые blob'ы выпали из reachable истории (amend единственного коммита уже это обеспечил).
- Reflog: amend оставляет старый коммит `beb5c3c` в reflog. Если хочется сразу почистить, выполнить `git reflog expire --expire=now --all && git gc --prune=now --aggressive`. Без этого реальный размер `.git/` останется ≈ 667M, пока ttl reflog (90 дней по умолчанию) не пройдёт.

---

## 11. Известные отклонения

- **`.git/` size не уменьшен прямо сейчас** — намеренно (пункт «НЕ запускай `git gc --aggressive`»). Логически история уже чистая (новый commit `cdf9769` не содержит тяжёлых файлов), физическая очистка — отдельный опциональный шаг пользователя.
- **`.idea/vcs.xml`** включён в amended Task 01 коммит (содержит только `VcsDirectoryMapping`, нейтрально, как разрешено в triage).
- **`.tasks/PLAN.md`** и task-spec файлы (task-01..10, research/) уже были в коммите `beb5c3c`, и amend сохранил их. Untracked task-package файлов больше нет.

Все TP findings из triage закрыты:
- B-F1 ✅ (5 файлов >50M удалены из индекса + добавлены в .gitignore)
- B-F2 ✅ (секреты покрыты)
- B-F3 ✅ (`*.jar` + whitelist убраны)
- B-F4 ✅ (`.idea/misc.xml` удалён + игнорируется)
- B-F5 ✅ (docs коммит `2ef9497` фиксирует артефакты)
- B-F6 ✅ (IntelliJ/Gradle/frontend extras добавлены)
- B-F8 ✅ (auto-detect/auto-download добавлены)
