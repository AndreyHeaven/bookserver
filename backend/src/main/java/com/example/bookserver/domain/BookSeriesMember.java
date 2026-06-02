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
@Table(name = "book_series_members")
public class BookSeriesMember {

    @EmbeddedId
    private BookSeriesMemberId id = new BookSeriesMemberId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("bookId")
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("seriesId")
    @JoinColumn(name = "series_id")
    private Series series;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    public BookSeriesMember() {}
    public BookSeriesMember(Book book, Series series, Integer sequenceNumber) {
        this.book = book;
        this.series = series;
        this.sequenceNumber = sequenceNumber;
        this.id = new BookSeriesMemberId(book.getId(), series.getId());
    }

    public BookSeriesMemberId getId() { return id; }
    public void setId(BookSeriesMemberId id) { this.id = id; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public Series getSeries() { return series; }
    public void setSeries(Series series) { this.series = series; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookSeriesMember that)) return false;
        return Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
