package ru.andreyheaven.bookserver.repository;

import org.springframework.cache.annotation.*;
import org.springframework.data.jdbc.repository.query.*;
import org.springframework.data.repository.*;
import org.springframework.data.repository.query.*;
import org.springframework.data.util.*;
import ru.andreyheaven.bookserver.domain.*;
import java.util.*;

public interface AuthorRepository extends CrudRepository<Author, Integer> {

    @Query(value = """
            select "left"(upper(a.surname), :prefixLength) as ch, count(*) as count
            from authors a
            where upper(a.surname) like :prefix
            group by ch
            order by ch;
            """)
    List<AuthorCount> findPrefixes_(@Param("prefix") String prefix, @Param("prefixLength") Integer prefixLength);

    @Cacheable("author_index")
    default List<Pair<String, Integer>> findPrefixes(String prefix) {
        return findPrefixes_(prefix + "%", prefix.length() + 1).stream()
                .map(stringObjectMap -> Pair.of(stringObjectMap.getCh(), stringObjectMap.getCount())).toList();
    }

    @Query("select * from authors where upper(surname) like :prefix order by surname")
    List<Author> findBySurnameStartsWith_(String prefix);

    @Cacheable("authors")
    default List<Author> findBySurnameStartsWith(String prefix) {
        return findBySurnameStartsWith_(prefix + "%");
    }

    @Override
    @Cacheable("author")
    Optional<Author> findById(Integer id);

    class AuthorCount {
        private String ch;
        private Integer count;

        public AuthorCount(String ch, Integer count) {
            this.ch = ch;
            this.count = count;
        }

        public String getCh() {
            return ch;
        }

        public Integer getCount() {
            return count;
        }
    }
}
