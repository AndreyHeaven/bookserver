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
import jakarta.persistence.Transient;

import java.util.Objects;

/**
 * Author / translator. Same entity for both — link to a book via
 * {@link BookAuthor} or {@link BookTranslator}. Maps to {@code persons}
 * (Liquibase 001-002). The {@code fts_tsv} column is maintained by a PG
 * trigger (changeset 002-007/002-008) and therefore not mapped here.
 */
@Entity
@Table(name = "persons")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(name = "first_name", length = 128)
    private String firstName;

    @Column(name = "middle_name", length = 128)
    private String middleName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_id")
    private Person master;

    /** Maintained by PG trigger {@code trg_persons_fts}; never written from Java. */
    @Transient
    private String ftsTsv;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public Person getMaster() { return master; }
    public void setMaster(Person master) { this.master = master; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Person that)) return false;
        return id != null && Objects.equals(id, that.id);
    }
    @Override public int hashCode() { return getClass().hashCode(); }
}
