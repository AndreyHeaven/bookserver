package ru.andreyheaven.bookserver.repository;

import org.springframework.data.jdbc.repository.query.*;
import org.springframework.data.repository.*;
import org.springframework.data.repository.query.*;
import ru.andreyheaven.bookserver.domain.*;
import java.util.*;

public interface BookRepository extends CrudRepository<Book, Integer> {

    @Query("select * from books where :id = any(authors) order by title limit :pageSize offset :offset")
    List<Book> findByAuthor(@Param("id") Integer id, @Param("pageSize") int pageSize, @Param("offset") long offset);

    @Query("select b.id from books b")
    Set<Integer> findAllIds();

    @Query("select * from books where :code = any(genres) order by title limit :pageSize offset :offset")
    List<Book> findByGenre(@Param("code") String genreCode, @Param("pageSize") int pageSize, @Param("offset") long offset);
}
