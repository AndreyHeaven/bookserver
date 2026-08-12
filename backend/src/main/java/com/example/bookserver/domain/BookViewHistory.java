package com.example.bookserver.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "book_view_history", uniqueConstraints = @UniqueConstraint(name = "uk_book_view_history_user_book", columnNames = {"user_id", "book_id"}), indexes = @Index(name = "ix_book_view_history_user_viewed", columnList = "user_id, viewed_at"))
public class BookViewHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "book_id", nullable = false)
    private Book book;
    @Column(name = "viewed_at", nullable = false)
    private OffsetDateTime viewedAt;
    public Long getId() { return id; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
    public OffsetDateTime getViewedAt() { return viewedAt; }
    public void setViewedAt(OffsetDateTime viewedAt) { this.viewedAt = viewedAt; }
}
