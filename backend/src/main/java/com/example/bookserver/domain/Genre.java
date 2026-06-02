package com.example.bookserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Genre / sub-genre. Maps to the {@code genres} table (Liquibase 001-001).
 */
@Entity
@Table(name = "genres")
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Genre parent;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(name = "meta_section", length = 128)
    private String metaSection;

    @Column(name = "position")
    private Integer position;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Genre getParent() { return parent; }
    public void setParent(Genre parent) { this.parent = parent; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMetaSection() { return metaSection; }
    public void setMetaSection(String metaSection) { this.metaSection = metaSection; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Genre that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
