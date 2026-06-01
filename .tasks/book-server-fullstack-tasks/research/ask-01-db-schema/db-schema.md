# Анализ SQL-схемы из `sql/` для проекта BookServerFull

## 0. Главный вывод (TL;DR)

SQL-файлы в папке `sql/` — это **дамп MySQL/MariaDB базы данных `library`** в стиле LibRusEc / Flibusta (исторически — формат, согласованный с `.inpx`-индексами). Это **НЕ PostgreSQL-схема**, и в ней **отсутствуют**:

- любые `CREATE SCHEMA` / namespaces;
- любые `tsvector` колонки;
- GIN-индексы;
- триггеры на обновление поискового индекса;
- миграции (нет Flyway/Liquibase-нумерации, нет директорий `V1__`, `db/migration` и т.д.);
- внешние ключи (`FOREIGN KEY`) — связи только по соглашению, обеспечивались приложением.

То, что есть, — это **DDL + INSERTs**, по сути «полный экспорт схемы и данных»:
- `lib.*.sql` — это пары `DROP TABLE / CREATE TABLE / INSERT INTO ... VALUES (...)` для одной таблицы каждый.
- `lib.libgenrelist.sql` и `lib.libgenretranslate.sql` — это **seed-данные** справочников (жанры + миграция кодов жанров).

Для нового проекта на Spring Boot 4 + PostgreSQL эту схему нужно **переписать**:
- порт MyISAM/InnoDB → PostgreSQL (типы, кодировки);
- добавить полнотекстовый поиск через `tsvector` + GIN-индексы и триггеры;
- расширить под inpx-импорт (там почти всё есть, но добавить поля архива/файла);
- добавить пользовательские списки книг + публичные ссылки (через QR-код) — этого в исходной схеме НЕТ.

---

## 1. Список SQL-файлов и их назначение

Все файлы — это MySQL dump (`mysqldump`), кодировка `utf8 / utf8_unicode_ci`, движки `MyISAM` (большинство) и `InnoDB` (аннотации/обложки). Каждый файл содержит DDL и seed/живые данные для одной таблицы.

| Файл | Таблица | Назначение |
| --- | --- | --- |
| `lib.libbook.sql` | `libbook` | **DDL + данные.** Центральная таблица «Книга» (метаданные fb2/файла). |
| `lib.libavtor.sql` | `libavtor` | DDL + данные. Связь Книга ↔ Автор (M:N, с порядком соавтора). |
| `lib.libavtorname.sql` | `libavtorname` | DDL + данные. Справочник «Автор» (имя/фамилия/ник/MasterId — слияние дублей). |
| `lib.libtranslator.sql` | `libtranslator` | DDL + данные. Связь Книга ↔ Переводчик (M:N), переводчики берутся из `libavtorname`. |
| `lib.libseq.sql` | `libseq` | DDL + данные. Связь Книга ↔ Серия (M:N), с номером в серии, уровнем подсерии и типом. |
| `lib.libseqname.sql` | `libseqname` | DDL + данные. Справочник «Серия». |
| `lib.libgenre.sql` | `libgenre` | DDL + данные. Связь Книга ↔ Жанр (M:N). |
| `lib.libgenrelist.sql` | `libgenrelist` | **DDL + seed-данные** (~298 жанров: `sf_history`, `prose_classic`, …). |
| `lib.libgenretranslate.sql` | `libgenretranslate` | **DDL + seed-данные**. Маппинг устаревших кодов жанров (`fantasy → sf_fantasy`, ...). |
| `lib.libfilename.sql` | `libfilename` | DDL + данные. Имя файла для книг, лежащих НЕ в стандартном fb2-архиве (PDF/DJVU/EPUB/RTF/…). |
| `lib.libjoinedbooks.sql` | `libjoinedbooks` | DDL + данные. История слияния дублей: `BadId → GoodId → realId`. |
| `lib.librate.sql` | `librate` | DDL + данные. Оценка книги пользователем (1..5), `UNIQUE(BookId,UserId)`. |
| `lib.librecs.sql` | `librecs` | DDL + данные. «Рекомендую/в избранное» от пользователя, `UNIQUE(bid,uid)`. |
| `lib.reviews.sql` | `libreviews` | DDL + данные. Отзывы на книги (свободный текст, без PK). |
| `lib.a.annotations.sql` | `libaannotations` | DDL + данные. Аннотация автора (HTML/bb-code, со ссылками и тегами). |
| `lib.a.annotations_pics.sql` | `libapics` | DDL + данные. Картинки/фото автора (путь к файлу). |
| `lib.b.annotations.sql` | `libbannotations` | DDL + данные. Аннотация книги (по аналогии с `libaannotations`, ключ `BookId`). |
| `lib.b.annotations_pics.sql` | `libbpics` | DDL + данные. Картинки/обложки книги (путь к файлу). |

