package ru.andreyheaven.bookserver.repository;

import org.springframework.cache.annotation.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.*;
import org.springframework.data.repository.query.*;
import org.springframework.data.util.*;
import ru.andreyheaven.bookserver.domain.*;
import java.math.*;
import java.util.*;
import java.util.stream.*;

public interface AuthorRepository extends CrudRepository<Author, Integer> {

    @Query(value = """
            select "left"(upper(a.surname), :prefixLength) as ch, count(*) as count
            from authors a
            where upper(a.surname) like :prefix
            group by ch
            order by ch;
            """, nativeQuery = true)
    List<Map<String, Object>> findPrefixes_(@Param("prefix") String prefix, @Param("prefixLength") Integer prefixLength);

    @Cacheable("author_index")
    default List<Pair<String, Long>> findPrefixes(String prefix) {
        return findPrefixes_(prefix + "%", prefix.length() + 1).stream()
                .map(stringObjectMap -> Pair.of(stringObjectMap.get("ch").toString(), ((BigInteger) stringObjectMap.get("count")).longValue())).collect(Collectors.toList());
    }

    @Query("select a from Author a where upper(a.surname) like :prefix")
    List<Author> findBySurnameStartsWith_(String prefix);

    @Cacheable("authors")
    default List<Author> findBySurnameStartsWith(String prefix) {
        return findBySurnameStartsWith_(prefix + "%");
    }

}
