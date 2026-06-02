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
@Table(name = "book_list_items")
public class BookListItem {

    @EmbeddedId
    private BookListItemId id = new BookListItemId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("listId")
    @JoinColumn(name = "list_id")
    private BookList list;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("bookId")
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(nullable = false)
    private int position;

    public BookListItem() {}
    public BookListItem(BookList list, Book book, int position) {
        this.list = list;
        this.book = book;
        this.position = position;
        this.id = new BookListItemId(list.getId(), book.getId());
    }

    public BookListItemId getId() { return id; }
    public void setId(BookListItemId id) { this.id = id; }
    public BookList getList() { return list; }
    public void setList(BookList list) { this.list = list; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookListItem that)) return false;
        return Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
