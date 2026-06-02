package com.example.bookserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "book_authors")
public class BookAuthor {

    @EmbeddedId
    private BookAuthorId id = new BookAuthorId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("bookId")
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("personId")
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(nullable = false)
    private int position;

    public BookAuthor() {}
    public BookAuthor(Book book, Person person, int position) {
        this.book = book;
        this.person = person;
        this.position = position;
        this.id = new BookAuthorId(book.getId(), person.getId());
    }

    public BookAuthorId getId() { return id; }
    public void setId(BookAuthorId id) { this.id = id; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookAuthor that)) return false;
        return Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
