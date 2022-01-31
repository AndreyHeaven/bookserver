package ru.andreyheaven.bookserver.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.*;
import org.springframework.data.repository.query.*;
import ru.andreyheaven.bookserver.domain.*;
import java.util.*;
import java.util.stream.*;

public interface AuthorRepository extends CrudRepository<Author, Integer> {

    @Query(value = """
            select "left"(upper(a.surname), :prefixLength) as ch , count(*) as count from books
                join books_authors ba on books.id = ba.book_id
                join authors a on a.id = ba.author_id where upper(a.surname) like :prefix
                group by ch order by ch;
            """, nativeQuery = true)
    List<Map<String, Object>> findPrefixes_(@Param("prefix") String prefix, @Param("prefixLength") Integer prefixLength);

    default Map<String, Long> findPrefixes(String prefix) {
        return findPrefixes_(prefix + "%", prefix.length() + 1).stream()
                .collect(Collectors.toMap(o -> o.get("ch").toString(), v -> (Long)v.get("count")));
    }

    List<Author> findBySurnameStartsWith(String prefix);
}
