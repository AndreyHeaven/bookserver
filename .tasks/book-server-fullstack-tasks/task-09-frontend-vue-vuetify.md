# Task 09: Frontend Vue 3 + Vuetify 3

**Type:** Code Modification
**Suggested agent:** Code

## Goal
Создать SPA-фронтенд на Vue 3 + Vuetify 3 + Vite + TypeScript + Pinia + Vue Router, который покрывает все user-сценарии backend: auth, поиск, детали книги, каталог авторов + детали автора, каталог жанров (дерево) + детали жанра, мои списки, public-list, импорт, конвертация.

## Why This Task Exists
Backend без UI не имеет ценности для пользователя. Фронт даёт окно во все API из Tasks 03–08, включая каталоги авторов и жанров с детальными страницами.

## Spec Coverage
- Requirements: R7, R9
- Scenarios: S2, S5, S6, S7, S8, S9

## Required Inputs
- REST API из Task 03 (`/api/auth/*`), Task 05 (`/api/books/*`), Task 06 (`/api/imports/*`), Task 07 (`/api/conversions/*`), Task 08 (`/api/lists/*`, `/api/public/lists/*`).
- Swagger UI на `/swagger-ui.html` (помогает в типизации).
- Корневая папка фронта — `frontend/` (создана как плейсхолдер в Task 01).

## Files/Areas
- `frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`, `frontend/index.html`.
- `frontend/.env.development`, `frontend/.env.production` — `VITE_API_BASE_URL`.
- `frontend/src/main.ts` — bootstrap Vuetify + Pinia + Router.
- `frontend/src/plugins/vuetify.ts`.
- `frontend/src/router/index.ts` — guards для auth.
- `frontend/src/stores/auth.ts` — Pinia store с JWT (access + refresh, localStorage).
- `frontend/src/api/http.ts` — axios instance с interceptor для Bearer и refresh-flow.
- `frontend/src/api/books.ts`, `auth.ts`, `imports.ts`, `conversions.ts`, `lists.ts`, `public.ts`, `authors.ts`, `genres.ts`.
- `frontend/src/types/*.ts` — TS-типы DTO (могут быть руками или сгенерированы из OpenAPI).
- `frontend/src/views/LoginView.vue`
- `frontend/src/views/RegisterView.vue`
- `frontend/src/views/BooksView.vue` — поиск + фасеты + пагинация.
- `frontend/src/views/BookDetailsView.vue` — детали + кнопки «добавить в список» и «конвертировать».
- `frontend/src/views/AuthorsView.vue` — каталог авторов: поиск по имени, алфавитная навигация (кириллица + латиница), пагинация, карточки с bookCount.
- `frontend/src/views/AuthorDetailsView.vue` — страница автора: ФИО, bookCount, список книг автора с фильтрами и пагинацией.
- `frontend/src/views/GenresView.vue` — каталог жанров: дерево (Vuetify `v-treeview` или эквивалент) с раскрытием поджанров и bookCount.
- `frontend/src/views/GenreDetailsView.vue` — страница жанра: путь от корня (breadcrumbs), список поджанров, переключатель «включать поджанры», пагинированный список книг.
- `frontend/src/views/MyListsView.vue` — мои списки.
- `frontend/src/views/ListDetailsView.vue` — детали списка + items + share + QR.
- `frontend/src/views/PublicListView.vue` — публичный просмотр списка (без auth).
- `frontend/src/views/ImportsView.vue` — запуск импорта и список jobs.
- `frontend/src/views/ConversionsView.vue` — список conversion jobs (для будущих real конвертеров).
- `frontend/src/components/AppLayout.vue`, `AppHeader.vue`, `AppNavDrawer.vue`.
- `frontend/src/components/BookCard.vue`, `BookFacets.vue`, `ShareQrDialog.vue`, `ConvertBookDialog.vue`.
- `frontend/src/components/AuthorCard.vue`, `AlphabetNav.vue`, `GenreTree.vue`, `GenreBreadcrumbs.vue`.

## Constraints / Non-Goals
- TypeScript обязателен; включить `strict: true`.
- Vuetify 3 (последняя совместимая с Vue 3.5+), Vite 5+.
- Pinia вместо Vuex.
- Vue Router 4, history mode.
- Не делать SSR (Nuxt не нужен).
- Не реализовывать i18n (только локализация UI на русском как дефолт).
- `axios` для HTTP. Не использовать другие HTTP-клиенты.
- Стиль кода: Composition API + `<script setup lang="ts">`.

## Output Artifacts
- Полноценный Vue 3 SPA, собирается через `npm run build`, запускается через `npm run dev`.

