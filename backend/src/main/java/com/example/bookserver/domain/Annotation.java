package com.example.bookserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Book annotation (1:1 with {@link Book}). PK is the book's id (shared
 * primary key, via {@code @MapsId}). Maps to {@code annotations} (Liquibase 001-010).
 */
@Entity
@Table(name = "annotations")
public class Annotation {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(columnDefinition = "TEXT")
    private String body;

    public Annotation() {}
    public Annotation(Book book, String body) {
        this.book = book;
        this.body = body;
    }

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Annotation that)) return false;
        return bookId != null && Objects.equals(bookId, that.bookId);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
