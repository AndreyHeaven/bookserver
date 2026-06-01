# Task 04: JPA Entities + Repositories

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать JPA-сущности и Spring Data репозитории для всех таблиц из Task 02, включая кастомные репозитории для full-text search.

## Why This Task Exists
Сервисный слой и REST API не могут работать без сущностей и репозиториев. FTS требует кастомных нативных запросов, не выраженных стандартным JPQL.

## Spec Coverage
- Requirements: R2, R3
- Scenarios: —

## Required Inputs
- Liquibase changeset-ы из Task 02 (имена таблиц, столбцов, FK).
- ER-документ `backend/src/main/resources/db/changelog/README.md` (из Task 02).
- Spring Boot 4 + Spring Data JPA + Hibernate 7.x.

## Files/Areas
- `backend/src/main/java/com/example/bookserver/domain/Book.java`
- `backend/src/main/java/com/example/bookserver/domain/Person.java`
- `backend/src/main/java/com/example/bookserver/domain/Series.java`
- `backend/src/main/java/com/example/bookserver/domain/Genre.java`
- `backend/src/main/java/com/example/bookserver/domain/BookFile.java`
- `backend/src/main/java/com/example/bookserver/domain/Annotation.java`
- `backend/src/main/java/com/example/bookserver/domain/BookAuthor.java` (+ `@Embeddable BookAuthorId`)
- `backend/src/main/java/com/example/bookserver/domain/BookTranslator.java` (+ id)
- `backend/src/main/java/com/example/bookserver/domain/BookSeriesMember.java`
- `backend/src/main/java/com/example/bookserver/domain/User.java`, `Role.java`
- `backend/src/main/java/com/example/bookserver/domain/BookList.java`, `BookListItem.java`, `BookListShare.java`
- `backend/src/main/java/com/example/bookserver/domain/ImportJob.java`, `ConversionJob.java`
- `backend/src/main/java/com/example/bookserver/domain/converter/*` — атрибут-конвертеры (например, для enum-статусов задач).
- `backend/src/main/java/com/example/bookserver/repo/*` — `BookRepository`, `PersonRepository`, `SeriesRepository`, `GenreRepository`, `BookFileRepository`, `UserRepository`, `RoleRepository`, `BookListRepository`, `BookListShareRepository`, `ImportJobRepository`, `ConversionJobRepository`.
- `backend/src/main/java/com/example/bookserver/repo/BookSearchRepository.java` (custom) + `BookSearchRepositoryImpl.java` — нативный SQL для FTS-поиска и фасетов.
- `backend/src/test/java/com/example/bookserver/repo/BookRepositoryIT.java` — интеграционный тест с testcontainers.

## Constraints / Non-Goals
- Не делать DDL через JPA (`ddl-auto=validate`, не `update`).
- Не маппить `fts_tsv` как обычное поле: либо `@Generated`/`insertable=false, updatable=false`, либо помечать как `@Transient` и не трогать с Java-стороны.
- Использовать `@MapsId`/`@EmbeddedId` для join-таблиц с дополнительной колонкой (position, sequence_number).
- Enums для статусов задач (`ImportStatus`, `ConversionStatus`: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`) — `@Enumerated(EnumType.STRING)`.
- `Person.fts_tsv` — `@Transient` или `insertable=false, updatable=false` (триггер заполняет).

## Output Artifacts
- Все entity-классы + репозитории + кастомный BookSearch.

## What to Do
1. Реализовать JPA-сущности под все таблицы из Task 02.
2. Маппить связи `Book` ↔ `Person` через `BookAuthor`/`BookTranslator` с `@ManyToOne` и embeddable id.
3. Маппить связь `Book` ↔ `Series` через `BookSeriesMember` с дополнительной колонкой `sequenceNumber`.
4. Маппить связи `Book` ↔ `Genre` через `book_genres` (`@ManyToMany`).
5. Маппить `User` ↔ `Role` через `user_roles` (`@ManyToMany`).
6. Маппить `BookList` (`@OneToMany` items, `@OneToOne` share opt.) — items ordered by position.
7. `ImportJob`/`ConversionJob`: статусы через enum.
8. `BookSearchRepository` — кастомный метод:
   - `Page<BookSearchProjection> search(String query, FacetFilter filter, Pageable pageable)`;
   - `FacetCounts facetCounts(String query)` (по языку, году, жанрам).
   - Реализация — нативный SQL с `plainto_tsquery('russian', :q)` и `ts_rank_cd`. Учесть join к авторам и аннотациям через подзапросы или дополнительные колонки.
9. Заменить временный in-memory UserDetailsService из Task 03 на JPA-вариант с `UserRepository`.
10. Подключить `@EntityListeners(AuditingEntityListener.class)` для `created_at`/`updated_at`; включить `@EnableJpaAuditing` в конфиге.

## Expected Output
- В IDE entities компилируются.
- `BookRepositoryIT` (с testcontainers PG) проходит: создать книгу с авторами/жанрами, сохранить, прочитать обратно, проверить FTS.
- `/api/auth/me` после login возвращает username из БД.

## Acceptance Criteria
- [ ] Все сущности из Files/Areas созданы и маппятся на таблицы из Task 02.
- [ ] Кастомный `BookSearchRepository` возвращает результаты по PG FTS с пагинацией и фасетами.
- [ ] Интеграционный тест `BookRepositoryIT` проходит с testcontainers PG.
- [ ] `ddl-auto=validate` не падает при старте.
- [ ] In-memory заглушка пользователей из Task 03 заменена на JPA-вариант.
- [ ] Covered requirements and scenarios are satisfied (R2, R3).
- [ ] I've created a git commit for this task.
