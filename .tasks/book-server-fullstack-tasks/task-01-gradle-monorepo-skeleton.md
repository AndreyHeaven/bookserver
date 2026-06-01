# Task 01: Gradle Monorepo Skeleton

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Создать с нуля скелет монорепозитория с двумя подпроектами: `backend/` (Spring Boot 4, Java 25) и `frontend/` (Vue 3 + Vuetify 3), управляемых одним Gradle-сборщиком (Kotlin DSL).

## Why This Task Exists
В корне проекта сейчас только папка `sql/` с MySQL-дампами и `.idea/`. Нет Gradle, нет `src`, нет `.gitignore`. Эта задача даёт каркас, на котором будут стоять все последующие задачи.

## Spec Coverage
- Requirements: R1
- Scenarios: —

## Required Inputs
- Корень проекта: `/Users/butt/Projects/my/BookServerFull/`.
- В корне присутствует только `sql/` (18 SQL-файлов) и `.idea/`. Подробности — в `research/ask-02-project-state/project-state.md`.
- Java 25 toolchain, Spring Boot 4.x (последняя GA на момент выполнения), Gradle 8.x с поддержкой Java 25.

## Files/Areas
- `settings.gradle.kts` — multi-project setup.
- `build.gradle.kts` — root build.
- `gradle.properties` — общие свойства.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar` — Gradle wrapper.
- `backend/build.gradle.kts` — заглушка с Java toolchain 25 и Spring Boot 4 plugin (детали наполняются в Task 03).
- `backend/src/main/java/com/example/bookserver/Application.java` — минимальный Spring Boot main-класс (`@SpringBootApplication`, hello-endpoint).
- `backend/src/main/resources/application.yml` — пустой шаблон (заполняется в Task 03).
- `frontend/` — пустая папка-плейсхолдер с `.gitkeep` (наполняется в Task 09).
- `.gitignore` — Java/Gradle/IntelliJ/Node/Vue/macOS/Linux.
- `.editorconfig` — общие настройки отступов (Java 4 spaces, Vue/JSON 2 spaces).
- `README.md` — заглушка с заголовком и кратким описанием (наполняется в Task 10).

## Constraints / Non-Goals
- Не наполнять `backend/build.gradle.kts` всеми зависимостями — только тем, что нужно для запуска пустого `@SpringBootApplication`-приложения (`spring-boot-starter-web`).
- Не создавать `frontend/`-каркас (это Task 09).
- Не инициализировать git-репозиторий и не делать первый commit — это делает исполнитель в рамках acceptance criteria.

## Output Artifacts
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`.
- `backend/build.gradle.kts`, `backend/src/main/java/com/example/bookserver/Application.java`, `backend/src/main/resources/application.yml`.
- `frontend/.gitkeep`.
- `.gitignore`, `.editorconfig`, `README.md`.

## What to Do
1. Сгенерировать Gradle wrapper 8.x (с поддержкой Java 25).
2. В `settings.gradle.kts` подключить `backend` (frontend пока не добавлять как Gradle-подпроект — он будет самостоятельным npm-проектом).
3. В root `build.gradle.kts` объявить Kotlin DSL, Java 25 toolchain через `toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }`, общую группу и версию.
4. В `backend/build.gradle.kts` подключить плагины `org.springframework.boot` (4.x), `io.spring.dependency-management`, `java`, добавить `spring-boot-starter-web` и `spring-boot-starter-test`.
5. Создать минимальный `Application.java` с `@SpringBootApplication` и `@RestController` для `/health` (возвращает `{"status":"UP"}`).
6. Создать пустой `application.yml` с `server.port: 8080` и `spring.application.name: bookserver`.
7. Создать `.gitignore` (Java/Gradle/IntelliJ/Node/macOS).
8. Создать `.editorconfig`.
9. Создать заглушку `README.md` с разделом «BookServerFull» и кратким TODO.

## Expected Output
- Проект собирается командой `./gradlew :backend:build` без ошибок.
- Проект запускается командой `./gradlew :backend:bootRun`; `GET http://localhost:8080/health` возвращает `{"status":"UP"}`.
- Структура каталогов соответствует разделу Files/Areas.

## Acceptance Criteria
- [ ] `./gradlew :backend:build` завершается успешно с использованием Java 25 toolchain.
- [ ] `./gradlew :backend:bootRun` поднимает приложение, `curl http://localhost:8080/health` возвращает `{"status":"UP"}`.
- [ ] `settings.gradle.kts` подключает только подпроект `backend`.
- [ ] `.gitignore`, `.editorconfig`, `README.md` созданы.
- [ ] Covered requirements and scenarios are satisfied (R1).
- [ ] I've created a git commit for this task.
