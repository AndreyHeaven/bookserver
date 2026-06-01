# scripts/

Утилитарные скрипты для проекта BookServerFull.

## parse_genres.py

Извлекает `seed/genres.csv` из MySQL-дампа `sql/lib.libgenrelist.sql`.

Используется однократно при подготовке seed-данных для Liquibase changeset `006-seed-genres.xml`.

### Запуск

```bash
python3 scripts/parse_genres.py \
  --input sql/lib.libgenrelist.sql \
  --output backend/src/main/resources/db/changelog/seed/genres.csv
```

### Поведение

- Парсит `INSERT INTO ... VALUES (...)` блоки.
- Извлекает колонки: GenreCode, GenreDesc, GenreMeta (соответствуют code/title/meta_section в CSV).
- parent_id оставляется пустым (NULL) — иерархия задаётся отдельно через meta_section в коде.
- position берётся как оригинальный GenreId для сохранения порядка.

Результат: 272 строки (соответствует уникальным GenreCode в исходном дампе).
