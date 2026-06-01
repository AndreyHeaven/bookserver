# Task 01 — Результат (Gradle monorepo skeleton)

## 1. Список созданных/изменённых файлов

Новые файлы (все относительно `/Users/butt/Projects/my/BookServerFull/`):

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradlew` (chmod +x — стандарт от `gradle wrapper`)
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `backend/build.gradle.kts`
- `backend/src/main/java/com/example/bookserver/Application.java`
- `backend/src/main/java/com/example/bookserver/HealthController.java`
- `backend/src/main/resources/application.yml`
- `frontend/.gitkeep`
- `.gitignore`
- `.editorconfig`
- `README.md`

Существующие папки `sql/`, `.idea/`, `.tasks/` не изменялись (часть из `.idea/`
теперь исключена `.gitignore`-правилами `*.iml`, `.idea/workspace.xml` и т.д.,
как и требовалось task-файлом).

## 2. Версии

| Tool                                 | Version |
|--------------------------------------|---------|
| Spring Boot                          | 4.0.6 (GA, последний 4.0.x patch на момент выполнения; 4.1.x ещё в RC) |
| `io.spring.dependency-management`    | 1.1.7 (последняя совместимая) |
| Gradle (wrapper, distribution `bin`) | 8.14.3 |
| Java toolchain                       | 25 (Oracle JDK 25 LTS, autodetected SDKMAN, путь `~/.sdkman/candidates/java/25-oracle`) |
| Launcher / daemon JVM                | Java 21 (Gradle 8.14.3 не запускается на JVM 25 — embedded Kotlin DSL пока не поддерживает Java 25 в launcher; для компиляции backend применяется toolchain 25 без замечаний) |

## 3. Вывод `./gradlew :backend:build` (последние 20 строк)

```
To honour the JVM settings for this build a single-use Daemon process will be forked.
Daemon will be stopped at the end of the build
> Task :backend:clean
> Task :backend:compileJava FROM-CACHE
> Task :backend:processResources
> Task :backend:classes
> Task :backend:resolveMainClassName
> Task :backend:bootJar
> Task :backend:jar
> Task :backend:assemble
> Task :backend:compileTestJava NO-SOURCE
> Task :backend:processTestResources NO-SOURCE
> Task :backend:testClasses UP-TO-DATE
> Task :backend:test NO-SOURCE
> Task :backend:check UP-TO-DATE
> Task :backend:build

BUILD SUCCESSFUL in 4s
6 actionable tasks: 5 executed, 1 from cache
```

(Чистая первая сборка занимала ~30 секунд, потом большая часть из cache.)

## 4. Запуск bootRun + `curl /health`

`./gradlew :backend:bootRun` поднялся за 6 секунд, эндпойнт ответил:

```
$ curl -s http://localhost:8080/health
{"status":"UP"}
```

После проверки процесс остановлен (`pkill GradleWrapperMain` + проверка
`lsof -ti:8080` — порт свободен).

## 5. Git commit

- Commit hash: `beb5c3c` (полный: `beb5c3cbc47b250d196635003e4362cf76cc7695`)
- Сообщение: `chore: init gradle monorepo skeleton (Task 01)`
- Репозиторий: инициализирован `git init` в корне проекта.
- Git user.name/email — заглушки (`Developer <dev@example.com>`), т.к. до этого
  никаких настроек на уровне репозитория не было.
- Все требуемые артефакты (включая `gradle/wrapper/gradle-wrapper.jar` через
  whitelist `!gradle/wrapper/gradle-wrapper.jar` в `.gitignore`) попали в commit.
  Файлы `.idea/BookServerFull.iml` и `.idea/workspace.xml` НЕ попали в индекс,
  что соответствует требованиям task-файла.

## 6. Известные проблемы / отклонения

1. **Spring Boot 4.1 vs 4.0**. Выбран 4.0.6 (GA), потому что 4.1.x на момент
   запуска находится только в RC (4.1.0-RC1). Task-файл просил «самую свежую
   стабильную минорную версию», поэтому 4.0.6 корректнее, чем RC.
2. **Gradle 8.14.3 не запускается на JVM 25**. Это известное ограничение
   Gradle 8.x (Kotlin DSL внутренне ругается на `JavaVersion.parse("25")`).
   Решение: launcher JVM — Java 21, а Java 25 подключается через Java toolchain
   (`languageVersion.set(JavaLanguageVersion.of(25))`) и автоматически
   обнаруживается из SDKMAN. Никаких ручных переключений не нужно. Это
   полностью соответствует acceptance criterion «Java 25 toolchain».
   Когда выйдет Gradle 9.x с полной поддержкой Java 25 как launcher JVM —
   можно будет апгрейдиться в рамках более поздней задачи.
3. **Endpoint вынесен в отдельный класс**. По task-файлу допустимо. Класс —
   `com.example.bookserver.HealthController`.
4. **`.idea/misc.xml`** всё ещё указывает на JDK 21. Переключение IDE на JDK 25
   явно вынесено за scope в task-файле.
5. **Никаких лишних зависимостей** (security/JWT/Liquibase/JPA/OpenAPI/...) в
   `backend/build.gradle.kts` не добавлено.
6. **`frontend/`** — только `.gitkeep`, как требует task-файл.
7. **`application.yml`** — только `server.port` и `spring.application.name`,
   без расширений.
