package ru.andreyheaven.bookserver.domain;

import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.*;
import java.util.*;

@Table("authors")
public class Author {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column("id")
    private Integer id;
    private String surname;
    private String name;
    private String patronymic;

    public Author(String surname) {
        this.surname = clearSurname(surname);
//        this.code = surname;
    }

    public Author(String surname, String name) {
        this(surname);
        this.name = name;
//        this.code = "%s %s".formatted(surname, name);

    }

    public Author(String surname, String name, String patronymic) {
        this(surname, name);
        this.patronymic = patronymic;
//        this.code = "%s %s %s".formatted(surname, name, patronymic);
    }

    public Author() {

    }

    public static Author fromString(String s) {
        final String[] split = s.split(",");
        if (split.length == 3)
            return new Author(split[0], split[1], split[2]);
        else if (split.length == 2)
            return new Author(split[0], split[1]);
        else if (split.length == 1)
            return new Author(split[0]);
        else
            return null;
    }

    private String clearSurname(String surname) {
        if (surname.startsWith("«")) surname = surname.substring(1);
        if (surname.endsWith("»")) surname = surname.substring(0, surname.length() - 1);
        return surname;
    }

    public Integer getId() {
        return id;
    }

    public String getSurname() {
        return surname;
    }

    public String getName() {
        return name;
    }

    public String getPatronymic() {
        return patronymic;
    }

//    public String getCode() {
//        return code;
//    }


    public String getInpCode() {
        return "%s,%s,%s".formatted(surname != null ? surname : "", name != null ? name : "", patronymic != null ? patronymic : "");
    }

    public String getFullName() {
        if (patronymic == null)
            return "%s %s".formatted(surname, name);
        else
            return "%s %s %s".formatted(surname, name, patronymic);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Author author = (Author) o;
        return Objects.equals(surname, author.surname) && Objects.equals(name, author.name) && Objects.equals(patronymic, author.patronymic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(surname, name, patronymic);
    }

    @Override
    public String toString() {
        return "Author{" + surname + " " + name + " " + patronymic + '}';
    }
}
