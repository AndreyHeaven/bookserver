package com.example.bookserver.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Book entity. Maps to {@code books} (Liquibase 001-004). The {@code fts_tsv}
 * tsvector column is maintained by PG triggers (changesets 002-002/003/004/005)
 * and never written from Java — it is therefore declared {@code @Transient}.
 */
@Entity
@Table(name = "books")
@EntityListeners(AuditingEntityListener.class)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(length = 8)
    private String lang;

    @Column
    private Integer year;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_type", length = 16)
    private String fileType;

    @Column(length = 32)
    private String md5;

    @Column(name = "archive_name", length = 512)
    private String archiveName;

    @Column(name = "inpx_source", length = 512)
    private String inpxSource;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    /** Maintained by PG trigger; do not set from Java. */
    @Transient
    private String ftsTsv;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "book_genres",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    private Set<Genre> genres = new HashSet<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookAuthor> authors = new HashSet<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookTranslator> translators = new HashSet<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookSeriesMember> seriesMembers = new HashSet<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BookFile> files = new HashSet<>();

    @OneToOne(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Annotation annotation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getMd5() { return md5; }
    public void setMd5(String md5) { this.md5 = md5; }
    public String getArchiveName() { return archiveName; }
    public void setArchiveName(String archiveName) { this.archiveName = archiveName; }
    public String getInpxSource() { return inpxSource; }
    public void setInpxSource(String inpxSource) { this.inpxSource = inpxSource; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Set<Genre> getGenres() { return genres; }
    public void setGenres(Set<Genre> genres) { this.genres = genres; }
    public Set<BookAuthor> getAuthors() { return authors; }
    public void setAuthors(Set<BookAuthor> authors) { this.authors = authors; }
    public Set<BookTranslator> getTranslators() { return translators; }
    public void setTranslators(Set<BookTranslator> translators) { this.translators = translators; }
    public Set<BookSeriesMember> getSeriesMembers() { return seriesMembers; }
    public void setSeriesMembers(Set<BookSeriesMember> seriesMembers) { this.seriesMembers = seriesMembers; }
    public Set<BookFile> getFiles() { return files; }
    public void setFiles(Set<BookFile> files) { this.files = files; }
    public Annotation getAnnotation() { return annotation; }
    public void setAnnotation(Annotation annotation) { this.annotation = annotation; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
