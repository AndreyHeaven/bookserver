# Task 06: Importers (Inpx+ZIP, fb2)

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать расширяемую архитектуру импортеров с двумя готовыми реализациями: `InpxZipImporter` (зипованные книги + inpx-описание) и `Fb2FolderImporter` (отдельные fb2-файлы).

## Why This Task Exists
Импортеры — главный способ наполнения библиотеки. Архитектура должна позволять легко добавлять новые источники (OPDS, OPDF-каталоги, удалённые архивы и т.д.).

## Spec Coverage
- Requirements: R4
- Scenarios: S3, S4

## Required Inputs
- JPA entities из Task 04: `Book`, `Person`, `Series`, `Genre`, `BookFile`, `ImportJob`.
- Liquibase schema из Task 02: `books.archive_name`, `books.inpx_source`, `books.md5`, `books.file_size`, `books.file_type`, `import_jobs`.
- Описание inpx-формата: пайп-разделённые строки в `*.inp` внутри `.inpx`-архива. Канонический порядок полей (одна из распространённых версий, на которую опирается реализация):
  `AUTHOR;GENRE;TITLE;SERIES;SERNO;FILE;SIZE;LIBID;DEL;EXT;DATE;LANG;LIBRATE;KEYWORDS`
  где AUTHOR — список авторов разделённых `:`, каждый автор — `LastName,FirstName,MiddleName`; GENRE — список через `:`. Точный список полей нужно подтвердить в коде реализации (см. `inpx-format.md` ниже).
- Описание fb2: XML с `<description>` (`<title-info>`, `<document-info>`).
- `BookFileStorage` интерфейс (определяется в этой задаче, реализация — `LocalBookFileStorage`).

## Files/Areas
- `backend/src/main/java/com/example/bookserver/imports/BookImporter.java` (интерфейс)
- `backend/src/main/java/com/example/bookserver/imports/ImporterRegistry.java`
- `backend/src/main/java/com/example/bookserver/imports/ImportContext.java` (тип источника, путь, options)
- `backend/src/main/java/com/example/bookserver/imports/ImportService.java` (создаёт ImportJob, диспатчит к импортеру, обновляет статус)
- `backend/src/main/java/com/example/bookserver/imports/ImportsController.java` (`POST /api/imports`, `GET /api/imports`, `GET /api/imports/{id}`)
- `backend/src/main/java/com/example/bookserver/imports/inpx/InpxZipImporter.java`
- `backend/src/main/java/com/example/bookserver/imports/inpx/InpxParser.java`
- `backend/src/main/java/com/example/bookserver/imports/fb2/Fb2FolderImporter.java`
- `backend/src/main/java/com/example/bookserver/imports/fb2/Fb2Parser.java`
- `backend/src/main/java/com/example/bookserver/storage/BookFileStorage.java` (интерфейс)
- `backend/src/main/java/com/example/bookserver/storage/LocalBookFileStorage.java`
- `backend/src/main/java/com/example/bookserver/imports/dto/StartImportRequest.java`, `ImportJobDto.java`
- `backend/src/test/java/com/example/bookserver/imports/InpxImporterIT.java`
- `backend/src/test/java/com/example/bookserver/imports/Fb2ImporterIT.java`
- `backend/src/test/resources/fixtures/inpx/sample.inpx`, `backend/src/test/resources/fixtures/inpx/sample-archive.zip` (синтетические тестовые данные с парой книг)
- `backend/src/test/resources/fixtures/fb2/sample.fb2`
- `backend/src/main/resources/META-INF/inpx-format.md` — документ о принятом формате `.inp` (поля, разделители, кодировка), чтобы зафиксировать выбор реализации.

## Constraints / Non-Goals
- Импортеры запускаются асинхронно (`@Async` + `ImportJob` со статусами). Не блокировать HTTP-поток.
- Не парсить полный текст книги; читать только метаданные.
- Дедупликация по `md5` (если `md5` есть и совпадает — обновлять, не создавать дубликат).
- Не подключать сторонние парсеры fb2 — использовать стандартный JAXP/StAX. JAXB опционально.
- Базовая директория для импортов — `app.imports.base-dir` (дефолт `./data/imports`); файлы вне этой директории отвергаются (path traversal guard).
- Хранилище — `LocalBookFileStorage` с базой `app.storage.books-dir` (дефолт `./data/books`).

