# Task 05: Books / Authors / Genres REST API + Full-Text Search

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать REST API для книг, авторов и жанров: каталог + детали + поиск с FTS и фасетами. Жанры отдаются как иерархия (с поджанрами). API должен быть готов для использования фронтендом (Task 09).

## Why This Task Exists
Это центральные сценарии пользователя — найти и просмотреть книгу, открыть каталог авторов или жанров с детальной страницей. Без этого API ни фронт, ни importers, ни conversion не имеют ценности.

## Spec Coverage
- Requirements: R3, R9
- Scenarios: S5, S8, S9

## Required Inputs
- Сущности и репозитории из Task 04, включая `BookSearchRepository`.
- Schema: `books`, `persons`, `genres`, `book_authors`, `book_genres`, `annotations` (Task 02).
- OpenAPI-конфиг и Security из Task 03.

## Files/Areas
- `backend/src/main/java/com/example/bookserver/books/BooksController.java`
- `backend/src/main/java/com/example/bookserver/books/BookSearchService.java`
- `backend/src/main/java/com/example/bookserver/books/dto/BookCardDto.java`
- `backend/src/main/java/com/example/bookserver/books/dto/BookDetailsDto.java`
- `backend/src/main/java/com/example/bookserver/books/dto/BookSearchRequest.java`
- `backend/src/main/java/com/example/bookserver/books/dto/BookSearchResponse.java`
- `backend/src/main/java/com/example/bookserver/books/dto/FacetCountsDto.java`
- `backend/src/main/java/com/example/bookserver/books/mapper/BookMapper.java` (MapStruct или вручную)
- `backend/src/main/java/com/example/bookserver/authors/AuthorsController.java`
- `backend/src/main/java/com/example/bookserver/authors/AuthorsService.java`
- `backend/src/main/java/com/example/bookserver/authors/dto/AuthorCardDto.java` (`id`, `lastName`, `firstName`, `middleName`, `fullName`, `bookCount`)
- `backend/src/main/java/com/example/bookserver/authors/dto/AuthorDetailsDto.java` (детали + краткая сводка)
- `backend/src/main/java/com/example/bookserver/authors/dto/AuthorSearchRequest.java` (`q`, `letter`, `page`, `size`, `sort`)
- `backend/src/main/java/com/example/bookserver/genres/GenresController.java`
- `backend/src/main/java/com/example/bookserver/genres/GenresService.java`
- `backend/src/main/java/com/example/bookserver/genres/dto/GenreNodeDto.java` (`id`, `code`, `title`, `metaSection`, `bookCount`, `children: List<GenreNodeDto>`) — рекурсивная DTO для дерева
- `backend/src/main/java/com/example/bookserver/genres/dto/GenreDetailsDto.java` (`id`, `code`, `title`, `metaSection`, `parentId`, `parentTitle`, `children`, `bookCount`)
- `backend/src/test/java/com/example/bookserver/books/BookSearchControllerIT.java`
- `backend/src/test/java/com/example/bookserver/authors/AuthorsControllerIT.java`
- `backend/src/test/java/com/example/bookserver/genres/GenresControllerIT.java`
- `backend/src/test/resources/fixtures/seed-books.sql` (несколько тестовых книг + авторов + жанров с иерархией для IT)

## Constraints / Non-Goals
- Не делать write-endpoints (POST/PUT/DELETE) для книг, авторов, жанров — книги/авторы создаются только через импортеры (Task 06), жанры приходят из seed (Task 02).
- Не реализовывать инфраструктуру конвертации (Task 07) и шаринга (Task 08).
- Использовать `ts_rank_cd` для ранжирования; запросы с пустым `query` должны корректно возвращать все книги (без `@@`-условия).
- Все endpoints — под `/api/books`, `/api/authors`, `/api/genres` и требуют auth (кроме `/api/public/...`, который не создаётся в этой задаче).
- Дерево жанров строится одним SQL-запросом (рекурсивный CTE или один SELECT + сборка в памяти, т.к. жанров ≤ 500), и кэшируется в Spring-кэше (`@Cacheable("genresTree")`) с инвалидацией при изменении (для этой задачи изменений нет → cache TTL 1h).
- `bookCount` в фасетах и каталогах считается на лету (без денормализации).
- Поиск по авторам — FTS по `persons.fts_tsv`; при пустом `q` сортировка по `last_name, first_name`.

## Output Artifacts
- Controllers, services, DTOs, MapStruct/manual mapper, IT-тесты, fixture-данные.
- Books / Authors / Genres REST API с пагинацией, FTS и иерархией жанров.

## What to Do
1. **Books endpoints** (`/api/books`):
   - `GET /api/books` — пагинация, query-параметры: `q`, `lang`, `year_from`, `year_to`, `genre_id` (multi), `author_id`, `page`, `size`, `sort`. Возвращает `BookSearchResponse`. Учесть рекурсивно поджанры при фильтре по `genre_id` (книги жанра-родителя включают книги поджанров).
   - `GET /api/books/{id}` — `BookDetailsDto` с авторами, жанрами, аннотацией, списком `BookFile`.
   - `GET /api/books/facets` — `FacetCountsDto` (по языкам, годам, жанрам).