Миграций / историй версий схемы нет — это снапшот.

---

## 2. Полная схема таблиц

### 2.1. `libbook` — Книга (центральная сущность)

```sql
CREATE TABLE `libbook` (
  `BookId` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `FileSize` int(10) unsigned NOT NULL DEFAULT '0',
  `Time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,        -- дата добавления
  `Title` varchar(254) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `Title1` varchar(254) CHARACTER SET utf8 NOT NULL,          -- оригинальное название
  `Lang` char(3) NOT NULL DEFAULT 'ru',                       -- ru, en, uk, be, fr…
  `LangEx` smallint(6) unsigned NOT NULL DEFAULT '0',
  `SrcLang` char(3) NOT NULL DEFAULT '',                      -- язык оригинала
  `FileType` char(4) NOT NULL,                                -- fb2, pdf, djvu, epub, doc, txt, rtf, html
  `Encoding` varchar(32) NOT NULL DEFAULT '',                 -- WINDOWS-1251, UTF-8…
  `Year` smallint(6) NOT NULL DEFAULT '0',
  `Deleted` char(1) NOT NULL DEFAULT '0',                     -- soft delete: '0' активна, '1' удалена
  `Ver` varchar(8) NOT NULL DEFAULT '',                       -- версия fb2-файла из дескриптора
  `FileAuthor` varchar(64) NOT NULL,                          -- кто залил файл (sci-fi-nick)
  `N` int(10) unsigned NOT NULL DEFAULT '0',                  -- технический счётчик
  `keywords` varchar(255) NOT NULL,                           -- ключевые слова из fb2
  `md5` binary(32) NOT NULL,                                  -- md5 файла, UNIQUE
  `Modified` timestamp NOT NULL DEFAULT '2009-11-29 05:00:00',-- дата последней модификации
  `pmd5` char(32) NOT NULL DEFAULT '',                        -- md5 «прочищенного» fb2 для дедупликации
  `InfoCode` tinyint(3) unsigned NOT NULL DEFAULT '0',
  `Pages` int(10) unsigned NOT NULL DEFAULT '0',              -- число страниц
  `Chars` int(10) unsigned NOT NULL DEFAULT '0',              -- число символов
  PRIMARY KEY (`BookId`),
  UNIQUE KEY `md5` (`md5`),
  UNIQUE KEY `BookDel` (`Deleted`,`BookId`),
  KEY `Title` (`Title`),
  KEY `Year` (`Year`),
  KEY `Deleted` (`Deleted`),
  KEY `FileType` (`FileType`),
  KEY `Lang` (`Lang`),
  KEY `FileSize` (`FileSize`),
  KEY `FileAuthor` (`FileAuthor`),
  KEY `N` (`N`),
  KEY `Title1` (`Title1`),
  KEY `FileTypeDel` (`Deleted`,`FileType`),
  KEY `LangDel` (`Deleted`,`Lang`)
) ENGINE=MyISAM AUTO_INCREMENT=875017 …;
```

Замечания:
- `BookId` достигает ~875 000 → ~875 тыс. книг в дампе.
- `md5` бинарный (32 байта) — это **HEX-строка md5**, не сырые байты (по данным видно: `'fb9ecbecf4b943336ac836202c98fdb3'`).
- Никаких полнотекстовых индексов нет; всё B-tree.

### 2.2. `libavtorname` — Автор / Переводчик (одна таблица для обеих ролей)

```sql
CREATE TABLE `libavtorname` (
  `AvtorId` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `FirstName`  varchar(99) NOT NULL DEFAULT '',
  `MiddleName` varchar(99) NOT NULL DEFAULT '',
  `LastName`   varchar(99) NOT NULL DEFAULT '',
  `NickName`   varchar(33) NOT NULL DEFAULT '',
  `uid`        int(11)     NOT NULL DEFAULT 0,
  `Email`      varchar(255) NOT NULL,
  `Homepage`   varchar(255) NOT NULL,
  `Gender`     char(1)     NOT NULL DEFAULT '',
  `MasterId`   int(10)     NOT NULL DEFAULT 0,   -- если != 0, то это «дубль» автора MasterId
  PRIMARY KEY (`AvtorId`),
  KEY `FirstName` (`FirstName`(20)),
  KEY `LastName`  (`LastName`(20)),
  KEY `email`     (`Email`),
  KEY `Homepage`  (`Homepage`),
  KEY `uid`       (`uid`),
  KEY `MasterId`  (`MasterId`)
) AUTO_INCREMENT=339981;
```

- `MasterId` — само-ссылка для дедупликации авторов («Дик / Филип Кайндред Дик / Филип К. Дик» → все указывают на одного `MasterId`).
- Особые значения: `AvtorId=1` — «Коллектив авторов», 2–7 — «Авторский коллектив / Сборник / Разные».

### 2.3. `libavtor` — связь книга ↔ автор

```sql
CREATE TABLE `libavtor` (
  `BookId`  int(10) unsigned NOT NULL DEFAULT 0,
  `AvtorId` int(10) unsigned NOT NULL DEFAULT 0,
  `Pos`     tinyint(4) unsigned NOT NULL DEFAULT 0,   -- порядок соавтора
  PRIMARY KEY (`BookId`,`AvtorId`),
  KEY `iav` (`AvtorId`)
);
```

### 2.4. `libtranslator` — связь книга ↔ переводчик

```sql
CREATE TABLE `libtranslator` (
  `BookId`       int(10) unsigned NOT NULL,
  `TranslatorId` int(10) unsigned NOT NULL,    -- ссылается на libavtorname.AvtorId
  `Pos`          tinyint(4) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`BookId`,`TranslatorId`),
  KEY `TranslatorId` (`TranslatorId`)
);
```

### 2.5. `libseqname` — Серия (цикл)

```sql
CREATE TABLE `libseqname` (
  `SeqId`   int(10) unsigned NOT NULL AUTO_INCREMENT,
  `SeqName` varchar(254) NOT NULL DEFAULT '',
  PRIMARY KEY (`SeqId`),
  UNIQUE KEY `SeqName_2` (`SeqName`)
) AUTO_INCREMENT=110492 COMMENT='Список форм (1-100) и названий сериа…';
```

- Спец-серии (SeqId 1..7): «Романы», «Повести», «Рассказы», «Эссе», «Поэмы», «Стихотворения», «Публицистика» — это **псевдо-серии для типов жанровых форм**.

### 2.6. `libseq` — связь книга ↔ серия

```sql
CREATE TABLE `libseq` (
  `BookId`  int(11) NOT NULL,
  `SeqId`   int(11) NOT NULL,
  `SeqNumb` int(11) NOT NULL,             -- номер в серии (0 если без номера)
  `Level`   tinyint(4) NOT NULL DEFAULT 0,-- уровень вложенности под-серии
  `Type`    tinyint(1) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`BookId`,`SeqId`),
  KEY `SeqId` (`SeqId`)
);
```

### 2.7. `libgenrelist` — справочник жанров (СИДОВЫЕ ДАННЫЕ)

```sql
CREATE TABLE `libgenrelist` (
  `GenreId`   int(10) unsigned NOT NULL AUTO_INCREMENT,
  `GenreCode` varchar(45) NOT NULL DEFAULT '',  -- sf_history, prose_classic, …
  `GenreDesc` varchar(99) NOT NULL DEFAULT '',  -- «Альтернативная история», «Классическая проза», …
  `GenreMeta` varchar(45) NOT NULL DEFAULT '',  -- «Фантастика», «Проза», «Любовные романы», … — раздел верхнего уровня
  PRIMARY KEY (`GenreId`,`GenreCode`),
  UNIQUE KEY `GenreCode` (`GenreCode`),
  KEY `meta` (`GenreMeta`)
) AUTO_INCREMENT=298;
```

- Всего ~298 жанров. Используется иерархия `GenreMeta → GenreCode/GenreDesc`.
- Примеры данных:
  ```
  (1,'sf_history','Альтернативная история','Фантастика')
  (12,'sf','Научная фантастика','Фантастика')
  (25,'prose','Проза','Проза')
  (293,'sci_psychology_popular','Популярная психология','Дом и семья')
  ```

### 2.8. `libgenre` — связь книга ↔ жанр

```sql
CREATE TABLE `libgenre` (
  `Id`      int(10) unsigned NOT NULL AUTO_INCREMENT,
  `BookId`  int(10) unsigned NOT NULL DEFAULT 0,
  `GenreId` int(10) unsigned NOT NULL DEFAULT 0,
  PRIMARY KEY (`Id`),
  UNIQUE KEY `u` (`BookId`,`GenreId`),
  KEY `igenre` (`GenreId`),
  KEY `ibook`  (`BookId`)
) AUTO_INCREMENT=1661655;
```

### 2.9. `libgenretranslate` — миграция устаревших кодов жанров (СИДОВЫЕ ДАННЫЕ)

```sql
CREATE TABLE `libgenretranslate` (
  `srcGenreCode` varchar(45) NOT NULL,
  `trgGenreCode` varchar(45) NOT NULL,
  PRIMARY KEY (`srcGenreCode`)
);
```

Примеры маппингов: `fantasy → sf_fantasy`, `comp_db → comp_db`, `entert_humor → home_entertain`, `psy_generic → sci_psychology`. Используется при импорте fb2/inpx, где в дескрипторе встречается старый код.

### 2.10. `libfilename` — оригинальное имя файла (для не-fb2)

```sql
CREATE TABLE `libfilename` (
  `BookId`   int(11) NOT NULL,
  `FileName` varchar(255) NOT NULL,
  PRIMARY KEY (`BookId`),
  UNIQUE KEY `FileName` (`FileName`)
);
```

Когда книга лежит в архиве с предсказуемым именем (`fb2-<BookId>.zip`), записи нет. Запись появляется для PDF/DJVU/EPUB/DOC/TXT/RTF/HTML, у которых имя «человеческое»: `George Kristof Lihtenberg_Lihtenberg G. K. Aforizmy.djvu`.

### 2.11. `libjoinedbooks` — история слияния дублей

```sql
CREATE TABLE `libjoinedbooks` (
  `Id`     int(11) NOT NULL AUTO_INCREMENT,
  `Time`   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `BadId`  int(11) NOT NULL DEFAULT 0,   -- BookId, объявленный дублем
  `GoodId` int(11) NOT NULL DEFAULT 0,   -- BookId, который оставили
  `realId` int(11) DEFAULT NULL,         -- актуальный BookId (после цепочки слияний)
  PRIMARY KEY (`Id`),
  UNIQUE KEY `BadId` (`BadId`),
  KEY `Time` (`Time`),
  KEY `GoodId` (`GoodId`),
  KEY `realId` (`realId`)
);
```

### 2.12. `librate` — рейтинг

```sql
CREATE TABLE `librate` (
  `ID`     int(11) NOT NULL AUTO_INCREMENT,
  `BookId` int(11) NOT NULL,
  `UserId` int(11) NOT NULL,
  `Rate`   char(1) NOT NULL,          -- '1'..'5'
  PRIMARY KEY (`ID`),
  UNIQUE KEY `BookId` (`BookId`,`UserId`),
  KEY `UserId`  (`UserId`,`Rate`),
  KEY `BookId1` (`BookId`)
);
```

### 2.13. `librecs` — рекомендации / избранное

```sql
CREATE TABLE `librecs` (
  `id`        int(11) unsigned NOT NULL AUTO_INCREMENT,
  `uid`       int(11) DEFAULT NULL,
  `bid`       int(11) DEFAULT NULL,
  `timestamp` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `bu` (`bid`,`uid`)
);
```

### 2.14. `libreviews` — отзывы

```sql
CREATE TABLE `libreviews` (
  `Name`   varchar(255) NOT NULL,                                   -- ник автора (часто пустой)
  `Time`   timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `BookId` int(10) unsigned NOT NULL,
  `Text`   text NOT NULL
);
```

В таблице **нет первичного ключа и индексов** — будет проблема при импорте; нужно добавить `id BIGSERIAL PRIMARY KEY` и `INDEX(BookId, Time)`.

### 2.15. `libbannotations` / `libaannotations` — аннотации

```sql
CREATE TABLE `libbannotations` (
  `BookId` int(10) unsigned NOT NULL,
  `nid`    int(10) unsigned NOT NULL,    -- технический id записи (Drupal node-id)
  `Title`  varchar(255) NOT NULL,
  `Body`   longtext                       -- HTML/BBCode
) ENGINE=InnoDB;
-- (PK/UNIQUE отсутствуют!)

