CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE languages
(
    id   INTEGER PRIMARY KEY,
    code TEXT NOT NULL,
    fb2  TEXT,
    UNIQUE (code)
);

CREATE INDEX languages_idx ON languages (code);

create table genres
(
    code  text not null
        constraint genres_pk
            primary key,
    title text
);

create table genres_genres
(
    genre_id  text
        constraint genres_genres_genres_code_fk references genres (code),
    parent_id text
        constraint genres_genres_parent_code_fk references genres (code)
);

create table authors
(
    id         int not null GENERATED BY DEFAULT AS IDENTITY
        constraint authors_pk
            primary key,
    surname    text not null,
    name       text,
    patronymic text
);

create table books
(
    id                int  not null
        constraint books_pk
            primary key,
    title             text not null,
    series_id          text,
    seq_number         text,
    isbn              text,
    file_id           text,
    archive           text,
    lang              text,
    format            text,
    pubdate           date,
    is_deleted        boolean,
    uncompressed_size int
);

create table books_authors
(
    book_id   int
        constraint books_authors_book_id_fk references books (id),
    author_id int
        constraint books_authors_author_id_fk references authors (id)
);

create table books_genres
(
    book_id  int
        constraint books_genres_book_id_fk references books (id),
    genre_id text
        constraint books_genres_genre_id_fk references genres (code)
);