## Output Artifacts
- Интерфейс + реестр + 2 импортера + storage + REST + IT-тесты + fixtures + `inpx-format.md`.

## What to Do
1. `BookImporter` интерфейс:
   ```java
   public interface BookImporter {
       String type(); // "inpx-zip" | "fb2-folder"
       String description();
       void importFrom(ImportContext context, ImportJobProgress progress) throws Exception;
   }
   ```
2. `ImporterRegistry` — Spring собирает все бины `BookImporter` и резолвит по `type()`.
3. `ImportService`:
   - `startImport(type, sourcePath, options)`: создаёт `ImportJob(PENDING)`, асинхронно вызывает `importer.importFrom(...)`.
   - Прогресс: `progress.update(processed, total)` обновляет `ImportJob.processed_count`/`total_count`.
   - Ошибки: статус `FAILED`, сообщение из exception.
4. `ImportsController`:
   - `POST /api/imports` body `StartImportRequest{type, sourcePath}` → `ImportJobDto`.
   - `GET /api/imports` paged.
   - `GET /api/imports/{id}` детали.
5. `InpxParser`: читает `.inpx` (ZIP), парсит `*.inp`-файлы построчно по pipe-разделённому формату (поля см. в `inpx-format.md`), формирует `InpxBookRecord`. Поддержка `version.info`/`structure.info` опциональна.
6. `InpxZipImporter`: для каждой `InpxBookRecord` находит `<libid>.<ext>` в соответствующем `.zip`, создаёт/обновляет `Book`, `Person`-ы, `Genre`-ы (по коду из `genres.code`), `Series`, сохраняет файл через `BookFileStorage`, создаёт `BookFile`. Все авторы/жанры/серии — upsert.
7. `Fb2Parser`: StAX-парсер, извлекает title, authors (FirstName/MiddleName/LastName), genres (genre-tags), annotation, series + number, lang, year. Не парсить body.
8. `Fb2FolderImporter`: проходит по `*.fb2` в указанной папке, парсит, upsert так же как inpx-импортер, сохраняет fb2-файл через storage.
9. `BookFileStorage`: `store(InputStream, fileName) -> StoredFile{path,size}`, `open(path) -> InputStream`, `delete(path)`. `LocalBookFileStorage` пишет в `app.storage.books-dir`, использует sharding по первым 2 символам md5 (если md5 есть) или uuid.
10. IT-тесты:
    - `InpxImporterIT`: подготовить fixture (sample.inpx + sample-archive.zip с 2 fb2-файлами), запустить через `ImportService`, проверить, что `books` содержит 2 записи, persons и genres связаны, файлы сохранены.
    - `Fb2ImporterIT`: то же для папки с 2 .fb2 файлами.
11. `inpx-format.md`: зафиксировать принятый формат `.inp` (поля, кодировка, разделители), чтобы downstream-задачи могли его расширять.

## Expected Output
- `POST /api/imports {"type":"inpx-zip","sourcePath":"./data/imports/lib1"}` создаёт job, через секунды переходит в `SUCCEEDED`.
- В БД появляются книги, авторы, жанры, серии и файлы.
- Аналогично для `fb2-folder`.

## Acceptance Criteria
- [ ] `BookImporter`-интерфейс и `ImporterRegistry` подключают все Spring-бины автоматически.
- [ ] `InpxZipImporter` и `Fb2FolderImporter` реализованы как Spring-бины.
- [ ] IT-тесты `InpxImporterIT` и `Fb2ImporterIT` проходят и создают записи в БД (testcontainers PG).
- [ ] `ImportJob` корректно отражает статусы PENDING → RUNNING → SUCCEEDED/FAILED.
- [ ] Дедупликация по md5 работает (повторный импорт того же файла не создаёт дубликат).
- [ ] Path traversal guard блокирует пути вне `app.imports.base-dir`.
- [ ] `inpx-format.md` документирует выбранный формат `.inp`.
- [ ] Covered requirements and scenarios are satisfied (R4, S3, S4).
- [ ] I've created a git commit for this task.