CREATE TABLE `libaannotations` (
  `AvtorId` int(10) unsigned NOT NULL,
  `nid`     int(10) unsigned NOT NULL,
  `Title`   varchar(255) NOT NULL,
  `Body`    longtext
) ENGINE=InnoDB;
```

`Body` — это смесь HTML и BB-кодов (`<strong>`, `[b]`, `[url=…]`).

### 2.16. `libbpics` / `libapics` — картинки

```sql
CREATE TABLE `libbpics` (
  `BookId` int(10) unsigned NOT NULL,
  `nid`    int(10) unsigned NOT NULL,
  `File`   varchar(255) NOT NULL    -- '38/25538/dakin.jpg' — путь относительно базового каталога обложек
) ENGINE=InnoDB;

CREATE TABLE `libapics` (
  `AvtorId` int(10) unsigned NOT NULL,
  `nid`     int(10) unsigned NOT NULL,
  `File`    varchar(255) NOT NULL
) ENGINE=InnoDB;
```

Опять же — **нет PK/UNIQUE**, формально допускает дубли. При миграции в PG надо добавить `PK (BookId, nid)`.

---

## 3. ER-обзор и связи

```
                       libgenrelist (~298 жанров, SEED)
                              │ 1
                              │
                              │ N
   libavtor   libavtorname    │      libgenre        libseq      libseqname
  (M:N связь)─►(автор/перев.)◄┘    (M:N связь)     (M:N связь)─►(серия)
        ▲           ▲                     │              │
        │           │                     │              │
        │           │ N                   │              │
        │           │                     ▼              ▼
        └──────────►libbook◄──────────────────────────────────
                       ▲  (Книга, ~875k записей)
                       │
        ┌──────────────┼──────────────────────────────┐
        │              │              │               │
   libtranslator   libfilename   libbannotations  libbpics
   (M:N перевод.)  (1:1 файл)    (1:N аннотации)  (1:N картинки)
        │
        │
   libavtorname (та же таблица, что и для авторов!)

   librate (UserId, BookId → 1..5)
   librecs (uid, bid → timestamp)
   libreviews (BookId → free-text)
   libjoinedbooks (BadId → GoodId → realId)        — таблица слияния дублей

   libgenretranslate (srcCode → trgCode, SEED)     — миграция старых кодов
