package ru.andreyheaven.bookserver.domain;

import javax.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "books")
public class Book {
    @ManyToMany
    @JoinTable(name = "books_authors",
            joinColumns =
            @JoinColumn(name = "book_id", referencedColumnName = "id"),
            inverseJoinColumns =
            @JoinColumn(name = "author_id", referencedColumnName = "id")
    )
    private Collection<Author> authors; // 0

    @ManyToMany
    @JoinTable(name = "books_genres",
            joinColumns =
            @JoinColumn(name = "book_id", referencedColumnName = "id"),
            inverseJoinColumns =
            @JoinColumn(name = "genre_id", referencedColumnName = "code")
    )
    private Collection<Genre> genres; // 1
    private String title; // 2
    private String seriesId; //3
    private String seqNumber; //4
    @Id
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;//5
    @Column(name = "uncompressed_size", nullable = false)
    private Integer uncompressedSize; //6
    @Column(name = "file_id", nullable = false)
    private String fileID;  //7
    private Boolean isDeleted; //8
    private String isbn;
    private String format; //9
    private LocalDate pubdate; //10
//    private String libRate; //11

    @Column(name = "archive", nullable = false)
    private String archive; //12
    private String lang; //13
//    private String folder; //14
//    private String keywords; //15


    public Book() {

    }

    public Book(Collection<Author> authors, Collection<Genre> genres, String title, String seriesId,
                String seqNumber, Integer id, Integer uncompressedSize, String fileID, Boolean isDeleted,
                String format, LocalDate pubdate, String archive, String lang) {
        this.authors = authors;
        this.genres = genres;
        this.title = title;
        this.seriesId = seriesId;
        this.seqNumber = seqNumber;
        this.id = id;
        this.uncompressedSize = uncompressedSize;
        this.fileID = fileID;
        this.isDeleted = isDeleted;
        this.format = format;
        this.pubdate = pubdate;
        this.archive = archive;
        this.lang = lang;
    }

    public Collection<Author> getAuthors() {
        return authors;
    }

    public Collection<Genre> getGenres() {
        return genres;
    }

    public String getTitle() {
        return title;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public String getSeqNumber() {
        return seqNumber;
    }

    public Integer getId() {
        return id;
    }

    public Integer getUncompressedSize() {
        return uncompressedSize;
    }

    public String getFileID() {
        return fileID;
    }

    public Boolean getDeleted() {
        return isDeleted;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getFormat() {
        return format;
    }

    public LocalDate getPubdate() {
        return pubdate;
    }

    public String getArchive() {
        return archive;
    }

    public String getLang() {
        return lang;
    }
}