2. **Authors endpoints** (`/api/authors`):
   - `GET /api/authors` — пагинация, query-параметры: `q` (FTS по `persons.fts_tsv`), `letter` (фильтр по первой букве фамилии для алфавитной навигации; cyrillic + latin), `page`, `size`, `sort` (по умолчанию `last_name,first_name`). Возвращает `Page<AuthorCardDto>` с `bookCount`.
   - `GET /api/authors/{id}` — `AuthorDetailsDto` (без книг).
   - `GET /api/authors/{id}/books` — пагинация книг автора, такие же фильтры, как у `/api/books` (lang, year_from, year_to, genre_id), сортировка по `series, sequence_number, title` (для серийных), затем по `title`. Возвращает `Page<BookCardDto>`.
   - `GET /api/authors/alphabet` — список букв с counts для построения алфавитной навигации на фронте (`[{letter:"А",count:1234},...]`). Опционально, если просто будет считаться на фронте — этот endpoint можно опустить, но он удобнее.
3. **Genres endpoints** (`/api/genres`):
   - `GET /api/genres/tree` — иерархическое дерево всех жанров с meta-разделами (root = meta-сегменты, например "Фантастика", дети = жанры). Возвращает `List<GenreNodeDto>` с `bookCount` на каждом узле. Закэшировано в Spring `@Cacheable("genresTree")`. Bookcount узла = собственные книги + рекурсивно книги поджанров.
   - `GET /api/genres/{id}` — `GenreDetailsDto` с родителем, прямыми потомками и `bookCount`.
   - `GET /api/genres/{id}/books` — пагинация книг жанра + (опционально) книг поджанров (флаг `includeSubgenres=true` по умолчанию). Те же фильтры, что у `/api/books`. Возвращает `Page<BookCardDto>`.
4. `BookSearchService` использует `BookSearchRepository` и собирает `Page<BookCardDto>`.
5. `AuthorsService` использует `PersonRepository` + кастомный native-метод для FTS-поиска и подсчёта книг (LEFT JOIN `book_authors`).
6. `GenresService`:
   - строит дерево: один SELECT всех жанров → собирает родителей и детей по `parent_id`;
   - bookCount: SELECT с GROUP BY с учётом иерархии (рекурсивный CTE + COUNT по `book_genres`);
   - кэширует дерево.
7. `BookCardDto`: id, title, authors (list of `{id, fullName}`), year, lang, fileType, hasFiles, coverUrl (nullable).
8. `BookDetailsDto`: + annotation, genres (с путём от корня), series (with sequenceNumber), files (`{id, format, sizeBytes, downloadUrl}`).
9. `AuthorCardDto`: id, lastName, firstName, middleName, fullName, bookCount.
10. `GenreNodeDto`: id, code, title, metaSection, bookCount, children (рекурсивно).
11. Все Request-DTO — records с валидацией (`@Min`, `@Max`, `@Pattern` для `letter`).
12. OpenAPI-аннотации на все endpoints.
13. IT: подготовить fixture с 3–5 книгами, 3 авторами, 4 жанрами (в т.ч. поджанр у одного жанра), одной аннотацией. Проверить:
    - `/api/books` search с пустым/непустым `q`, фильтр по жанру (вкл. поджанры);
    - `/api/books/facets` корректно считает counts;
    - `/api/authors?q=...` находит автора, `/api/authors/{id}/books` отдаёт книги автора;
    - `/api/authors?letter=А` корректно фильтрует по первой букве;
    - `/api/genres/tree` возвращает корректное дерево с правильными `bookCount` (с учётом поджанров);
    - `/api/genres/{id}/books?includeSubgenres=true` включает книги поджанров, `false` — только прямые;
    - 401 без токена для всех endpoints.

## Expected Output
- `GET /api/books?q=эхо&size=10` возвращает корректную страницу.
- `GET /api/books/{id}` возвращает детали.
- `GET /api/authors?q=толстой` находит автора, `/api/authors/{id}/books` возвращает его книги.
- `GET /api/genres/tree` возвращает дерево жанров с `bookCount` (включая поджанры).
- `GET /api/genres/{id}/books?includeSubgenres=true` возвращает книги жанра и его поджанров.
- Swagger UI показывает все новые endpoints.

## Acceptance Criteria
- [ ] `GET /api/books` поддерживает `q`, фильтры (включая `genre_id` с учётом поджанров), пагинацию и сортировку.
- [ ] `GET /api/books/{id}` возвращает 404 для несуществующих и 200 для существующих.
- [ ] `GET /api/books/facets` возвращает counts по языкам, годам и жанрам.
- [ ] `GET /api/authors` поддерживает `q` (FTS), `letter` и пагинацию; `GET /api/authors/{id}/books` возвращает книги автора с фильтрами.
- [ ] `GET /api/genres/tree` возвращает корректную иерархию с `bookCount` на каждом узле (учитывая поджанры).
- [ ] `GET /api/genres/{id}` возвращает узел с прямыми детьми и родителем.
- [ ] `GET /api/genres/{id}/books?includeSubgenres=true|false` корректно фильтрует с учётом/без учёта поджанров.
- [ ] FTS-запрос с `q=эхо` использует GIN-индекс (проверено `EXPLAIN ANALYZE` локально или в комментарии к тесту).
- [ ] Все endpoints требуют auth и возвращают 401 без токена.
- [ ] IT-тесты `BookSearchControllerIT`, `AuthorsControllerIT`, `GenresControllerIT` проходят на testcontainers PG.
- [ ] Covered requirements and scenarios are satisfied (R3, R9, S5, S8, S9).
- [ ] I've created a git commit for this task.
