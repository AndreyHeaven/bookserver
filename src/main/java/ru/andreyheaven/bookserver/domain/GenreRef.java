package ru.andreyheaven.bookserver.domain;

import org.springframework.data.relational.core.mapping.*;

@Table("books_genres")
public class GenreRef {
    @Column("genre_id")
    private String genreCode;

    public GenreRef(String genreCode) {
        this.genreCode = genreCode;
    }
}
