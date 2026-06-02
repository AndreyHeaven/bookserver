package com.example.bookserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class BookListItemId implements Serializable {

    @Column(name = "list_id")
    private Long listId;

    @Column(name = "book_id")
    private Long bookId;

    public BookListItemId() {}
    public BookListItemId(Long listId, Long bookId) {
        this.listId = listId;
        this.bookId = bookId;
    }

    public Long getListId() { return listId; }
    public void setListId(Long listId) { this.listId = listId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookListItemId that)) return false;
        return Objects.equals(listId, that.listId) && Objects.equals(bookId, that.bookId);
    }
    @Override public int hashCode() { return Objects.hash(listId, bookId); }
}
