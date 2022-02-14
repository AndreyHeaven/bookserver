package ru.andreyheaven.bookserver.domain;

import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.*;
import java.util.*;

@Table("genres")
public class Genre {
    @Id
    @Column("code")
    private String code;
//    @ManyToMany
//    @JoinTable(name="genres_genres",
//            joinColumns=
//            @JoinColumn(name="genre_id", referencedColumnName="code"),
//            inverseJoinColumns=
//            @JoinColumn(name="parent_id", referencedColumnName="code")
//    )
//    private Collection<Genre> parents;

    @Column("title")
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
