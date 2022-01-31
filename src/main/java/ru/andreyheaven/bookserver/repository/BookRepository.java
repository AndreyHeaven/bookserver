package ru.andreyheaven.bookserver.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.*;
import org.springframework.data.repository.query.*;
import ru.andreyheaven.bookserver.domain.*;
import java.util.*;

public interface BookRepository extends CrudRepository<Book, Integer> {

    @Query("select b from Book b join fetch b.authors a where a.id = :id")
    List<Book> findByAuthorsContains(@Param("id") Integer id);

    @Query("select b.id from Book b")
    List<Integer> findAllIds();

}