```

**Главные сущности предметной области, выраженные в схеме:**

| Сущность | Таблица | Связи |
| --- | --- | --- |
| Книга | `libbook` | M:N → авторы, переводчики, жанры, серии; 1:N → аннотации, обложки, отзывы, рейтинги, рекомендации |
| Автор | `libavtorname` | M:N → книги (через `libavtor`); 1:N → аннотации `libaannotations`, фото `libapics`; self-ref `MasterId` (дубль) |
| Переводчик | `libavtorname` (та же!) | M:N → книги (через `libtranslator`) |
| Серия / цикл | `libseqname` | M:N → книги (через `libseq` с `SeqNumb` и `Level`) |
| Жанр | `libgenrelist` | M:N → книги (через `libgenre`); 1:N → миграция кодов |
| Файл / формат | `libbook.FileType` + `libfilename` | `FileType` хранит расширение (fb2/pdf/djvu/epub/doc/txt/rtf/html). Отдельной таблицы «формат» нет. |
| Язык | `libbook.Lang` / `SrcLang` | Inline-поле, отдельной таблицы нет. |
| Обложка | `libbpics` | 1:N → книга |

Чего в схеме **нет**, но нужно для нового проекта:
- Пользователи (есть только `UserId` числом — без своей таблицы).
- Списки книг пользователя (нужно для функции «публичные списки + QR-код»).
- Архив `.zip` и `.inpx` (нет таблиц для отслеживания исходных архивов).
- Полнотекстовый индекс.

---

## 4. inpx-специфика

Файлы inpx (LibRusEc / Flibusta / LibGen) — это плоский CSV-индекс книг внутри zip-архивов. Структура одной inp-строки (упрощённо):

```
AUTHOR;GENRE;TITLE;SERIES;SERNO;FILE;SIZE;LIBID;DEL;EXT;DATE;LANG;LIBRATE;KEYWORDS
```

Сопоставление с существующей схемой:

| Поле inpx | Куда мапится |
| --- | --- |
| `AUTHOR` (Фамилия,Имя,Отчество:...:) | `libavtorname` + `libavtor` |
| `GENRE` (sf_history:detective:) | `libgenre` + `libgenrelist.GenreCode` |
| `TITLE` | `libbook.Title` |
| `SERIES`, `SERNO` | `libseqname` + `libseq.SeqNumb` |
| `FILE` (имя fb2 в архиве) | обычно `<BookId>.fb2` (тогда записи в `libfilename` нет) или произвольное → `libfilename.FileName` |
| `SIZE` | `libbook.FileSize` |
| `LIBID` | `libbook.BookId` (это и есть libgen/flibusta id) |
| `DEL` | `libbook.Deleted` ('0'/'1') |
| `EXT` | `libbook.FileType` |
| `DATE` | `libbook.Time` / `Modified` |
| `LANG` | `libbook.Lang` |
| `LIBRATE` | в `librate` НЕ маппится напрямую (там user-specific); inpx-rate — это усреднённый |
| `KEYWORDS` | `libbook.keywords` |

Дополнительно из fb2-дескриптора, чего нет в inp, но есть в схеме:
- `md5` файла → `libbook.md5` (UNIQUE!);
- `pmd5` (md5 «нормализованного» содержимого) → `libbook.pmd5`;
- `FileAuthor` — никнейм заливщика fb2;
- `Ver` — версия fb2;
- `Pages`, `Chars` — статистика;
- `Encoding`, `SrcLang`, `LangEx`, `InfoCode`, `N` — внутренние.

В схеме нет полей:
- имени самого zip/inpx-архива;
- offset внутри архива;
- hash самого архива;
- идентификатора «коллекции» (например, fb2-2024-12 vs flibusta-202301).

Это нужно будет добавить при импорте, если приложение должно различать несколько источников inpx. Минимальный апгрейд:

```sql
ALTER TABLE book
  ADD COLUMN archive_name varchar(255),     -- 'fb2-001-010.zip'
  ADD COLUMN inpx_source  varchar(64),      -- 'flibusta-2024-12'
  ADD COLUMN libgen_id    bigint;           -- для интеграции с LibGen ID, если отличается от BookId