## What to Do
1. Инициализировать `frontend/` через `npm create vite@latest -- --template vue-ts` (или эквивалент вручную).
2. Установить зависимости: `vuetify@3`, `@mdi/font`, `pinia`, `vue-router@4`, `axios`. Dev: `eslint`, `prettier`, `@vue/tsconfig`.
3. Настроить Vuetify 3 plugin, добавить тему, mdi-iconset.
4. Настроить Pinia store `auth`: `state{user, accessToken, refreshToken}`, `actions{login, register, logout, refresh, fetchMe}`.
5. axios `http`: baseURL из `VITE_API_BASE_URL`; request interceptor — Bearer; response interceptor — на 401 пробует refresh + retry; при провале — logout + redirect на `/login`.
6. Router: маршруты со `meta.requiresAuth`, guard в `router.beforeEach`. Public маршрут `/p/lists/:token` без auth.
7. Views:
   - `LoginView` / `RegisterView` — формы Vuetify с валидацией.
   - `BooksView` — search-input (debounce), facets sidebar (язык/год/жанр), пагинация (v-pagination), карточки книг. Авторы и жанры в карточке кликабельны и ведут на `AuthorDetailsView` / `GenreDetailsView`.
   - `BookDetailsView` — обложка, заголовок, авторы (links), жанры (links с путём), аннотация, файлы (с кнопками download/convert), кнопка «добавить в список».
   - `AuthorsView` — список авторов с поиском (debounce), алфавитной навигацией (компонент `AlphabetNav` — горизонтальный ряд букв с counts), пагинацией. Карточка автора (`AuthorCard`) — ФИО + bookCount + клик → details.
   - `AuthorDetailsView` — header с ФИО и bookCount, фильтры (lang, year_from/to, genre), список книг (переиспользуется `BookCard`), пагинация.
   - `GenresView` — дерево жанров (`GenreTree`), узлы раскрываются on-demand, каждый узел кликабелен → `GenreDetailsView`. Под каждым узлом показывается bookCount.
   - `GenreDetailsView` — breadcrumbs от корня (`GenreBreadcrumbs`), список поджанров (chips/cards), toggle «включать поджанры», пагинированный список книг.
   - `MyListsView` — список моих списков + CRUD.
   - `ListDetailsView` — items в drag-and-drop порядке (или просто up/down кнопки), share + dialog с QR.
   - `PublicListView` — read-only, без header'а с auth.
   - `ImportsView` — форма (select type, input path) + список jobs со статусом, polling каждые 3s пока есть RUNNING.
   - `ConversionsView` — список conversion jobs со статусом (на сегодня будут только FAILED — это нормально).
8. `ShareQrDialog.vue` — модалка со ссылкой и `<img :src="qrPngBase64">`.
9. `ConvertBookDialog.vue` — выбор target format, отправка POST, отображение результата с пояснением, что real-конвертеры пока не подключены.
10. Скрипты `npm run dev` (Vite dev на 5173), `npm run build`, `npm run preview`, `npm run lint`, `npm run format`.

## Expected Output
- `npm --prefix frontend run dev` поднимает SPA на http://localhost:5173.
- В UI можно зарегистрироваться, залогиниться, искать книги, открывать детали, создавать список, расшаривать, видеть QR.
- Публичный URL `/p/lists/:token` открывается без авторизации.

## Acceptance Criteria
- [ ] `npm install` и `npm run build` отрабатывают без ошибок.
- [ ] Все views из Files/Areas созданы и видны через router.
- [ ] Auth-flow работает: register, login, logout, /me с JWT.
- [ ] Search + facets + пагинация работают на `/books`.
- [ ] `AuthorsView` поддерживает поиск, алфавитную навигацию и пагинацию; `AuthorDetailsView` показывает книги автора с фильтрами.
- [ ] `GenresView` рендерит дерево с раскрытием поджанров и bookCount; `GenreDetailsView` показывает breadcrumbs, поджанры и список книг с toggle `includeSubgenres`.
- [ ] Клики на авторов и жанры в карточках книг ведут на соответствующие детальные страницы.
- [ ] Public list URL открывается без auth и показывает список.
- [ ] QR-код показывается в `ShareQrDialog` и валиден (визуально — открывается сторонним сканером).
- [ ] Импорт можно запустить, статус job-а обновляется через polling.
- [ ] Запуск конвертации создаёт job, UI отображает статус (на сегодня — `FAILED`).
- [ ] TypeScript strict mode, `tsc --noEmit` без ошибок.
- [ ] Covered requirements and scenarios are satisfied (R7, R9, S2, S5, S6, S7, S8, S9).
- [ ] I've created a git commit for this task.
