package ru.andreyheaven.bookserver.domain;

import org.springframework.data.relational.core.mapping.*;

@Table("books_authors")
public class AuthorRef {
    @Column("author_id")
    private Integer authorId;

    public AuthorRef(Integer authorId) {
        this.authorId = authorId;
    }
}