```

`libbook.BookId` уже исторически = LibRusEc/Flibusta libid, поэтому для inpx с того же источника можно использовать его напрямую как естественный ключ; для других источников нужен `libgen_id` отдельным полем + уникальность по `(inpx_source, libgen_id)`.

---

## 5. Полнотекстовый поиск

**В исходной MySQL-схеме его НЕТ.** Никаких `tsvector`, GIN, FULLTEXT (MyISAM FULLTEXT тоже не объявлен), триггеров на обновление, refresh.

Есть только обычные B-tree индексы по:
- `libbook.Title`, `libbook.Title1`, `libbook.FileAuthor` (полное значение поля);
- `libavtorname.FirstName(20)`, `libavtorname.LastName(20)` — префиксные;
- `libseqname.SeqName` (UNIQUE);
- `libgenrelist.GenreCode` (UNIQUE).

Это значит, что поиск типа «фрагмент в названии или аннотации» в дампе работает плохо: либо `LIKE 'x%'` (по B-tree), либо полный скан.

**Для нового PostgreSQL-проекта надо добавить:**

```sql
ALTER TABLE book ADD COLUMN search_tsv tsvector;

UPDATE book SET search_tsv =
    setweight(to_tsvector('russian', coalesce(title,'')),  'A') ||
    setweight(to_tsvector('russian', coalesce(title1,'')), 'B') ||
    setweight(to_tsvector('russian', coalesce(keywords,'')),'C') ||
    setweight(to_tsvector('russian', coalesce(annotation,'')),'D');

