package com.example.bookserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class BookSeriesMemberId implements Serializable {

    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "series_id")
    private Long seriesId;

    public BookSeriesMemberId() {}
    public BookSeriesMemberId(Long bookId, Long seriesId) {
        this.bookId = bookId;
        this.seriesId = seriesId;
    }

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookSeriesMemberId that)) return false;
        return Objects.equals(bookId, that.bookId) && Objects.equals(seriesId, that.seriesId);
    }
    @Override public int hashCode() { return Objects.hash(bookId, seriesId); }
}
