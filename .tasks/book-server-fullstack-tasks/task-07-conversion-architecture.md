# Task 07: Conversion Architecture (без реализации)

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Создать архитектурный слот под конвертацию форматов: интерфейс `FormatConverter`, реестр по `(sourceFormat, targetFormat)`, сущность `ConversionJob`, очередь/исполнитель, REST. Реальной реализации нет — единственный зарегистрированный бин `NotImplementedFormatConverter` помечает задачу `FAILED` с сообщением `Conversion not implemented yet`.

## Why This Task Exists
Пользователь явно отказался от Calibre, но хочет, чтобы инфраструктура для конвертации была готова. Это позволит позже добавить любой конвертер (Calibre, ebook-convert, kindlegen, своя реализация) без переписывания REST/DB/UI.

## Spec Coverage
- Requirements: R5
- Scenarios: S6

## Required Inputs
- JPA entity `ConversionJob` из Task 04 + миграция в Task 02.
- `BookFileStorage` из Task 06.
- Сущность `BookFile` из Task 04.

## Files/Areas
- `backend/src/main/java/com/example/bookserver/conversion/FormatConverter.java` (интерфейс)
- `backend/src/main/java/com/example/bookserver/conversion/FormatPair.java` (record `{sourceFormat, targetFormat}`)
- `backend/src/main/java/com/example/bookserver/conversion/FormatConverterRegistry.java`
- `backend/src/main/java/com/example/bookserver/conversion/ConversionService.java`
- `backend/src/main/java/com/example/bookserver/conversion/ConversionWorker.java` (async executor)
- `backend/src/main/java/com/example/bookserver/conversion/NotImplementedFormatConverter.java`
- `backend/src/main/java/com/example/bookserver/conversion/ConversionsController.java`
- `backend/src/main/java/com/example/bookserver/conversion/dto/StartConversionRequest.java`, `ConversionJobDto.java`
- `backend/src/main/java/com/example/bookserver/conversion/exception/ConverterNotFoundException.java`
- `backend/src/test/java/com/example/bookserver/conversion/ConversionServiceIT.java`
- `backend/src/main/resources/META-INF/conversion-architecture.md` — документ-описание как добавлять новый конвертер.

## Constraints / Non-Goals
- НЕ реализовывать реальную конвертацию (не подключать Calibre, kindlegen, своё что-либо).
- Единственный бин `FormatConverter` в продакшене — `NotImplementedFormatConverter`.
- Сообщение об ошибке должно быть статичным и понятным: `Conversion not implemented yet`.
- Архитектура должна поддерживать добавление реального конвертера без изменения существующих классов (Open/Closed).

## Output Artifacts
- Интерфейс + реестр + worker + REST + not-implemented бин + IT-тест + документация по расширению.

## What to Do
1. `FormatConverter` интерфейс:
   ```java
   public interface FormatConverter {
       Set<FormatPair> supportedPairs();
       Path convert(Path source, String targetFormat) throws Exception;
   }
   ```
2. `FormatConverterRegistry`:
   - инжектит `List<FormatConverter>`;
   - строит `Map<FormatPair, FormatConverter>` при старте;
   - `resolve(source, target)` → `Optional<FormatConverter>`.
3. `NotImplementedFormatConverter`:
   - `supportedPairs()` возвращает ВСЕ комбинации из enum supported formats (`fb2`, `epub`, `mobi`, `pdf`, `azw3`) кроме identity-пар (target = source);
   - `convert(...)` бросает `UnsupportedOperationException("Conversion not implemented yet")`.
4. `ConversionService`:
   - `startConversion(bookFileId, targetFormat)`: создаёт `ConversionJob(PENDING)`, асинхронно вызывает worker.
   - `worker` берёт source BookFile, резолвит конвертер через реестр, вызывает `convert(...)`. При исключении устанавливает `FAILED` + сообщение из exception.
   - Если конвертер не найден — `ConverterNotFoundException` → 422 в REST.
5. `ConversionsController`:
   - `POST /api/conversions` body `{bookFileId, targetFormat}` → `ConversionJobDto`.
   - `GET /api/conversions/{id}` детали.
   - `GET /api/conversions` paged.
6. `ConversionServiceIT` (testcontainers PG):
   - подготовить `BookFile` с форматом `fb2`;
   - запустить конвертацию в `epub`;
   - дождаться, что `ConversionJob.status = FAILED`, `message = "Conversion not implemented yet"`;
   - запустить конвертацию из/в неподдерживаемый формат → 422.
7. `conversion-architecture.md`:
   - описать, как реализовать новый конвертер (Spring-бин + `supportedPairs()` + регистрация);
   - перечислить точки расширения (asyncExecutor pool size, max concurrent conversions);
   - указать, что `NotImplementedFormatConverter` отключается удалением @Component или через профиль.

## Expected Output
- `POST /api/conversions {"bookFileId":1,"targetFormat":"epub"}` создаёт job, который сразу переходит в `FAILED` с понятным сообщением.
- Архитектура задокументирована.

## Acceptance Criteria
- [ ] `FormatConverter`, `FormatConverterRegistry`, `ConversionService`, `ConversionWorker` реализованы.
- [ ] `NotImplementedFormatConverter` — единственный продакшен-бин `FormatConverter`.
- [ ] `POST /api/conversions` создаёт job, который через короткое время становится `FAILED` с сообщением `Conversion not implemented yet`.
- [ ] Конвертация на неподдерживаемую пару форматов возвращает 422.
- [ ] `ConversionServiceIT` проходит на testcontainers PG.
- [ ] `conversion-architecture.md` объясняет, как добавить реальный конвертер.
- [ ] Covered requirements and scenarios are satisfied (R5, S6).
- [ ] I've created a git commit for this task.