CREATE INDEX idx_book_search_tsv ON book USING GIN (search_tsv);

-- Триггер на UPDATE/INSERT (через tsvector_update_trigger или собственный):
CREATE TRIGGER trg_book_search_tsv
BEFORE INSERT OR UPDATE OF title, title1, keywords, annotation
ON book FOR EACH ROW
EXECUTE FUNCTION tsvector_update_trigger(search_tsv, 'pg_catalog.russian',
                                        title, title1, keywords, annotation);
```

Для смешанного русско-английского контента (а в дампе ~25 % книг английских) рекомендуется делать **два tsvector-поля**: `tsv_ru` (config `russian`) и `tsv_en` (config `english`) либо использовать `simple` + кастомный словарь. Также можно отдельно индексировать ФИО автора и серию — это пригодится для facet-поиска.

---

## 6. Seed-данные

Полностью seed-данными являются:

1. **`lib.libgenrelist.sql`** — 298 строк-жанров (`sf_history`, `sf`, `prose_classic`, `prose_contemporary`, `love_history`, `child_*`, `sci_*`, `home_*`, `nonfiction`, `home_pets`, и т.д.) с разделом верхнего уровня (`GenreMeta`).
2. **`lib.libgenretranslate.sql`** — таблица миграции устаревших кодов жанров на актуальные (десятки строк: `fantasy → sf_fantasy`, `entert_comics → home_entertain` и т.п.).

Остальные файлы — это **живые данные**, не seed:
- `libavtorname` — ~340 000 авторов;
- `libseqname` — ~110 500 серий;
- `libbook` — ~875 000 книг;
- `libavtor`, `libgenre`, `libseq`, `libtranslator` — связки;
- `librate`, `librecs`, `libreviews`, `libjoinedbooks`, `libfilename`, аннотации, картинки — пользовательские/импортные данные.

Для нового проекта **обязательно нужно сидировать**:
- `libgenrelist` (298 жанров) — это стандартный словарь FB2-жанров, без которого inpx не разберётся;
- `libgenretranslate` — нужен для импорта старых fb2/inpx.

Опционально пересеять `libseqname` (110k серий) и `libavtorname` (340k авторов) сразу из дампа — это сэкономит первичный импорт inpx.

---

## 7. Индексы и что они оптимизируют

| Индекс | Таблица | Цель |
| --- | --- | --- |
| `PRIMARY (BookId)` | `libbook` | первичный поиск по id |
| `UNIQUE md5` | `libbook` | дедупликация по md5 файла при импорте |
| `UNIQUE BookDel (Deleted,BookId)` | `libbook` | фильтрация активных книг + пагинация |
| `KEY Title`, `Title1` | `libbook` | поиск по префиксу названия |
| `KEY Year` | `libbook` | фильтр по году |
| `KEY FileType`, `FileTypeDel` | `libbook` | фильтр «только fb2» / «только pdf» |
| `KEY Lang`, `LangDel` | `libbook` | фильтр по языку |
| `KEY FileSize`, `FileAuthor`, `N`, `Deleted` | `libbook` | вспомогательные сортировки/фильтры |
| `UNIQUE u (BookId,GenreId)` + `KEY igenre/ibook` | `libgenre` | M:N навигация в обе стороны |
| `PK (BookId,AvtorId)` + `KEY iav` | `libavtor` | M:N навигация |
| `KEY FirstName(20)`, `LastName(20)` | `libavtorname` | префиксный поиск автора |
| `KEY MasterId` | `libavtorname` | поиск дублей |
| `PK (BookId,SeqId)` + `KEY SeqId` | `libseq` | книги в серии / серии книги |
| `UNIQUE SeqName_2` | `libseqname` | дедупликация серий по имени |
| `UNIQUE GenreCode` + `KEY meta` | `libgenrelist` | поиск по коду жанра и группировка по разделу |
| `UNIQUE FileName` | `libfilename` | защита от коллизий имён |
| `UNIQUE BadId` + `KEY GoodId, realId, Time` | `libjoinedbooks` | разрешение цепочек дублей |
| `UNIQUE (BookId,UserId)` + `KEY (UserId,Rate)` | `librate` | один рейтинг на пару + top-by-user |
| `UNIQUE (bid,uid)` | `librecs` | один like на пару |

Чего НЕТ:
- индекса по `BookId` в `libreviews` (нужен);
- PK/UNIQUE в `libbannotations/libaannotations/libbpics/libapics`;
- FULLTEXT/GIN для поиска по тексту;
- внешних ключей (целостность поддерживалась приложением).

---

## 8. Схемы (`CREATE SCHEMA`) и namespaces

`CREATE SCHEMA` нет ни в одном файле. Все таблицы — в database `library` (это указано в шапке дампа: `-- Host: slave-db    Database: library`).

В PostgreSQL-проекте можно (и стоит) положить всё в отдельную схему, например `public.book`, `public.author`, …, или сделать `CREATE SCHEMA library` и работать в ней. Spring Boot настраивается через `spring.jpa.properties.hibernate.default_schema=library`.

---

## 9. Замечания и потенциальные риски

1. **Это MySQL/MariaDB дамп, а целевая БД — PostgreSQL.** Нужно вручную транслировать:
   - `int(10) unsigned NOT NULL AUTO_INCREMENT` → `BIGSERIAL` / `INTEGER GENERATED ALWAYS AS IDENTITY`;
   - `binary(32)` для md5 → `CHAR(32)` или `BYTEA(16)` (с учётом того, что в дампе лежит **HEX-строка**, не сырые байты, — это тонкость, можно ошибиться);
   - `varchar(...) CHARACTER SET utf8 COLLATE utf8_unicode_ci` → `text` / `varchar(...)` без collation;
   - `timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` → нужен триггер;
   - `ENGINE=MyISAM/InnoDB` — игнорировать;
   - tinyint(1) → `smallint` или `boolean`;
   - `char(1) Deleted '0'/'1'` → `boolean deleted DEFAULT false`.
2. **Md5 хранится как HEX-строка в `binary(32)`** — нужно осторожно при миграции, иначе можно поломать UNIQUE.
3. **Нет PK в `libreviews`, `libbannotations`, `libaannotations`, `libbpics`, `libapics`** — в JPA это плохо. Нужно добавить суррогатные `id BIGSERIAL` или составные `(BookId, nid)`.
4. **Авторы и переводчики — одна таблица.** В JPA это лучше выразить как одну сущность `Person` и две связи (`@ManyToMany BookAuthor` + `@ManyToMany BookTranslator`).
5. **Дубли авторов (`MasterId`).** В новом проекте надо либо сразу схлопывать на импорте, либо хранить флаг is_alias + ссылку на master.
6. **Слияние книг (`libjoinedbooks`).** Это историческая таблица для бесшовной редиректы старых ссылок. Можно либо сохранить как есть (`book_alias`), либо переделать в `book.canonical_book_id`.
7. **Объём данных большой:** 875k книг, 340k авторов, 110k серий, ~1.66M связок книга-жанр, ~3M записей рейтинга. Импорт в PG — это часы, нужен `COPY` + отключение индексов на время загрузки.
8. **Аннотации в HTML+BBcode.** Нужно нормализовать (только HTML или только Markdown).
9. **Картинки хранятся как путь к файлу (`'38/25538/dakin.jpg'`)** — двухуровневая хэш-структура файловой системы. В новом приложении нужно либо сохранить эту схему, либо положить в S3/MinIO и переделать поле `path` на URL/key.
10. **Полнотекстовый поиск нужно проектировать с нуля.** Кандидаты: PostgreSQL `tsvector + GIN`, либо отдельный поисковый движок (Meilisearch, OpenSearch). Schema под `tsvector` лучше предусмотреть в JPA через `@Column(columnDefinition = "tsvector")` + миграция Flyway, добавляющая GIN-индекс и триггер.
11. **inpx-импорт.** В исходной схеме нет поля «исходный архив». Нужно добавить `import_batch_id`, `archive_name`, чтобы можно было пере-импортировать или удалять источник.
12. **Списки пользователя + QR-share** — этих сущностей в дампе нет вообще, их надо проектировать с нуля (`user_book_list`, `book_list_item`, `share_token`).
13. **Нет внешних ключей** — в JPA это не страшно, но при миграции данных надо проверять «висячие» строки (например, `libgenre.GenreId`, которого нет в `libgenrelist`).
14. **Один автор может иметь несколько записей в `libavtorname` с `MasterId`-связями.** При построении агрегатов («книги автора») нужно учитывать `MasterId`, иначе будет частичный результат.

---

## 10. Рекомендации по проектированию JPA-сущностей

Минимальный набор JPA-сущностей для нового проекта (схема `book`):

- `Book` (`books`) — соответствует `libbook`. Добавить `tsv_search` (`@Column(columnDefinition="tsvector")`), `archive_name`, `inpx_source`, `cover_path`, поля для конвертации форматов (`available_formats` через отдельную таблицу `book_format` или JSON-массив).
- `Author` / `Person` (`persons`) — соответствует `libavtorname`. Поле `master_id` (self-ref). Можно завести `role`-связки.
- `BookAuthor` (`book_authors`, M:N) — соответствует `libavtor` (с `position`).
- `BookTranslator` (`book_translators`, M:N) — соответствует `libtranslator`.
- `Genre` (`genres`) — `libgenrelist`. Поле `meta_category` (раздел).
- `BookGenre` (`book_genres`) — `libgenre`.
- `GenreAlias` (`genre_aliases`) — `libgenretranslate`.
- `Series` (`series`) — `libseqname`.
- `BookSeries` (`book_series`) — `libseq` (с `number`, `level`, `type`).
- `BookFile` (`book_files`) — расширенная `libfilename` + информация о формате: `format`, `size`, `md5`, `archive_name`, `path_in_archive`, `mime`.
- `BookAnnotation` (`book_annotations`) — `libbannotations` (HTML).
- `BookCover` (`book_covers`) — `libbpics`.
- `AuthorAnnotation`, `AuthorPicture` — `libaannotations`, `libapics`.
- `BookRating` (`book_ratings`) — `librate` (uniq `(book_id, user_id)`).
- `BookFavorite` (`book_favorites`) — `librecs`.
- `BookReview` (`book_reviews`) — `libreviews` + добавить `id`, `user_id`, `parent_id` для тредов.
- `BookAlias` (`book_aliases`) — `libjoinedbooks`.

Новые сущности (нет в исходной схеме):
- `AppUser` / `AccountUser` — пользователи приложения.
- `BookList` (`book_lists`) и `BookListItem` (`book_list_items`) — списки книг.
- `BookListShare` (`book_list_shares`) — публичные ссылки + поле `qr_token` (UUID/short-id) для QR.
- `ImportJob` (`import_jobs`) — статус импорта zip/inpx/fb2.
- `ConversionJob` (`conversion_jobs`) — конвертация форматов (fb2 → epub/pdf/mobi).

---

## Источник

Все данные извлечены из:
- `sql/lib.libbook.sql`
- `sql/lib.libavtor.sql`
- `sql/lib.libavtorname.sql`
- `sql/lib.libgenre.sql`
- `sql/lib.libgenrelist.sql`
- `sql/lib.libgenretranslate.sql`
- `sql/lib.libseq.sql`
- `sql/lib.libseqname.sql`
- `sql/lib.libtranslator.sql`
- `sql/lib.libfilename.sql`
- `sql/lib.libjoinedbooks.sql`
- `sql/lib.librate.sql`
- `sql/lib.librecs.sql`
- `sql/lib.reviews.sql`
- `sql/lib.a.annotations.sql`
- `sql/lib.b.annotations.sql` (DDL не удалось дочитать целиком из-за размера файла, структура восстановлена по аналогии с `libaannotations` и образцам данных)
- `sql/lib.a.annotations_pics.sql`
- `sql/lib.b.annotations_pics.sql`
