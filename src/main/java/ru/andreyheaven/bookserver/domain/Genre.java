package ru.andreyheaven.bookserver.domain;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "genres")
public class Genre {
    @Id
    @Column(name = "code")
    private String code;
    @ManyToMany
    @JoinTable(name="genres_genres",
            joinColumns=
            @JoinColumn(name="genre_id", referencedColumnName="code"),
            inverseJoinColumns=
            @JoinColumn(name="parent_id", referencedColumnName="code")
    )
    private Collection<Genre> parents;

    @Column(name = "title")
    private String title;

    public Genre() {
    }

    public Genre(String code) {
        this.code = code;
    }

    public Genre(String code, String title) {
        this(code);
        this.title = title;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }
}
