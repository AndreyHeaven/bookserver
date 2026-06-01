# Текущее состояние корня проекта `/Users/butt/Projects/my/BookServerFull/`

## TL;DR
Проект **практически пустой**: в корне есть только папка `sql/` (с дампами таблиц) и папка `.idea/` (минимальная конфигурация IntelliJ IDEA для JAVA_MODULE с JDK 21). Папки `.tasks/` физически на диске нет. **Проект нужно собирать с нуля** — никакого скелета (Gradle, Spring Boot, Vue, src/) ещё нет.

---

## 1. Что есть в корне (кроме `sql/` и `.tasks/`)

Полный список содержимого корня:
- `sql/` — папка с 18 SQL-дампами (`lib.libbook.sql`, `lib.libavtor.sql`, и т.п.) — исходные данные библиотеки.
- `.idea/` — конфигурация IntelliJ IDEA. Содержит:
  - `.gitignore` — стандартные исключения IDE (`/shelf/`, `/workspace.xml`, `/httpRequests/`, `/queries/`, `/dataSources/`, `/dataSources.local.xml`).
  - `BookServerFull.iml` — модуль типа `JAVA_MODULE`, ничего особенного (inheritedJdk, sourceFolder).
  - `misc.xml` — `languageLevel=JDK_21`, project-jdk-name="21 (2)", `JavaSDK`.
  - `modules.xml` — ссылка на `BookServerFull.iml`.
  - `sqldialects.xml`, `workspace.xml` — служебные.

**Папки `.tasks/` физически на файловой системе не существует** (по результатам `list_dir` и `task_list_dir`). Возможно, она создаётся оркестратором по требованию.

## 2. Gradle / Maven

- ❌ Нет `settings.gradle` / `settings.gradle.kts`
- ❌ Нет `build.gradle` / `build.gradle.kts`
- ❌ Нет папки `gradle/` (нет wrapper-jar/properties)
- ❌ Нет `gradlew`, `gradlew.bat`
- ❌ Нет `pom.xml`

Сборки нет в принципе.

## 3. Папки исходников

- ❌ Нет `src/`
- ❌ Нет `backend/`
- ❌ Нет `frontend/`
- ❌ Нет любых других папок с кодом.

## 4. Файлы конфигурации

- ❌ Нет `.gitignore` в корне (только в `.idea/`).
- ❌ Нет `.editorconfig`.
- ❌ Нет `README.md` / `README.adoc`.
- ❌ Нет `application.yml` / `application.properties`.
- ❌ Нет `package.json`, `vite.config.*`, `tsconfig.json` и подобных Vue/JS-конфигов.
- ❌ Нет `Dockerfile`, `docker-compose.yml`.

## 5. Код (Java/Kotlin/Vue/JS/TS)

Никакого кода нет — только `.sql` файлы данных в `sql/` и XML-конфиги IDE в `.idea/`.

## 6. Скелет или с нуля?

**С нуля.** Существующий `.idea/` — пустая заглушка модуля без source-roots; настоящего проекта (build-системы, исходников, конфигов) ещё нет. Полностью свободно от существующего кода — никаких ограничений на структуру нет.

## 7. Git-репозиторий

❌ **`.git/` не существует** — репозиторий **не инициализирован**. `.gitignore` в корне также отсутствует, поэтому правил игнорирования на уровне проекта нет (есть только `.idea/.gitignore` с IDE-исключениями).

---

## Рекомендация по дальнейшим действиям

1. Создать структуру multi-project Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle/wrapper/`, `gradlew*`.
2. Завести модули `backend/` (Spring Boot 4 + Java 25) и `frontend/` (Vue 3 + Vuetify, Vite).
3. Создать корневые `.gitignore`, `.editorconfig`, `README.md`.
4. `git init` + первый commit.
5. JDK в `.idea/misc.xml` сейчас выставлен на 21 — после перехода на Java 25 потребуется обновить.
