# scripts/

Утилитарные скрипты для проекта BookServerFull.

## parse_genres.py

Извлекает `seed/genres.csv` из MySQL-дампа `sql/lib.libgenrelist.sql`.

Используется однократно при подготовке seed-данных для Liquibase changeset `006-seed-genres.xml`.

### Запуск

```bash
python3 scripts/parse_genres.py
```

(пути `sql/lib.libgenrelist.sql` → `backend/.../seed/genres.csv` зашиты в скрипт.)

### Поведение

- Парсит `INSERT INTO ... VALUES (...)` блоки.
- Извлекает колонки: GenreCode, GenreDesc, GenreMeta.
- `GenreMeta` — это не полноценный родитель, а текстовый признак секции
  ("Фантастика", "Проза", …). Скрипт **выносит каждое уникальное значение
  `GenreMeta` в отдельную родительскую строку-жанр** и связывает листья через
  `parent_id`, формируя настоящую двухуровневую иерархию.
- id проставляются явно (родители идут первыми, чтобы self-FK выполнялся при
  вставке), поэтому changeset 006 после `loadData` сбрасывает sequence.
- Родителю генерируется уникальный `code` вида `meta_<translit-slug>`;
  `meta_section` у родителя пустой (он сам и есть секция).
- Лист сохраняет `meta_section` = `GenreMeta` как денормализованную подпись,
  `position` = оригинальный GenreId.

Результат: 296 строк (24 родительские секции + 272 жанра).
