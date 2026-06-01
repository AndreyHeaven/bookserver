# Task 08: Book Lists + Public Share + QR

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Реализовать создание и управление списками книг, публичную шару списка по share-token и генерацию QR-кода для публичного URL.

## Why This Task Exists
Это специально заказанная пользователем фича — возможность собрать список книг и поделиться им так, чтобы кто угодно мог открыть его по QR-коду на сервере.

## Spec Coverage
- Requirements: R6
- Scenarios: S7

## Required Inputs
- JPA сущности `BookList`, `BookListItem`, `BookListShare` из Task 04 (Liquibase schema из Task 02).
- `Book` и `Person` из Task 04 для рендеринга.
- Зависимость `com.google.zxing:core` + `javase` уже добавлена в Task 03.

## Files/Areas
- `backend/src/main/java/com/example/bookserver/lists/BookListsController.java` (private API)
- `backend/src/main/java/com/example/bookserver/lists/PublicListController.java` (public API под `/api/public/lists`)
- `backend/src/main/java/com/example/bookserver/lists/BookListsService.java`
- `backend/src/main/java/com/example/bookserver/lists/ShareService.java`
- `backend/src/main/java/com/example/bookserver/lists/QrCodeService.java`
- `backend/src/main/java/com/example/bookserver/lists/dto/BookListDto.java`, `BookListItemDto.java`, `ShareLinkDto.java`, `PublicBookListDto.java`, `CreateBookListRequest.java`, `UpdateBookListRequest.java`, `AddBookToListRequest.java`
- `backend/src/test/java/com/example/bookserver/lists/BookListsControllerIT.java`
- `backend/src/test/java/com/example/bookserver/lists/PublicListControllerIT.java`

## Constraints / Non-Goals
- Public endpoints находятся под `/api/public/**` и НЕ требуют auth (whitelist в SecurityConfig из Task 03 уже это разрешает).
- Share-token — URL-safe Base64 (24 байта random), уникальный.
- QR-код возвращается как PNG (`image/png`); размер по умолчанию 400×400.
- Endpoints для управления списками — только для владельца (`owner_id == current user`). Возвращать 403 при попытке чужого доступа.
- Один список — один активный share-token (новый вызов share — заменяет старый ИЛИ возвращает существующий; выбрать «возвращает существующий», добавив параметр `regenerate=true` для замены).

## Output Artifacts
- Контроллеры (private + public), сервисы, DTO, IT-тесты.

## What to Do
1. **Private API** (под `/api/lists`):
   - `POST /api/lists` создать список.
   - `GET /api/lists` мои списки (paged).
   - `GET /api/lists/{id}` детали + items.
   - `PUT /api/lists/{id}` обновить title/description.
   - `DELETE /api/lists/{id}` удалить.
   - `POST /api/lists/{id}/items` добавить книгу (`{bookId, position?}`).
   - `DELETE /api/lists/{id}/items/{bookId}` удалить книгу.
   - `PUT /api/lists/{id}/items/order` изменить порядок (массив bookIds).
   - `POST /api/lists/{id}/share?regenerate=false` → `ShareLinkDto{token, publicUrl, qrPngBase64}`.
   - `DELETE /api/lists/{id}/share` отозвать.
2. **Public API** (под `/api/public/lists`):
   - `GET /api/public/lists/{token}` → `PublicBookListDto` (title, description, items с заголовками и авторами книг, без приватных полей вроде owner).
   - `GET /api/public/lists/{token}/qr.png` → бинарь PNG QR-кода для удобства встраивания.
3. `BookListsService`:
   - проверка владения (`@PreAuthorize("@listAccess.isOwner(...)")` или явная проверка в сервисе);
   - управление позициями items (по умолчанию append, перенумерация при reorder).
4. `ShareService`:
   - `share(listId, regenerate)`: если уже есть и `regenerate=false` — вернуть существующий; иначе создать новый (заменив старый);
   - `revoke(listId)`: удалить share;
   - `publicUrl(token)`: вычислить URL на основе `app.public.base-url` (дефолт `http://localhost:8080`).
5. `QrCodeService`:
   - вход — URL, размер;
   - выход — PNG bytes (через `com.google.zxing.qrcode.QRCodeWriter` + `MatrixToImageWriter`).
6. IT-тесты:
   - создать список → добавить книги → расшарить → проверить, что public endpoint доступен без токена и возвращает корректные данные;
   - проверить, что после `DELETE /share` public endpoint возвращает 404;
   - проверить, что чужой пользователь не может изменить список (403);
   - проверить content-type=`image/png` у `qr.png`.

## Expected Output
- Пользователь может создать список, добавить книги, расшарить и получить QR.
- Публичный URL открывается без авторизации.

## Acceptance Criteria
- [ ] Private CRUD списков работает, реализована проверка owner-ship (403 для чужих).
- [ ] `POST /api/lists/{id}/share` возвращает `{token, publicUrl, qrPngBase64}`.
- [ ] `GET /api/public/lists/{token}` доступен без auth и возвращает корректный список.
- [ ] `GET /api/public/lists/{token}/qr.png` возвращает PNG-картинку с правильным MIME.
- [ ] При `DELETE /share` публичный URL начинает возвращать 404.
- [ ] IT-тесты `BookListsControllerIT` и `PublicListControllerIT` проходят.
- [ ] Covered requirements and scenarios are satisfied (R6, S7).
- [ ] I've created a git commit for this task.
